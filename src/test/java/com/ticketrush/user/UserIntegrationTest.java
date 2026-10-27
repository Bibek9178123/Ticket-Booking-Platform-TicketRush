package com.ticketrush.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketrush.auth.dto.LoginRequest;
import com.ticketrush.user.dto.ChangePasswordRequest;
import com.ticketrush.user.dto.UpdateProfileRequest;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private com.ticketrush.booking.repository.BookingSeatRepository bookingSeatRepository;

    @Autowired
    private com.ticketrush.seat.repository.EventSeatRepository seatRepository;

    @Autowired
    private com.ticketrush.booking.repository.BookingRepository bookingRepository;

    @Autowired
    private com.ticketrush.event.repository.EventRepository eventRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String customerToken;
    private User customer;

    @BeforeEach
    void setUp() throws Exception {
        bookingSeatRepository.deleteAllInBatch();
        seatRepository.deleteAllInBatch();
        bookingRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();

        customer = new User("customer_profile@test.com", passwordEncoder.encode("Password123"), "Jane Doe", Role.CUSTOMER);
        customer.setPhone("+91 9876543210");
        customer.setPreferredCity("Mumbai");
        userRepository.save(customer);

        LoginRequest loginRequest = new LoginRequest("customer_profile@test.com", "Password123");
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        customerToken = objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    @Test
    @DisplayName("GET /api/users/me: Returns authenticated customer profile")
    void testGetProfile_Success() throws Exception {
        mockMvc.perform(get("/api/users/me")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("customer_profile@test.com"))
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.phone").value("+91 9876543210"))
                .andExpect(jsonPath("$.preferredCity").value("Mumbai"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("GET /api/users/me: Unauthenticated request returns 401 Unauthorized")
    void testGetProfile_Unauthenticated() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("PUT /api/users/me: Updates customer profile details successfully")
    void testUpdateProfile_Success() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("Jane Smith", "+91 9123456789", "Bangalore");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Smith"))
                .andExpect(jsonPath("$.phone").value("+91 9123456789"))
                .andExpect(jsonPath("$.preferredCity").value("Bangalore"));

        User updated = userRepository.findById(customer.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Jane Smith");
        assertThat(updated.getPhone()).isEqualTo("+91 9123456789");
        assertThat(updated.getPreferredCity()).isEqualTo("Bangalore");
    }

    @Test
    @DisplayName("PUT /api/users/me: Blank name returns 400 Bad Request")
    void testUpdateProfile_BlankName_Returns400() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("", "+91 9123456789", "Bangalore");

        mockMvc.perform(put("/api/users/me")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Name cannot be blank")));
    }

    @Test
    @DisplayName("PUT /api/users/me/password: Successfully changes password")
    void testChangePassword_Success() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("Password123", "NewSecretPass456");

        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated successfully."));

        // Verify login works with new password
        LoginRequest newLogin = new LoginRequest("customer_profile@test.com", "NewSecretPass456");
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newLogin)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("PUT /api/users/me/password: Incorrect current password returns 400 Bad Request")
    void testChangePassword_WrongCurrentPassword_Returns400() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("WrongPass999", "NewSecretPass456");

        mockMvc.perform(put("/api/users/me/password")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password does not match our records."));
    }

    @Test
    @DisplayName("GET /api/users/me/stats: Returns user lifetime booking stats")
    void testGetStats_Success() throws Exception {
        mockMvc.perform(get("/api/users/me/stats")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalBookings").value(0))
                .andExpect(jsonPath("$.confirmedTickets").value(0))
                .andExpect(jsonPath("$.totalSpent").value(0.00))
                .andExpect(jsonPath("$.memberSince").isNotEmpty());
    }
}
