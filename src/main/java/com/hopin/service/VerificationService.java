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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class VerificationService {
    private static final Logger log = LoggerFactory.getLogger(VerificationService.class);
    private static final Set<String> ID_TYPES = Set.of("AADHAAR", "DRIVING_LICENCE", "PAN", "PASSPORT", "VOTER_ID");
    private static final Map<String, String> ID_HELP = Map.of(
            "AADHAAR", "That isn't a valid Aadhaar number. Check all 12 digits",
            "PAN", "That isn't a valid PAN. It has 5 letters, 4 digits and 1 letter",
            "DRIVING_LICENCE", "That isn't a valid driving licence number. It has a 2-letter state code followed by 13 digits",
            "PASSPORT", "That isn't a valid passport number. It has 1 letter followed by 7 digits",
            "VOTER_ID", "That isn't a valid voter ID. It has 3 letters followed by 7 digits");
    private final UserRepository users;
    private final VerificationCodeRepository codes;
    private final MailService mail;
    private final String pepper;
    private final SecureRandom random = new SecureRandom();

    public VerificationService(UserRepository users, VerificationCodeRepository codes, MailService mail,
                               @Value("${hopin.jwt.secret}") String pepper) {
        this.users = users;
        this.codes = codes;
        this.mail = mail;
        this.pepper = pepper;
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
    public Map<String, Object> submitId(User me, String type, String number) {
        User u = fresh(me);
        if ("APPROVED".equals(u.idStatus)) {
            throw new VerificationException("Your ID is already verified");
        }
        String t = type == null ? "" : type.trim().toUpperCase();
        if (!ID_TYPES.contains(t)) {
            throw new VerificationException("Choose a valid ID type");
        }
        String n = number == null ? "" : number.replaceAll("[\\s-]", "").toUpperCase();
        if (!validId(t, n)) {
            throw new VerificationException(ID_HELP.get(t));
        }
        String hash = sha256(t + ":" + n + ":" + pepper);
        if (users.existsByIdHashAndIdNot(hash, u.id)) {
            throw new VerificationException("This ID is already verified on another HopIn account");
        }
        u.idType = t;
        u.idLast4 = n.substring(n.length() - 4);
        u.idHash = hash;
        u.idStatus = "APPROVED";
        return Views.self(users.save(u));
    }

    static boolean validId(String type, String n) {
        switch (type) {
            case "AADHAAR":
                return n.matches("[2-9]\\d{11}") && verhoeff(n);
            case "PAN":
                return n.matches("[A-Z]{3}[ABCFGHLJPT][A-Z]\\d{4}[A-Z]");
            case "DRIVING_LICENCE":
                if (!n.matches("[A-Z]{2}\\d{13}")) {
                    return false;
                }
                int year = Integer.parseInt(n.substring(4, 8));
                return year >= 1950 && year <= java.time.Year.now().getValue();
            case "PASSPORT":
                return n.matches("[A-PR-WY][1-9]\\d{6}");
            case "VOTER_ID":
                return n.matches("[A-Z]{3}\\d{7}");
            default:
                return false;
        }
    }

    private static final int[][] D = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, {1, 2, 3, 4, 0, 6, 7, 8, 9, 5}, {2, 3, 4, 0, 1, 7, 8, 9, 5, 6},
            {3, 4, 0, 1, 2, 8, 9, 5, 6, 7}, {4, 0, 1, 2, 3, 9, 5, 6, 7, 8}, {5, 9, 8, 7, 6, 0, 4, 3, 2, 1},
            {6, 5, 9, 8, 7, 1, 0, 4, 3, 2}, {7, 6, 5, 9, 8, 2, 1, 0, 4, 3}, {8, 7, 6, 5, 9, 3, 2, 1, 0, 4},
            {9, 8, 7, 6, 5, 4, 3, 2, 1, 0}};
    private static final int[][] P = {
            {0, 1, 2, 3, 4, 5, 6, 7, 8, 9}, {1, 5, 7, 6, 2, 8, 3, 0, 9, 4}, {5, 8, 0, 3, 7, 9, 6, 1, 4, 2},
            {8, 9, 1, 6, 0, 4, 3, 5, 2, 7}, {9, 4, 5, 3, 1, 2, 6, 8, 7, 0}, {4, 2, 8, 6, 5, 7, 3, 9, 0, 1},
            {2, 7, 9, 3, 8, 0, 6, 4, 1, 5}, {7, 0, 4, 6, 9, 1, 3, 2, 5, 8}};

    static boolean verhoeff(String num) {
        int c = 0;
        for (int i = 0; i < num.length(); i++) {
            int digit = num.charAt(num.length() - 1 - i) - '0';
            c = D[c][P[i % 8][digit]];
        }
        return c == 0;
    }

    private static String sha256(String s) {
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : h) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
