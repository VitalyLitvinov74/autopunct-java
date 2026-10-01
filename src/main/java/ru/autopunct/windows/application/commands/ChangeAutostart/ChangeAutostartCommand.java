package ru.autopunct.windows.application.commands.ChangeAutostart;
import an.awesome.pipelinr.Command;
public record ChangeAutostartCommand(boolean enabled) implements Command<Void> {}
