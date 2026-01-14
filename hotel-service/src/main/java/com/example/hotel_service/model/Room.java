package com.example.hotel_service.model;

import jakarta.persistence.*;

@Entity
@Table(name = "rooms")
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "hotel_id")
    private Hotel hotel;

    private String number;

    private boolean available = true;

    @Column(name = "times_booked")
    private long timesBooked = 0;

    public Room() {
    }

    public Long getId() {
        return id;
    }

    public Hotel getHotel() {
        return hotel;
    }

    public String getNumber() {
        return number;
    }

    public boolean isAvailable() {
        return available;
    }

    public long getTimesBooked() {
        return timesBooked;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public void setHotel(Hotel hotel) {
        this.hotel = hotel;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public void setTimesBooked(long timesBooked) {
        this.timesBooked = timesBooked;
    }
}
