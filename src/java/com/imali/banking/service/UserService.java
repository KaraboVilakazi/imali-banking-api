package com.imali.banking.service;

import com.imali.banking.domain.entity.User;
import com.imali.banking.exception.UserNotFoundException;
import com.imali.banking.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public User getByEmail(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new UserNotFoundException("User not found with email: " + email));
    }

    public User getById(UUID id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found with id: " + id));
    }

    public User getAuthenticatedUser(Authentication authentication) {
        return getByEmail(authentication.getName());
    }
}
