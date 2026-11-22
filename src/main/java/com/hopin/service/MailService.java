package com.hopin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class MailService {
    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private final ObjectProvider<JavaMailSender> sender;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> sender, @Value("${spring.mail.username:}") String from) {
        this.sender = sender;
        this.from = from;
    }

    public boolean enabled() {
        return from != null && !from.isBlank() && sender.getIfAvailable() != null;
    }

    public boolean send(String to, String subject, String text) {
        if (!enabled()) {
            log.info("[HopIn mail not configured] to={} subject={} text={}", to, subject, text);
            return false;
        }
        try {
            SimpleMailMessage m = new SimpleMailMessage();
            m.setFrom(from);
            m.setTo(to);
            m.setSubject(subject);
            m.setText(text);
            sender.getObject().send(m);
            return true;
        } catch (Exception e) {
            log.warn("Could not send email to {}: {}", to, e.getMessage());
            return false;
        }
    }
}
