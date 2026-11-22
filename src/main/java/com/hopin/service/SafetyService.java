package com.hopin.service;

import com.hopin.exception.RideNotFoundException;
import com.hopin.exception.SafetyException;
import com.hopin.exception.UnauthorizedActionException;
import com.hopin.exception.UserException;
import com.hopin.model.*;
import com.hopin.repository.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SafetyService {
    private final UserRepository users;
    private final UserBlockRepository blocks;
    private final ReportRepository reports;
    private final RideRepository rides;
    private final BookingRepository bookings;
    private final TripShareRepository shares;
    private final NotificationService notifications;
    private final SecureRandom random = new SecureRandom();

    public SafetyService(UserRepository users, UserBlockRepository blocks, ReportRepository reports,
                         RideRepository rides, BookingRepository bookings, TripShareRepository shares,
                         NotificationService notifications) {
        this.users = users;
        this.blocks = blocks;
        this.reports = reports;
        this.rides = rides;
        this.bookings = bookings;
        this.shares = shares;
        this.notifications = notifications;
    }

    public record ShareView(String token, Long rideId) {
    }

    public boolean blockedEitherWay(Long a, Long b) {
        return blocks.existsByBlockerIdAndBlockedId(a, b) || blocks.existsByBlockerIdAndBlockedId(b, a);
    }

    @Transactional
    public Report report(User me, Long targetId, String reason, String details) {
        User target = other(me, targetId);
        String r = reason == null ? "" : reason.trim();
        if (r.isEmpty() || r.length() > 60) {
            throw new SafetyException("Choose a reason for the report", 400);
        }
        String d = details == null ? null : details.trim();
        if (d != null && d.length() > 500) {
            throw new SafetyException("Details can be at most 500 characters", 400);
        }
        Report rep = new Report();
        rep.reporter = me;
        rep.reported = target;
        rep.reason = r;
        rep.details = d == null || d.isEmpty() ? null : d;
        reports.save(rep);
        for (User admin : users.findByRole(Role.ADMIN)) {
            notifications.notify(admin, "New report: " + me.name + " reported " + target.name + " (" + r + ")", "reports");
        }
        return rep;
    }

    @Transactional
    public void block(User me, Long targetId) {
        User target = other(me, targetId);
        if (target.role == Role.ADMIN) {
            throw new SafetyException("You cannot block an admin", 400);
        }
        if (!blocks.existsByBlockerIdAndBlockedId(me.id, target.id)) {
            UserBlock b = new UserBlock();
            b.blocker = me;
            b.blocked = target;
            blocks.save(b);
        }
    }

    @Transactional
    public void unblock(User me, Long targetId) {
        blocks.findByBlockerIdAndBlockedId(me.id, targetId).ifPresent(blocks::delete);
    }

    public List<User> blocked(User me) {
        return blocks.findByBlockerIdOrderByIdDesc(me.id).stream().map(b -> b.blocked).toList();
    }

    @Transactional
    public Map<String, Object> sos(User me, Long rideId, Double lat, Double lng, String note) {
        Report rep = new Report();
        rep.reporter = me;
        rep.reason = "SOS";
        rep.sos = true;
        rep.rideId = rideId;
        rep.lat = validLat(lat) ? lat : null;
        rep.lng = validLng(lng) ? lng : null;
        rep.details = note == null || note.isBlank() ? null : note.trim().substring(0, Math.min(500, note.trim().length()));
        reports.save(rep);
        for (User admin : users.findByRole(Role.ADMIN)) {
            notifications.notify(admin, "SOS alert from " + me.name + (rideId != null ? " on ride #" + rideId : ""), "reports");
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("reportId", rep.id);
        m.put("emergencyNumber", "112");
        return m;
    }

    @Transactional
    public ShareView startShare(User me, Long rideId) {
        Ride ride = rides.findById(rideId).orElseThrow(() -> new RideNotFoundException(rideId));
        if (!isParticipant(ride, me.id)) {
            throw new UnauthorizedActionException("Only the driver and confirmed riders can share this trip");
        }
        if (ride.status != RideStatus.OPEN) {
            throw new SafetyException("Live sharing is only for upcoming or ongoing rides", 409);
        }
        TripShare existing = shares.findFirstByRideIdAndSharerIdAndActiveTrue(rideId, me.id).orElse(null);
        if (existing != null) {
            return new ShareView(existing.token, rideId);
        }
        byte[] bytes = new byte[18];
        random.nextBytes(bytes);
        TripShare s = new TripShare();
        s.token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        s.ride = ride;
        s.sharer = me;
        shares.save(s);
        return new ShareView(s.token, rideId);
    }

    @Transactional
    public void updateLocation(User me, String token, Double lat, Double lng, Double accuracy) {
        TripShare s = mine(me, token);
        if (!s.active) {
            throw new SafetyException("This live share has ended", 409);
        }
        if (!validLat(lat) || !validLng(lng)) {
            throw new SafetyException("Invalid location", 400);
        }
        s.lat = lat;
        s.lng = lng;
        s.accuracy = accuracy;
        s.updatedAt = LocalDateTime.now();
    }

    @Transactional
    public void stopShare(User me, String token) {
        mine(me, token).active = false;
    }

    public List<ShareView> activeShares(User me) {
        return shares.findBySharerIdAndActiveTrue(me.id).stream()
                .filter(s -> s.ride.status == RideStatus.OPEN)
                .map(s -> new ShareView(s.token, s.ride.id)).toList();
    }

    public Map<String, Object> publicView(String token) {
        TripShare s = shares.findByToken(token).orElseThrow(() -> new SafetyException("This tracking link does not exist", 404));
        Ride r = s.ride;
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("active", s.active && r.status == RideStatus.OPEN);
        m.put("sharerName", s.sharer.name);
        m.put("driverName", r.driver.name);
        m.put("driverId", r.driver.id);
        m.put("driverHasPhoto", Boolean.TRUE.equals(r.driver.hasPhoto));
        m.put("driverPhotoVersion", r.driver.photoVersion);
        m.put("vehicle", r.vehicle == null ? null : r.vehicle.model + " · " + r.vehicle.plate);
        m.put("departTime", r.departTime);
        m.put("rideStatus", r.status);
        List<Map<String, Object>> pts = new ArrayList<>();
        for (RouteUtil.Point p : RouteUtil.points(r)) {
            Map<String, Object> pm = new LinkedHashMap<>();
            pm.put("name", p.name());
            pm.put("lat", p.lat());
            pm.put("lng", p.lng());
            pts.add(pm);
        }
        m.put("points", pts);
        m.put("lat", s.lat);
        m.put("lng", s.lng);
        m.put("accuracy", s.accuracy);
        m.put("updatedAt", s.updatedAt);
        return m;
    }

    private TripShare mine(User me, String token) {
        TripShare s = shares.findByToken(token).orElseThrow(() -> new SafetyException("Live share not found", 404));
        if (!s.sharer.id.equals(me.id)) {
            throw new UnauthorizedActionException("This is not your live share");
        }
        return s;
    }

    private boolean isParticipant(Ride ride, Long userId) {
        if (ride.driver.id.equals(userId)) {
            return true;
        }
        return bookings.findByRideId(ride.id).stream()
                .anyMatch(b -> b.rider.id.equals(userId) && b.status == BookingStatus.CONFIRMED);
    }

    private User other(User me, Long targetId) {
        if (me.id.equals(targetId)) {
            throw new SafetyException("You cannot do this to yourself", 400);
        }
        return users.findById(targetId).orElseThrow(() -> new UserException("User not found", 404));
    }

    private boolean validLat(Double v) {
        return v != null && v >= -90 && v <= 90;
    }

    private boolean validLng(Double v) {
        return v != null && v >= -180 && v <= 180;
    }
}
