package ru.itis.diploma.experiment.bot;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import ru.itis.diploma.game.dto.CreateGameDto;
import ru.itis.diploma.game.GameSimulation;
import ru.itis.diploma.game.model.Account;
import ru.itis.diploma.game.model.Game;
import ru.itis.diploma.game.model.Manufacturer;
import ru.itis.diploma.game.repository.AccountRepository;
import ru.itis.diploma.game.repository.ManufacturerRepository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    /**
     * Создаёт игру с переиспользуемыми служебными аккаунтами и закрепляет выбранную
     * политику за предприятием каждого бота. Вызывается внутри транзакции выдачи
     * попытки: при ошибке все аккаунты и игра откатываются.
     *
     * @param mode режим экспериментальной серии
     * @param policies политики, участвующие в ротации
     * @param number порядковый номер игры в серии, начиная с единицы
     * @param bots количество создаваемых участников-ботов
     * @param settings настройки новой игры; список аккаунтов будет заполнен методом
     * @return созданная игра с привязанными предприятиями ботов
     */
    //прописать 3 момента в отчете
    // Документы сначала научному руководителю потом Ильнаре
    //1 Итеграция бота в систему  2 основные параметры бота 3 организация записи статистики как это устроенно как в каком объеме куда записывается , отчет должен составлять 7-8 старниц (можно вставлять код меньше чем на пол страницы, если больше пол страницы то в приложение)
    public Game create(String mode, String[] policies, int number,
                       int bots, CreateGameDto settings) {
        // Claims are serialized per series, but two different series can create games at once.
        // Lock the shared pool until this claim transaction commits, including account inserts.
        jdbc.query("SELECT pg_advisory_xact_lock(?)", rs -> {}, 7130341201L);
        List<String> emails = new ArrayList<>();
        List<String> assignedPolicies = new ArrayList<>();
        Map<String, Integer> slots = new HashMap<>();
        for (int i = 0; i < bots; i++) {
            String policy = policyFor(mode, policies, number, i);
            int slot = slots.merge(policy, 1, Integer::sum);
            String email = "bot-" + policy + "-" + slot + "@experiment.invalid";
            assignedPolicies.add(policy);
            emails.add(email);
        }

        List<Account> existingAccounts = accounts.findByEmailInAndServiceAccountTrueAndServiceAccountType(
            emails, Account.ServiceAccountType.EXPERIMENT_BOT);
        Map<String, Account> pool = new HashMap<>();
        for (Account account : existingAccounts) {
            pool.put(account.getEmail(), account);
        }

        List<Long> ids = new ArrayList<>();
        for (String email : emails) {
            Account account = pool.get(email);
            if (account == null) {
                int addressEnd = email.indexOf('@');
                String accountName = email.substring(4, addressEnd);
                String fullName = "Бот " + accountName.replace('-', ' ');
                var builder = Account.builder();
                builder.email(email);
                builder.fullName(fullName);
                builder.password("!disabled!");
                builder.role(Account.Role.USER);
                builder.serviceAccount(true);
                builder.serviceAccountType(Account.ServiceAccountType.EXPERIMENT_BOT);
                Account newAccount = builder.build();
                account = accounts.save(newAccount);
            }
            Long accountId = account.getId();
            ids.add(accountId);
        }

        settings.setAccountIds(ids);
        // Фабрика обычной игры создаёт Manufacturer; здесь лишь закрепляем политику за каждым предприятием.
        Game game = gameService.create(settings);
        Long gameId = game.getId();
        List<Manufacturer> participants = manufacturers.findByGame_Id(gameId);
        Map<Long, Manufacturer> players = new HashMap<>();
        for (Manufacturer participant : participants) {
            Account participantAccount = participant.getAccount();
            Long accountId = participantAccount.getId();
            players.put(accountId, participant);
        }

        boolean collectTraining = "COLLECT".equals(mode);
        for (int i = 0; i < ids.size(); i++) {
            Long accountId = ids.get(i);
            Manufacturer player = players.get(accountId);
            String policy = assignedPolicies.get(i);
            player.setSelectedStrategy(policy);
            player.setAllowStrategyTraining(collectTraining);
        }
        return game;
    }

    /**
     * Предпросмотр и создание игры используют одну ротацию базовых политик.
     *
     * @param mode режим экспериментальной серии
     * @param policies политики, участвующие в ротации
     * @param number порядковый номер игры в серии, начиная с единицы
     * @param index индекс бота внутри игры
     * @return имя политики для указанной позиции
     */
    public static String policyFor(String mode, String[] policies, int number, int index) {
        int rotatedIndex = (number - 1 + index) % policies.length;
        return policies[rotatedIndex];
    }
}
