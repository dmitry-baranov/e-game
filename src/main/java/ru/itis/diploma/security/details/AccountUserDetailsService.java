package ru.itis.diploma.security.details;

import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import ru.itis.diploma.game.repository.AccountRepository;

@RequiredArgsConstructor
@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final AccountRepository accountRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return new AccountUserDetails(
                accountRepository.findByEmail(email)
                .orElseThrow(
                        () -> new UsernameNotFoundException("User not found")));
    }
}
