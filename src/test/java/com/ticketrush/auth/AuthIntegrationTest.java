package com.ticketrush.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketrush.auth.dto.LoginRequest;
import com.ticketrush.auth.dto.RegisterRequest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthIntegrationTest {

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

    @BeforeEach
    void setUp() {
        bookingSeatRepository.deleteAllInBatch();
        seatRepository.deleteAllInBatch();
        bookingRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("Should successfully register a new customer and return JWT")
    void testRegisterNewCustomer_Success() throws Exception {
        RegisterRequest request = new RegisterRequest(
                "john.doe@example.com",
                "secretPassword123",
                "John Doe"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.email").value("john.doe@example.com"))
                .andExpect(jsonPath("$.name").value("John Doe"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));

        assertThat(userRepository.findByEmail("john.doe@example.com")).isPresent();
    }

    @Test
    @DisplayName("Should return 409 Conflict when registering with duplicate email")
    void testRegisterDuplicateEmail_Returns409() throws Exception {
        User existing = new User("duplicate@example.com", passwordEncoder.encode("pass123"), "Existing", Role.CUSTOMER);
        userRepository.save(existing);

        RegisterRequest request = new RegisterRequest(
                "duplicate@example.com",
                "anotherPassword123",
                "Duplicate User"
        );

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").value("Email is already registered: duplicate@example.com"));
    }

    @Test
    @DisplayName("Should successfully log in with valid credentials")
    void testLogin_Success() throws Exception {
        User user = new User("login.user@example.com", passwordEncoder.encode("correctPass123"), "Login User", Role.CUSTOMER);
        userRepository.save(user);

        LoginRequest request = new LoginRequest("login.user@example.com", "correctPass123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.email").value("login.user@example.com"));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized with invalid password")
    void testLogin_InvalidPassword_Returns401() throws Exception {
        User user = new User("wrong.pass@example.com", passwordEncoder.encode("realPass123"), "User", Role.CUSTOMER);
        userRepository.save(user);

        LoginRequest request = new LoginRequest("wrong.pass@example.com", "incorrectPassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Should access /api/auth/me with valid Bearer token")
    void testGetCurrentUser_WithValidToken() throws Exception {
        User user = new User("profile.user@example.com", passwordEncoder.encode("pass123"), "Profile User", Role.CUSTOMER);
        userRepository.save(user);

        LoginRequest loginRequest = new LoginRequest("profile.user@example.com", "pass123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("profile.user@example.com"))
                .andExpect(jsonPath("$.name").value("Profile User"))
                .andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    @DisplayName("Should return 401 Unauthorized when accessing /api/auth/me without token")
    void testGetCurrentUser_WithoutToken_Returns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("RBAC: Customer should receive 403 Forbidden on admin endpoint")
    void testAdminEndpoint_WithCustomerToken_Returns403() throws Exception {
        User customer = new User("customer@test.com", passwordEncoder.encode("pass123"), "Customer", Role.CUSTOMER);
        userRepository.save(customer);

        LoginRequest loginRequest = new LoginRequest("customer@test.com", "pass123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String customerToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("RBAC: Admin should successfully access admin endpoint")
    void testAdminEndpoint_WithAdminToken_Returns200() throws Exception {
        User admin = new User("admin@test.com", passwordEncoder.encode("adminPass123"), "Admin", Role.ADMIN);
        userRepository.save(admin);

        LoginRequest loginRequest = new LoginRequest("admin@test.com", "adminPass123");
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest)))
                .andExpect(status().isOk())
                .andReturn();

        String adminToken = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("accessToken").asText();

        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }
}
