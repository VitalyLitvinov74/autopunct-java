package ru.autopunct.typing.commas.infrastructure.pipe;

import jakarta.validation.constraints.*;

/** Передаёт неизменяемый снимок поля Windows для одного запроса проверки запятых. */
public record Snapshot(
    @Min(1) @Max(1) int version,
    @Positive long requestId,
    @NotBlank @Size(max = 128) String contextId,
    @PositiveOrZero long revision,
    @NotNull @Size(max = 512) String text,
    @PositiveOrZero int caretOffset,
    @NotBlank @Size(max = 128) String application
) {}
