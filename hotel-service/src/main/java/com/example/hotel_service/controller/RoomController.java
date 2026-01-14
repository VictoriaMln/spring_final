package com.example.hotel_service.controller;

import com.example.hotel_service.model.Hotel;
import com.example.hotel_service.model.Room;
import com.example.hotel_service.repository.HotelRepository;
import com.example.hotel_service.repository.RoomHoldRepository;
import com.example.hotel_service.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import com.example.hotel_service.model.RoomHold;
import org.springframework.security.access.prepost.PreAuthorize;

import java.time.LocalDate;
import java.util.stream.Collectors;


import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;
    private final RoomHoldRepository roomHoldRepository;

    public RoomController(RoomRepository roomRepository, HotelRepository hotelRepository, RoomHoldRepository roomHoldRepository) {
        this.roomRepository = roomRepository;
        this.hotelRepository = hotelRepository;
        this.roomHoldRepository = roomHoldRepository;
    }

    public static class CreateRoomRequest {
        private Long hotelId;
        private String number;
        private Boolean available;

        public CreateRoomRequest() {
        }

        public Long getHotelId() {
            return hotelId;
        }

        public String getNumber() {
            return number;
        }

        public Boolean getAvailable() {
            return available;
        }

        public void setHotelId(Long hotelId) {
            this.hotelId = hotelId;
        }

        public void setNumber(String number) {
            this.number = number;
        }

        public void setAvailable(Boolean available) {
            this.available = available;
        }
    }

    @PostMapping
    public Room create(@RequestBody CreateRoomRequest request) {
        if (request.getHotelId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "hotelId is required");
        }
        if (request.getNumber() == null || request.getNumber().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "number is required");
        }

        Hotel hotel = hotelRepository.findById(request.getHotelId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Hotel not found"));

        Room room = new Room();
        room.setHotel(hotel);
        room.setNumber(request.getNumber());
        if (request.getAvailable() != null) {
            room.setAvailable(request.getAvailable());
        }

        return roomRepository.save(room);
    }

    public static class RoomStatsDto {
        private Long roomId;
        private Long hotelId;
        private String hotelName;
        private String number;
        private boolean available;
        private long timesBooked;
        private long activeHoldsToday;

        public Long getRoomId() { return roomId; }
        public Long getHotelId() { return hotelId; }
        public String getHotelName() { return hotelName; }
        public String getNumber() { return number; }
        public boolean isAvailable() { return available; }
        public long getTimesBooked() { return timesBooked; }
        public long getActiveHoldsToday() { return activeHoldsToday; }

        public void setRoomId(Long roomId) { this.roomId = roomId; }
        public void setHotelId(Long hotelId) { this.hotelId = hotelId; }
        public void setHotelName(String hotelName) { this.hotelName = hotelName; }
        public void setNumber(String number) { this.number = number; }
        public void setAvailable(boolean available) { this.available = available; }
        public void setTimesBooked(long timesBooked) { this.timesBooked = timesBooked; }
        public void setActiveHoldsToday(long activeHoldsToday) { this.activeHoldsToday = activeHoldsToday; }
    }

    @GetMapping
    public List<Room> getAvailableRooms() {
        return roomRepository.findByAvailableTrue();
    }

    @GetMapping("/recommend")
    public List<Room> recommendRooms() {
        List<Room> rooms = roomRepository.findByAvailableTrue();
        rooms.sort(Comparator
                .comparingLong(Room::getTimesBooked)
                .thenComparing(Room::getId));
        return rooms;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public List<RoomStatsDto> getRoomsStats() {
        LocalDate today = LocalDate.now();

        return roomRepository.findAll()
                .stream()
                .map(room -> {
                    RoomStatsDto dto = new RoomStatsDto();
                    dto.setRoomId(room.getId());
                    dto.setNumber(room.getNumber());
                    dto.setAvailable(room.isAvailable());
                    dto.setTimesBooked(room.getTimesBooked());

                    if (room.getHotel() != null) {
                        dto.setHotelId(room.getHotel().getId());
                        dto.setHotelName(room.getHotel().getName());
                    }

                    long activeHolds = roomHoldRepository.countByRoomIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThan(
                            room.getId(),
                            RoomHold.HoldStatus.HOLD,
                            today,
                            today
                    );
                    dto.setActiveHoldsToday(activeHolds);

                    return dto;
                })
                .collect(Collectors.toList());
    }

}
