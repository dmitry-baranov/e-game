package ru.itis.diploma.experiment.analytics;

import lombok.RequiredArgsConstructor;
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
        model.addAttribute("training", hasTrainingReports() ? analytics.trainingReports(id) : List.of());
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
     * from и параметры фильтра нужны только для возврата в исходный список;
     * они проверяются сервером, а bot должен принадлежать выбранной игре.
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
        if (bot == null && !players.isEmpty()) bot = ((Number) players.get(0).get("id")).longValue();
        if (bot != null) {
            Long selectedBot = bot;
            if (players.stream().noneMatch(p -> ((Number) p.get("id")).longValue() == selectedBot.longValue()))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (compareBot != null && (compareBot.equals(bot) || players.stream().noneMatch(p ->
            ((Number) p.get("id")).longValue() == compareBot.longValue())))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
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
            model.addAttribute("cycles", analytics.cycles(bot).stream()
                .map(BotAnalyticsReadRepository.CycleRow::templateModel).toList());
            model.addAttribute("ads", analytics.advertisements(bot));
            model.addAttribute("investmentPayments", analytics.investmentPayments(bot));
            model.addAttribute("businessPayments", analytics.businessPayments(bot));
            model.addAttribute("taxes", analytics.taxes(bot));
            model.addAttribute("trades", analytics.trades(bot));
            var decisions = analytics.decisions(id, bot);
            // Вверху хронологии показываем смысл плана; исходный JSON остаётся в деталях.
            for (var decision : decisions) {
                try {
                    var action = mapper.readTree((String) decision.get("proposed_action_json"));
                    decision.put("plan", "Партия " + action.path("productCount").asText("—") +
                        ", цена " + action.path("price").asText("—") +
                        ", качество " + action.path("qualityIndex").asText("—") +
                        ", ассортимент " + action.path("assortment").asText("—") +
                        ", реклама " + action.path("advertisingIntensityIndex").asText("—") +
                        " × " + action.path("advertisingDays").asText("—") + " дн.");
                } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                    decision.put("plan", "План доступен в исходной записи");
                }
            }
            model.addAttribute("decisions", decisions);
            model.addAttribute("episodes", analytics.episodes(bot));
        }
        return "bot_analytics_run";
    }

    @GetMapping("/evaluations/{id}")
    public String evaluation(@PathVariable Long id, Model model) {
        var group = series.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!"EVALUATE".equals(group.getMode())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        model.addAttribute("title", "Оценка серии #" + id);
        model.addAttribute("group", group);
        model.addAttribute("report", reports.report(id));
        model.addAttribute("trainingId", hasTrainingReports() && group.getModelVersion() != null ?
            analytics.trainingModel(group.getModelVersion()) : List.of());
        return "bot_analytics_evaluation";
    }

    private boolean hasTrainingReports() {
        return analytics.hasTrainingReports();
    }

    private static boolean marketAllowed(String name) {
        return name.isEmpty() || ExperimentScenarios.MARKET_NAMES.contains(name);
    }

    @GetMapping("/training-report") @ResponseBody
    public com.fasterxml.jackson.databind.JsonNode trainingReport() { return manager.trainingReport(); }

    @GetMapping("/training")
    public String training(Model model) {
        model.addAttribute("runs", mapper.convertValue(manager.trainingRuns(), List.class));
        return "bot_analytics_training";
    }

    @GetMapping("/training/{id}")
    public String training(@PathVariable String id, @RequestParam(defaultValue = "training") String from,
                           @RequestParam(required = false) Long series, Model model) {
        if (!List.of("training", "series", "evaluation").contains(from))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var report = manager.trainingRun(id);
        if (!"training".equals(from)) {
            var origin = series == null ? null : this.series.findById(series).orElse(null);
            if (origin == null || ("evaluation".equals(from) && !"EVALUATE".equals(origin.getMode())) ||
                ("series".equals(from) && !java.util.stream.StreamSupport.stream(
                    report.path("series_ids").spliterator(), false).anyMatch(item -> item.asText().equals(series.toString()))))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
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

    @GetMapping("/training/{id}/json") @ResponseBody
    public com.fasterxml.jackson.databind.JsonNode trainingJson(@PathVariable String id) {
        return manager.trainingRun(id);
    }
}
