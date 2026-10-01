package ru.autopunct.typing.commas.presentation;

import an.awesome.pipelinr.Pipeline;
import com.fasterxml.jackson.databind.json.JsonMapper;
import org.springframework.stereotype.Component;
import ru.autopunct.typing.commas.application.queries.SuggestCommas.SuggestCommasQuery;
import ru.autopunct.typing.commas.infrastructure.pipe.Reply;
import ru.autopunct.windows.infrastructure.Desktop;
import java.io.IOException;

/** Проверяет входной снимок, отправляет запрос через CQRS и сохраняет идентичность ответа. */
@Component
public final class SnapshotEndpoint {
    private final Pipeline pipeline;
    private final IncomingSnapshot incomingSnapshot;
    private final Desktop desktop;
    private final JsonMapper json = new JsonMapper();

    public SnapshotEndpoint(Pipeline pipeline, IncomingSnapshot incomingSnapshot, Desktop desktop) {
        this.pipeline = pipeline;
        this.incomingSnapshot = incomingSnapshot;
        this.desktop = desktop;
    }

    public byte[] respond(byte[] request) {
        try {
            var snapshot = this.incomingSnapshot.read(request);
            this.desktop.rememberApplication(snapshot.application());
            var result = this.pipeline.send(new SuggestCommasQuery(snapshot.text(), snapshot.application()));
            var reply = new Reply(
                1,
                snapshot.requestId(),
                snapshot.contextId(),
                snapshot.revision(),
                result.permitted() ? "ok" : "disabled",
                result.commas()
            );
            return this.json.writeValueAsBytes(reply);
        } catch (IOException failure) {
            throw new IllegalArgumentException("snapshot_invalid", failure);
        }
    }
}
