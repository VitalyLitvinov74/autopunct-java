package ru.autopunct.typing.assistance.application.commands.SwitchPause;

import an.awesome.pipelinr.Command;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import ru.autopunct.typing.assistance.infrastructure.ProfileRepository;

@Component
public final class SwitchPauseHandler implements Command.Handler<SwitchPauseCommand, Void> {
    private final ProfileRepository profileRepository;
    private final ApplicationEventPublisher events;

    public SwitchPauseHandler(ProfileRepository profileRepository, ApplicationEventPublisher events) {
        this.profileRepository = profileRepository;
        this.events = events;
    }

    @Override
    public Void handle(SwitchPauseCommand command) {
        try {
            var profile = this.profileRepository.current();
            profile.switchPause();
            this.profileRepository.save(profile);
            profile.releaseEvents().forEach(this.events::publishEvent);
            return null;
        } catch (Exception failure) {
            throw new IllegalStateException("profile_storage_failed", failure);
        }
    }
}
