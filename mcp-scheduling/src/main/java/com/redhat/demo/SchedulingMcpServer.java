package com.redhat.demo;

import java.util.List;

import io.quarkiverse.mcp.server.Tool;
import io.quarkiverse.mcp.server.Tool.Annotations;
import io.quarkiverse.mcp.server.ToolArg;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SchedulingMcpServer {

    @Inject
    ScheduleDataStore scheduleDataStore;

    @Tool(
            description = "Search available appointment slots by medical specialty and date range.",
            annotations = @Annotations(readOnlyHint = true))
    public List<SchedulingData.AvailableSlot> getAvailableSlots(
            @ToolArg(description = "Medical specialty, e.g. cardiology, pulmonology") String specialty,
            @ToolArg(description = "Date range, e.g. 'today', 'tomorrow', or 'next week'") String dateRange) {
        return scheduleDataStore.findAvailableSlotsBySpecialty(specialty);
    }

    @Tool(
            description = "Get information about a specific doctor including specialty and credentials.",
            annotations = @Annotations(readOnlyHint = true))
    public SchedulingData.DoctorInfo getDoctorInfo(@ToolArg(description = "Doctor ID") String doctorId) {
        return scheduleDataStore.findDoctorById(doctorId);
    }

    @Tool(description = "Book an appointment for a patient with a specific doctor and time slot.")
    public SchedulingData.AppointmentConfirmation bookAppointment(
            @ToolArg(description = "Patient ID") String patientId,
            @ToolArg(description = "Doctor ID") String doctorId,
            @ToolArg(description = "Appointment date and time in ISO format") String dateTime,
            @ToolArg(description = "Urgency level: routine, urgent, emergent") String urgency,
            @ToolArg(description = "Reason for visit") String reason) {
        return scheduleDataStore.bookAppointment(patientId, doctorId, dateTime, urgency, reason);
    }
}
