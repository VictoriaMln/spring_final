package com.example.hotel_service.repository;

import com.example.hotel_service.model.RoomHold;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Optional;

public interface RoomHoldRepository extends JpaRepository<RoomHold, Long> {
    Optional<RoomHold> findByRequestId(String requestId);

    boolean existsByRoomIdAndStatusAndStartDateLessThanAndEndDateGreaterThan(
            Long roomId,
            RoomHold.HoldStatus status,
            LocalDate endDateExclusive,
            LocalDate startDateExclusive
    );

    long countByRoomIdAndStatusAndStartDateLessThanEqualAndEndDateGreaterThan(
            Long roomId,
            RoomHold.HoldStatus status,
            LocalDate today,
            LocalDate today2
    );
}
