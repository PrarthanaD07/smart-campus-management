package edu.smartcampus.service;

import edu.smartcampus.model.Booking;
import edu.smartcampus.repository.JdbcCrudRepository;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class BookingService implements AutoCloseable {
    private final JdbcCrudRepository<Booking> bookings;
    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    public BookingService(JdbcCrudRepository<Booking> bookings) {
        this.bookings = bookings;
    }

    public synchronized Booking book(Booking booking) {
        return saveWithoutConflict(booking);
    }

    public synchronized Booking update(Booking booking) {
        return saveWithoutConflict(booking);
    }

    private Booking saveWithoutConflict(Booking booking) {
        boolean conflict = bookings.findAll().stream()
                .filter(existing -> !existing.getId().equals(booking.getId()))
                .filter(existing -> existing.resourceId().equals(booking.resourceId()))
                .anyMatch(existing -> booking.startTime().isBefore(existing.endTime())
                        && booking.endTime().isAfter(existing.startTime()));
        if (conflict) {
            throw new IllegalArgumentException("That resource is already booked during the requested time.");
        }
        bookings.save(booking);
        return booking;
    }

    public Future<Booking> bookAsync(Booking booking) {
        return executor.submit(() -> book(booking));
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
