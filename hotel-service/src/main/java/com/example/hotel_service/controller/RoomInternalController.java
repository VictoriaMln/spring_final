package com.example.hotel_service.controller;

import com.example.hotel_service.model.Room;
import com.example.hotel_service.model.RoomHold;
import com.example.hotel_service.repository.RoomHoldRepository;
import com.example.hotel_service.repository.RoomRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/internal/rooms")
public class RoomInternalController {

    private static final Logger log = LoggerFactory.getLogger(RoomInternalController.class);

    private final RoomRepository roomRepository;
    private final RoomHoldRepository holdRepository;

    public RoomInternalController(RoomRepository roomRepository, RoomHoldRepository holdRepository) {
        this.roomRepository = roomRepository;
        this.holdRepository = holdRepository;
    }

    public static class AvailabilityRequest {
        private String requestId;
        private String startDate;
        private String endDate;

        public String getRequestId() { return requestId; }
        public String getStartDate() { return startDate; }
        public String getEndDate() { return endDate; }

        public void setRequestId(String requestId) { this.requestId = requestId; }
        public void setStartDate(String startDate) { this.startDate = startDate; }
        public void setEndDate(String endDate) { this.endDate = endDate; }
    }

    @PostMapping("/{id}/confirm-availability")
    @Transactional
    public void confirm(@PathVariable Long id, @RequestBody AvailabilityRequest req) {
        if (req.getRequestId() == null || req.getRequestId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId is required");
        }

        var start = java.time.LocalDate.parse(req.getStartDate());
        var end = java.time.LocalDate.parse(req.getEndDate());
        if (!start.isBefore(end)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must be before endDate");
        }
        log.info(
                "Confirm availability request: roomId={}, requestId={}, startDate={}, endDate={}",
                id,
                req.getRequestId(),
                req.getStartDate(),
                req.getEndDate()
        );

        var existing = holdRepository.findByRequestId(req.getRequestId());
        if (existing.isPresent()) {
            if (existing.get().getStatus() == RoomHold.HoldStatus.HOLD) {
                log.info("Idempotent confirm request detected, skipping: requestId={}", req.getRequestId());
                return;
            }
            log.warn("Confirm received for already RELEASED hold: requestId={}", req.getRequestId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Hold already released");
        }

        Room room = roomRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Room not found"));

        if (!room.isAvailable()) {
            log.warn("Room disabled (available=false): roomId={}, requestId={}", id, req.getRequestId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is disabled (available=false)");
        }

        boolean overlap = holdRepository.existsByRoomIdAndStatusAndStartDateLessThanAndEndDateGreaterThan(
                room.getId(),
                RoomHold.HoldStatus.HOLD,
                end,
                start
        );
        if (overlap) {
            log.warn("Room not available for requested dates: roomId={}, requestId={}", id, req.getRequestId());
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Room is already booked for these dates");
        }

        RoomHold hold = new RoomHold();
        hold.setRequestId(req.getRequestId());
        hold.setRoom(room);
        hold.setStartDate(start);
        hold.setEndDate(end);
        hold.setStatus(RoomHold.HoldStatus.HOLD);
        holdRepository.save(hold);
        log.info(
                "Room hold created: roomId={}, requestId={}, startDate={}, endDate={}",
                id, req.getRequestId(), start, end
        );

        room.setTimesBooked(room.getTimesBooked() + 1);
        roomRepository.save(room);
    }

    @PostMapping("/{id}/release")
    @Transactional
    public void release(@PathVariable Long id, @RequestBody AvailabilityRequest req) {
        if (req.getRequestId() == null || req.getRequestId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "requestId is required");
        }
        log.info(
                "Release request received: roomId={}, requestId={}",
                id,
                req.getRequestId()
        );

        var holdOpt = holdRepository.findByRequestId(req.getRequestId());
        if (holdOpt.isEmpty()) return;

        var hold = holdOpt.get();
        if (!hold.getRoom().getId().equals(id)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "requestId belongs to another room");
        }

        if (hold.getStatus() == RoomHold.HoldStatus.RELEASED) {
            log.info(
                    "Hold already released (idempotent): requestId={}",
                    req.getRequestId()
            );
            return;
        }

        hold.setStatus(RoomHold.HoldStatus.RELEASED);
        holdRepository.save(hold);
        log.info("Hold released: roomId={}, requestId={}", id, req.getRequestId());
    }
}