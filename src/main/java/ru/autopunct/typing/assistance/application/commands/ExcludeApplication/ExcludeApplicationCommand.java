package ru.autopunct.typing.assistance.application.commands.ExcludeApplication;
import an.awesome.pipelinr.Command;
public record ExcludeApplicationCommand(String application, boolean excluded) implements Command<Void> {}
