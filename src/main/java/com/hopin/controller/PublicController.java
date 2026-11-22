package com.hopin.controller;

import com.hopin.service.ProfileService;
import com.hopin.service.SafetyService;
import java.util.Map;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/public")
public class PublicController {
    private final ProfileService profiles;
    private final SafetyService safety;

    public PublicController(ProfileService profiles, SafetyService safety) {
        this.profiles = profiles;
        this.safety = safety;
    }

    @GetMapping("/photos/{id}")
    public ResponseEntity<byte[]> photo(@PathVariable Long id) {
        ProfileService.Photo p = profiles.photo(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(p.type()))
                .cacheControl(CacheControl.noCache())
                .body(p.bytes());
    }

    @GetMapping("/live/{token}")
    public Map<String, Object> live(@PathVariable String token) {
        return safety.publicView(token);
    }
}
