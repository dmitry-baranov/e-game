package ru.itis.diploma;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import ru.itis.diploma.dto.SignUpForm;
import ru.itis.diploma.model.Account;
import ru.itis.diploma.repository.AccountRepository;
import ru.itis.diploma.service.SignUpService;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class SignUpServiceIntegrationTest {
    @Autowired private SignUpService signUpService;
    @Autowired private AccountRepository accounts;

    @Test
    @Transactional
    void firstRegistrationBecomesAdminAndLaterRegistrationsAreUsers() {
        boolean empty = accounts.count() == 0;
        String firstEmail = UUID.randomUUID() + "@example.test";
        signUpService.signUp(form(firstEmail));
        assertEquals(empty ? Account.Role.ADMIN : Account.Role.USER,
            accounts.findByEmail(firstEmail).orElseThrow().getRole());

        String secondEmail = UUID.randomUUID() + "@example.test";
        signUpService.signUp(form(secondEmail));
        assertEquals(Account.Role.USER, accounts.findByEmail(secondEmail).orElseThrow().getRole());
    }

    private SignUpForm form(String email) {
        return SignUpForm.builder().email(email).fullName("Test user").password("Test12345!").build();
    }
}
