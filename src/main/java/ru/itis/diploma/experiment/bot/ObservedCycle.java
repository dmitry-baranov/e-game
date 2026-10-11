package ru.itis.diploma.experiment.bot;

import java.math.BigDecimal;

/**
 * Собственный производственный цикл бота, для которого уже записаны ВСЕ торговые дни.
 * Продажи и выручка — наблюдаемые факты прошлой игры, а не прогноз спроса на следующий ход;
 * неполный цикл не передаётся политикам для сравнения цены, рекламы и качества.
 */
public record ObservedCycle(int day, int days, int count, BigDecimal price, BigDecimal quality,
                            int assortment, int advertising, int advertisingDays, int sold,
                            BigDecimal revenue) {}
