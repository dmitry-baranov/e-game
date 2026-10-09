package ru.itis.diploma.experiment;

import java.math.BigDecimal;

/** Completed, actually observed cycle of this bot; never a counterfactual forecast. */
public record ObservedCycle(int day, int days, int count, BigDecimal price, BigDecimal quality,
                            int assortment, int advertising, int advertisingDays, int sold,
                            BigDecimal revenue) {}
