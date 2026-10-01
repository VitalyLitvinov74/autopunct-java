package ru.autopunct.typing.commas.presentation;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;
import ru.autopunct.typing.commas.infrastructure.pipe.Snapshot;
import java.io.IOException;
import static org.junit.jupiter.api.Assertions.*;

class IncomingSnapshotTest {
    private final JsonMapper json = new JsonMapper();

    @Test
    void acceptsTheUtf16BoundaryAndPreservesText() throws Exception {
        try (var input = new IncomingSnapshot()) {
            var snapshot = new Snapshot(1, 1, "test", 0, "😀" + "я".repeat(510), 512, "notepad.exe");
            assertEquals(snapshot, input.read(this.json.writeValueAsBytes(snapshot)));
        }
    }

    @Test
    void rejectsOversizedTextInvalidCaretAndUnsupportedVersion() throws Exception {
        try (var input = new IncomingSnapshot()) {
            assertThrows(IOException.class, () -> input.read(this.json.writeValueAsBytes(
                new Snapshot(1, 1, "test", 0, "я".repeat(513), 513, "notepad.exe")
            )));
            assertThrows(IOException.class, () -> input.read(this.json.writeValueAsBytes(
                new Snapshot(1, 1, "test", 0, "😀", 1, "notepad.exe")
            )));
            assertThrows(IOException.class, () -> input.read(this.json.writeValueAsBytes(
                new Snapshot(2, 1, "test", 0, "текст ", 6, "notepad.exe")
            )));
            assertThrows(IOException.class, () -> input.read(new byte[8193]));
            assertThrows(IOException.class, () -> input.read("invalid json".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        }
    }
}
