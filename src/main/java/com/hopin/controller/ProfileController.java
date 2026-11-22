package com.hopin.controller;

import com.hopin.model.User;
import com.hopin.service.ProfileService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/users")
public class ProfileController {
    private final ProfileService profiles;

    public ProfileController(ProfileService profiles) {
        this.profiles = profiles;
    }

    @GetMapping("/me")
    public User me(@RequestAttribute("user") User user) {
        return profiles.me(user);
    }

    @PutMapping("/me")
    public User update(@RequestAttribute("user") User user, @RequestBody ProfileService.ProfileUpdate p) {
        return profiles.update(user, p);
    }

    @GetMapping("/{id}/profile")
    public ProfileService.Profile profile(@PathVariable Long id) {
        return profiles.profile(id);
    }
}
