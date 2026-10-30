package ru.itis.diploma.experiment.service;

import ru.itis.diploma.experiment.config.ExperimentConfig;

import ru.itis.diploma.experiment.model.ExperimentRun;
import ru.itis.diploma.experiment.model.ExperimentSeries;
import ru.itis.diploma.experiment.repository.ExperimentRunRepository;
import ru.itis.diploma.experiment.repository.ExperimentSeriesRepository;
import ru.itis.diploma.experiment.repository.ExperimentPairRepository;

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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Жизненный цикл серии: создаёт настройки, выдаёт свободные слоты играм и позволяет
 * приостановить/возобновить/остановить серию. Глобальный семафор ограничивает число
 * одновременно выполняемых игр десятью; собственный parallel каждой серии
 * проверяется в Engine.claim под блокировкой строки серии. Один поток = одна игра;
 * отдельные боты внутри игрового дня не запускаются параллельно.
 */
@Service @Slf4j @RequiredArgsConstructor
public class ExperimentManager {
    private final ExperimentSeriesRepository series;
    private final ExperimentRunRepository runs;
    private final ExperimentPairRepository pairs;
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

    /**
     * Сообщает, доступен ли экспериментальный режим в текущей конфигурации приложения.
     *
     * @return {@code true}, если операции с экспериментами разрешены
     */
    public boolean enabled() {
        return enabled;
    }

    /**
     * После готовности приложения восстанавливает прерванные запуски и запускает диспетчер.
     * При отключённом экспериментальном режиме не создаёт рабочие потоки.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!enabled) {
            return;
        }
        // Виртуальные потоки включаются только для независимых игр эксперимента.
        if (virtualThreads) {
            workers = Executors.newVirtualThreadPerTaskExecutor();
        } else {
            workers = Executors.newFixedThreadPool(10);
        }
        recover();
        dispatcher.scheduleWithFixedDelay(this::dispatchSafe, 0, 1, TimeUnit.SECONDS);
    }

    /**
     * После рестарта атомарно возвращает RUNNING-запуски в очередь; игра и завершённые
     * дни не пересоздаются. Поддерживается ровно один экземпляр экспериментального приложения.
     */
    public void recover() {
        // Поддерживается ровно один экземпляр экспериментального приложения.
        transactions.executeWithoutResult(tx -> runs.requeueRunning());
    }

    /**
     * Останавливает диспетчер и рабочие потоки при завершении приложения.
     * Уже прерванные RUNNING-запуски будут восстановлены следующим вызовом {@link #recover()}.
     */
    @PreDestroy
    public void shutdown() {
        dispatcher.shutdownNow();
        if (workers != null) {
            workers.shutdownNow();
        }
    }

    /**
     * Создаёт серию, сохраняя исходную конфигурацию и версии генератора, политики и модели.
     *
     * @param config конфигурация новой экспериментальной серии
     * @return сохранённая серия
     * @throws IllegalArgumentException если конфигурация некорректна или не сериализуется
     */
    public ExperimentSeries create(ExperimentConfig config) {
        requireEnabled();
        config.validateNewSeries();
        var group = new ExperimentSeries();
        group.setMode(config.getMode());
        group.setTarget(config.getTarget());
        group.setGeneratorVersion(ExperimentScenarios.VERSION);
        group.setPolicyVersion("bots-v7");
        try {
            String configJson = mapper.writeValueAsString(config);
            group.setConfigJson(configJson);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Ошибка конфигурации", e);
        }
        return series.save(group);
    }

    /**
     * Возвращает все экспериментальные серии от новых к старым.
     *
     * @return список серий в порядке убывания идентификатора
     */
    public List<ExperimentSeries> list() {
        requireEnabled();
        return series.findAllByOrderByIdDesc();
    }

    /**
     * Возвращает запуски указанной серии от новых к старым.
     *
     * @param id идентификатор серии
     * @return список запусков серии
     */
    public List<ExperimentRun> runs(Long id) {
        requireEnabled();
        return runs.findBySeriesIdOrderByNumberDesc(id);
    }

    /**
     * Находит экспериментальную серию по идентификатору.
     *
     * @param id идентификатор серии
     * @return найденная серия
     * @throws java.util.NoSuchElementException если серия не существует
     */
    public ExperimentSeries get(Long id) {
        requireEnabled();
        return series.findById(id).orElseThrow();
    }

    /**
     * Изменяет состояние серии под блокировкой; уже начатый игровой день завершается атомарно.
     * При остановке инвалидирует ожидающие пары и отменяет запуски в очереди.
     *
     * @param id идентификатор серии
     * @param operation операция {@code pause}, {@code resume} или {@code stop}
     * @throws IllegalArgumentException если передана неизвестная операция
     */
    @Transactional
    public void change(Long id, String operation) {
        requireEnabled();
        var group = series.lockById(id).orElseThrow();
        switch (operation) {
            case "pause":
                if ("RUNNING".equals(group.getStatus())) {
                    group.setStatus("PAUSED");
                }
                break;
            case "resume":
                if ("PAUSED".equals(group.getStatus())) {
                    group.setStatus("RUNNING");
                }
                break;
            case "stop":
                if ("RUNNING".equals(group.getStatus()) || "PAUSED".equals(group.getStatus())) {
                    group.setStatus("STOPPED");
                }
                for (var pair : pairs.lockWithQueuedRun(id)) {
                    List<ExperimentRun> pairRuns = runs.findByPairId(pair.getId());
                    pair.invalidate(pairRuns);
                }
                runs.cancelQueuedBySeriesId(id);
                break;
            default: throw new IllegalArgumentException("Неизвестная операция");
        }
    }

    /**
     * Получает у ML-сервиса сводный отчёт о готовности обучающей выборки.
     *
     * @return JSON-отчёт ML-сервиса
     */
    public JsonNode trainingReport() {
        requireEnabled();
        return mlRequest("/training-report", "GET");
    }

    /**
     * Получает у ML-сервиса список запусков обучения.
     *
     * @return JSON со списком запусков обучения
     */
    public JsonNode trainingRuns() {
        requireEnabled();
        return mlRequest("/training-runs", "GET");
    }

    /**
     * Получает сведения о конкретном запуске обучения после проверки формата идентификатора.
     *
     * @param id UUID запуска обучения в строковом виде
     * @return JSON с состоянием запуска
     * @throws org.springframework.web.server.ResponseStatusException если идентификатор имеет неверный формат
     */
    public JsonNode trainingRun(String id) {
        requireEnabled();
        try {
            java.util.UUID.fromString(id);
        } catch (IllegalArgumentException e) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "Неверный идентификатор запуска");
        }
        return mlRequest("/training-runs/" + id, "GET");
    }

    /**
     * Запускает обучение по явному запросу администратора после проверки режима серии
     * и наличия не менее двенадцати завершённых игр сбора данных.
     *
     * @param id идентификатор серии сбора данных
     * @return JSON-ответ ML-сервиса о запуске обучения
     * @throws IllegalArgumentException если серия не предназначена для сбора или данных недостаточно
     */
    public JsonNode train(Long id) {
        requireEnabled();
        var group = get(id);
        if (!"COLLECT".equals(group.getMode())) {
            throw new IllegalArgumentException("Для обучения нужно не менее 12 завершённых игр сбора данных");
        }
        long completed = runs.countBySeriesModeAndStatus("COLLECT", "COMPLETED");
        if (completed < 12) {
            throw new IllegalArgumentException("Для обучения нужно не менее 12 завершённых игр сбора данных");
        }
        return mlRequest("/train", "POST");
    }

    /**
     * Выполняет служебный GET- или POST-запрос к ML-сервису и разбирает JSON-ответ.
     * Прерывание потока восстанавливается перед преобразованием ошибки в прикладное исключение.
     *
     * @param path путь относительно настроенного адреса ML-сервиса
     * @param method HTTP-метод; значение {@code POST} отправляет пустой POST, остальные значения дают GET
     * @return разобранный JSON-ответ
     * @throws IllegalArgumentException если сервис недоступен или вернул ошибку
     */
    private JsonNode mlRequest(String path, String method) {
        try {
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
            URI address = URI.create(predictorUrl + path);
            HttpRequest.Builder builder = HttpRequest.newBuilder(address).timeout(Duration.ofSeconds(10));
            HttpRequest request;
            if ("POST".equals(method)) {
                request = builder.POST(HttpRequest.BodyPublishers.noBody()).build();
            } else {
                request = builder.GET().build();
            }
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.NOT_FOUND, "Запуск обучения не найден");
            }
            if (response.statusCode() >= 400) {
                throw new IllegalArgumentException("ML-сервис: " + response.body());
            }
            return mapper.readTree(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalArgumentException("ML-сервис недоступен", e);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException) {
                throw (IllegalArgumentException) e;
            }
            throw new IllegalArgumentException("ML-сервис недоступен", e);
        }
    }

    /**
     * Запрещает обращение к экспериментальному API, когда режим отключён конфигурацией.
     *
     * @throws org.springframework.web.server.ResponseStatusException если экспериментальный режим отключён
     */
    private void requireEnabled() {
        if (!enabled) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.NOT_FOUND, "Экспериментальный режим недоступен");
        }
    }

    /**
     * Распределяет свободные глобальные слоты между активными сериями по кругу.
     * Ошибки планирования журналируются и не завершают периодическую задачу диспетчера.
     */
    private void dispatchSafe() {
        try {
            // Между активными сериями чередуем стартовую позицию. Слот берётся
            // до claim и освобождается после игры даже при ошибке или паузе.
            List<ExperimentSeries> active = series.findByStatusOrderByIdDesc("RUNNING");
            if (active.isEmpty()) {
                return;
            }
            int start = Math.floorMod(nextSeriesOffset++, active.size());
            boolean claimed;
            do {
                claimed = false;
                for (int i = 0; i < active.size(); i++) {
                    if (!gameSlots.tryAcquire()) {
                        return;
                    }
                    int seriesIndex = (start + i) % active.size();
                    ExperimentSeries group = active.get(seriesIndex);
                    if (startGame(group)) {
                        claimed = true;
                    }
                }
            } while (claimed);
        } catch (Exception e) {
            log.error("Ошибка планировщика экспериментов", e);
        }
    }

    /**
     * Выдаёт запуск серии занявшему слот рабочему потоку; если работа не получена,
     * слот освобождается непосредственно в этом методе.
     *
     * @param group активная экспериментальная серия
     * @return {@code true}, если запуск передан рабочему потоку
     */
    private boolean startGame(ExperimentSeries group) {
        try {
            ExperimentConfig config = mapper.readValue(group.getConfigJson(), ExperimentConfig.class);
            Long runId = engine.claim(group.getId(), config);
            if (runId == null) {
                gameSlots.release();
                return false;
            }
            try {
                workers.submit(() -> {
                    try {
                        execute(runId);
                    } finally {
                        gameSlots.release();
                    }
                });
            } catch (java.util.concurrent.RejectedExecutionException e) {
                fail(runId, e); // Выданная игра не должна оставаться RUNNING без потока.
                throw e;
            }
            return true;
        } catch (Exception e) {
            gameSlots.release();
            log.error("Ошибка запуска игры серии {}", group.getId(), e);
            return false;
        }
    }

    /**
     * Последовательно проводит дни одной игры, каждый в собственной транзакции,
     * и при сбое переводит запуск в повтор или ошибку.
     *
     * @param id идентификатор исполняемого запуска
     */
    private void execute(Long id) {
        // Каждый вызов advance открывает СВОЮ транзакцию дня; ошибки не оставляют
        // половину обработанных продаж и учитываются через fail/повтор в очереди.
        List<Long> dayTimes = new ArrayList<>();
        long started = System.nanoTime();
        try {
            while (!Thread.currentThread().isInterrupted()) {
                long dayStarted = System.nanoTime();
                if (!engine.advance(id)) {
                    break;
                }
                dayTimes.add(System.nanoTime() - dayStarted);
            }
            if (!dayTimes.isEmpty()) {
                dayTimes.sort(Long::compareTo);
                int medianIndex = (dayTimes.size() - 1) / 2;
                int percentileIndex = (int) Math.ceil(dayTimes.size() * 0.95) - 1;
                long medianMs = TimeUnit.NANOSECONDS.toMillis(dayTimes.get(medianIndex));
                long percentileMs = TimeUnit.NANOSECONDS.toMillis(dayTimes.get(percentileIndex));
                long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
                log.info("Игра эксперимента {}: {} дней, p50={} мс, p95={} мс, участок={} мс",
                    id, dayTimes.size(), medianMs, percentileMs, elapsedMs);
            }
        } catch (Exception e) {
            log.error("Игра эксперимента {} остановлена", id, e);
            fail(id, e);
        }
    }

    /**
     * Атомарно учитывает ошибку запуска: первая ошибка возвращает игру в очередь,
     * повторная переводит её в ERROR и инвалидирует оценочную пару.
     *
     * @param id идентификатор ошибочного запуска
     * @param e исключение, описывающее причину сбоя
     */
    public void fail(Long id, Exception e) {
        // Первая ошибка даёт повтор существующей игры с сохранённого дня;
        // повторная завершает её ERROR и помечает ожидающего партнёра непригодным.
        transactions.executeWithoutResult(tx -> {
            var run = runs.findById(id).orElseThrow();
            run.setAttempts(run.getAttempts() + 1);
            boolean retry = run.getAttempts() < 2 && "RUNNING".equals(run.getSeries().getStatus());
            run.setStatus(retry ? "QUEUED" : "ERROR");
            if (!retry) {
                invalidatePair(run);
            }
            String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            int length = Math.min(1000, error.length());
            run.setError(error.substring(0, length));
        });
    }

    /**
     * Инвалидирует оценочную пару ошибочного запуска; для запуска без пары ничего не делает.
     * Метод вызывается внутри транзакции обработки ошибки.
     *
     * @param run ошибочный экспериментальный запуск
     */
    private void invalidatePair(ExperimentRun run) {
        if (run.getPair() == null) {
            return;
        }
        var pair = pairs.lockById(run.getPair().getId()).orElseThrow();
        List<ExperimentRun> pairRuns = runs.findByPairId(pair.getId());
        pair.invalidate(pairRuns);
    }
}
