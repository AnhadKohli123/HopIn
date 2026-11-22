package com.hopin.controller;

import com.hopin.model.Notification;
import com.hopin.model.RideAlert;
import com.hopin.model.User;
import com.hopin.service.AlertService;
import com.hopin.service.NotificationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AlertController {
    private final AlertService alerts;
    private final NotificationService notifications;

    public AlertController(AlertService alerts, NotificationService notifications) {
        this.alerts = alerts;
        this.notifications = notifications;
    }

    @PostMapping("/alerts")
    public RideAlert create(@RequestAttribute("user") User user, @Valid @RequestBody AlertService.AlertReq r) {
        return alerts.create(user, r);
    }

    @GetMapping("/alerts")
    public List<RideAlert> list(@RequestAttribute("user") User user) {
        return alerts.list(user);
    }

    @DeleteMapping("/alerts/{id}")
    public Map<String, Boolean> delete(@RequestAttribute("user") User user, @PathVariable Long id) {
        alerts.delete(user, id);
        return Map.of("ok", true);
    }

    @GetMapping("/notifications")
    public List<Notification> notifications(@RequestAttribute("user") User user) {
        return notifications.list(user);
    }

    @PostMapping("/notifications/read")
    public Map<String, Boolean> read(@RequestAttribute("user") User user) {
        notifications.readAll(user);
        return Map.of("ok", true);
    }
}
