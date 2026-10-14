package ru.itis.diploma.experiment.bot.policy;

import static ru.itis.diploma.experiment.bot.policy.PolicyMetadata.Family.*;

enum PolicyCatalog {
    CAUTIOUS("Осторожный выпуск", BASIC, true),
    PRICE("Цена дешевле видимого конкурента", BASIC, true),
    QUALITY("Высокое качество и реклама", BASIC, true),
    STOCK("Выпуск по размеру склада", BASIC, true),
    EXPLORE("Воспроизводимый перебор допустимых решений", BASIC, true),
    RULES("Рекомендации по правилам", RECOMMENDATION, true),
    PRICE_MEDIAN("Цена к медиане конкурентов", PolicyMetadata.Family.PRICE, true),
    PRICE_PREMIUM("Цена выше медианы конкурентов", PolicyMetadata.Family.PRICE, true),
    PRICE_CUT_AFTER_ZERO("Снижать цену после нескольких нулевых продаж", PolicyMetadata.Family.PRICE, true),
    PRICE_RAISE_AFTER_SELLOUT("Повышать цену после распродаж", PolicyMetadata.Family.PRICE, true),
    PRICE_MARGIN_GUARD("Цена с запасом маржи после налога", PolicyMetadata.Family.PRICE, true),
    PRICE_HYSTERESIS("Не менять цену при мелких колебаниях рынка", PolicyMetadata.Family.PRICE, true),
    SALES_SMOOTH("Сглаживать выпуск по прошлым продажам", INVENTORY, true),
    STOCK_CLEAR("Уменьшать выпуск при большом остатке", INVENTORY, true),
    STOCKOUT_RECOVER("Увеличивать выпуск после распродаж", INVENTORY, true),
    CAPACITY_STEADY("Стабильная партия по собственной мощности", INVENTORY, true),
    SHORT_CYCLE("Короткие производственные циклы", INVENTORY, true),
    LONG_CYCLE("Длинные циклы при устойчивом спросе", INVENTORY, true),
    QUALITY_STEP_UP("Постепенно улучшать качество", PRODUCT, true),
    QUALITY_BUDGET("Снижать затраты на качество при нехватке денег", PRODUCT, true),
    ASSORTMENT_BROADEN("Постепенно расширять ассортимент при распродажах", PRODUCT, true),
    ASSORTMENT_FOCUS("Сокращать ассортимент при слабом сбыте", PRODUCT, true),
    ADS_PULSE("Чередовать рекламные импульсы и паузы", ADVERTISING, true),
    ADS_STOP_LOSS("Останавливать рекламу после слабого рекламного цикла", ADVERTISING, true),
    CASH_RESERVE("Сохранять денежный резерв", FINANCE, true),
    DEBT_AVOID("Не начинать новый цикл с оборотным кредитом", FINANCE, true),
    PAYMENT_AWARE("Резервировать деньги под известные платежи", FINANCE, true),
    RIVAL_QUALITY_GAP("Подтягивать качество к близким по цене конкурентам", MARKET, true),
    RIVAL_AD_GAP("Реагировать на заметную рекламу конкурентов", MARKET, true),
    PRICE_TWO_LEVELS("Чередовать два уровня цены", EXPERIMENT, true),
    PRICE_REVENUE_WINDOW("Опираться на выручку прошлых полных циклов", EXPERIMENT, true),
    VOLATILITY_BUFFER("Снижать выпуск при изменчивых продажах", EXPERIMENT, true),
    STOCK_RUNWAY("Сопоставлять срок покрытия запасом и длину цикла", EXPERIMENT, true),
    ZERO_SALES_PIVOT("Менять качество или ассортимент после двух нулевых циклов", EXPERIMENT, true),
    CONSERVATIVE_CASH_SCORE("Сравнивать допустимые цену и партию по осторожной оценке денег", EXPERIMENT, true),
    LIMITED_CREDIT_GROWTH("Расти с жёстким лимитом нового кредита", EXPERIMENT, true),
    ADS_DURATION_TEST("Чередовать и сравнивать длительность рекламы", EXPERIMENT, true),
    QUALITY_PRICE_BUNDLE("Менять качество и цену вместе", EXPERIMENT, true),
    EVAL_RULES("Правила в парной оценке", RECOMMENDATION, false),
    MODEL("Модель в парной оценке", RECOMMENDATION, false);

    private final PolicyMetadata metadata;

    PolicyCatalog(String description, PolicyMetadata.Family family, boolean selectable) {
        metadata = new PolicyMetadata(name(), description, family, selectable);
    }

    PolicyMetadata metadata() {
        return metadata;
    }
}
