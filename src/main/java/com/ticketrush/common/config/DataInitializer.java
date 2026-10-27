package com.ticketrush.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketrush.event.catalog.service.CatalogSyncService;
import com.ticketrush.event.dto.TierRule;
import com.ticketrush.event.entity.Event;
import com.ticketrush.event.entity.EventStatus;
import com.ticketrush.event.repository.EventRepository;
import com.ticketrush.seat.service.SeatService;
import com.ticketrush.user.entity.Role;
import com.ticketrush.user.entity.User;
import com.ticketrush.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Profile("!test")
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UserRepository userRepository;
    private final EventRepository eventRepository;
    private final SeatService seatService;
    private final CatalogSyncService catalogSyncService;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    public DataInitializer(
            UserRepository userRepository,
            EventRepository eventRepository,
            SeatService seatService,
            CatalogSyncService catalogSyncService,
            PasswordEncoder passwordEncoder,
            ObjectMapper objectMapper) {
        this.userRepository = userRepository;
        this.eventRepository = eventRepository;
        this.seatService = seatService;
        this.catalogSyncService = catalogSyncService;
        this.passwordEncoder = passwordEncoder;
        this.objectMapper = objectMapper;
    }

    @Override
    public void run(String... args) {
        // 1. Seed initial users if none exist
        if (userRepository.count() == 0) {
            log.info("Seeding initial users (admin, organizer, customer)...");

            User admin = new User(
                    "admin@ticketrush.com",
                    passwordEncoder.encode("Admin@123"),
                    "System Admin",
                    Role.ADMIN
            );

            User organizer = new User(
                    "organizer@ticketrush.com",
                    passwordEncoder.encode("Organizer@123"),
                    "Acme Events",
                    Role.ORGANIZER
            );

            User customer = new User(
                    "customer@ticketrush.com",
                    passwordEncoder.encode("Customer@123"),
                    "Alice Customer",
                    Role.CUSTOMER
            );

            userRepository.save(admin);
            userRepository.save(organizer);
            userRepository.save(customer);

            log.info("Users seeded successfully.");
        }

        // 2. Seed events & catalog if none exist
        if (eventRepository.count() == 0) {
            log.info("Database has 0 events. Initializing live catalog with Hindi cinema & live shows...");
            try {
                var result = catalogSyncService.syncCatalog();
                log.info("Live catalog sync completed: {}", result.message());
            } catch (Exception ex) {
                log.warn("Catalog sync failed during startup: {}. Seeding baseline sample event...", ex.getMessage());
                seedBaselineEvent();
            }
        }
    }

    private void seedBaselineEvent() {
        User organizer = userRepository.findByEmail("organizer@ticketrush.com")
                .orElseGet(() -> {
                    User newOrg = new User("organizer@ticketrush.com", passwordEncoder.encode("Organizer@123"), "Acme Events", Role.ORGANIZER);
                    return userRepository.save(newOrg);
                });

        List<TierRule> tiers = List.of(
                new TierRule("VIP", 1, 2, BigDecimal.valueOf(120.00)),
                new TierRule("General", 3, 5, BigDecimal.valueOf(45.00))
        );

        String tiersJson = "[]";
        try {
            tiersJson = objectMapper.writeValueAsString(tiers);
        } catch (Exception ignored) {}

        Event sampleEvent = new Event(
                organizer,
                "Rock Revolution 2026 Live",
                "Experience the electrifying stadium tour featuring legendary rock icons. High energy, lasers, and unmatched sound.",
                "Concerts",
                "San Francisco",
                "Metropolis Arena",
                LocalDateTime.now().plusDays(7).withHour(19).withMinute(0),
                LocalDateTime.now().plusDays(7).withHour(23).withMinute(0),
                5,
                8,
                tiersJson
        );
        sampleEvent.setStatus(EventStatus.PUBLISHED);
        Event savedEvent = eventRepository.save(sampleEvent);

        seatService.generateSeatsForEvent(savedEvent, tiers);
        log.info("Baseline sample event 'Rock Revolution 2026 Live' seeded with 40 seats.");
    }
}
