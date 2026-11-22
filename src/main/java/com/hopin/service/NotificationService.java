package com.hopin.service;

import com.hopin.model.Notification;
import com.hopin.model.User;
import com.hopin.repository.NotificationRepository;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {
    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public void notify(User user, String text, String tab) {
        Notification n = new Notification();
        n.user = user;
        n.text = text.length() > 300 ? text.substring(0, 300) : text;
        n.tab = tab;
        notifications.save(n);
    }

    public void notifySearch(User user, String text, String from, String to, LocalDate date) {
        Notification n = new Notification();
        n.user = user;
        n.text = text.length() > 300 ? text.substring(0, 300) : text;
        n.tab = "search";
        n.searchFrom = from;
        n.searchTo = to;
        n.searchDate = date;
        notifications.save(n);
    }

    public List<Notification> list(User me) {
        return notifications.findTop30ByUserIdOrderByIdDesc(me.id);
    }

    @Transactional
    public void readAll(User me) {
        for (Notification n : notifications.findByUserIdAndSeenFalse(me.id)) {
            n.seen = true;
        }
    }

    public long unread(User me) {
        return notifications.countByUserIdAndSeenFalse(me.id);
    }
}
