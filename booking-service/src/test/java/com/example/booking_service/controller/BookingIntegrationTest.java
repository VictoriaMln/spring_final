package com.example.booking_service.controller;

import com.example.booking_service.model.Booking;
import com.example.booking_service.model.BookingStatus;
import com.example.booking_service.model.User;
import com.example.booking_service.repository.BookingRepository;
import com.example.booking_service.repository.UserRepository;
import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.stubbing.Scenario.STARTED;

import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cloud.contract.wiremock.AutoConfigureWireMock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.concurrent.*;

import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@AutoConfigureWireMock(port = 0)
@TestPropertySource(properties = {
        "hotel.service.url=http://localhost:${wiremock.server.port}",
        "app.jwt.secret=test-secret-test-secret-test-secret",
        "app.jwt.issuer=test-issuer",
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false"
})
class BookingIntegrationTest {

    @TestConfiguration
    static class TestWebClientConfig {
        @Bean
        @Primary
        WebClient testWebClient() {
            return WebClient.builder().build();
        }
    }

    @Autowired MockMvc mockMvc;
    @Autowired BookingRepository bookingRepository;
    @Autowired UserRepository userRepository;

   /* @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("hotel.service.url", () -> "http://localhost:" + System.getProperty("wiremock.server.port"));
        r.add("app.jwt.secret", () -> "test-secret-test-secret-test-secret");
        r.add("app.jwt.issuer", () -> "test-issuer");
    } */

    @BeforeEach
    void setup() {
        resetAllRequests();
        resetToDefault();

        bookingRepository.deleteAll();
        userRepository.deleteAll();

        User u = new User();
        u.setUsername("vi");
        u.setPassword("x");
        u.setRole("USER");
        userRepository.save(u);
    }

    @Test
    @WithMockUser(username = "vi", roles = "USER")
    void parallelBookings_oneConfirmed_oneCancelled_andCompensationRuns() throws Exception {

        stubFor(post(urlEqualTo("/api/internal/rooms/10/confirm-availability"))
                .inScenario("confirm")
                .whenScenarioStateIs(STARTED)
                .willSetStateTo("SECOND")
                .willReturn(aResponse().withStatus(200)));

        stubFor(post(urlEqualTo("/api/internal/rooms/10/confirm-availability"))
                .inScenario("confirm")
                .whenScenarioStateIs("SECOND")
                .willReturn(aResponse().withStatus(409)));

        stubFor(post(urlEqualTo("/api/internal/rooms/10/release"))
                .willReturn(aResponse().withStatus(200)));

        String body = """
          {"autoSelect":false, "roomId":10, "startDate":"2026-04-01", "endDate":"2026-04-03"}
        """;

        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);

        Callable<Integer> call = () -> {
            start.await(2, TimeUnit.SECONDS);
            return mockMvc.perform(
                    org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/booking")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("vi").roles("USER"))
                        .contentType(APPLICATION_JSON)
                        .header("Authorization", "TestAuth")
                        .content(body)
                    )
                    .andReturn()
                    .getResponse()
                    .getStatus();
        };

        Future<Integer> f1 = pool.submit(call);
        Future<Integer> f2 = pool.submit(call);

        start.countDown();

        int s1 = f1.get(5, TimeUnit.SECONDS);
        int s2 = f2.get(5, TimeUnit.SECONDS);
        pool.shutdown();

        assertEquals(200, s1);
        assertEquals(200, s2);

        List<Booking> all = bookingRepository.findAll();
        assertEquals(2, all.size());

        long confirmed = all.stream().filter(b -> b.getStatus() == BookingStatus.CONFIRMED).count();
        long cancelled  = all.stream().filter(b -> b.getStatus() == BookingStatus.CANCELLED).count();

        assertEquals(1, confirmed);
        assertEquals(1, cancelled);

        verify(2, postRequestedFor(urlEqualTo("/api/internal/rooms/10/confirm-availability")));
        verify(1, postRequestedFor(urlEqualTo("/api/internal/rooms/10/release")));
    }

    @Test
    @WithMockUser(username = "vi", roles = "USER")
    void bookingCancelled_whenHotelReturns500() throws Exception {

        stubFor(post(urlEqualTo("/api/internal/rooms/10/confirm-availability"))
                .willReturn(aResponse().withStatus(500)));

        stubFor(post(urlEqualTo("/api/internal/rooms/10/release"))
                .willReturn(aResponse().withStatus(200)));

        String body = """
          {"autoSelect":false, "roomId":10, "startDate":"2026-04-01", "endDate":"2026-04-03"}
        """;

        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/booking")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user("vi").roles("USER"))
                        .contentType(APPLICATION_JSON)
                        .header("Authorization", "TestAuth")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomId", is(10)))
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        verify(3, postRequestedFor(urlEqualTo("/api/internal/rooms/10/confirm-availability")));
        verify(1, postRequestedFor(urlEqualTo("/api/internal/rooms/10/release")));
    }
}
