package com.example.booking_service.controller;

import com.example.booking_service.dto.HotelAvailabilityRequest;
import com.example.booking_service.model.*;
import com.example.booking_service.repository.BookingRepository;
import com.example.booking_service.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.util.retry.Retry;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.LocalDate;

@RestController
@RequestMapping("/api")
public class BookingController {

    private final WebClient webClient;
    private final BookingRepository bookingRepository;
    private final UserRepository userRepository;
    private static final Logger log = LoggerFactory.getLogger(BookingController.class);

    @Value("${hotel.service.url:http://hotel-service}")
    private String hotelServiceUrl;


    public BookingController(BookingRepository bookingRepository, UserRepository userRepository, WebClient webClient) {
        this.bookingRepository = bookingRepository;
        this.userRepository = userRepository;
        this.webClient = webClient;
    }

    public static class CreateBookingRequest {
        private Long roomId;
        private Boolean autoSelect;
        private LocalDate startDate;
        private LocalDate endDate;

        public CreateBookingRequest() {}

        public Long getRoomId() { return roomId; }
        public Boolean getAutoSelect() { return autoSelect; }
        public LocalDate getStartDate() { return startDate; }
        public LocalDate getEndDate() { return endDate; }

        public void setRoomId(Long roomId) { this.roomId = roomId; }
        public void setAutoSelect(Boolean autoSelect) { this.autoSelect = autoSelect; }
        public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
        public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    }

    public static class RoomDto {
        private Long id;
        private String number;

        public RoomDto() {}

        public Long getId() { return id; }
        public String getNumber() { return number; }

        public void setId(Long id) { this.id = id; }
        public void setNumber(String number) { this.number = number; }
    }

    @PreAuthorize("hasRole('USER')")
    @PostMapping("/booking")
    public Booking create(@RequestBody CreateBookingRequest request,
                          @RequestHeader("Authorization") String authHeader,
                          Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (request.getStartDate() == null || request.getEndDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate/endDate are required");
        }
        if (!request.getStartDate().isBefore(request.getEndDate())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before endDate");
        }

        boolean auto = Boolean.TRUE.equals(request.getAutoSelect());

        log.info(
                "Create booking request: user={}, autoSelect={}, roomId={}, startDate={}, endDate={}",
                user.getUsername(),
                request.getAutoSelect(),
                request.getRoomId(),
                request.getStartDate(),
                request.getEndDate()
        );

        if (!auto && request.getRoomId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "roomId is required when autoSelect=false");
        }
        Long selectedRoomId = request.getRoomId();

        if (auto) {
            selectedRoomId = autoSelectRoomId(request, authHeader);
            if (selectedRoomId == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No available rooms for selected dates");
            }
        }

        Booking booking = new Booking();
        booking.setRoomId(selectedRoomId);
        booking.setUser(user);
        booking.setStartDate(request.getStartDate());
        booking.setEndDate(request.getEndDate());
        booking.setStatus(BookingStatus.PENDING);

        Booking saved = bookingRepository.save(booking);

        System.out.println("BOOKING " + saved.getId() + " created, status=PENDING");

        log.info(
                "Booking created with status PENDING: bookingId={}, user={}",
                booking.getId(),
                user.getUsername()
        );

        String requestId = saved.getId().toString();

        HotelAvailabilityRequest hotelReq = new HotelAvailabilityRequest(
                requestId,
                request.getStartDate().toString(),
                request.getEndDate().toString()
        );

        try {
            log.info(
                    "Requesting room availability confirmation: bookingId={}, roomId={}, requestId={}",
                    booking.getId(),
                    selectedRoomId,
                    requestId
            );

            webClient.post()
                    .uri(hotelServiceUrl + "/api/internal/rooms/" + selectedRoomId + "/confirm-availability")
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .bodyValue(hotelReq)
                    .header("Authorization", authHeader)
                    .retrieve()
                    .toBodilessEntity()
                    .timeout(Duration.ofSeconds(2))
                    .retryWhen(
                            Retry.backoff(2, Duration.ofMillis(200))
                                    .filter(ex -> isRetryable(ex))
                    )
                    .block();

            log.info(
                    "Room availability confirmed: bookingId={}, roomId={}, requestId={}",
                    booking.getId(),
                    selectedRoomId,
                    requestId
            );

            saved.setStatus(BookingStatus.CONFIRMED);
            log.info(
                    "Booking status updated to CONFIRMED: bookingId={}",
                    booking.getId()
            );

            Booking confirmed = bookingRepository.save(saved);
            System.out.println("BOOKING " + confirmed.getId() + " confirmed");

            return confirmed;

        } catch (Exception ex) {
            System.out.println("BOOKING " + saved.getId() + " failed, status=CANCELLED, running compensation");
            log.warn(
                    "Room availability confirmation failed: bookingId={}, roomId={}, requestId={}, reason={}",
                    booking.getId(),
                    selectedRoomId,
                    requestId,
                    ex.getMessage()
            );

            saved.setStatus(BookingStatus.CANCELLED);
            Booking cancelled = bookingRepository.save(saved);

            try {
                log.info(
                        "Sending compensation release: bookingId={}, roomId={}, requestId={}",
                        booking.getId(),
                        selectedRoomId,
                        requestId
                );

                webClient.post()
                        .uri(hotelServiceUrl + "/api/internal/rooms/" + selectedRoomId + "/release")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .bodyValue(hotelReq)
                        .header("Authorization", authHeader)
                        .retrieve()
                        .toBodilessEntity()
                        .timeout(Duration.ofSeconds(2))
                        .retryWhen(Retry.backoff(2, Duration.ofMillis(200)).filter(this::isRetryable))
                        .block();
            } catch (Exception ignore) {}

            log.info(
                    "Compensation completed, booking cancelled: bookingId={}",
                    booking.getId()
            );

            return cancelled;
        }
    }

    private boolean isRetryable(Throwable ex) {
        if (ex instanceof java.util.concurrent.TimeoutException) return true;

        if (ex instanceof WebClientResponseException wex) {
            return wex.getStatusCode().is5xxServerError();
        }

        return false;
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/bookings")
    public Object myBookings(Authentication auth) {
        String username = auth.getName();
        User u = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return bookingRepository.findByUserId(u.getId());
    }

    @PreAuthorize("hasRole('USER')")
    @GetMapping("/booking/{id}")
    public Booking getBookingById(@PathVariable Long id,
                                  org.springframework.security.core.Authentication auth) {

        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        return bookingRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));
    }

    @PreAuthorize("hasRole('USER')")
    @DeleteMapping("/booking/{id}")
    public Booking cancelBooking(@PathVariable Long id,
                                 org.springframework.security.core.Authentication auth) {

        String username = auth.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));

        Booking booking = bookingRepository.findByIdAndUser(id, user)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Booking not found"));

        log.info(
                "Cancel booking request: bookingId={}, user={}",
                booking.getId(),
                user.getUsername()
        );

        if (booking.getStatus() == BookingStatus.CANCELLED) {
            log.info(
                    "Booking already cancelled (idempotent): bookingId={}",
                    booking.getId()
            );
            return booking;
        }

        booking.setStatus(BookingStatus.CANCELLED);
        return bookingRepository.save(booking);
    }

    private Long autoSelectRoomId(CreateBookingRequest request, String authHeader) {

        RoomDto[] rooms = webClient.get()
                .uri(hotelServiceUrl + "/api/rooms/recommend")
                .header("Authorization", authHeader)
                .retrieve()
                .bodyToMono(RoomDto[].class)
                .timeout(Duration.ofSeconds(2))
                .retryWhen(Retry.backoff(2, Duration.ofMillis(200)).filter(this::isRetryable))
                .block();

        if (rooms == null || rooms.length == 0) {
            return null;
        }

        for (RoomDto room : rooms) {
            Long roomId = room.getId();
            if (roomId == null) continue;

            HotelAvailabilityRequest hotelReq = new HotelAvailabilityRequest(
                    "precheck-" + System.currentTimeMillis(),
                    request.getStartDate().toString(),
                    request.getEndDate().toString()
            );

            try {
                webClient.post()
                        .uri(hotelServiceUrl + "/api/internal/rooms/" + roomId + "/confirm-availability")
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .bodyValue(hotelReq)
                        .header("Authorization", authHeader)
                        .retrieve()
                        .toBodilessEntity()
                        .timeout(Duration.ofSeconds(2))
                        .retryWhen(Retry.backoff(2, Duration.ofMillis(200)).filter(this::isRetryable))
                        .block();

                try {
                    webClient.post()
                            .uri(hotelServiceUrl + "/api/internal/rooms/" + roomId + "/release")
                            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                            .bodyValue(hotelReq)
                            .header("Authorization", authHeader)
                            .retrieve()
                            .toBodilessEntity()
                            .timeout(Duration.ofSeconds(2))
                            .retryWhen(Retry.backoff(2, Duration.ofMillis(200)).filter(this::isRetryable))
                            .block();
                } catch (Exception ignore) {}

                return roomId;

            } catch (WebClientResponseException wex) {
                if (wex.getStatusCode().value() == 409) {
                    continue;
                }
                throw wex;
            }
        }

        return null;
    }
}
