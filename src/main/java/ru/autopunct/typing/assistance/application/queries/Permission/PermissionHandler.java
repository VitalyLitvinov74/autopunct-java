package ru.autopunct.typing.assistance.application.queries.Permission;

import an.awesome.pipelinr.Command;
import org.springframework.stereotype.Component;
import ru.autopunct.typing.assistance.infrastructure.ProfileRepository;

@Component
public final class PermissionHandler implements Command.Handler<PermissionQuery, Boolean> {
    private final ProfileRepository profileRepository;
    public PermissionHandler(ProfileRepository profileRepository) {
        this.profileRepository = profileRepository;
    }

    @Override
    public Boolean handle(PermissionQuery query) {
        try {
            return this.profileRepository.current().permits(query.application());
        } catch (Exception failure) {
            throw new IllegalStateException("profile_storage_failed", failure);
        }
    }
}
