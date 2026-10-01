package ru.autopunct.typing.assistance.application.queries.Status;
import an.awesome.pipelinr.Command;
public record StatusQuery(String application) implements Command<StatusResult> {}
