package ru.autopunct.typing.commas.infrastructure.pipe;

import java.util.List;

/** Возвращает позиции запятых для строго определённой версии поля ввода. */
public record Reply(
    int version,
    long requestId,
    String contextId,
    long revision,
    String status,
    List<Integer> commas
) {}
