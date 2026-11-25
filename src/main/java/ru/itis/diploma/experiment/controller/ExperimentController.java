package ru.itis.diploma.experiment.controller;

import ru.itis.diploma.experiment.config.ExperimentConfig;
import ru.itis.diploma.experiment.service.ExperimentManager;
import ru.itis.diploma.experiment.service.ExperimentScenarios;

import ru.itis.diploma.experiment.bot.BotPolicies;
import ru.itis.diploma.experiment.bot.BotParticipants;
import ru.itis.diploma.experiment.model.ExperimentRun;
import ru.itis.diploma.experiment.model.ExperimentSeries;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Администраторский интерфейс управления сериями. Контроллер существует только
 * в экспериментальном приложении; создание и изменение состояния идут через
 * ExperimentManager, а частый JSON-опрос статуса читает лёгкие счётчики и 30
 * последних попыток. Полный статистический отчёт читается отдельно по требованию.
 */
@Controller @RequiredArgsConstructor
@RequestMapping("/admin/bot-experiments")
@PreAuthorize("hasAuthority('ADMIN')")
@ConditionalOnProperty(name = "experiment.enabled", havingValue = "true")
public class ExperimentController {
    private final ExperimentManager manager;
    private final ExperimentScenarios scenarios;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /**
     * Показывает форму создания серии с доступными политиками и рынками.
     *
     * @param model модель представления
     * @return имя шаблона управления экспериментами
     */
    @GetMapping
    public String page(Model model) {
        model.addAttribute("config", new ExperimentConfig());
        formOptions(model);
        return "bot_experiments";
    }

    private void formOptions(Model model) {
        model.addAttribute("policyNames", BotPolicies.NAMES);
        model.addAttribute("policyDescriptions", BotPolicies.DESCRIPTIONS);
        model.addAttribute("markets", ExperimentScenarios.MARKETS);
        model.addAttribute("marketNames", ExperimentScenarios.MARKET_NAMES);
    }

    /**
     * Возвращает краткий список последних серий для административного интерфейса.
     *
     * @return не более двадцати последних серий
     */
    @GetMapping("/series") @ResponseBody
    public List<SeriesItem> series() {
        return jdbc.query("SELECT id, status, mode FROM experiment_series ORDER BY id DESC LIMIT 20",
            (rs, row) -> new SeriesItem(rs.getLong("id"), rs.getString("status"), rs.getString("mode")));
    }

    /**
     * Формирует предпросмотр первых сценариев по введённой конфигурации.
     *
     * @param config проверяемая конфигурация серии
     * @return до пяти сгенерированных сценариев
     * @throws IllegalArgumentException если конфигурация недопустима
     */
    @GetMapping("/preview") @ResponseBody
    public List<Preview> preview(@ModelAttribute ExperimentConfig config) {
        config.validateNewSeries();
        String[] policies = config.policies();
        List<Preview> previews = new ArrayList<>();
        int count = Math.min(5, config.getTarget());
        for (int number = 1; number <= count; number++) {
            ExperimentScenarios.Scenario scenario = scenarios.generate(config, number);
            Map<String, Long> chosen = new LinkedHashMap<>();
            for (int bot = 0; bot < scenario.bots(); bot++) {
                String policy = BotParticipants.policyFor(config.getMode(), policies, number, bot);
                chosen.merge(policy, 1L, Long::sum);
            }
            var game = scenario.game();
            Preview preview = new Preview(number, Long.toString(scenario.seed()), scenario.market(),
                scenario.bots(), scenario.horizon(), game.getBaseCostPrice().toString(),
                game.getPurchaseLimit(), game.getDailySpendingLimit().toString(), chosen.toString());
            previews.add(preview);
        }
        return previews;
    }

    /**
     * Создаёт серию либо повторно показывает форму с ошибкой валидации.
     *
     * @param config конфигурация новой серии
     * @param model модель представления для отображения ошибки
     * @return перенаправление на созданную серию или имя шаблона формы
     */
    @PostMapping
    public String create(@ModelAttribute ExperimentConfig config, Model model) {
        try {
            ExperimentSeries series = manager.create(config);
            return "redirect:/admin/bot-experiments/" + series.getId();
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("config", config);
            formOptions(model);
            return "bot_experiments";
        }
    }

    /**
     * Показывает страницу текущего состояния серии.
     *
     * @param id идентификатор серии
     * @param error необязательное сообщение об ошибке операции
     * @param model модель представления
     * @return имя шаблона состояния серии
     * @throws java.util.NoSuchElementException если серия не найдена
     */
    @GetMapping("/{id}")
    public String detail(@PathVariable Long id, @RequestParam(required = false) String error, Model model) {
        ExperimentSeries series = manager.get(id);
        model.addAttribute("seriesId", series.getId());
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "bot_experiment_status";
    }

    /**
     * Возвращает сводные счётчики и последние попытки серии для периодического опроса.
     *
     * @param id идентификатор серии
     * @return текущее состояние серии и до тридцати последних попыток
     * @throws java.util.NoSuchElementException если серия не найдена
     */
    @GetMapping("/{id}/status") @ResponseBody
    public Status status(@PathVariable Long id) {
        ExperimentSeries series = manager.get(id);
        // Частый опрос интерфейса читает агрегат и только последние игры, а не всю историю серии.
        var counts = jdbc.queryForMap("""
            SELECT count(*) FILTER (WHERE status='COMPLETED') AS completed,
                   count(*) FILTER (WHERE status='RUNNING') AS active,
                   count(*) FILTER (WHERE status='ERROR') AS errors,
                   count(*) FILTER (WHERE status='QUEUED') AS queued,
                   count(*) FILTER (WHERE status='INCOMPLETE') AS incomplete,
                   coalesce(sum(completed_days),0) AS days
            FROM experiment_run WHERE series_id=?
            """, id);
        long completed = ((Number) counts.get("completed")).longValue();
        Duration runningTime = Duration.between(series.getCreatedAt(), LocalDateTime.now());
        long elapsed = Math.max(0, runningTime.getSeconds());
        Long etaSeconds = null;
        if (completed >= 3 && "RUNNING".equals(series.getStatus())) {
            long remaining = Math.max(0, configTarget(series.getConfigJson()) - completed);
            etaSeconds = elapsed * remaining / completed;
        }
        List<Run> recentRuns = jdbc.query("""
            SELECT id, number, status, seed::text AS seed, market, bots, horizon, completed_days,
                   cycles, best_result, error, model_decisions, model_fallbacks, attempts, stock_skips, credit_skips
            FROM experiment_run WHERE series_id=? ORDER BY number DESC LIMIT 30
            """, runRow(), id);
        return new Status(series.getStatus(), series.getConfigJson(), series.getModelVersion(),
            series.getGeneratorVersion(), series.getPolicyVersion(),
            completed,
            ((Number) counts.get("active")).longValue(), ((Number) counts.get("errors")).longValue(),
            ((Number) counts.get("queued")).longValue(), ((Number) counts.get("incomplete")).longValue(),
            ((Number) counts.get("days")).longValue(), etaSeconds, recentRuns);
    }

    private RowMapper<Run> runRow() {
        return (rs, row) -> {
            BigDecimal result = rs.getBigDecimal("best_result");
            String bestResult = result == null ? null : result.toString();
            return new Run(rs.getLong("id"), rs.getInt("number"), rs.getString("status"),
                rs.getString("seed"), rs.getString("market"), rs.getInt("bots"), rs.getInt("horizon"),
                rs.getInt("completed_days"), rs.getInt("cycles"), bestResult, rs.getString("error"),
                rs.getInt("model_decisions"), rs.getInt("model_fallbacks"), rs.getInt("attempts"),
                rs.getInt("stock_skips"), rs.getInt("credit_skips"));
        };
    }

    /**
     * Извлекает целевое число пригодных игр из сохранённой конфигурации.
     *
     * @param json сериализованная конфигурация серии
     * @return значение поля {@code target} либо ноль при некорректном JSON
     */
    private long configTarget(String json) {
        try {
            var config = mapper.readTree(json);
            return config.path("target").asLong();
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return 0;
        }
    }

    /**
     * Возвращает текущее состояние обучения в формате JSON.
     *
     * @return отчёт подсистемы обучения
     */
    @GetMapping("/training-report") @ResponseBody
    public com.fasterxml.jackson.databind.JsonNode trainingReport() {
        return manager.trainingReport();
    }

    /**
     * Запускает обучение по общей накопленной COLLECT-выборке после проверки,
     * что выбранная серия относится к режиму сбора данных.
     *
     * @param id идентификатор серии
     * @param redirect атрибуты перенаправления для сообщения об ошибке
     * @return перенаправление на страницу состояния серии
     */
    @PostMapping("/{id}/train")
    public String train(@PathVariable Long id, org.springframework.web.servlet.mvc.support.RedirectAttributes redirect) {
        try {
            manager.train(id);
        } catch (IllegalArgumentException e) {
            redirect.addAttribute("error", e.getMessage());
        }
        return "redirect:/admin/bot-experiments/" + id;
    }

    /**
     * Выполняет допустимую операцию управления состоянием серии.
     *
     * @param id идентификатор серии
     * @param operation операция {@code pause}, {@code resume} или {@code stop}
     * @return перенаправление на страницу состояния серии
     * @throws ResponseStatusException если имя операции не поддерживается
     */
    @PostMapping("/{id}/{operation}")
    public String change(@PathVariable Long id, @PathVariable String operation) {
        if (!List.of("pause", "resume", "stop").contains(operation)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        manager.change(id, operation);
        return "redirect:/admin/bot-experiments/" + id;
    }

    /**
     * Преобразует ошибку аргументов контроллера в ответ с кодом 400.
     *
     * @param error перехваченное исключение
     * @return текст ошибки для тела ответа
     */
    @ExceptionHandler(IllegalArgumentException.class) @ResponseStatus(HttpStatus.BAD_REQUEST) @ResponseBody
    public String invalid(IllegalArgumentException error) {
        return error.getMessage();
    }

    public record Preview(int number, String seed, String market, int bots, int horizon,
                          String baseCost, int purchaseLimit, String dailyBudget, String policies) {}
    public record SeriesItem(long id, String status, String mode) {}
    public record Run(long id, int number, String status, String seed, String market, int bots, int horizon,
                      int days, int cycles, String bestResult, String error, int modelDecisions, int modelFallbacks,
                      int attempts, int stockSkips, int creditSkips) {}
    public record Status(String state, String config, String modelVersion, String generatorVersion, String policyVersion,
                         long completed, long active, long errors, long queued, long incomplete,
                         long processedDays, Long etaSeconds, List<Run> runs) {}
}
