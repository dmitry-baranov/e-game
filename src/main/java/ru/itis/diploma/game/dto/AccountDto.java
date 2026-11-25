package ru.itis.diploma.game.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import ru.itis.diploma.game.model.Account;

import java.util.List;
import java.util.stream.Collectors;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
/** Публичные сведения об аккаунте без пароля и служебных флагов. */
public class AccountDto {
    /** ID аккаунта. */
    private Long id;
    /** Отображаемое имя пользователя. */
    private String fullName;
    /** Адрес почты аккаунта. */
    private String email;

    public static AccountDto from(Account account) {
        return AccountDto.builder()
                .id(account.getId())
                .fullName(account.getFullName())
                .email(account.getEmail())
                .build();
    }

    public static List<AccountDto> from(List<Account> accounts) {
        return accounts.stream().map(AccountDto::from).toList();
    }
}
