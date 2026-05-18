package com.redhat.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ScheduleDataStore {

    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    private final Map<String, DoctorSlot> doctors = new LinkedHashMap<>();

    @PostConstruct
    void init() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        addDoctor(
                "D-101",
                "Dr. Sarah Chen",
                "Cardiology",
                "MD, FACC, Hypertensive Heart Disease specialist",
                "Cardiology Clinic, Building A",
                List.of(LocalDateTime.of(today, LocalTime.of(14, 30)), LocalDateTime.of(tomorrow, LocalTime.of(9, 0))));
        addDoctor(
                "D-102",
                "Dr. Raj Patel",
                "Cardiology",
                "MD, General Cardiology",
                "Heart Center, Building A",
                List.of(LocalDateTime.of(today, LocalTime.of(16, 0))));
        addDoctor(
                "D-103",
                "Dr. Emily Torres",
                "Pulmonology",
                "MD, FCCP",
                "Pulmonary Services, Building B",
                List.of(
                        LocalDateTime.of(tomorrow, LocalTime.of(10, 0)),
                        LocalDateTime.of(tomorrow, LocalTime.of(14, 0))));
        addDoctor(
                "D-104",
                "Dr. Michael O'Brien",
                "Obstetrics",
                "MD, OB-GYN",
                "Women's Health Pavilion",
                List.of(
                        LocalDateTime.of(today, LocalTime.of(15, 0)),
                        LocalDateTime.of(tomorrow, LocalTime.of(11, 0))));
        addDoctor(
                "D-105",
                "Dr. Anna Kowalski",
                "Internal Medicine",
                "MD, Board Certified",
                "Internal Medicine, Main Campus",
                List.of(
                        LocalDateTime.of(today, LocalTime.of(13, 0)),
                        LocalDateTime.of(tomorrow, LocalTime.of(8, 30))));
    }

    private void addDoctor(
            String id,
            String name,
            String specialty,
            String credentials,
            String location,
            List<LocalDateTime> slots) {
        SchedulingData.DoctorInfo info = new SchedulingData.DoctorInfo(id, name, specialty, credentials, location);
        doctors.put(id, new DoctorSlot(info, new ArrayList<>(slots)));
    }

    public SchedulingData.DoctorInfo findDoctorById(String doctorId) {
        if (doctorId == null) {
            return null;
        }
        DoctorSlot ds = doctors.get(doctorId.trim());
        return ds == null ? null : ds.info();
    }

    public synchronized List<SchedulingData.AvailableSlot> findAvailableSlotsBySpecialty(String specialty) {
        String needle = specialty == null ? "" : specialty.trim().toLowerCase(Locale.ROOT);
        List<SchedulingData.AvailableSlot> out = new ArrayList<>();
        for (DoctorSlot ds : doctors.values()) {
            if (!needle.isEmpty()
                    && !ds.info().specialty().toLowerCase(Locale.ROOT).equals(needle)) {
                continue;
            }
            for (LocalDateTime when : ds.availableTimes()) {
                out.add(new SchedulingData.AvailableSlot(
                        ds.info().doctorId(),
                        ds.info().name(),
                        ds.info().specialty(),
                        when.format(ISO),
                        ds.info().location()));
            }
        }
        out.sort(Comparator.comparing(SchedulingData.AvailableSlot::dateTime));
        return out;
    }

    public synchronized SchedulingData.AppointmentConfirmation bookAppointment(
            String patientId,
            String doctorId,
            String dateTime,
            String urgency,
            String reason) {

        if (patientId == null || patientId.isBlank()) {
            return failed(patientId, doctorId, "", dateTime, urgency, "Patient ID is required.");
        }

        LocalDateTime parsed;
        try {
            parsed = LocalDateTime.parse(dateTime, ISO);
        } catch (DateTimeParseException e) {
            return failed(
                    patientId,
                    doctorId,
                    "",
                    dateTime,
                    urgency,
                    "Invalid date and time. Use ISO local date time format, e.g. 2026-05-18T14:30:00.");
        }

        DoctorSlot ds = doctors.get(doctorId == null ? "" : doctorId.trim());
        if (ds == null) {
            return failed(
                    patientId,
                    doctorId,
                    "",
                    dateTime,
                    urgency,
                    "No doctor found for ID " + doctorId + ".");
        }

        boolean removed = ds.availableTimes().removeIf(t -> t.equals(parsed));
        if (!removed) {
            return failed(
                    patientId,
                    doctorId,
                    ds.info().name(),
                    dateTime,
                    urgency,
                    "No available slot for "
                            + ds.info().name()
                            + " at "
                            + dateTime
                            + ".");
        }

        String confirmationNumber =
                "APT-" + String.format("%06d", ThreadLocalRandom.current().nextInt(0, 1_000_000));
        return new SchedulingData.AppointmentConfirmation(
                confirmationNumber,
                patientId.trim(),
                ds.info().doctorId(),
                ds.info().name(),
                parsed.format(ISO),
                urgency == null ? "" : urgency,
                reason == null ? "" : reason);
    }

    private static SchedulingData.AppointmentConfirmation failed(
            String patientId,
            String doctorId,
            String doctorName,
            String dateTime,
            String urgency,
            String message) {
        return new SchedulingData.AppointmentConfirmation(
                "FAILED",
                patientId == null ? "" : patientId,
                doctorId == null ? "" : doctorId,
                doctorName == null ? "" : doctorName,
                dateTime == null ? "" : dateTime,
                urgency == null ? "" : urgency,
                message);
    }

    private record DoctorSlot(SchedulingData.DoctorInfo info, List<LocalDateTime> availableTimes) {}
}
