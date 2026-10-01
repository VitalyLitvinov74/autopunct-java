package ru.autopunct.typing.assistance.application.queries.Permission;
import an.awesome.pipelinr.Command;
public record PermissionQuery(String application) implements Command<Boolean> {}
