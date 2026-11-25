package ru.itis.diploma.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
/** Отображаемые параметры рекламной кампании предприятия. */
public class AdvertisingIntensity {

    /** Первый день действия рекламы. */
    private Integer startDate;

    /** Последний день действия рекламы включительно. */
    private Integer endDate;

    /** Индекс интенсивности рекламной кампании. */
    private Integer intensityIndex;

}
