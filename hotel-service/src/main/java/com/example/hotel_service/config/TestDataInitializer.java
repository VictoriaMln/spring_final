package com.example.hotel_service.config;

import com.example.hotel_service.model.Hotel;
import com.example.hotel_service.model.Room;
import com.example.hotel_service.repository.HotelRepository;
import com.example.hotel_service.repository.RoomRepository;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
public class TestDataInitializer {

    @Bean
    @Profile("dev")
    public ApplicationRunner initHotelData(HotelRepository hotelRepository, RoomRepository roomRepository) {
        return args -> {
            if (hotelRepository.count() > 0) {
                return;
            }

            Hotel hotel = new Hotel();
            hotel.setName("Hilton");
            hotel.setAddress("Moscow");
            Hotel savedHotel = hotelRepository.save(hotel);

            Room r1 = new Room();
            r1.setHotel(savedHotel);
            r1.setNumber("101");
            r1.setAvailable(true);
            r1.setTimesBooked(0);

            Room r2 = new Room();
            r2.setHotel(savedHotel);
            r2.setNumber("102");
            r2.setAvailable(true);
            r2.setTimesBooked(0);

            roomRepository.save(r1);
            roomRepository.save(r2);

            System.out.println("DEV DATA: created hotel id=" + savedHotel.getId()
                    + ", rooms: 101 and 102");
        };
    }
}
