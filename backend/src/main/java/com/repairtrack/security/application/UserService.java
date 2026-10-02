package com.repairtrack.security.application;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.repairtrack.security.infrastructure.UserRepository;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public UserProfile getProfile(UUID userId) {
        return users.findById(userId)
                .map(UserProfile::of)
                .orElseThrow(UserNotFoundException::new);
    }
}
