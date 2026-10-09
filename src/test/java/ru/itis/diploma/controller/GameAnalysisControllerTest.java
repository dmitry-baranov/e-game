package ru.itis.diploma.controller;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.server.ResponseStatusException;
import ru.itis.diploma.model.Account;
import ru.itis.diploma.security.details.AccountUserDetails;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GameAnalysisControllerTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final GameAnalysisController controller = new GameAnalysisController(jdbc);
    private final AccountUserDetails player = new AccountUserDetails(Account.builder()
        .id(11L).email("player@example.invalid").role(Account.Role.USER).build());

    @Test
    void unfinishedGameCannotBeAnalyzed() {
        when(jdbc.queryForList(contains("status='FINISHED'"), eq(42L))).thenReturn(List.of());
        assertThrows(ResponseStatusException.class,
            () -> controller.analysis(42L, player, new ExtendedModelMap()));
    }

    @Test
    void finishedGameOfAnotherAccountCannotBeAnalyzed() {
        when(jdbc.queryForList(contains("status='FINISHED'"), eq(42L)))
            .thenReturn(List.of(Map.of("id", 42L, "status", "FINISHED")));
        when(jdbc.queryForList(contains("account_id=?"), eq(42L), eq(11L))).thenReturn(List.of());
        assertThrows(AccessDeniedException.class,
            () -> controller.analysis(42L, player, new ExtendedModelMap()));
    }
}
