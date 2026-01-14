package com.example.hotel_service.bootstrap;

import com.example.hotel_service.model.Hotel;
import com.example.hotel_service.model.Room;
import com.example.hotel_service.repository.HotelRepository;
import com.example.hotel_service.repository.RoomRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("dev")
public class DevDataLoader implements CommandLineRunner {

    private final HotelRepository hotelRepository;
    private final RoomRepository roomRepository;

    public DevDataLoader(HotelRepository hotelRepository,
                         RoomRepository roomRepository) {
        this.hotelRepository = hotelRepository;
        this.roomRepository = roomRepository;
    }

    @Override
    public void run(String... args) {

        if (hotelRepository.findByName("Hilton").isPresent()) {
            System.out.println("DEV DATA: Hilton exists, skip");
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

        System.out.println("DEV DATA: created hotel + rooms");
    }

}
