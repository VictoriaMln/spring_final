package com.example.booking_service.dto;

public class HotelAvailabilityRequest {

    private String requestId;
    private String startDate;
    private String endDate;

    public HotelAvailabilityRequest() {
    }

    public HotelAvailabilityRequest(String requestId, String startDate, String endDate) {
        this.requestId = requestId;
        this.startDate = startDate;
        this.endDate = endDate;
    }

    public String getRequestId() {
        return requestId;
    }

    public String getStartDate() {
        return startDate;
    }

    public String getEndDate() {
        return endDate;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }
}
