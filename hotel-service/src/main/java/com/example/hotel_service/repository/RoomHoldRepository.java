package com.example.hotel_service.repository;

import com.example.hotel_service.model.RoomHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RoomHoldRepository extends JpaRepository<RoomHold, Long> {
    Optional<RoomHold> findByRequestId(String requestId);

    boolean existsByRoomIdAndStatusAndStartDateLessThanAndEndDateGreaterThan(
            Long roomId,
            RoomHold.HoldStatus status,
            java.time.LocalDate endDateExclusive,
            java.time.LocalDate startDateExclusive
    );
}
