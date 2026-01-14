package com.example.hotel_service.controller;

import com.example.hotel_service.model.Hotel;
import com.example.hotel_service.model.Room;
import com.example.hotel_service.repository.HotelRepository;
import com.example.hotel_service.repository.RoomRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final RoomRepository roomRepository;
    private final HotelRepository hotelRepository;

    public RoomController(RoomRepository roomRepository, HotelRepository hotelRepository) {
        this.roomRepository = roomRepository;
        this.hotelRepository = hotelRepository;
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
}
