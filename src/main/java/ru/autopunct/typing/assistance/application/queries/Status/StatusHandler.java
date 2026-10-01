package ru.autopunct.typing.assistance.application.queries.Status;
import an.awesome.pipelinr.Command;
import org.springframework.stereotype.Component;
import ru.autopunct.typing.assistance.infrastructure.StatusReadModel;
import java.io.IOException;

@Component
public final class StatusHandler implements Command.Handler<StatusQuery, StatusResult> {
    private final StatusReadModel statusReadModel;
    public StatusHandler(StatusReadModel statusReadModel) {
        this.statusReadModel = statusReadModel;
    }
    @Override
    public StatusResult handle(StatusQuery query) {
        try {
            return this.statusReadModel.forApplication(query.application());
        } catch (IOException failure) {
            throw new IllegalStateException("profile_storage_failed", failure);
        }
    }
}
