package ru.itis.diploma.security.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import javax.sql.DataSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {
    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final DataSource dataSource;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        http.authenticationProvider(provider)
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(requests -> requests.anyRequest().permitAll())
            .rememberMe(remember -> remember.rememberMeParameter("rememberMe")
                .tokenRepository(tokenRepository()).tokenValiditySeconds(60 * 60 * 24 * 365))
            .formLogin(form -> form.loginPage("/sign-in").defaultSuccessUrl("/profile")
                .failureUrl("/sign-in?error").usernameParameter("email").passwordParameter("password"))
            .logout(logout -> logout.logoutUrl("/logout")
                .logoutSuccessUrl("/sign-in?logout").deleteCookies("SESSION", "JSESSIONID")
                .invalidateHttpSession(true));
        return http.build();
    }

    @Bean
    public PersistentTokenRepository tokenRepository() {
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }
}
