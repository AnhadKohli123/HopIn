package com.hopin.controller;

import com.hopin.model.User;
import com.hopin.service.VerificationService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/verify")
public class VerificationController {
    private final VerificationService verification;

    public VerificationController(VerificationService verification) {
        this.verification = verification;
    }

    public record CodeReq(String code) {
    }

    public record PhoneReq(String phone) {
    }

    public record IdReq(String idType, String number) {
    }

    @PostMapping("/email/send")
    public VerificationService.SendResult sendEmail(@RequestAttribute("user") User user) {
        return verification.sendEmail(user);
    }

    @PostMapping("/email/confirm")
    public Map<String, Object> confirmEmail(@RequestAttribute("user") User user, @RequestBody CodeReq r) {
        return verification.confirm(user, "EMAIL", r.code());
    }

    @PostMapping("/phone/send")
    public VerificationService.SendResult sendPhone(@RequestAttribute("user") User user, @RequestBody PhoneReq r) {
        return verification.sendPhone(user, r.phone());
    }

    @PostMapping("/phone/confirm")
    public Map<String, Object> confirmPhone(@RequestAttribute("user") User user, @RequestBody CodeReq r) {
        return verification.confirm(user, "PHONE", r.code());
    }

    @PostMapping("/id")
    public Map<String, Object> submitId(@RequestAttribute("user") User user, @RequestBody IdReq r) {
        return verification.submitId(user, r.idType(), r.number());
    }
}
