package com.hopin.controller;

import com.hopin.exception.SafetyException;
import com.hopin.exception.UnauthorizedActionException;
import com.hopin.model.*;
import com.hopin.repository.BookingRepository;
import com.hopin.repository.ReportRepository;
import com.hopin.repository.RideRepository;
import com.hopin.repository.UserRepository;
import com.hopin.service.NotificationService;
import com.hopin.service.Views;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final UserRepository users;
    private final RideRepository rides;
    private final BookingRepository bookings;
    private final ReportRepository reports;
    private final NotificationService notifications;

    public AdminController(UserRepository users, RideRepository rides, BookingRepository bookings,
                           ReportRepository reports, NotificationService notifications) {
        this.users = users;
        this.rides = rides;
        this.bookings = bookings;
        this.reports = reports;
        this.notifications = notifications;
    }

    @GetMapping("/users")
    public List<Map<String, Object>> users(@RequestAttribute("user") User admin) {
        requireAdmin(admin);
        return users.findAll().stream().map(Views::self).toList();
    }

    @PostMapping("/users/{id}/block")
    public Map<String, Object> block(@RequestAttribute("user") User admin, @PathVariable Long id) {
        return setBlocked(admin, id, true);
    }

    @PostMapping("/users/{id}/unblock")
    public Map<String, Object> unblock(@RequestAttribute("user") User admin, @PathVariable Long id) {
        return setBlocked(admin, id, false);
    }

    @GetMapping("/reports")
    public List<Report> reports(@RequestAttribute("user") User admin) {
        requireAdmin(admin);
        return reports.findAllByOrderByIdDesc();
    }

    @PostMapping("/reports/{id}/resolve")
    public Report resolve(@RequestAttribute("user") User admin, @PathVariable Long id) {
        requireAdmin(admin);
        Report r = reports.findById(id).orElseThrow(() -> new SafetyException("Report not found", 404));
        r.status = "RESOLVED";
        return reports.save(r);
    }

    @GetMapping("/rides")
    public List<Ride> rides(@RequestAttribute("user") User admin) {
        requireAdmin(admin);
        return rides.findAll();
    }

    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestAttribute("user") User admin) {
        requireAdmin(admin);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("users", users.count());
        m.put("rides", rides.count());
        m.put("bookings", bookings.count());
        m.put("confirmedBookings", bookings.findAll().stream().filter(b -> b.status == BookingStatus.CONFIRMED).count());
        m.put("openReports", reports.countByStatus("OPEN"));
        m.put("openSos", reports.countBySosTrueAndStatus("OPEN"));
        return m;
    }

    private Map<String, Object> setBlocked(User admin, Long id, boolean value) {
        requireAdmin(admin);
        User u = users.findById(id).orElseThrow(() -> new UnauthorizedActionException("User not found"));
        if (u.role == Role.ADMIN) {
            throw new UnauthorizedActionException("Cannot block an admin");
        }
        u.blocked = value;
        return Views.self(users.save(u));
    }

    private void requireAdmin(User u) {
        if (u.role != Role.ADMIN) {
            throw new UnauthorizedActionException("Admin access only");
        }
    }
}
