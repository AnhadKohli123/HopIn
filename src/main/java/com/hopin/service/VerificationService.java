package com.hopin.service;

import com.hopin.exception.UserException;
import com.hopin.exception.VerificationException;
import com.hopin.model.User;
import com.hopin.model.VerificationCode;
import com.hopin.repository.UserRepository;
import com.hopin.repository.VerificationCodeRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationService {
    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final Set<String> ID_TYPES = Set.of("AADHAAR", "DRIVING_LICENCE", "PAN", "PASSPORT", "VOTER_ID");
    private final UserRepository users;
    private final VerificationCodeRepository codes;
    private final MailService mail;
    private final SecureRandom random = new SecureRandom();

    public VerificationService(UserRepository users, VerificationCodeRepository codes, MailService mail) {
        this.users = users;
        this.codes = codes;
        this.mail = mail;
    }

    public record SendResult(boolean simulated, String demoCode, String message) {
    }

    @Transactional
    public SendResult sendEmail(User me) {
        User u = fresh(me);
        if (Boolean.TRUE.equals(u.emailVerified)) {
            throw new VerificationException("Your email is already verified");
        }
        VerificationCode c = newCode(u, "EMAIL", u.email);
        boolean sent = mail.send(u.email, "Your HopIn verification code",
                "Hi " + u.name + ",\n\nYour HopIn email verification code is " + c.code
                        + ".\nIt expires in 10 minutes.\n\nIf you did not ask for this, ignore this email.");
        if (sent) {
            return new SendResult(false, null, "Code sent to " + maskEmail(u.email) + ". Check your inbox and spam folder.");
        }
        if (mail.enabled()) {
            throw new VerificationException("The email could not be sent. Please try again in a minute");
        }
        return new SendResult(true, c.code, "Email sending is not set up on this server yet, so here is a demo code.");
    }

    @Transactional
    public SendResult sendPhone(User me, String phone) {
        String p = normalisePhone(phone);
        User u = fresh(me);
        if (users.existsByPhoneAndPhoneVerifiedTrueAndIdNot(p, u.id)) {
            throw new VerificationException("This number is already verified on another account");
        }
        VerificationCode c = newCode(u, "PHONE", p);
        log.info("[HopIn SMS simulated] to +91{} code {}", p, c.code);
        return new SendResult(true, c.code, "SMS is simulated in this demo, so your code is shown here.");
    }

    @Transactional(noRollbackFor = VerificationException.class)
    public Map<String, Object> confirm(User me, String channel, String code) {
        User u = fresh(me);
        VerificationCode c = codes.findTopByUserIdAndChannelOrderByIdDesc(u.id, channel)
                .orElseThrow(() -> new VerificationException("Request a code first"));
        if (c.used) {
            throw new VerificationException("This code was already used. Request a new one");
        }
        if (c.expiresAt.isBefore(LocalDateTime.now())) {
            throw new VerificationException("This code has expired. Request a new one");
        }
        if (c.attempts >= 5) {
            throw new VerificationException("Too many wrong attempts. Request a new code");
        }
        if (code == null || !c.code.equals(code.trim())) {
            c.attempts++;
            codes.save(c);
            throw new VerificationException("Wrong code. " + (5 - c.attempts) + " attempt(s) left");
        }
        c.used = true;
        codes.save(c);
        if ("EMAIL".equals(channel)) {
            u.emailVerified = true;
        } else {
            u.phone = c.target;
            u.phoneVerified = true;
        }
        return Views.self(users.save(u));
    }

    @Transactional
    public Map<String, Object> submitId(User me, String type, String last4) {
        User u = fresh(me);
        if ("APPROVED".equals(u.idStatus)) {
            throw new VerificationException("Your ID is already verified");
        }
        String t = type == null ? "" : type.trim().toUpperCase();
        if (!ID_TYPES.contains(t)) {
            throw new VerificationException("Choose a valid ID type");
        }
        String l = last4 == null ? "" : last4.trim().toUpperCase();
        if (!l.matches("[A-Z0-9]{4}")) {
            throw new VerificationException("Enter exactly the last 4 characters of your ID");
        }
        u.idType = t;
        u.idLast4 = l;
        u.idStatus = "PENDING";
        return Views.self(users.save(u));
    }

    private VerificationCode newCode(User u, String channel, String target) {
        Optional<VerificationCode> last = codes.findTopByUserIdAndChannelOrderByIdDesc(u.id, channel);
        if (last.isPresent()) {
            long wait = 30 - Duration.between(last.get().createdAt, LocalDateTime.now()).getSeconds();
            if (wait > 0) {
                throw new VerificationException("Please wait " + wait + " seconds before asking for another code");
            }
        }
        VerificationCode c = new VerificationCode();
        c.user = u;
        c.channel = channel;
        c.target = target;
        c.code = String.format("%06d", random.nextInt(1_000_000));
        c.expiresAt = LocalDateTime.now().plusMinutes(10);
        return codes.save(c);
    }

    private String normalisePhone(String phone) {
        String p = phone == null ? "" : phone.replaceAll("[\\s-]", "");
        if (p.startsWith("+91")) {
            p = p.substring(3);
        } else if (p.length() == 12 && p.startsWith("91")) {
            p = p.substring(2);
        } else if (p.length() == 11 && p.startsWith("0")) {
            p = p.substring(1);
        }
        if (!p.matches("[6-9]\\d{9}")) {
            throw new VerificationException("Enter a valid 10-digit Indian mobile number");
        }
        return p;
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return email;
        }
        return email.charAt(0) + "***" + email.substring(at);
    }

    private User fresh(User me) {
        return users.findById(me.id).orElseThrow(() -> new UserException("User not found", 404));
    }
}
