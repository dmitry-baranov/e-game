package ru.itis.diploma.experiment.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.itis.diploma.game.dto.CreateGameDto;
import ru.itis.diploma.game.GameSimulation;
import ru.itis.diploma.game.model.Account;
import ru.itis.diploma.game.model.Game;
import ru.itis.diploma.game.repository.AccountRepository;
import ru.itis.diploma.game.repository.ManufacturerRepository;

import java.util.ArrayList;
import java.util.List;

/**
 * Создаёт обычных участников игры: аккаунт USER и предприятие Manufacturer для каждого бота.
 * Автоматизированы только решения о производстве; покупатель, кредиты и торговля используют
 * те же игровые сервисы, что и игра с людьми. Вызывающая сторона выбирает БД и режим.
 */
@Service
@RequiredArgsConstructor
public class BotParticipants {
    private final AccountRepository accounts;
    private final ManufacturerRepository manufacturers;
    private final GameSimulation gameService;

    /** Вызывается внутри транзакции выдачи попытки: при ошибке все аккаунты и игра откатываются. */
    public Game create(Long seriesId, String mode, String[] policies, int number,
                       int bots, CreateGameDto settings) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < bots; i++) {
            var account = Account.builder().email("bot-" + seriesId + "-" + number + "-" + i + "@experiment.invalid")
                .fullName("Бот " + (i + 1)).password("!disabled!").role(Account.Role.USER).build();
            ids.add(accounts.save(account).getId());
        }
        settings.setAccountIds(ids);
        // Фабрика обычной игры создаёт Manufacturer; здесь лишь закрепляем политику за каждым предприятием.
        var game = gameService.create(settings);
        var players = manufacturers.findByGame_Id(game.getId());
        for (int i = 0; i < players.size(); i++) {
            var player = players.get(i);
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
