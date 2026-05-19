package com.redhat.demo;

public class SchedulingData {

    public record DoctorInfo(String doctorId, String name, String specialty, String credentials, String location) {}
    public record AvailableSlot(String doctorId, String doctorName, String specialty, String dateTime, String location) {}
    public record AppointmentConfirmation(String confirmationNumber, String patientId, String doctorId, String doctorName, String dateTime, String urgency, String reason) {}
    
}
