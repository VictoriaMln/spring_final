package com.example.booking_service.repository;

import com.example.booking_service.model.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import com.example.booking_service.model.User;

import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {
    List<Booking> findByUserId(Long userId);

    Optional<Booking> findByIdAndUser(Long id, User user);
}
