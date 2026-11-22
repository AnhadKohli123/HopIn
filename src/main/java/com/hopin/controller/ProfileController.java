package com.hopin.controller;

import com.hopin.model.User;
import com.hopin.service.ProfileService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    public record PhotoReq(String dataUrl) {
    }

    @GetMapping("/me")
    public Map<String, Object> me(@RequestAttribute("user") User user) {
        return profiles.me(user);
    }

    @PutMapping("/me")
    public Map<String, Object> update(@RequestAttribute("user") User user, @RequestBody ProfileService.ProfileUpdate p) {
        return profiles.update(user, p);
    }

    @PutMapping("/me/photo")
    public Map<String, Object> setPhoto(@RequestAttribute("user") User user, @RequestBody PhotoReq r) {
        return profiles.setPhoto(user, r.dataUrl());
    }

    @DeleteMapping("/me/photo")
    public Map<String, Object> removePhoto(@RequestAttribute("user") User user) {
        return profiles.removePhoto(user);
    }

    @GetMapping("/{id}/profile")
    public ProfileService.Profile profile(@PathVariable Long id) {
        return profiles.profile(id);
    }
}
