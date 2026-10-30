package ru.itis.diploma.game.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.Serializable;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
@Table(name = "account")
/** Учётная запись человека или служебного бота; игровое состояние хранится в Manufacturer. */
public class Account implements Serializable {

    private static final long serialVersionUID = 9132222312087392904L;

    /** Идентификатор аккаунта. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Адрес для входа человека или служебное имя бота из общего пула. */
    private String email;

    /** Хеш пароля человека; для бота вход отключён служебным значением. */
    private String password;

    /** Отображаемое имя участника. */
    private String fullName;

    /** Права доступа к страницам приложения. */
    @Enumerated(value = EnumType.STRING)
    private Role role;

    /** Признак системного аккаунта, не принадлежащего человеку. */
    private boolean serviceAccount;

    /** Вид системного аккаунта; у обычного пользователя null. */
    @Enumerated(value = EnumType.STRING)
    private ServiceAccountType serviceAccountType;

    /** USER — участник, ADMIN — администратор. */
    public enum Role {
        USER, ADMIN
    }

    /** EXPERIMENT_BOT — аккаунт для автоматических игр. */
    public enum ServiceAccountType {
        EXPERIMENT_BOT
    }

}
