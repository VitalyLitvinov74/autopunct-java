package ru.autopunct.typing.assistance.presentation;

import an.awesome.pipelinr.Pipeline;
import jakarta.annotation.PreDestroy;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import ru.autopunct.typing.assistance.application.commands.ExcludeApplication.ExcludeApplicationCommand;
import ru.autopunct.typing.assistance.application.commands.SwitchPause.SwitchPauseCommand;
import ru.autopunct.typing.assistance.application.queries.Status.StatusQuery;
import ru.autopunct.typing.assistance.domain.events.ProfileChangedEvent;
import ru.autopunct.windows.application.commands.ChangeAutostart.ChangeAutostartCommand;
import ru.autopunct.windows.infrastructure.Desktop;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

/** Владеет стандартным меню Windows; пользовательские действия отправляются через CQRS. */
@Component
@ConditionalOnProperty(name = "autopunct.tray.enabled", havingValue = "true")
public final class TrayMenu implements AutoCloseable {
    private final Pipeline pipeline;
    private final Desktop desktop;
    private final ConfigurableApplicationContext context;
    private final MenuItem pause = new MenuItem("Пауза");
    private final CheckboxMenuItem exclusion = new CheckboxMenuItem("Исключить приложение");
    private final CheckboxMenuItem autoStart = new CheckboxMenuItem("Автозапуск");
    private TrayIcon icon;
    private String application = "";

    public TrayMenu(Pipeline pipeline, Desktop desktop, ConfigurableApplicationContext context) {
        this.pipeline = pipeline;
        this.desktop = desktop;
        this.context = context;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void open() throws AWTException {
        if (!SystemTray.isSupported()) {
            throw new IllegalStateException("system_tray_unavailable");
        }
        var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        graphics.setColor(new Color(25, 110, 210));
        graphics.setFont(new Font(Font.SERIF, Font.BOLD, 37));
        graphics.drawString(",", 9, 22);
        graphics.dispose();
        var menu = new PopupMenu();
        menu.add(this.pause);
        menu.add(this.exclusion);
        menu.add(this.autoStart);
        menu.addSeparator();
        var exit = new MenuItem("Выход");
        menu.add(exit);
        this.icon = new TrayIcon(image, "Автозапятые", menu);
        this.icon.setImageAutoSize(true);
        this.icon.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent event) {
                TrayMenu.this.application = TrayMenu.this.desktop.foregroundApplication();
                TrayMenu.this.refresh();
            }
        });
        this.pause.addActionListener(event -> this.perform(() -> this.pipeline.send(new SwitchPauseCommand())));
        this.exclusion.addItemListener(event -> this.perform(() -> this.pipeline.send(new ExcludeApplicationCommand(this.application, this.exclusion.getState()))));
        this.autoStart.addItemListener(event -> this.perform(() -> this.pipeline.send(new ChangeAutostartCommand(this.autoStart.getState()))));
        exit.addActionListener(event -> this.context.close());
        SystemTray.getSystemTray().add(this.icon);
        this.refresh();
    }

    @EventListener(ProfileChangedEvent.class)
    public void profileChanged() {
        EventQueue.invokeLater(this::refresh);
    }

    private void perform(Runnable action) {
        try {
            action.run();
            this.refresh();
        } catch (RuntimeException failure) {
            this.icon.setToolTip("Автозапятые: действие не выполнено");
            System.err.println("desktop_action_failed");
        }
    }

    private void refresh() {
        if (this.icon == null) {
            return;
        }
        try {
            var status = this.pipeline.send(new StatusQuery(this.application));
            this.pause.setLabel(status.paused() ? "Продолжить" : "Пауза");
            this.exclusion.setEnabled(!this.application.isEmpty());
            this.exclusion.setLabel(this.application.isEmpty() ? "Приложение неизвестно" : "Исключить " + this.application);
            this.exclusion.setState(status.excluded());
            this.autoStart.setState(status.autoStart());
            this.icon.setToolTip(status.paused() ? "Автозапятые: пауза" : "Автозапятые: работают");
        } catch (RuntimeException failure) {
            this.icon.setToolTip("Автозапятые: настройки недоступны");
        }
    }

    @PreDestroy
    @Override
    public void close() {
        if (this.icon != null) {
            SystemTray.getSystemTray().remove(this.icon);
        }
    }
}
