package ru.itis.diploma.experiment;

import lombok.Data;

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
    private String marketMix = "BUDGET,COMPETITIVE,ADVERTISING";

    public String[] policies() { return java.util.Arrays.stream(policyMix.split(",", -1)).map(String::trim).toArray(String[]::new); }
    public String[] markets() { return java.util.Arrays.stream(marketMix.split(",", -1)).map(String::trim).toArray(String[]::new); }

    public void validate() {
        if (maxAttempts == 0) maxAttempts = target + Math.max(10, target / 5);
        if (target < 1 || target > 10000 || parallel < 1 || parallel > 10 ||
            minBots < 2 || minBots > maxBots || maxBots > 30 ||
            minDays < 30 || minDays > maxDays || maxDays > 180 ||
            maxAttempts < target || maxAttempts > 30000 ||
            !("COLLECT".equals(mode) || "EVALUATE".equals(mode)) ||
            ("EVALUATE".equals(mode) && (target % 2 != 0 || maxAttempts % 2 != 0)) ||
            policyMix == null || marketMix == null || policyMix.length() > 1200 || marketMix.length() > 100)
            throw new IllegalArgumentException("Некорректные пределы серии (проверьте также лимит попыток)");
        if (policies().length == 0 || markets().length == 0 ||
            java.util.Arrays.stream(policies()).anyMatch(p ->
                java.util.Arrays.stream(BotPolicies.NAMES).noneMatch(p::equals)) ||
            java.util.Arrays.stream(markets()).anyMatch(m ->
                !java.util.Set.of("BUDGET", "COMPETITIVE", "ADVERTISING").contains(m)) ||
            java.util.Arrays.stream(policies()).distinct().count() != policies().length ||
            java.util.Arrays.stream(markets()).distinct().count() != markets().length)
            throw new IllegalArgumentException("Неизвестный или повторённый рынок/политика");
    }
}
