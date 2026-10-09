package ru.itis.diploma.experiment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import com.fasterxml.jackson.databind.JsonNode;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service @Slf4j @RequiredArgsConstructor
public class ExperimentManager {
    private final ExperimentSeriesRepository series;
    private final ExperimentRunRepository runs;
    private final ExperimentEngine engine;
    private final TransactionTemplate transactions;
    private final ObjectMapper mapper;
    @Value("${experiment.enabled:false}") private boolean enabled;
    @Value("${experiment.virtual-threads:true}") private boolean virtualThreads;
    @Value("${strategy.predictor.url:}") private String predictorUrl;
    private final ScheduledExecutorService dispatcher = Executors.newSingleThreadScheduledExecutor();
    // Keep the global number of games bounded even when workers use virtual threads.
    private final java.util.concurrent.Semaphore gameSlots = new java.util.concurrent.Semaphore(10);
    private java.util.concurrent.ExecutorService workers;
    private int nextSeriesOffset;

    public boolean enabled() { return enabled; }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) return;
        workers = virtualThreads ? Executors.newVirtualThreadPerTaskExecutor() : Executors.newFixedThreadPool(10);
        recover();
        dispatcher.scheduleWithFixedDelay(this::dispatchSafe, 0, 1, TimeUnit.SECONDS);
    }

    public void recover() {
        // Supported deployment: exactly one experimental application instance.
        transactions.executeWithoutResult(tx -> {
            for (var group : series.findAll())
                for (var run : runs.findBySeriesIdOrderByNumberDesc(group.getId()))
                    if ("RUNNING".equals(run.getStatus())) run.setStatus("QUEUED");
        });
    }

    @PreDestroy
    public void shutdown() {
        dispatcher.shutdownNow();
        if (workers != null) workers.shutdownNow();
    }

    public ExperimentSeries create(ExperimentConfig config) {
        requireEnabled();
        config.validate();
        var group = new ExperimentSeries();
        group.setMode(config.getMode());
        group.setGeneratorVersion("market-v3");
        group.setPolicyVersion("bots-v6");
        if ("EVALUATE".equals(config.getMode())) group.setModelVersion(activeModelVersion());
        try { group.setConfigJson(mapper.writeValueAsString(config)); }
        catch (JsonProcessingException e) { throw new IllegalArgumentException("Ошибка конфигурации", e); }
        return series.save(group);
    }

    private String activeModelVersion() {
        if (predictorUrl.isBlank()) throw new IllegalArgumentException("Для оценки нужна обученная модель");
        try {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            var request = HttpRequest.newBuilder(URI.create(predictorUrl + "/health"))
                .timeout(Duration.ofSeconds(3)).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            var health = mapper.readTree(response.body());
            if (response.statusCode() != 200 || !health.path("ready").asBoolean() ||
                health.path("version").asText().isBlank()) throw new IllegalArgumentException("Модель ещё не готова");
            return health.path("version").asText();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("Не удалось проверить версию модели", e);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            throw new IllegalArgumentException("Не удалось проверить версию модели", e);
        }
    }

    public List<ExperimentSeries> list() { requireEnabled(); return series.findAllByOrderByIdDesc(); }
    public List<ExperimentRun> runs(Long id) { requireEnabled(); return runs.findBySeriesIdOrderByNumberDesc(id); }
    public ExperimentSeries get(Long id) { requireEnabled(); return series.findById(id).orElseThrow(); }

    @Transactional
    public void change(Long id, String operation) {
        requireEnabled();
        var group = series.lockById(id).orElseThrow();
        switch (operation) {
            case "pause":
                if ("RUNNING".equals(group.getStatus())) group.setStatus("PAUSED");
                break;
            case "resume":
                if ("PAUSED".equals(group.getStatus())) group.setStatus("RUNNING");
                break;
            case "stop":
                if ("RUNNING".equals(group.getStatus()) || "PAUSED".equals(group.getStatus()))
                    group.setStatus("STOPPED");
                for (var run : runs.findBySeriesIdOrderByNumberDesc(id))
                    if ("QUEUED".equals(run.getStatus())) run.setStatus("CANCELLED");
                break;
            default: throw new IllegalArgumentException("Неизвестная операция");
        }
    }

    public JsonNode trainingReport() { requireEnabled(); return mlRequest("/training-report", "GET"); }

    public JsonNode trainingRuns() { requireEnabled(); return mlRequest("/training-runs", "GET"); }

    public JsonNode trainingRun(String id) {
        requireEnabled();
        try { java.util.UUID.fromString(id); }
        catch (IllegalArgumentException e) { throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.BAD_REQUEST, "Неверный идентификатор запуска"); }
        return mlRequest("/training-runs/" + id, "GET");
    }

    public JsonNode train(Long id) {
        requireEnabled();
        var group = get(id);
        if (!"COLLECT".equals(group.getMode()) ||
            runs.countBySeriesModeAndStatus("COLLECT", "COMPLETED") < 12)
            throw new IllegalArgumentException("Для обучения нужно не менее 12 завершённых игр сбора данных");
        return mlRequest("/train", "POST");
    }

    private JsonNode mlRequest(String path, String method) {
        try {
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            var builder = HttpRequest.newBuilder(URI.create(predictorUrl + path))
                .timeout(Duration.ofSeconds(10));
            var response = client.send("POST".equals(method) ?
                builder.POST(HttpRequest.BodyPublishers.noBody()).build() : builder.GET().build(),
                HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Запуск обучения не найден");
            if (response.statusCode() >= 400) throw new IllegalArgumentException("ML-сервис: " + response.body());
            return mapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("ML-сервис недоступен", e);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) throw (IllegalArgumentException) e;
            throw new IllegalArgumentException("ML-сервис недоступен", e);
        }
    }

    private void requireEnabled() {
        if (!enabled) throw new org.springframework.web.server.ResponseStatusException(
            org.springframework.http.HttpStatus.NOT_FOUND, "Экспериментальный режим недоступен");
    }

    private void dispatchSafe() {
        try {
            var active = series.findAllByOrderByIdDesc().stream()
                .filter(group -> "RUNNING".equals(group.getStatus())).toList();
            if (active.isEmpty()) return;
            int start = Math.floorMod(nextSeriesOffset++, active.size());
            boolean claimed;
            do {
                claimed = false;
                for (int i = 0; i < active.size(); i++) {
                    if (!gameSlots.tryAcquire()) return;
                    var group = active.get((start + i) % active.size());
                    try {
                        ExperimentConfig config = mapper.readValue(group.getConfigJson(), ExperimentConfig.class);
                        Long id = engine.claim(group.getId(), config);
                        if (id == null) { gameSlots.release(); continue; }
                        try {
                            workers.submit(() -> {
                                try { execute(id); }
                                finally { gameSlots.release(); }
                            });
                        } catch (java.util.concurrent.RejectedExecutionException e) {
                            fail(id, e); // The claimed game must not remain RUNNING without a worker.
                            throw e;
                        }
                        claimed = true;
                    } catch (Exception e) {
                        gameSlots.release();
                        log.error("Ошибка запуска игры серии {}", group.getId(), e);
                    }
                }
            } while (claimed);
        } catch (Exception e) { log.error("Ошибка планировщика экспериментов", e); }
    }

    private void execute(Long id) {
        var dayTimes = new java.util.ArrayList<Long>();
        long started = System.nanoTime();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                long dayStarted = System.nanoTime();
                if (!engine.advance(id)) break;
                dayTimes.add(System.nanoTime() - dayStarted);
            }
            if (!dayTimes.isEmpty()) {
                dayTimes.sort(Long::compareTo);
                log.info("Игра эксперимента {}: {} дней, p50={} мс, p95={} мс, участок={} мс",
                    id, dayTimes.size(), TimeUnit.NANOSECONDS.toMillis(dayTimes.get((dayTimes.size() - 1) / 2)),
                    TimeUnit.NANOSECONDS.toMillis(dayTimes.get((int) Math.ceil(dayTimes.size() * 0.95) - 1)),
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
            }
        } catch (Exception e) {
            log.error("Игра эксперимента {} остановлена", id, e);
            fail(id, e);
        }
    }

    public void fail(Long id, Exception e) {
        transactions.executeWithoutResult(tx -> {
            var run = runs.findById(id).orElseThrow();
            run.setAttempts(run.getAttempts() + 1);
            boolean retry = run.getAttempts() < 2 && "RUNNING".equals(run.getSeries().getStatus());
            run.setStatus(retry ? "QUEUED" : "ERROR");
            if (!retry && "EVALUATE".equals(run.getSeries().getMode())) {
                int partnerNumber = run.getNumber() % 2 == 0 ? run.getNumber() - 1 : run.getNumber() + 1;
                runs.findBySeriesIdAndNumber(run.getSeries().getId(), partnerNumber).ifPresent(partner -> {
                    if ("WAITING_PAIR".equals(partner.getStatus())) partner.setStatus("INCOMPLETE");
                });
            }
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            run.setError(error.substring(0, Math.min(1000, error.length())));
        });
    }
}
