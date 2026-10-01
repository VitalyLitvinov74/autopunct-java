package ru.autopunct.windows.application.commands.ChangeAutostart;
import an.awesome.pipelinr.Command;
import org.springframework.stereotype.Component;
import ru.autopunct.windows.infrastructure.Desktop;

@Component
public final class ChangeAutostartHandler implements Command.Handler<ChangeAutostartCommand, Void> {
    private final Desktop desktop;
    public ChangeAutostartHandler(Desktop desktop) {
        this.desktop = desktop;
    }
    @Override
    public Void handle(ChangeAutostartCommand command) {
        this.desktop.autoStart(command.enabled());
        return null;
    }
}
