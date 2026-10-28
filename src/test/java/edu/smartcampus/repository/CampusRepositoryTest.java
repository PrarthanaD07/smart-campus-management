package edu.smartcampus.repository;

import edu.smartcampus.model.Booking;
import edu.smartcampus.model.Course;
import edu.smartcampus.model.CourseEnrollment;
import edu.smartcampus.model.Student;
import edu.smartcampus.service.BookingService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampusRepositoryTest {
    private CampusRepositories repositories;
    private BookingService bookingService;

    @BeforeEach
    void setUp() {
        repositories = new CampusRepositories("jdbc:sqlite::memory:");
        bookingService = new BookingService(repositories.bookings);
    }

    @AfterEach
    void tearDown() {
        bookingService.close();
        repositories.close();
    }

    @Test
    void studentRepositorySupportsCreateUpdateSearchAndDelete() {
        repositories.students.save(new Student("S1", "Asha Rao", "asha@example.edu", "Computer Science", 2));
        repositories.students.save(new Student("S1", "Asha Rao", "asha@example.edu", "Computer Science", 3));

        assertEquals(1, repositories.students.findAll().size());
        assertEquals(3, repositories.students.findById("S1").orElseThrow().getYear());
        assertTrue(repositories.students.delete("S1"));
        assertTrue(repositories.students.findById("S1").isEmpty());
    }

    @Test
    void deletingStudentRemovesTheirCourseEnrollments() {
        repositories.students.save(new Student("S1", "Asha Rao", "asha@example.edu", "Computer Science", 2));
        repositories.courses.save(new Course("CS101", "Programming", "", 4));
        repositories.enrollments.save(new CourseEnrollment("CS101::S1", "CS101", "S1"));

        assertEquals("S1", repositories.enrollments.findById("CS101::S1").orElseThrow().studentId());
        assertTrue(repositories.students.delete("S1"));
        assertTrue(repositories.enrollments.findAll().isEmpty());
    }

    @Test
    void bookingRejectsOverlappingTimesForTheSameResource() {
        LocalDateTime start = LocalDateTime.of(2026, 10, 23, 10, 0);
        bookingService.book(new Booking("B1", "ROOM1", "S1", start, start.plusHours(1)));

        assertThrows(IllegalArgumentException.class, () ->
                bookingService.book(new Booking("B2", "ROOM1", "S2", start.plusMinutes(30), start.plusHours(2))));
        bookingService.book(new Booking("B3", "ROOM2", "S2", start, start.plusHours(1)));
        assertEquals(2, repositories.bookings.findAll().size());
    }

    @Test
    void concurrentBookingsCannotClaimTheSameSlot() throws Exception {
        LocalDateTime start = LocalDateTime.of(2026, 10, 23, 10, 0);
        Future<Booking> first = bookingService.bookAsync(
                new Booking("B1", "ROOM1", "S1", start, start.plusHours(1)));
        Future<Booking> second = bookingService.bookAsync(
                new Booking("B2", "ROOM1", "S2", start, start.plusHours(1)));
        int successfulBookings = 0;
        for (Future<Booking> future : List.of(first, second)) {
            try {
                future.get();
                successfulBookings++;
            } catch (ExecutionException exception) {
                assertTrue(exception.getCause() instanceof IllegalArgumentException);
            }
        }

        assertEquals(1, successfulBookings);
        assertEquals(1, repositories.bookings.findAll().size());
    }
}
