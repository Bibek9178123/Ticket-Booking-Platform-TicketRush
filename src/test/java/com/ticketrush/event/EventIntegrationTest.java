package com.ticketrush.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketrush.auth.dto.LoginRequest;
import com.ticketrush.event.dto.CreateEventRequest;
import com.ticketrush.event.dto.TierRule;
import com.ticketrush.event.entity.Event;
import com.ticketrush.event.repository.EventRepository;
import com.ticketrush.seat.repository.EventSeatRepository;
import com.ticketrush.user.entity.Role;
import com.ticketrush.user.entity.User;
import com.ticketrush.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EventIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private EventSeatRepository seatRepository;

    @Autowired
    private com.ticketrush.booking.repository.BookingSeatRepository bookingSeatRepository;

    @Autowired
    private com.ticketrush.booking.repository.BookingRepository bookingRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String organizerToken;
    private String customerToken;
    private Long organizerId;

    @BeforeEach
    void setUp() throws Exception {
        bookingSeatRepository.deleteAllInBatch();
        seatRepository.deleteAllInBatch();
        bookingRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        // Create Organizer
        User organizer = new User("org@test.com", passwordEncoder.encode("orgPass123"), "Organizer One", Role.ORGANIZER);
        organizer = userRepository.save(organizer);
        organizerId = organizer.getId();
        organizerToken = obtainToken("org@test.com", "orgPass123");

        // Create Customer
        User customer = new User("cust@test.com", passwordEncoder.encode("custPass123"), "Customer One", Role.CUSTOMER);
        userRepository.save(customer);
        customerToken = obtainToken("cust@test.com", "custPass123");
    }

    private String obtainToken(String email, String password) throws Exception {
        LoginRequest req = new LoginRequest(email, password);
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    @Test
    @DisplayName("Organizer can create a draft event with seat grid and tiers")
    void testCreateEvent_AsOrganizer_Success() throws Exception {
        CreateEventRequest req = new CreateEventRequest(
                "Jazz Night 2026",
                "Smooth jazz evening under the stars.",
                "Music",
                "Chicago",
                "Blue Note Club",
                LocalDateTime.now().plusDays(10),
                LocalDateTime.now().plusDays(10).plusHours(3),
                4,
                6,
                List.of(
                        new TierRule("Front Row", 1, 1, BigDecimal.valueOf(80.00)),
                        new TierRule("Standard", 2, 4, BigDecimal.valueOf(40.00))
                )
        );

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("Jazz Night 2026"))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.rowCount").value(4))
                .andExpect(jsonPath("$.seatsPerRow").value(6));
    }

    @Test
    @DisplayName("Customer cannot create an event (returns 403 Forbidden)")
    void testCreateEvent_AsCustomer_Returns403() throws Exception {
        CreateEventRequest req = new CreateEventRequest(
                "Unauthorized Event",
                "Description",
                "Music",
                "Chicago",
                "Club",
                LocalDateTime.now().plusDays(5),
                LocalDateTime.now().plusDays(5).plusHours(2),
                2,
                4,
                List.of(new TierRule("General", 1, 2, BigDecimal.valueOf(25.00)))
        );

        mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Publishing an event generates all physical event_seats correctly")
    void testPublishEvent_GeneratesSeatsCorrectly() throws Exception {
        // 1. Create Draft: 3 rows x 4 seats = 12 seats
        CreateEventRequest req = new CreateEventRequest(
                "Indie Showcase",
                "Live indie bands",
                "Concerts",
                "Seattle",
                "Pine Box",
                LocalDateTime.now().plusDays(14),
                LocalDateTime.now().plusDays(14).plusHours(4),
                3,
                4,
                List.of(
                        new TierRule("Balcony", 1, 1, BigDecimal.valueOf(75.00)),
                        new TierRule("Floor", 2, 3, BigDecimal.valueOf(35.00))
                )
        );

        MvcResult createRes = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long eventId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        // 2. Publish Event
        mockMvc.perform(post("/api/events/" + eventId + "/publish")
                        .header("Authorization", "Bearer " + organizerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.totalSeats").value(12))
                .andExpect(jsonPath("$.availableSeats").value(12));

        // 3. Verify in Database
        long seatCount = seatRepository.countByEventId(eventId);
        assertThat(seatCount).isEqualTo(12);

        // 4. Verify Seat Map API
        mockMvc.perform(get("/api/events/" + eventId + "/seats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats.length()").value(12))
                .andExpect(jsonPath("$.seats[0].rowLabel").value("A"))
                .andExpect(jsonPath("$.seats[0].seatNumber").value(1))
                .andExpect(jsonPath("$.seats[0].tier").value("Balcony"))
                .andExpect(jsonPath("$.seats[0].price").value(75.00))
                .andExpect(jsonPath("$.seats[0].effectiveStatus").value("AVAILABLE"));
    }

    @Test
    @DisplayName("Another organizer cannot publish someone else's event (403 Forbidden)")
    void testPublishEvent_ByAnotherOrganizer_Returns403() throws Exception {
        // Create second organizer
        User otherOrg = new User("other@test.com", passwordEncoder.encode("pass123"), "Other Org", Role.ORGANIZER);
        userRepository.save(otherOrg);
        String otherOrgToken = obtainToken("other@test.com", "pass123");

        // Org 1 creates event
        CreateEventRequest req = new CreateEventRequest(
                "Secret Gig",
                "Desc",
                "Comedy",
                "New York",
                "Comedy Cellar",
                LocalDateTime.now().plusDays(20),
                LocalDateTime.now().plusDays(20).plusHours(2),
                2,
                2,
                List.of(new TierRule("General", 1, 2, BigDecimal.valueOf(30.00)))
        );

        MvcResult createRes = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long eventId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();

        // Org 2 tries to publish Org 1's event
        mockMvc.perform(post("/api/events/" + eventId + "/publish")
                        .header("Authorization", "Bearer " + otherOrgToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Public search filters events by keyword and city")
    void testSearchEvents_ByKeywordAndCity() throws Exception {
        // Create & publish event
        CreateEventRequest req = new CreateEventRequest(
                "Electronic Dance Festival",
                "Top DJs all night long",
                "EDM",
                "Miami",
                "Bayfront Park",
                LocalDateTime.now().plusDays(30),
                LocalDateTime.now().plusDays(30).plusHours(6),
                2,
                5,
                List.of(new TierRule("GA", 1, 2, BigDecimal.valueOf(99.00)))
        );

        MvcResult createRes = mockMvc.perform(post("/api/events")
                        .header("Authorization", "Bearer " + organizerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andReturn();

        Long eventId = objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asLong();
        mockMvc.perform(post("/api/events/" + eventId + "/publish")
                .header("Authorization", "Bearer " + organizerToken)).andExpect(status().isOk());

        // Search matching
        mockMvc.perform(get("/api/events?keyword=electronic&city=Miami"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Electronic Dance Festival"));

        // Search non-matching
        mockMvc.perform(get("/api/events?keyword=nonexistent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(0));
    }
}
