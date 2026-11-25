package ru.itis.diploma.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
/** Данные формы изменения отображаемого имени аккаунта. */
public class EditAccount {
    /** Новое имя пользователя. */
    private String fullName;
}
