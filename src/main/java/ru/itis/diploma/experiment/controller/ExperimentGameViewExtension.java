package ru.itis.diploma.experiment.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import ru.itis.diploma.game.GameViewExtension;

import java.util.Optional;

/** Ссылка на журнал бота появляется только в экспериментальном приложении. */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "experiment.enabled", havingValue = "true")
public class ExperimentGameViewExtension implements GameViewExtension {
    private final JdbcTemplate jdbc;

    @Override
    public Optional<Long> runForGame(Long gameId) {
        return jdbc.query("SELECT id FROM experiment_run WHERE game_id=? LIMIT 1",
            (rs, row) -> rs.getLong("id"), gameId).stream().findFirst();
    }
}
