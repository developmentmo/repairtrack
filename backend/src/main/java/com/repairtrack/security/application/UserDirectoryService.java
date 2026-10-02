package com.repairtrack.security.application;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.security.UserDirectory;
import com.repairtrack.security.UserSummary;
import com.repairtrack.security.domain.User;
import com.repairtrack.security.infrastructure.UserRepository;

@Service
class UserDirectoryService implements UserDirectory {

    private final UserRepository users;

    UserDirectoryService(UserRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UserSummary> findActiveByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        return users.findByEmail(User.normalizeEmail(email))
                .filter(User::isActive)
                .map(UserDirectoryService::toSummary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, UserSummary> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(ids).stream()
                .map(UserDirectoryService::toSummary)
                .collect(Collectors.toMap(UserSummary::id, Function.identity()));
    }

    private static UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getEmail(), user.getFirstName(), user.getLastName(), user.isActive());
    }
}
