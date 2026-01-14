package com.example.booking_service.controller;

import com.example.booking_service.model.Booking;
import com.example.booking_service.model.User;
import com.example.booking_service.repository.BookingRepository;
import com.example.booking_service.repository.UserRepository;
import com.example.booking_service.security.SecurityConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

import java.time.LocalDate;
import java.util.Optional;

import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = BookingController.class)
@AutoConfigureMockMvc(addFilters = true)
@Import({SecurityConfig.class})
class BookingControllerTest {

    @Autowired MockMvc mockMvc;

    @Autowired BookingRepository bookingRepository;
    @Autowired UserRepository userRepository;
    @Autowired WebClient webClient;

    WebClient.RequestBodyUriSpec postSpec;
    WebClient.RequestBodySpec postBodySpec;
    WebClient.RequestHeadersSpec postHeadersSpec;
    WebClient.ResponseSpec postRespSpec;

    WebClient.RequestHeadersUriSpec getSpec;
    WebClient.RequestHeadersSpec getHeadersSpec;
    WebClient.ResponseSpec getRespSpec;

    @TestConfiguration
    static class TestConfig {
        @Bean BookingRepository bookingRepository() { return Mockito.mock(BookingRepository.class); }
        @Bean UserRepository userRepository() { return Mockito.mock(UserRepository.class); }
        @Bean WebClient webClient() { return Mockito.mock(WebClient.class); }
    }

    @BeforeEach
    void setupWebClientMocks() {
        getSpec = Mockito.mock(WebClient.RequestHeadersUriSpec.class);
        getHeadersSpec = Mockito.mock(WebClient.RequestHeadersSpec.class);
        getRespSpec = Mockito.mock(WebClient.ResponseSpec.class);

        postSpec = Mockito.mock(WebClient.RequestBodyUriSpec.class);
        postBodySpec = Mockito.mock(WebClient.RequestBodySpec.class);
        postHeadersSpec = Mockito.mock(WebClient.RequestHeadersSpec.class);
        postRespSpec = Mockito.mock(WebClient.ResponseSpec.class);

        Mockito.reset(webClient, bookingRepository, userRepository);
    }

    @Test
    void createBooking_shouldReturn400_whenDatesInvalid() throws Exception {
        User u = new User();
        u.setId(1L);
        u.setUsername("vi");
        u.setRole("USER");
        when(userRepository.findByUsername("vi")).thenReturn(Optional.of(u));

        String body = """
                    {"autoSelect":true, "startDate":"2026-04-01", "endDate":"2026-04-01"}
                """;

        mockMvc.perform(post("/api/booking")
                        .contentType(APPLICATION_JSON)
                        .header("Authorization", "TestAuth")
                        .content(body)
                        .with(user("vi").roles("USER"))
                )
                .andExpect(status().isBadRequest());
    }

        @Test
    void createBooking_autoSelect_shouldReturn200_andConfirmed() throws Exception {
        User u = new User();
        u.setId(1L);
        u.setUsername("vi");
        u.setRole("USER");
        when(userRepository.findByUsername("vi")).thenReturn(Optional.of(u));

        BookingController.RoomDto roomDto = new BookingController.RoomDto();
        roomDto.setId(10L);
        roomDto.setNumber("101");

        when(webClient.get()).thenReturn(getSpec);
        when(getSpec.uri(eq("http://hotel-service/api/rooms/recommend"))).thenReturn(getHeadersSpec);
        when(getHeadersSpec.header(eq("Authorization"), anyString())).thenReturn(getHeadersSpec);
        when(getHeadersSpec.retrieve()).thenReturn(getRespSpec);
        when(getRespSpec.bodyToMono(eq(BookingController.RoomDto[].class)))
                .thenReturn(Mono.just(new BookingController.RoomDto[]{roomDto}));

        when(webClient.post()).thenReturn(postSpec);
        when(postSpec.uri(startsWith("http://hotel-service/api/internal/rooms/"))).thenReturn(postBodySpec);
        when(postBodySpec.contentType(any())).thenReturn(postBodySpec);
        when(postBodySpec.bodyValue(any())).thenReturn(postHeadersSpec);
        when(postHeadersSpec.header(eq("Authorization"), anyString())).thenReturn(postHeadersSpec);
        when(postHeadersSpec.retrieve()).thenReturn(postRespSpec);
        when(postRespSpec.toBodilessEntity()).thenReturn(Mono.just(ResponseEntity.ok().build()));

        when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(inv -> {
                    Booking b = inv.getArgument(0);
                    if (b.getId() == null) b.setId(777L);
                    return b;
                });

        String body = """
            {"autoSelect":true, "startDate":"2026-04-01", "endDate":"2026-04-03"}
        """;

            mockMvc.perform(post("/api/booking")
                            .contentType(APPLICATION_JSON)
                            .header("Authorization", "TestAuth")
                            .content(body)
                            .with(user("vi").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(777)))
                .andExpect(jsonPath("$.roomId", is(10)))
                .andExpect(jsonPath("$.status", is("CONFIRMED")));

        verify(webClient, atLeastOnce()).post();
        verify(webClient, atLeastOnce()).get();
    }

    @Test
    void myBookings_shouldReturn401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/bookings"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void createBooking_shouldReturn400_whenRoomIdMissing_andAutoSelectFalse() throws Exception {
        User u = new User();
        u.setId(1L);
        u.setUsername("vi");
        u.setRole("USER");
        when(userRepository.findByUsername("vi")).thenReturn(Optional.of(u));

        String body = """
        {"autoSelect":false, "startDate":"2026-04-01", "endDate":"2026-04-03"}
    """;

        mockMvc.perform(post("/api/booking")
                        .contentType(APPLICATION_JSON)
                        .header("Authorization", "TestAuth")
                        .content(body)
                        .with(user("vi").roles("USER")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createBooking_shouldReturn403_whenRoleNotUser() throws Exception {
        String body = """
        {"autoSelect":true, "startDate":"2026-04-01", "endDate":"2026-04-03"}
    """;

        mockMvc.perform(post("/api/booking")
                        .contentType(APPLICATION_JSON)
                        .header("Authorization", "TestAuth")
                        .content(body)
                        .with(user("vi").roles("ADMIN")))  // нет USER
                .andExpect(status().isForbidden());
    }
    @Test
    void createBooking_shouldReturn200_andCancelled_whenHotelConfirmFails() throws Exception {
        User u = new User();
        u.setId(1L);
        u.setUsername("vi");
        u.setRole("USER");
        when(userRepository.findByUsername("vi")).thenReturn(Optional.of(u));

        BookingController.RoomDto roomDto = new BookingController.RoomDto();
        roomDto.setId(10L);
        roomDto.setNumber("101");

        when(webClient.get()).thenReturn(getSpec);
        when(getSpec.uri(anyString())).thenReturn(getHeadersSpec);
        when(getHeadersSpec.header(eq("Authorization"), anyString()))
                .thenReturn(getHeadersSpec);
        when(getHeadersSpec.retrieve()).thenReturn(getRespSpec);
        when(getRespSpec.bodyToMono(eq(BookingController.RoomDto[].class)))
                .thenReturn(Mono.just(new BookingController.RoomDto[]{roomDto}));

        when(webClient.post()).thenReturn(postSpec);
        when(postSpec.uri(anyString())).thenReturn(postBodySpec);
        when(postBodySpec.contentType(any())).thenReturn(postBodySpec);
        when(postBodySpec.bodyValue(any())).thenReturn(postHeadersSpec);
        when(postHeadersSpec.header(eq("Authorization"), anyString())).thenReturn(postHeadersSpec);
        when(postHeadersSpec.retrieve()).thenReturn(postRespSpec);

        when(postRespSpec.toBodilessEntity())
                .thenReturn(Mono.error(new RuntimeException("hotel down")))
                .thenReturn(Mono.just(ResponseEntity.ok().build()));

        when(bookingRepository.save(any(Booking.class)))
                .thenAnswer(inv -> {
                    Booking b = inv.getArgument(0);
                    if (b.getId() == null) b.setId(999L);
                    return b;
                });

        String body = """
            {"autoSelect":false, "roomId":10, "startDate":"2026-04-01", "endDate":"2026-04-03"}
        """;

        mockMvc.perform(post("/api/booking")
                        .contentType(APPLICATION_JSON)
                        .header("Authorization", "TestAuth")
                        .content(body)
                        .with(user("vi").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(999)))
                .andExpect(jsonPath("$.roomId", is(10)))
                .andExpect(jsonPath("$.status", is("CANCELLED")));

        verify(bookingRepository, atLeast(2)).save(any(Booking.class));
        verify(webClient, atLeast(2)).post();
    }
}
