package ru.itis.diploma.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.itis.diploma.validation.ValidPassword;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
/** Поля формы создания аккаунта с проверкой ввода. */
public class SignUpForm {
    /** Имя нового пользователя, показываемое в игре. */
    @Size(min = 4, max =  100)
    /** Пароль для входа в аккаунт. */
    @NotBlank
    private String fullName;

    @NotBlank
    @ValidPassword(message = "{validation.password.error-message}")
    private String password;

    /** Адрес почты, используемый как имя входа. */
    @Email
    @NotBlank
    private String email;
}
