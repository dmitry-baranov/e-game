package ru.itis.diploma.game.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum GameStatus {
    CREATED("Cоздана"),
    STARTED("Активная игра. Осуществляется процесс совершения покупок"),
    STOPPED("Остановлены покупки(пауза)"),
    FINISHED("Завершена");

    private final String name;
}
