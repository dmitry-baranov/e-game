package ru.itis.diploma.experiment.config;

import ru.itis.diploma.experiment.bot.BotPolicies;
import ru.itis.diploma.experiment.service.ExperimentScenarios;

import lombok.Data;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Настройки серии до генерации отдельных игр. target — требуемое число пригодных игр,
 * maxAttempts — верхняя граница попыток (0 превращается в автоматический лимит);
 * INCOMPLETE и ERROR тратят попытку, но не выполняют цель.
 */
@Data
public class ExperimentConfig {
    private int target = 20;
    private int parallel = 2;
    private int minBots = 2;
    private int maxBots = 4;
    private int minDays = 45;
    private int maxDays = 90;
    private long seed = 42;
    private int maxAttempts = 0;
    private String mode = "COLLECT";
    private String policyMix = String.join(",", BotPolicies.NAMES);
    private String marketMix = String.join(",", ExperimentScenarios.MARKETS);

    /**
     * Разбирает настроенную последовательность политик и удаляет пробелы по краям имён.
     *
     * @return политики в порядке их перечисления в конфигурации
     */
    public String[] policies() {
        return splitMix(policyMix);
    }

    /**
     * Разбирает настроенную последовательность рынков и удаляет пробелы по краям имён.
     *
     * @return рынки в порядке их перечисления в конфигурации
     */
    public String[] markets() {
        return splitMix(marketMix);
    }

    private static String[] splitMix(String mix) {
        String[] names = mix.split(",", -1);
        for (int i = 0; i < names.length; i++) {
            names[i] = names[i].trim();
        }
        return names;
    }

    /**
     * Проверяет ограничения серии и при необходимости устанавливает автоматический лимит попыток.
     * Метод вызывается на предпросмотре, при создании серии и при возобновлении генерации.
     *
     * @throws IllegalArgumentException если пределы, режим, политики или рынки недопустимы
     */
    public void validate() {
        if (maxAttempts == 0) {
            maxAttempts = target + Math.max(10, target / 5);
        }

        boolean invalidLimits = target < 1 || target > 10000
            || parallel < 1 || parallel > 10
            || minBots < 2 || minBots > maxBots || maxBots > 30
            || minDays < 30 || minDays > maxDays || maxDays > 180
            || maxAttempts < target || maxAttempts > 30000;
        boolean invalidMix = policyMix == null || marketMix == null
            || policyMix.length() > 1200 || marketMix.length() > 100;
        if (invalidLimits || invalidMix || !"COLLECT".equals(mode)) {
            throw new IllegalArgumentException("Некорректные пределы серии (проверьте также лимит попыток)");
        }

        Set<String> allowedPolicies = new HashSet<>(Arrays.asList(BotPolicies.NAMES));
        Set<String> seenPolicies = new HashSet<>();
        for (String policy : policies()) {
            if (!allowedPolicies.contains(policy) || !seenPolicies.add(policy)) {
                throw new IllegalArgumentException("Неизвестный или повторённый рынок/политика");
            }
        }

        Set<String> seenMarkets = new HashSet<>();
        for (String market : markets()) {
            if (!ExperimentScenarios.MARKETS.contains(market) || !seenMarkets.add(market)) {
                throw new IllegalArgumentException("Неизвестный или повторённый рынок/политика");
            }
        }
    }
}
