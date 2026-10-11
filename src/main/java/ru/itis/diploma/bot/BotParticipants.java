package ru.itis.diploma.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.itis.diploma.experiment.ExperimentConfig;
import ru.itis.diploma.experiment.ExperimentScenarios;
import ru.itis.diploma.experiment.ExperimentSeries;
import ru.itis.diploma.model.Account;
import ru.itis.diploma.model.Game;
import ru.itis.diploma.repository.AccountRepository;
import ru.itis.diploma.repository.ManufacturerRepository;
import ru.itis.diploma.service.impl.GameServiceImpl;

import java.util.ArrayList;
import java.util.List;

/** Creates regular game participants; only their decisions are automated. */
@Service
@RequiredArgsConstructor
public class BotParticipants {
    private final AccountRepository accounts;
    private final ManufacturerRepository manufacturers;
    private final GameServiceImpl gameService;

    /** Called inside the transaction that claims a run; creation rolls back as a unit. */
    public Game create(ExperimentSeries group, ExperimentConfig config, int number,
                       ExperimentScenarios.Scenario scenario) {
        Long seriesId = group.getId();
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < scenario.bots(); i++) {
            var account = Account.builder().email("bot-" + seriesId + "-" + number + "-" + i + "@experiment.invalid")
                .fullName("Бот " + (i + 1)).password("!disabled!").role(Account.Role.USER).build();
            ids.add(accounts.save(account).getId());
        }
        scenario.game().setAccountIds(ids);
        // Reuse the normal game factory and its Manufacturer creation; the experimental DB is separate.
        var game = gameService.createGameForExperiment(scenario.game());
        var players = manufacturers.findByGame_Id(game.getId());
        for (int i = 0; i < players.size(); i++) {
            var player = players.get(i);
            player.setSelectedStrategy(policyFor(config, number, i));
            player.setAllowStrategyTraining("COLLECT".equals(config.getMode()));
        }
        return game;
    }

    /** The preview and the real game use exactly the same policy rotation. */
    public static String policyFor(ExperimentConfig config, int number, int index) {
        boolean evaluate = "EVALUATE".equals(config.getMode());
        if (evaluate && index == 0) return number % 2 == 1 ? "EVAL_RULES" : "MODEL";
        int rotation = evaluate ? (number - 1) / 2 : number - 1;
        String[] policies = config.policies();
        return policies[(rotation + index - (evaluate ? 1 : 0) + policies.length) % policies.length];
    }
}
