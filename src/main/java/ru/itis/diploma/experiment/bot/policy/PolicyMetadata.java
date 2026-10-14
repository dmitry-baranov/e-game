package ru.itis.diploma.experiment.bot.policy;

public record PolicyMetadata(String name, String description, Family family, boolean selectable) {
    public enum Family { BASIC, PRICE, INVENTORY, PRODUCT, ADVERTISING, FINANCE, MARKET, EXPERIMENT, RECOMMENDATION }
}
