package ru.autopunct;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.jna.platform.win32.Kernel32;
import com.sun.jna.ptr.IntByReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import ru.autopunct.typing.commas.infrastructure.pipe.Reply;
import ru.autopunct.typing.commas.infrastructure.pipe.Snapshot;
import ru.autopunct.windows.infrastructure.Desktop;
import java.util.Arrays;
import static org.junit.jupiter.api.Assertions.*;

@EnabledOnOs(OS.WINDOWS)
@SpringBootTest(properties = {"autopunct.pipe.enabled=true", "autopunct.tray.enabled=false", "autopunct.preferences-node=/autopunct-tests/windows-pipe"})
@DirtiesContext
class WindowsPipeTest {
    @Autowired
    private Desktop desktop;
    private final JsonMapper json = new JsonMapper();

    @Test
    void realProtectedPipePreservesCorrelationAndUtf16Offsets() throws Exception {
        String text = "😀 Известно что на улице красивый вид ";
        var snapshot = new Snapshot(1, 97, "field-A", 42, text, text.length(), "notepad.exe");
        byte[] request = this.json.writeValueAsBytes(snapshot);
        byte[] response = new byte[8192];
        var length = new IntByReference();
        assertTrue(Kernel32.INSTANCE.WaitNamedPipe(this.desktop.pipeName(), 2000));
        assertTrue(Kernel32.INSTANCE.CallNamedPipe(this.desktop.pipeName(), request, request.length, response, response.length, length, 2000));
        Reply reply = this.json.readValue(Arrays.copyOf(response, length.getValue()), Reply.class);
        assertEquals(97, reply.requestId());
        assertEquals("field-A", reply.contextId());
        assertEquals(42, reply.revision());
        assertEquals("ok", reply.status());
        assertEquals(java.util.List.of(11), reply.commas());
    }
}
