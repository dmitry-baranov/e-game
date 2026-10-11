package ru.itis.diploma.game;

import java.util.Optional;

/** Необязательные ссылки на внешние разделы со страницы обычной игры. */
public interface GameViewExtension {
    Optional<Long> runForGame(Long gameId);
}
