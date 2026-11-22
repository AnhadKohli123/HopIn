package com.hopin.service;

import com.hopin.exception.BlockedException;
import com.hopin.exception.ChatException;
import com.hopin.exception.RideNotFoundException;
import com.hopin.exception.UserException;
import com.hopin.model.*;
import com.hopin.repository.BookingRepository;
import com.hopin.repository.ChatMessageRepository;
import com.hopin.repository.RideRepository;
import com.hopin.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {
    private final ChatMessageRepository messages;
    private final RideRepository rides;
    private final UserRepository users;
    private final BookingRepository bookings;
    private final SafetyService safety;

    public ChatService(ChatMessageRepository messages, RideRepository rides, UserRepository users,
                       BookingRepository bookings, SafetyService safety) {
        this.messages = messages;
        this.rides = rides;
        this.users = users;
        this.bookings = bookings;
        this.safety = safety;
    }

    public record MessageView(Long id, Long senderId, String text, LocalDateTime at) {
    }

    public record Conversation(Long rideId, Long riderId, User other, String route, LocalDateTime departTime,
                               String lastText, boolean lastMine, LocalDateTime lastAt, long unread) {
    }

    public record ChatThread(Long rideId, Long riderId, User other, String route, LocalDateTime departTime,
                         RideStatus rideStatus, boolean blocked, List<MessageView> messages) {
    }

    private record Party(Ride ride, User rider, boolean meIsDriver) {
    }

    @Transactional
    public MessageView send(User me, Long rideId, Long riderId, String text) {
        Party p = party(me, rideId, riderId);
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) {
            throw new ChatException("Message cannot be empty", 400);
        }
        if (t.length() > 1000) {
            throw new ChatException("Message can be at most 1000 characters", 400);
        }
        if (safety.blockedEitherWay(p.ride().driver.id, p.rider().id)) {
            throw new BlockedException();
        }
        if (p.meIsDriver() && !messages.existsByRide_IdAndRider_Id(rideId, riderId)
                && !bookings.existsByRideIdAndRiderIdAndStatusIn(rideId, riderId, Arrays.asList(BookingStatus.values()))) {
            throw new ChatException("You can message riders who booked or contacted you about this ride", 403);
        }
        ChatMessage m = new ChatMessage();
        m.ride = p.ride();
        m.rider = p.rider();
        m.sender = me;
        m.text = t;
        messages.save(m);
        return view(m);
    }

    @Transactional
    public ChatThread thread(User me, Long rideId, Long riderId) {
        Party p = party(me, rideId, riderId);
        List<MessageView> list = new ArrayList<>();
        for (ChatMessage m : messages.findByRide_IdAndRider_IdOrderByIdAsc(rideId, riderId)) {
            if (!m.sender.id.equals(me.id) && !m.seen) {
                m.seen = true;
            }
            list.add(view(m));
        }
        User other = p.meIsDriver() ? p.rider() : p.ride().driver;
        return new ChatThread(rideId, riderId, other, route(p.ride()), p.ride().departTime, p.ride().status,
                safety.blockedEitherWay(me.id, other.id), list);
    }

    public List<Conversation> conversations(User me) {
        Map<String, Conversation> map = new LinkedHashMap<>();
        Map<String, Long> unread = new LinkedHashMap<>();
        List<ChatMessage> all = messages.findByRide_Driver_IdOrRider_IdOrderByIdDesc(me.id, me.id);
        for (ChatMessage m : all) {
            String key = m.ride.id + ":" + m.rider.id;
            if (!m.sender.id.equals(me.id) && !m.seen) {
                unread.merge(key, 1L, Long::sum);
            }
        }
        for (ChatMessage m : all) {
            String key = m.ride.id + ":" + m.rider.id;
            if (map.containsKey(key)) {
                continue;
            }
            boolean meIsDriver = m.ride.driver.id.equals(me.id);
            User other = meIsDriver ? m.rider : m.ride.driver;
            map.put(key, new Conversation(m.ride.id, m.rider.id, other, route(m.ride), m.ride.departTime,
                    m.text, m.sender.id.equals(me.id), m.createdAt, unread.getOrDefault(key, 0L)));
        }
        return new ArrayList<>(map.values());
    }

    public long unread(User me) {
        return messages.countByRide_Driver_IdAndSender_IdNotAndSeenFalse(me.id, me.id)
                + messages.countByRider_IdAndSender_IdNotAndSeenFalse(me.id, me.id);
    }

    private Party party(User me, Long rideId, Long riderId) {
        Ride ride = rides.findById(rideId).orElseThrow(() -> new RideNotFoundException(rideId));
        User rider = users.findById(riderId).orElseThrow(() -> new UserException("User not found", 404));
        if (rider.role != Role.RIDER) {
            throw new ChatException("Conversations are between a driver and a rider", 400);
        }
        boolean meIsDriver = ride.driver.id.equals(me.id);
        boolean meIsRider = me.id.equals(riderId);
        if (!meIsDriver && !meIsRider) {
            throw new ChatException("You are not part of this conversation", 403);
        }
        return new Party(ride, rider, meIsDriver);
    }

    private String route(Ride r) {
        return r.source + " → " + r.destination;
    }

    private MessageView view(ChatMessage m) {
        return new MessageView(m.id, m.sender.id, m.text, m.createdAt);
    }
}
