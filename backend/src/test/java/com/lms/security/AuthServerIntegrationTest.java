package com.lms.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
public class AuthServerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testOauth2AuthorizeRedirectsToLogin() throws Exception {
        mockMvc.perform(get("/oauth2/authorize")
                        .param("response_type", "code")
                        .param("client_id", "lms-frontend")
                        .param("redirect_uri", "https://ishaanvijaywargia12.github.io/adaptive-lms/callback")
                        .param("scope", "openid profile email roles")
                        .param("code_challenge", "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGv-h-6nE8")
                        .param("code_challenge_method", "S256"))
                .andExpect(status().is3xxRedirection()); 
                // Should redirect to /login since we are unauthenticated,
                // and NOT throw a 400 Bad Request (which happens if redirect_uri is invalid)
    }
}
