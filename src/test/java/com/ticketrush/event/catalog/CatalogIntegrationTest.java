package com.ticketrush.event.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketrush.auth.dto.LoginRequest;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String adminToken;
    private String customerToken;

    @BeforeEach
    void setUp() throws Exception {
        if (!userRepository.existsByEmail("admin_catalog@test.com")) {
            userRepository.save(new User("admin_catalog@test.com", passwordEncoder.encode("Pass@123"), "Admin Catalog", Role.ADMIN));
        }
        if (!userRepository.existsByEmail("cust_catalog@test.com")) {
            userRepository.save(new User("cust_catalog@test.com", passwordEncoder.encode("Pass@123"), "Customer Catalog", Role.CUSTOMER));
        }

        adminToken = obtainToken("admin_catalog@test.com", "Pass@123");
        customerToken = obtainToken("cust_catalog@test.com", "Pass@123");
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
    @DisplayName("Integration: ADMIN can trigger catalog synchronization")
    void testCatalogSyncAdminAllowed() throws Exception {
        mockMvc.perform(post("/api/catalog/sync")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("Integration: CUSTOMER is forbidden from triggering catalog synchronization")
    void testCatalogSyncCustomerForbidden() throws Exception {
        mockMvc.perform(post("/api/catalog/sync")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Integration: Search movies endpoint returns matching results")
    void testSearchMoviesEndpoint() throws Exception {
        mockMvc.perform(get("/api/catalog/search-movies?query=Dune")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
