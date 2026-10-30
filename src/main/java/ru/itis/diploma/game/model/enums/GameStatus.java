package ru.itis.diploma.game.model.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
/** Этап жизненного цикла игры. */
public enum GameStatus {
    /** Игра создана, но начальное производство ещё не открыто. */
    CREATED("Cоздана"),
    /** Игра запущена, торговые дни проводятся. */
    STARTED("Активная игра. Осуществляется процесс совершения покупок"),
    /** Торговля временно остановлена. */
    STOPPED("Остановлены покупки(пауза)"),
    /** Игра завершена, итоговый результат посчитан. */
    FINISHED("Завершена");

    /** Отображаемое игроку русскоязычное название состояния игры. */
    private final String name;
}
