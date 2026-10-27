package com.ticketrush.auth.service;

import com.ticketrush.auth.dto.AuthResponse;
import com.ticketrush.auth.dto.LoginRequest;
import com.ticketrush.auth.dto.RegisterRequest;
import com.ticketrush.common.exception.BusinessRuleException;
import com.ticketrush.common.security.JwtTokenProvider;
import com.ticketrush.common.security.UserPrincipal;
import com.ticketrush.user.entity.Role;
import com.ticketrush.user.entity.User;
import com.ticketrush.user.repository.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final AuthenticationManager authenticationManager;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider tokenProvider,
            AuthenticationManager authenticationManager) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.authenticationManager = authenticationManager;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("Email is already registered: " + request.email());
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        // Default role is CUSTOMER for all registrations
        User user = new User(request.email(), encodedPassword, request.name(), Role.CUSTOMER);
        User savedUser = userRepository.save(user);

        UserPrincipal principal = UserPrincipal.create(savedUser);
        String token = tokenProvider.generateToken(principal);

        return new AuthResponse(
                token,
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getName(),
                savedUser.getRole().name()
        );
    }

    public AuthResponse login(LoginRequest request) {
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        SecurityContextHolder.getContext().setAuthentication(authentication);
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        String token = tokenProvider.generateToken(principal);

        return new AuthResponse(
                token,
                principal.getId(),
                principal.getUsername(),
                principal.getName(),
                principal.getRole().name()
        );
    }
}
