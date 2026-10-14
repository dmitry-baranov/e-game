package ru.itis.diploma.experiment.bot.model;

import ru.itis.diploma.game.dto.CommonProductionParameters;

/** Данные предложенного ботом плана; принятие плана и его запись выполняет эксперимент. */
public record BotDecision(CommonProductionParameters action) {}
