package com.hopin.controller;

import com.hopin.model.User;
import com.hopin.service.SafetyService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class SafetyController {
    private final SafetyService safety;

    public SafetyController(SafetyService safety) {
        this.safety = safety;
    }

    public record ReportReq(String reason, String details) {
    }

    public record SosReq(Long rideId, Double lat, Double lng, String note) {
    }

    public record RideReq(Long rideId) {
    }

    public record LocationReq(Double lat, Double lng, Double accuracy) {
    }

    @PostMapping("/users/{id}/report")
    public Map<String, Object> report(@RequestAttribute("user") User user, @PathVariable Long id, @RequestBody ReportReq r) {
        return Map.of("reportId", safety.report(user, id, r.reason(), r.details()).id);
    }

    @PostMapping("/users/{id}/block")
    public Map<String, Boolean> block(@RequestAttribute("user") User user, @PathVariable Long id) {
        safety.block(user, id);
        return Map.of("blocked", true);
    }

    @DeleteMapping("/users/{id}/block")
    public Map<String, Boolean> unblock(@RequestAttribute("user") User user, @PathVariable Long id) {
        safety.unblock(user, id);
        return Map.of("blocked", false);
    }

    @GetMapping("/users/blocked")
    public List<User> blocked(@RequestAttribute("user") User user) {
        return safety.blocked(user);
    }

    @PostMapping("/sos")
    public Map<String, Object> sos(@RequestAttribute("user") User user, @RequestBody SosReq r) {
        return safety.sos(user, r.rideId(), r.lat(), r.lng(), r.note());
    }

    @PostMapping("/live/start")
    public SafetyService.ShareView start(@RequestAttribute("user") User user, @RequestBody RideReq r) {
        return safety.startShare(user, r.rideId());
    }

    @PostMapping("/live/{token}/location")
    public Map<String, Boolean> location(@RequestAttribute("user") User user, @PathVariable String token,
                                         @RequestBody LocationReq r) {
        safety.updateLocation(user, token, r.lat(), r.lng(), r.accuracy());
        return Map.of("ok", true);
    }

    @PostMapping("/live/{token}/stop")
    public Map<String, Boolean> stop(@RequestAttribute("user") User user, @PathVariable String token) {
        safety.stopShare(user, token);
        return Map.of("ok", true);
    }

    @GetMapping("/live/active")
    public List<SafetyService.ShareView> active(@RequestAttribute("user") User user) {
        return safety.activeShares(user);
    }
}
