package com.hopin.controller;

import com.hopin.exception.UnauthorizedActionException;
import com.hopin.model.*;
import com.hopin.repository.BookingRepository;
import com.hopin.repository.RideRepository;
import com.hopin.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserRepository users;
    private final RideRepository rides;
    private final BookingRepository bookings;

    public AdminController(UserRepository users, RideRepository rides, BookingRepository bookings) {
        this.users = users;
        this.rides = rides;
        this.bookings = bookings;
    }

    @GetMapping("/users")
    public List<User> users(@RequestAttribute("user") User admin) {
        requireAdmin(admin);
        return users.findAll();
    }

    @PostMapping("/users/{id}/block")
    public User block(@RequestAttribute("user") User admin, @PathVariable Long id) {
        return setBlocked(admin, id, true);
    }

    @PostMapping("/users/{id}/unblock")
    public User unblock(@RequestAttribute("user") User admin, @PathVariable Long id) {
        return setBlocked(admin, id, false);
    }

    @GetMapping("/rides")
    public List<Ride> rides(@RequestAttribute("user") User admin) {
        requireAdmin(admin);
        return rides.findAll();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestAttribute("user") User admin) {
        requireAdmin(admin);
        return Map.of(
                "users", users.count(),
                "rides", rides.count(),
                "bookings", bookings.count(),
                "confirmedBookings", bookings.findAll().stream()
                        .filter(b -> b.status == BookingStatus.CONFIRMED).count());
    }

    private User setBlocked(User admin, Long id, boolean value) {
        requireAdmin(admin);
        User u = users.findById(id).orElseThrow(() -> new UnauthorizedActionException("User not found"));
        if (u.role == Role.ADMIN) {
            throw new UnauthorizedActionException("Cannot block an admin");
        }
        u.blocked = value;
        return users.save(u);
    }

    private void requireAdmin(User u) {
        if (u.role != Role.ADMIN) {
            throw new UnauthorizedActionException("Admin access only");
        }
    }
}
