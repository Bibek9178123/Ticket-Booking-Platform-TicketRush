package com.ticketrush.user.service;

import com.ticketrush.booking.repository.BookingRepository;
import com.ticketrush.booking.repository.BookingSeatRepository;
import com.ticketrush.common.exception.ResourceNotFoundException;
import com.ticketrush.common.security.UserPrincipal;
import com.ticketrush.user.dto.ChangePasswordRequest;
import com.ticketrush.user.dto.UpdateProfileRequest;
import com.ticketrush.user.dto.UserAccountStatsResponse;
import com.ticketrush.user.dto.UserProfileResponse;
import com.ticketrush.user.entity.User;
import com.ticketrush.user.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final BookingRepository bookingRepository;
    private final BookingSeatRepository bookingSeatRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(
            UserRepository userRepository,
            BookingRepository bookingRepository,
            BookingSeatRepository bookingSeatRepository,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.bookingRepository = bookingRepository;
        this.bookingSeatRepository = bookingSeatRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UserPrincipal principal) {
        User user = findUserById(principal.getId());
        return UserProfileResponse.from(user);
    }

    @Transactional
    public UserProfileResponse updateProfile(UserPrincipal principal, UpdateProfileRequest request) {
        User user = findUserById(principal.getId());

        if (request.name() != null && !request.name().isBlank()) {
            user.setName(request.name().trim());
        }

        if (request.phone() != null) {
            user.setPhone(request.phone().trim().isEmpty() ? null : request.phone().trim());
        }

        if (request.preferredCity() != null && !request.preferredCity().isBlank()) {
            user.setPreferredCity(request.preferredCity().trim());
        }

        User saved = userRepository.save(user);
        return UserProfileResponse.from(saved);
    }

    @Transactional
    public void changePassword(UserPrincipal principal, ChangePasswordRequest request) {
        User user = findUserById(principal.getId());

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("Current password does not match our records.");
        }

        if (passwordEncoder.matches(request.newPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("New password cannot be the same as your current password.");
        }

        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public UserAccountStatsResponse getStats(UserPrincipal principal) {
        User user = findUserById(principal.getId());

        long totalBookings = bookingRepository.countByUserId(user.getId());
        long confirmedTickets = bookingSeatRepository.countConfirmedTicketsByUserId(user.getId());
        BigDecimal totalSpent = bookingRepository.sumConfirmedSpentByUserId(user.getId());

        String memberSince = "Recently";
        if (user.getCreatedAt() != null) {
            memberSince = user.getCreatedAt().format(DateTimeFormatter.ofPattern("MMM yyyy"));
        }

        return new UserAccountStatsResponse(
                totalBookings,
                confirmedTickets,
                totalSpent != null ? totalSpent : BigDecimal.ZERO,
                memberSince
        );
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
    }
}
