package ru.itis.diploma.experiment.bot;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import ru.itis.diploma.game.GameSimulation;
import ru.itis.diploma.game.dto.CreateGameDto;
import ru.itis.diploma.game.model.Account;
import ru.itis.diploma.game.model.Game;
import ru.itis.diploma.game.model.Manufacturer;
import ru.itis.diploma.game.repository.AccountRepository;
import ru.itis.diploma.game.repository.ManufacturerRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BotParticipantsTest {
    @Test
    void collectRotatesPoliciesBetweenAttempts() {
        String[] policies = {"CAUTIOUS", "PRICE", "QUALITY"};
        assertEquals("CAUTIOUS", BotParticipants.policyFor("COLLECT", policies, 1, 0));
        assertEquals("PRICE", BotParticipants.policyFor("COLLECT", policies, 1, 1));
        assertEquals("PRICE", BotParticipants.policyFor("COLLECT", policies, 2, 0));
        assertEquals("QUALITY", BotParticipants.policyFor("COLLECT", policies, 2, 1));
    }

    @Test
    void evaluationOnlyChangesFirstBotInPairedGames() {
        String[] policies = {"CAUTIOUS", "PRICE", "QUALITY"};
        assertEquals("EVAL_RULES", BotParticipants.policyFor("EVALUATE", policies, 1, 0));
        assertEquals("MODEL", BotParticipants.policyFor("EVALUATE", policies, 2, 0));
        for (int i = 1; i < 10; i++)
            assertEquals(BotParticipants.policyFor("EVALUATE", policies, 1, i),
                BotParticipants.policyFor("EVALUATE", policies, 2, i));
    }

    @Test
    void reusesAccountsAcrossGamesButKeepsSlotsAndGameStateSeparate() {
        var accounts = mock(AccountRepository.class);
        var manufacturers = mock(ManufacturerRepository.class);
        var games = mock(GameSimulation.class);
        var jdbc = mock(JdbcTemplate.class);
        var byEmail = new HashMap<String, Account>();
        var playersByGame = new HashMap<Long, List<Manufacturer>>();
        var nextAccount = new AtomicLong();
        var nextGame = new AtomicLong();
        when(accounts.findByEmailInAndServiceAccountTrueAndServiceAccountType(any(),
            any(Account.ServiceAccountType.class))).thenAnswer(call -> {
            List<String> emails = call.getArgument(0);
            return emails.stream().filter(byEmail::containsKey).map(byEmail::get).toList();
        });
        when(accounts.save(any(Account.class))).thenAnswer(call -> {
            Account account = call.getArgument(0);
            account.setId(nextAccount.incrementAndGet());
            byEmail.put(account.getEmail(), account);
            return account;
        });
        when(games.create(any())).thenAnswer(call -> {
            CreateGameDto settings = call.getArgument(0);
            Game game = Game.builder().id(nextGame.incrementAndGet()).build();
            var players = new ArrayList<Manufacturer>();
            for (Long id : settings.getAccountIds())
                players.add(Manufacturer.builder().account(byEmail.values().stream()
                    .filter(account -> account.getId().equals(id)).findFirst().orElseThrow()).build());
            playersByGame.put(game.getId(), players);
            return game;
        });
        when(manufacturers.findByGame_Id(anyLong())).thenAnswer(call -> {
            var reversed = new ArrayList<>(playersByGame.get((Long) call.getArgument(0)));
            java.util.Collections.reverse(reversed);
            return reversed;
        });

        var participants = new BotParticipants(accounts, manufacturers, games, jdbc);
        var first = participants.create("COLLECT", new String[]{"CAUTIOUS"}, 1, 3, new CreateGameDto());
        var second = participants.create("COLLECT", new String[]{"CAUTIOUS"}, 2, 3, new CreateGameDto());
        assertEquals(3, byEmail.size());
        assertEquals(List.of("bot-CAUTIOUS-1@experiment.invalid", "bot-CAUTIOUS-2@experiment.invalid",
            "bot-CAUTIOUS-3@experiment.invalid"), byEmail.keySet().stream().sorted().toList());
        for (int i = 0; i < 3; i++) {
            var firstPlayer = playersByGame.get(first.getId()).get(i);
            var secondPlayer = playersByGame.get(second.getId()).get(i);
            assertEquals(firstPlayer.getAccount().getId(), secondPlayer.getAccount().getId());
            assertNotSame(firstPlayer, secondPlayer);
            assertEquals("CAUTIOUS", secondPlayer.getSelectedStrategy());
            assertTrue(secondPlayer.getAllowStrategyTraining());
        }

        var mixed = participants.create("COLLECT", new String[]{"CAUTIOUS", "PRICE"}, 1, 2, new CreateGameDto());
        assertEquals(4, byEmail.size());
        assertEquals("CAUTIOUS", playersByGame.get(mixed.getId()).get(0).getSelectedStrategy());
        assertEquals("PRICE", playersByGame.get(mixed.getId()).get(1).getSelectedStrategy());
    }
}
