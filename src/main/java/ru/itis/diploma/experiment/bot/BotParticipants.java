package ru.itis.diploma.experiment.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.itis.diploma.game.dto.CreateGameDto;
import ru.itis.diploma.game.GameSimulation;
import ru.itis.diploma.game.model.Account;
import ru.itis.diploma.game.model.Game;
import ru.itis.diploma.game.repository.AccountRepository;
import ru.itis.diploma.game.repository.ManufacturerRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Переиспользует аккаунты USER по политике и слоту; в каждой игре создаёт отдельное
 * предприятие Manufacturer для каждого бота.
 * Автоматизированы только решения о производстве; покупатель, кредиты и торговля используют
 * те же игровые сервисы, что и игра с людьми. Вызывающая сторона выбирает БД и режим.
 */
@Service
@RequiredArgsConstructor
public class BotParticipants {
    private final AccountRepository accounts;
    private final ManufacturerRepository manufacturers;
    private final GameSimulation gameService;
    private final JdbcTemplate jdbc;

    /** Вызывается внутри транзакции выдачи попытки: при ошибке все аккаунты и игра откатываются. */
    public Game create(String mode, String[] policies, int number,
                       int bots, CreateGameDto settings) {
        // Claims are serialized per series, but two different series can create games at once.
        // Lock the shared pool until this claim transaction commits, including account inserts.
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> {}, 7130341201L);
        List<String> emails = new ArrayList<>();
        Map<String, Integer> slots = new HashMap<>();
        for (int i = 0; i < bots; i++) {
            String policy = policyFor(mode, policies, number, i);
            int slot = slots.merge(policy, 1, Integer::sum);
            emails.add("bot-" + policy + "-" + slot + "@experiment.invalid");
        }
        Map<String, Account> pool = accounts.findByEmailInAndServiceAccountTrueAndServiceAccountType(
                emails, Account.ServiceAccountType.EXPERIMENT_BOT).stream()
            .collect(Collectors.toMap(Account::getEmail, Function.identity()));
        List<Long> ids = new ArrayList<>();
        for (String email : emails) {
            var account = pool.get(email);
            if (account == null) {
                account = accounts.save(Account.builder().email(email)
                    .fullName("Бот " + email.substring(4, email.indexOf('@')).replace('-', ' '))
                    .password("!disabled!").role(Account.Role.USER).serviceAccount(true)
                    .serviceAccountType(Account.ServiceAccountType.EXPERIMENT_BOT).build());
            }
            ids.add(account.getId());
        }
        settings.setAccountIds(ids);
        // Фабрика обычной игры создаёт Manufacturer; здесь лишь закрепляем политику за каждым предприятием.
        var game = gameService.create(settings);
        var players = manufacturers.findByGame_Id(game.getId()).stream()
            .collect(Collectors.toMap(player -> player.getAccount().getId(), Function.identity()));
        for (int i = 0; i < ids.size(); i++) {
            var player = players.get(ids.get(i));
            player.setSelectedStrategy(policyFor(mode, policies, number, i));
            player.setAllowStrategyTraining("COLLECT".equals(mode));
        }
        return game;
    }

    /**
     * Предпросмотр и создание игры используют одну ротацию. В паре EVALUATE первый бот
     * сравнивает правила (нечётная игра) и модель (чётная); остальные боты пары
     * сохраняют те же политики, чтобы не менять состав соперников между играми.
     */
    public static String policyFor(String mode, String[] policies, int number, int index) {
        boolean evaluate = "EVALUATE".equals(mode);
        if (evaluate && index == 0) return number % 2 == 1 ? "EVAL_RULES" : "MODEL";
        int rotation = evaluate ? (number - 1) / 2 : number - 1;
        return policies[(rotation + index - (evaluate ? 1 : 0) + policies.length) % policies.length];
    }
}
