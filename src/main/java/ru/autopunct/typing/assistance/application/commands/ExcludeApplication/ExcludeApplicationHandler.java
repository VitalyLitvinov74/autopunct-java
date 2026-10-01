package ru.autopunct.typing.assistance.application.commands.ExcludeApplication;

import an.awesome.pipelinr.Command;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import ru.autopunct.typing.assistance.infrastructure.ProfileRepository;

@Component
public final class ExcludeApplicationHandler implements Command.Handler<ExcludeApplicationCommand, Void> {
    private final ProfileRepository profileRepository;
    private final ApplicationEventPublisher events;

    public ExcludeApplicationHandler(ProfileRepository profileRepository, ApplicationEventPublisher events) {
        this.profileRepository = profileRepository;
        this.events = events;
    }

    @Override
    public Void handle(ExcludeApplicationCommand command) {
        try {
            var profile = this.profileRepository.current();
            profile.exclude(command.application(), command.excluded());
            this.profileRepository.save(profile);
            profile.releaseEvents().forEach(this.events::publishEvent);
            return null;
        } catch (Exception failure) {
            throw new IllegalStateException("profile_storage_failed", failure);
        }
    }
}
