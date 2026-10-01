package ru.autopunct.typing.commas.infrastructure.pipe;

import com.sun.jna.platform.win32.*;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.autopunct.windows.infrastructure.Desktop;
import ru.autopunct.windows.infrastructure.PipeApi;
import ru.autopunct.windows.infrastructure.SecurityApi;
import java.util.Arrays;
import java.util.function.Function;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Принимает ограниченные локальные запросы и передаёт их через готовую шину CQRS. */
@Component
@ConditionalOnProperty(name = "autopunct.pipe.enabled", havingValue = "true")
public final class PipeSession implements AutoCloseable {
    private final Function<byte[], byte[]> requests;
    private final Desktop desktop;
    private final Set<WinNT.HANDLE> handles = ConcurrentHashMap.newKeySet();
    private final AtomicLong generation = new AtomicLong();
    private final ExecutorService connections = Executors.newFixedThreadPool(4);
    private final ThreadPoolExecutor analysis = new ThreadPoolExecutor(
        1,
        1,
        0,
        TimeUnit.SECONDS,
        new ArrayBlockingQueue<>(4),
        new ThreadPoolExecutor.AbortPolicy()
    );
    private volatile boolean running;
    private boolean firstPipe = true;
    private String name;

    public PipeSession(Function<byte[], byte[]> requests, Desktop desktop) {
        this.requests = requests;
        this.desktop = desktop;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void open() {
        this.name = this.desktop.pipeName();
        this.running = true;
        var listening = new Thread(this::listen, "autopunct-pipe");
        listening.start();
    }

    private void listen() {
        while (this.running) {
            WinNT.HANDLE pipe = null;
            try {
                pipe = this.createPipe();
                if (pipe == null) {
                    return;
                }
                var operation = new WinBase.OVERLAPPED();
                operation.hEvent = Kernel32.INSTANCE.CreateEvent(null, true, false, null);
                boolean connected = Kernel32.INSTANCE.ConnectNamedPipe(pipe, operation);
                int error = connected ? 0 : Kernel32.INSTANCE.GetLastError();
                boolean acceptedConnection;
                try {
                    acceptedConnection = error == WinError.ERROR_PIPE_CONNECTED
                        || this.complete(pipe, operation, connected, error, -1);
                } finally {
                    Kernel32.INSTANCE.CloseHandle(operation.hEvent);
                }
                if (!acceptedConnection) {
                    this.release(pipe);
                    continue;
                }
                WinNT.HANDLE accepted = pipe;
                this.connections.submit(() -> this.respond(accepted));
            } catch (RuntimeException failure) {
                if (pipe != null) {
                    this.release(pipe);
                }
                if (this.running) {
                    System.err.println("pipe_unavailable");
                    this.close();
                }
            }
        }
    }

    private synchronized WinNT.HANDLE createPipe() {
        if (!this.running) {
            return null;
        }
        String sid = this.name.substring("\\\\.\\pipe\\AutoPunct-".length(), this.name.lastIndexOf('-'));
        var descriptor = new PointerByReference();
        if (!SecurityApi.INSTANCE.ConvertStringSecurityDescriptorToSecurityDescriptor("D:P(A;;GA;;;" + sid + ")", 1, descriptor, null)) {
            throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
        }
        try {
            var security = new WinBase.SECURITY_ATTRIBUTES();
            security.lpSecurityDescriptor = descriptor.getValue();
            security.bInheritHandle = false;
            var pipe = Kernel32.INSTANCE.CreateNamedPipe(
                this.name,
                WinBase.PIPE_ACCESS_DUPLEX | WinNT.FILE_FLAG_OVERLAPPED | (this.firstPipe ? 0x00080000 : 0),
                WinBase.PIPE_TYPE_MESSAGE | WinBase.PIPE_READMODE_MESSAGE | 0x00000008,
                8,
                8192,
                8192,
                1000,
                security
            );
            if (WinBase.INVALID_HANDLE_VALUE.equals(pipe)) {
                throw new Win32Exception(Kernel32.INSTANCE.GetLastError());
            }
            this.firstPipe = false;
            this.handles.add(pipe);
            return pipe;
        } finally {
            Kernel32.INSTANCE.LocalFree(descriptor.getValue());
        }
    }

    private void respond(WinNT.HANDLE pipe) {
        try {
            byte[] buffer = new byte[8192];
            int count = this.transfer(pipe, buffer, false);
            byte[] request = Arrays.copyOf(buffer, count);
            long ticket = this.generation.incrementAndGet();
            var response = this.analysis.submit(() -> {
                if (ticket != this.generation.get()) {
                    throw new CancellationException("superseded");
                }
                byte[] result = this.requests.apply(request);
                if (ticket != this.generation.get()) {
                    throw new CancellationException("superseded");
                }
                return result;
            });
            byte[] bytes = response.get(1000, TimeUnit.MILLISECONDS);
            this.transfer(pipe, bytes, true);
        } catch (Exception failure) {
            if (this.running) {
                System.err.println("pipe_request_skipped");
            }
        } finally {
            this.release(pipe);
        }
    }

    private void release(WinNT.HANDLE pipe) {
        if (this.handles.remove(pipe)) {
            Kernel32.INSTANCE.DisconnectNamedPipe(pipe);
            Kernel32.INSTANCE.CloseHandle(pipe);
        }
    }

    private int transfer(WinNT.HANDLE pipe, byte[] bytes, boolean writing) throws java.io.IOException {
        var operation = new WinBase.OVERLAPPED();
        operation.hEvent = Kernel32.INSTANCE.CreateEvent(null, true, false, null);
        var count = new IntByReference();
        try {
            boolean immediate = writing
                ? Kernel32.INSTANCE.WriteFile(pipe, bytes, bytes.length, count, operation)
                : Kernel32.INSTANCE.ReadFile(pipe, bytes, bytes.length, count, operation);
            int error = immediate ? 0 : Kernel32.INSTANCE.GetLastError();
            if (!this.complete(pipe, operation, immediate, error, 1000)
                    || !PipeApi.INSTANCE.GetOverlappedResult(pipe, operation, count, false)) {
                throw new java.io.IOException("pipe_transfer_failed");
            }
            return count.getValue();
        } finally {
            Kernel32.INSTANCE.CloseHandle(operation.hEvent);
        }
    }

    private boolean complete(WinNT.HANDLE pipe, WinBase.OVERLAPPED operation, boolean immediate, int error, int timeout) {
        if (immediate) {
            return true;
        }
        if (error != WinError.ERROR_IO_PENDING) {
            return false;
        }
        if (Kernel32.INSTANCE.WaitForSingleObject(operation.hEvent, timeout) == WinBase.WAIT_OBJECT_0) {
            return this.running;
        }
        PipeApi.INSTANCE.CancelIoEx(pipe, operation);
        PipeApi.INSTANCE.GetOverlappedResult(pipe, operation, new IntByReference(), true);
        return false;
    }

    @PreDestroy
    @Override
    public synchronized void close() {
        this.running = false;
        for (var pipe : this.handles) {
            PipeApi.INSTANCE.CancelIoEx(pipe, null);
            this.release(pipe);
        }
        this.connections.shutdownNow();
        this.analysis.shutdownNow();
    }
}
