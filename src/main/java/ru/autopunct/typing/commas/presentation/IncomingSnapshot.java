package ru.autopunct.typing.commas.presentation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import ru.autopunct.typing.commas.infrastructure.pipe.Snapshot;
import java.io.IOException;
import org.springframework.stereotype.Component;

/** Проверяет технические границы входящего сообщения перед обращением к языковым правилам. */
@Component
public final class IncomingSnapshot implements AutoCloseable {
    private final JsonMapper json = JsonMapper.builder()
        .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .enable(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
        .build();
    private final ValidatorFactory validation = Validation.byDefaultProvider()
        .configure()
        .messageInterpolator(new ParameterMessageInterpolator())
        .buildValidatorFactory();

    public Snapshot read(byte[] bytes) throws IOException {
        if (bytes.length > 8192) {
            throw new IOException("protocol_frame_too_large");
        }
        Snapshot snapshot = this.json.readValue(bytes, Snapshot.class);
        if (!this.validation.getValidator().validate(snapshot).isEmpty()
                || snapshot.caretOffset() != snapshot.text().length()) {
            throw new IOException("protocol_snapshot_invalid");
        }
        return snapshot;
    }

    @Override
    public void close() {
        this.validation.close();
    }
}
