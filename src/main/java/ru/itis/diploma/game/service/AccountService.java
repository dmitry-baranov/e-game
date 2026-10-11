package ru.itis.diploma.game.service;

import ru.itis.diploma.game.dto.AccountDto;
import ru.itis.diploma.game.dto.EditAccount;
import ru.itis.diploma.game.model.Account;

import java.util.List;

public interface AccountService {

    Account getById(Long id);

    List<AccountDto> getAccountsForNewGame();

    AccountDto updateAccountInfo(Long id, EditAccount account);

    AccountDto getByEmail(String email);

}
