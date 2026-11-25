package ru.itis.diploma.experiment.analytics;

import lombok.RequiredArgsConstructor;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import ru.itis.diploma.experiment.service.ExperimentManager;
import ru.itis.diploma.experiment.model.ExperimentRun;
import ru.itis.diploma.experiment.repository.ExperimentRunRepository;
import ru.itis.diploma.experiment.service.ExperimentScenarios;
import ru.itis.diploma.experiment.repository.ExperimentSeriesRepository;

import java.util.List;
import java.util.Map;

/**
 * Администраторские страницы чтения результатов: серия → отфильтрованные игры
 * → выбранный бот → его дни, реальные циклы, предложенные решения и платежи.
 * Страница серии выводит агрегаты ВСЕХ COMPLETED игр; фильтр статус/рынок
 * действует только на пагинированную таблицу отдельных попыток.
 */
@Controller @RequiredArgsConstructor
@RequestMapping("/admin/bot-analytics")
@PreAuthorize("hasAuthority('ADMIN')")
@ConditionalOnProperty(name = "experiment.enabled", havingValue = "true")
public class BotAnalyticsController {
    private final ExperimentRunRepository runs;
    private final ExperimentSeriesRepository series;
    private final ExperimentReports reports;
    private final ExperimentManager manager;
    private final BotAnalyticsReadRepository analytics;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;

    /**
     * Показывает страницу серий с фильтрами по статусу и режиму.
     *
     * @param status фильтр статуса серии
     * @param mode фильтр режима серии
     * @param page номер страницы, начиная с нуля
     * @param model модель представления
     * @return имя шаблона списка серий
     * @throws ResponseStatusException если фильтр или номер страницы недопустим
     */
    @GetMapping
    public String index(@RequestParam(defaultValue = "") String status,
                        @RequestParam(defaultValue = "") String mode,
                        @RequestParam(defaultValue = "0") int page, Model model) {
        if (page < 0 || page > 100000 || !List.of("", "RUNNING", "PAUSED", "DONE", "EXHAUSTED", "STOPPED").contains(status) ||
            !List.of("", "COLLECT", "EVALUATE").contains(mode))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        model.addAttribute("title", "Аналитика ботов и моделей");
        model.addAttribute("series", analytics.series(status, mode, page * 50));
        long count = analytics.seriesCount(status, mode);
        model.addAttribute("statusFilter", status);
        model.addAttribute("modeFilter", mode);
        model.addAttribute("page", page);
        model.addAttribute("count", count);
        model.addAttribute("hasNext", count > (long) (page + 1) * 50);
        return "bot_analytics";
    }

    /**
     * Показывает агрегаты и страницу попыток выбранной серии.
     *
     * @param id идентификатор серии
     * @param page номер страницы попыток, начиная с нуля
     * @param status фильтр статуса попытки
     * @param market фильтр рынка
     * @param model модель представления
     * @return имя шаблона аналитики серии
     * @throws ResponseStatusException если серия не найдена или параметры фильтрации недопустимы
     */
    @GetMapping("/series/{id}")
    public String series(@PathVariable Long id, @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "") String status,
                         @RequestParam(defaultValue = "") String market, Model model) {
        var group = series.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (page < 0 || page > 100000) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        if (!List.of("", "COMPLETED", "INCOMPLETE", "ERROR", "CANCELLED", "RUNNING", "QUEUED", "WAITING_PAIR").contains(status) ||
            !marketAllowed(market))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        model.addAttribute("title", "Серия #" + id);
        model.addAttribute("group", group);
        model.addAttribute("coverage", reports.report(id));
        // Срез по рынку и политике вычисляется по завершённым играм; нулевые/неполные
        // попытки остаются в счётчиках статусов, но не превращаются в финансовые итоги.
        model.addAttribute("marketPolicies", analytics.marketPolicies(id));
        boolean trainingAvailable = analytics.hasTrainingReports();
        model.addAttribute("training", trainingAvailable ? analytics.trainingReports(id) : List.of());
        model.addAttribute("games", analytics.runs(id, status, market, page * 50));
        model.addAttribute("page", page);
        long count = analytics.runCount(id, status, market);
        model.addAttribute("count", count);
        model.addAttribute("statusFilter", status);
        model.addAttribute("marketFilter", market);
        model.addAttribute("markets", ExperimentScenarios.MARKET_NAMES);
        model.addAttribute("hasNext", count > (long) (page + 1) * 50);
        return "bot_analytics_series";
    }

    /**
     * Показывает подробную аналитику попытки и выбранного в её игре бота.
     * Параметр {@code from} и фильтры нужны только для возврата в исходный список;
     * они проверяются сервером, а бот должен принадлежать выбранной игре.
     *
     * @param id идентификатор попытки
     * @param bot идентификатор анализируемого производителя или {@code null} для выбора первого
     * @param compareBot идентификатор производителя для сравнения продаж
     * @param from тип исходной страницы
     * @param page номер страницы исходного списка
     * @param status фильтр статуса исходного списка
     * @param market фильтр рынка исходного списка
     * @param model модель представления
     * @return имя шаблона аналитики попытки
     * @throws ResponseStatusException если попытка, игра или бот не найдены либо параметры недопустимы
     */
    @GetMapping("/runs/{id}")
    public String run(@PathVariable Long id, @RequestParam(required = false) Long bot,
                      @RequestParam(required = false) Long compareBot,
                      @RequestParam(defaultValue = "series") String from,
                      @RequestParam(defaultValue = "0") int page,
                      @RequestParam(defaultValue = "") String status,
                      @RequestParam(defaultValue = "") String market, Model model) {
        var run = runs.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (run.getGame() == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        if (!List.of("series", "evaluation", "game", "status").contains(from) || page < 0 || page > 100000 ||
            !List.of("", "COMPLETED", "INCOMPLETE", "ERROR", "CANCELLED", "RUNNING", "QUEUED", "WAITING_PAIR").contains(status) ||
            !marketAllowed(market) ||
            ("evaluation".equals(from) && !"EVALUATE".equals(run.getSeries().getMode())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        Long game = run.getGame().getId();
        String seriesUrl = UriComponentsBuilder.fromPath("/admin/bot-analytics/series/{id}")
            .queryParam("page", page).queryParam("status", status).queryParam("market", market)
            .buildAndExpand(run.getSeries().getId()).encode().toUriString();
        String backUrl = switch (from) {
            case "evaluation" -> "/admin/bot-analytics/evaluations/" + run.getSeries().getId();
            case "game" -> "/game/" + game;
            case "status" -> "/admin/bot-experiments/" + run.getSeries().getId();
            default -> seriesUrl;
        };
        model.addAttribute("backUrl", backUrl);
        model.addAttribute("from", from);
        model.addAttribute("page", page);
        model.addAttribute("statusFilter", status);
        model.addAttribute("marketFilter", market);
        model.addAttribute("backLabel", switch (from) {
            case "evaluation" -> "К парной оценке";
            case "game" -> "К странице игры";
            case "status" -> "К управлению серией";
            default -> "К списку игр серии";
        });
        model.addAttribute("seriesUrl", seriesUrl);
        model.addAttribute("botSelectionUrl", UriComponentsBuilder.fromPath("/admin/bot-analytics/runs/{id}")
            .queryParam("from", from).queryParam("page", page).queryParam("status", status)
            .queryParam("market", market).buildAndExpand(id).encode().toUriString());
        var players = analytics.players(game);
        if (bot == null && !players.isEmpty()) {
            bot = ((Number) players.get(0).get("id")).longValue();
        }
        if (bot != null && !containsPlayer(players, bot)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (compareBot != null && (compareBot.equals(bot) || !containsPlayer(players, compareBot))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("title", "Игра #" + run.getNumber());
        model.addAttribute("run", run);
        model.addAttribute("game", analytics.game(game));
        model.addAttribute("players", players);
        model.addAttribute("seriesMode", run.getSeries().getMode());
        model.addAttribute("bot", bot);
        model.addAttribute("compareBot", compareBot);
        model.addAttribute("compareDays", compareBot == null ? List.of() : analytics.salesDays(compareBot));
        if (bot != null) {
            model.addAttribute("days", analytics.days(bot));
            // Продажи цикла — сумма за наблюдаемые дни (start_date, start_date+duration].
            // Если дней меньше duration, метка неполная: не выдавать её за прибыль цикла.
            List<Map<String, Object>> cycles = analytics.cycles(bot).stream()
                .map(BotAnalyticsReadRepository.CycleRow::templateModel).toList();
            model.addAttribute("cycles", cycles);
            model.addAttribute("ads", analytics.advertisements(bot));
            model.addAttribute("investmentPayments", analytics.investmentPayments(bot));
            model.addAttribute("businessPayments", analytics.businessPayments(bot));
            model.addAttribute("taxes", analytics.taxes(bot));
            model.addAttribute("trades", analytics.trades(bot));
            var decisions = analytics.decisions(id, bot);
            // Вверху хронологии показываем смысл плана; исходный JSON остаётся в деталях.
            for (Map<String, Object> decision : decisions) {
                String actionJson = (String) decision.get("proposed_action_json");
                decision.put("plan", describePlan(actionJson));
            }
            model.addAttribute("decisions", decisions);
            model.addAttribute("episodes", analytics.episodes(bot));
        }
        return "bot_analytics_run";
    }

    private static boolean containsPlayer(List<Map<String, Object>> players, long manufacturerId) {
        for (Map<String, Object> player : players) {
            long playerId = ((Number) player.get("id")).longValue();
            if (playerId == manufacturerId) {
                return true;
            }
        }
        return false;
    }

    private String describePlan(String actionJson) {
        try {
            JsonNode action = mapper.readTree(actionJson);
            return "Партия " + action.path("productCount").asText("—") +
                ", цена " + action.path("price").asText("—") +
                ", качество " + action.path("qualityIndex").asText("—") +
                ", ассортимент " + action.path("assortment").asText("—") +
                ", реклама " + action.path("advertisingIntensityIndex").asText("—") +
                " × " + action.path("advertisingDays").asText("—") + " дн.";
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            return "План доступен в исходной записи";
        }
    }

    /**
     * Показывает результаты парной оценки правил и модели для серии.
     *
     * @param id идентификатор оценочной серии
     * @param model модель представления
     * @return имя шаблона парной оценки
     * @throws ResponseStatusException если серия не найдена или не работает в режиме оценки
     */
    @GetMapping("/evaluations/{id}")
    public String evaluation(@PathVariable Long id, Model model) {
        var group = series.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!"EVALUATE".equals(group.getMode())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        model.addAttribute("title", "Оценка серии #" + id);
        model.addAttribute("group", group);
        model.addAttribute("report", reports.report(id));
        boolean trainingAvailable = analytics.hasTrainingReports() && group.getModelVersion() != null;
        model.addAttribute("trainingId", trainingAvailable ? analytics.trainingModel(group.getModelVersion()) : List.of());
        return "bot_analytics_evaluation";
    }

    /**
     * Проверяет, допустимо ли имя рынка в фильтре аналитики.
     *
     * @param name имя рынка или пустая строка без фильтрации
     * @return {@code true}, если рынок известен либо фильтр не задан
     */
    private static boolean marketAllowed(String name) {
        return name.isEmpty() || ExperimentScenarios.MARKET_NAMES.contains(name);
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
     * Показывает историю запусков обучения моделей.
     *
     * @param model модель представления
     * @return имя шаблона истории обучения
     */
    @GetMapping("/training")
    public String training(Model model) {
        model.addAttribute("runs", mapper.convertValue(manager.trainingRuns(), List.class));
        return "bot_analytics_training";
    }

    /**
     * Показывает отчёт конкретного запуска обучения и проверяет контекст перехода.
     *
     * @param id идентификатор запуска обучения
     * @param from тип исходной страницы
     * @param series идентификатор связанной серии при возврате к серии или оценке
     * @param model модель представления
     * @return имя шаблона отчёта запуска обучения
     * @throws ResponseStatusException если контекст перехода не поддерживается или не соответствует отчёту
     */
    @GetMapping("/training/{id}")
    public String training(@PathVariable String id, @RequestParam(defaultValue = "training") String from,
                           @RequestParam(required = false) Long series, Model model) {
        if (!List.of("training", "series", "evaluation").contains(from)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        var report = manager.trainingRun(id);
        if (!"training".equals(from)) {
            var origin = series == null ? null : this.series.findById(series).orElse(null);
            if (origin == null || ("evaluation".equals(from) && !"EVALUATE".equals(origin.getMode()))) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
            }
            if ("series".equals(from)) {
                boolean belongsToSeries = false;
                for (JsonNode item : report.path("series_ids")) {
                    if (item.asText().equals(series.toString())) {
                        belongsToSeries = true;
                        break;
                    }
                }
                if (!belongsToSeries) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
                }
            }
        }
        model.addAttribute("backUrl", switch (from) {
            case "series" -> "/admin/bot-analytics/series/" + series;
            case "evaluation" -> "/admin/bot-analytics/evaluations/" + series;
            default -> "/admin/bot-analytics/training";
        });
        model.addAttribute("backLabel", switch (from) {
            case "series" -> "К серии";
            case "evaluation" -> "К парной оценке";
            default -> "К истории обучения";
        });
        model.addAttribute("report", mapper.convertValue(report, Map.class));
        return "bot_analytics_training_run";
    }

    /**
     * Возвращает исходный JSON-отчёт запуска обучения.
     *
     * @param id идентификатор запуска обучения
     * @return отчёт выбранного запуска
     */
    @GetMapping("/training/{id}/json") @ResponseBody
    public com.fasterxml.jackson.databind.JsonNode trainingJson(@PathVariable String id) {
        return manager.trainingRun(id);
    }
}
