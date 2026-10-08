package ru.itis.diploma.service.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import ru.itis.diploma.dto.SignUpForm;
import ru.itis.diploma.model.Account;
import ru.itis.diploma.repository.AccountRepository;
import ru.itis.diploma.service.SignUpService;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;

@Service
@RequiredArgsConstructor
public class SignUpServiceImpl implements SignUpService {
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public void signUp(SignUpForm form) {
        // Serialize registrations, so concurrent requests cannot both become the first admin.
        entityManager.createNativeQuery("SELECT CAST(pg_advisory_xact_lock(712390481) AS text)").getSingleResult();
        Account.Role role = accountRepository.count() == 0 ? Account.Role.ADMIN : Account.Role.USER;
        Account account = Account.builder()
                .fullName(form.getFullName())
                .email(form.getEmail())//.toLowerCase(Locale.ROOT)
                .password(passwordEncoder.encode(form.getPassword()))
                .role(role)
                .build();

        accountRepository.save(account);
    }

}
