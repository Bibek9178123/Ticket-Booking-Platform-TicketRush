package com.ticketrush.admin.service;

import com.ticketrush.admin.dto.AdminBookingResponse;
import com.ticketrush.admin.dto.AdminUserResponse;
import com.ticketrush.admin.dto.PlatformStatsResponse;
import com.ticketrush.booking.repository.BookingRepository;
import com.ticketrush.common.exception.ResourceNotFoundException;
import com.ticketrush.event.repository.EventRepository;
import com.ticketrush.user.entity.Role;
import com.ticketrush.user.entity.User;
import com.ticketrush.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final BookingRepository bookingRepository;

    public AdminService(
            UserRepository userRepository,
            EventRepository eventRepository,
            BookingRepository bookingRepository) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.bookingRepository = bookingRepository;
    }

    @Transactional(readOnly = true)
    public Page<AdminUserResponse> listUsers(Pageable pageable) {
        return userRepository.findAll(pageable).map(AdminUserResponse::from);
    }

    @Transactional(readOnly = true)
    public Page<AdminBookingResponse> listAllBookings(Pageable pageable) {
        return bookingRepository.findAll(pageable).map(AdminBookingResponse::from);
    }

    @Transactional
    public AdminUserResponse updateUserRole(Long userId, Role newRole) {
        return updateUserRole(userId, newRole, null);
    }

    @Transactional
    public AdminUserResponse updateUserRole(Long userId, Role newRole, Long currentUserId) {
        if (currentUserId != null && userId.equals(currentUserId) && newRole != Role.ADMIN) {
            throw new com.ticketrush.common.exception.BusinessRuleException(
                    "Self-demotion is not allowed: An administrator cannot revoke their own admin privileges.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        user.setRole(newRole);
        User updated = userRepository.save(user);
        return AdminUserResponse.from(updated);
    }

    @Transactional
    public AdminUserResponse updateUserStatus(Long userId, boolean active) {
        return updateUserStatus(userId, active, null);
    }

    @Transactional
    public AdminUserResponse updateUserStatus(Long userId, boolean active, Long currentUserId) {
        if (currentUserId != null && userId.equals(currentUserId) && !active) {
            throw new com.ticketrush.common.exception.BusinessRuleException(
                    "Self-deactivation is not allowed: You cannot deactivate your own administrator account.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        user.setActive(active);
        User updated = userRepository.save(user);
        return AdminUserResponse.from(updated);
    }

    @Transactional(readOnly = true)
    public PlatformStatsResponse getPlatformStats() {
        long totalUsers = userRepository.count();
        long totalEvents = eventRepository.count();
        long totalBookings = bookingRepository.count();
        BigDecimal totalRevenue = bookingRepository.sumConfirmedRevenue();
        return new PlatformStatsResponse(
                totalUsers,
                totalEvents,
                totalBookings,
                totalRevenue != null ? totalRevenue : BigDecimal.ZERO
        );
    }
}
