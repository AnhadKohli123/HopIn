package com.hopin.service;

import com.hopin.exception.RideException;
import com.hopin.exception.UnauthorizedActionException;
import com.hopin.model.Ride;
import com.hopin.model.RideAlert;
import com.hopin.model.Role;
import com.hopin.model.User;
import com.hopin.repository.RideAlertRepository;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlertService {
    private final RideAlertRepository alerts;
    private final NotificationService notifications;
    private final MailService mail;
    private final SafetyService safety;

    public AlertService(RideAlertRepository alerts, NotificationService notifications, MailService mail,
                        SafetyService safety) {
        this.alerts = alerts;
        this.notifications = notifications;
        this.mail = mail;
        this.safety = safety;
    }

    public record AlertReq(@NotBlank String source, @NotBlank String destination, LocalDate date, Integer seats,
                           Double fromLat, Double fromLng, Double toLat, Double toLng, Double radiusKm) {
    }

    @Transactional
    public RideAlert create(User me, AlertReq r) {
        if (me.role != Role.RIDER) {
            throw new UnauthorizedActionException("Only riders can create ride alerts");
        }
        if (alerts.countByUserId(me.id) >= 10) {
            throw new RideException("You can have at most 10 ride alerts. Delete one first", 400);
        }
        if (r.date() != null && r.date().isBefore(LocalDate.now())) {
            throw new RideException("Alert date cannot be in the past", 400);
        }
        RideAlert a = new RideAlert();
        a.user = me;
        a.source = r.source().trim();
        a.destination = r.destination().trim();
        a.date = r.date();
        a.seats = r.seats() == null || r.seats() < 1 ? 1 : r.seats();
        a.fromLat = r.fromLat();
        a.fromLng = r.fromLng();
        a.toLat = r.toLat();
        a.toLng = r.toLng();
        a.radiusKm = clampRadius(r.radiusKm());
        return alerts.save(a);
    }

    public List<RideAlert> list(User me) {
        return alerts.findByUserIdOrderByIdDesc(me.id);
    }

    @Transactional
    public void delete(User me, Long id) {
        RideAlert a = alerts.findById(id).orElseThrow(() -> new RideException("Alert not found", 404));
        if (!a.user.id.equals(me.id)) {
            throw new UnauthorizedActionException("This is not your alert");
        }
        alerts.delete(a);
    }

    public void onRidePosted(Ride ride) {
        LocalDate day = ride.departTime.toLocalDate();
        List<RouteUtil.Point> pts = RouteUtil.points(ride);
        for (RideAlert a : alerts.findAll()) {
            if (a.date != null && a.date.isBefore(LocalDate.now())) {
                continue;
            }
            if (a.date != null && !a.date.equals(day)) {
                continue;
            }
            if (a.seats > ride.seatsLeft || a.user.id.equals(ride.driver.id)) {
                continue;
            }
            if (safety.blockedEitherWay(a.user.id, ride.driver.id)) {
                continue;
            }
            double radius = clampRadius(a.radiusKm);
            RouteUtil.Match m = RouteUtil.match(pts,
                    new RouteUtil.End(a.source, a.fromLat, a.fromLng, radius),
                    new RouteUtil.End(a.destination, a.toLat, a.toLng, radius));
            if (m == null) {
                continue;
            }
            String when = ride.departTime.format(DateTimeFormatter.ofPattern("d MMM, h:mm a"));
            String text = "Ride alert: " + ride.driver.name + " posted " + pts.get(m.from()).name() + " → "
                    + pts.get(m.to()).name() + " on " + when;
            notifications.notifySearch(a.user, text, a.source, a.destination, day);
            if (Boolean.TRUE.equals(a.user.emailVerified) && mail.enabled()) {
                mail.send(a.user.email, "HopIn: a ride matching your alert was posted",
                        "Hi " + a.user.name + ",\n\n" + text + ".\nOpen HopIn and search " + a.source + " → "
                                + a.destination + " to book it before the seats go.");
            }
        }
    }

    private double clampRadius(Double r) {
        if (r == null) {
            return 10;
        }
        return Math.max(1, Math.min(50, r));
    }
}
