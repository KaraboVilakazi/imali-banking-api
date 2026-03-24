package com.imali.banking.service;

import com.imali.banking.domain.entity.User;
import com.imali.banking.domain.enums.AuditAction;
import com.imali.banking.domain.enums.UserRole;
import com.imali.banking.dto.request.LoginRequest;
import com.imali.banking.dto.request.RegisterRequest;
import com.imali.banking.dto.response.AuthResponse;
import com.imali.banking.exception.UserAlreadyExistsException;
import com.imali.banking.repository.UserRepository;
import com.imali.banking.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final AuditLogService auditLogService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("An account with email " + request.getEmail() + " already exists");
        }

        User user = User.builder()
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(UserRole.CUSTOMER)
                .build();

        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername(user.getEmail());
        String token = jwtTokenProvider.generateToken(userDetails);

        auditLogService.log(AuditAction.REGISTER, user.getId(), "New customer registered: " + user.getEmail());

        return AuthResponse.builder()
                .accessToken(token)
                .build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        UserDetails userDetails = userDetailsService.loadUserByUsername(request.getEmail());
        String token = jwtTokenProvider.generateToken(userDetails);

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();
        auditLogService.log(AuditAction.LOGIN, user.getId(), "Login from: " + request.getEmail());

        return AuthResponse.builder()
                .accessToken(token)
                .build();
    }
}
