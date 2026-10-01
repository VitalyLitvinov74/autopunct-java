package ru.autopunct.typing.assistance.application.queries.Status;
/** Показывает текущие настройки помощи и фактическую регистрацию автозапуска. */
public record StatusResult(boolean paused, boolean excluded, boolean autoStart) {}
