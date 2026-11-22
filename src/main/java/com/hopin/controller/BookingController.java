package com.hopin.controller;

import com.hopin.model.Booking;
import com.hopin.model.Rating;
import com.hopin.model.User;
import com.hopin.service.BookingService;
import com.hopin.service.ChatService;
import com.hopin.service.NotificationService;
import com.hopin.service.RatingService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class BookingController {
    private final BookingService bookings;
    private final RatingService ratings;
    private final ChatService chats;
    private final NotificationService notifications;

    public BookingController(BookingService bookings, RatingService ratings, ChatService chats,
                             NotificationService notifications) {
        this.bookings = bookings;
        this.ratings = ratings;
        this.chats = chats;
        this.notifications = notifications;
    }

    public record BookReq(@Min(1) int seats, Integer pickupIdx, Integer dropIdx, Double offeredPrice) {
    }

    public record CounterReq(@NotNull @Positive Double price) {
    }

    public record RateReq(@NotNull Long rideId, @NotNull Long toUserId, @Min(1) @Max(5) int score, String comment) {
    }

    @PostMapping("/rides/{id}/book")
    public Booking book(@RequestAttribute("user") User user, @PathVariable Long id,
                        @Valid @RequestBody BookReq r) {
        return bookings.request(user, id, r.seats(), r.pickupIdx(), r.dropIdx(), r.offeredPrice());
    }

    @GetMapping("/bookings/mine")
    public List<Booking> mine(@RequestAttribute("user") User user) {
        return bookings.forRider(user);
    }

    @GetMapping("/bookings/badge")
    public Map<String, Long> badge(@RequestAttribute("user") User user) {
        return Map.of("count", bookings.badge(user), "messages", chats.unread(user),
                "notifications", notifications.unread(user));
    }

    @GetMapping("/bookings/requests")
    public List<Booking> requests(@RequestAttribute("user") User user) {
        return bookings.forDriver(user);
    }

    @PostMapping("/bookings/{id}/accept")
    public Booking accept(@RequestAttribute("user") User user, @PathVariable Long id) {
        return bookings.respond(user, id, true);
    }

    @PostMapping("/bookings/{id}/reject")
    public Booking reject(@RequestAttribute("user") User user, @PathVariable Long id) {
        return bookings.respond(user, id, false);
    }

    @PostMapping("/bookings/{id}/counter")
    public Booking counter(@RequestAttribute("user") User user, @PathVariable Long id,
                           @Valid @RequestBody CounterReq r) {
        return bookings.counter(user, id, r.price());
    }

    @PostMapping("/bookings/{id}/accept-counter")
    public Booking acceptCounter(@RequestAttribute("user") User user, @PathVariable Long id) {
        return bookings.acceptCounter(user, id);
    }

    @PostMapping("/bookings/{id}/cancel")
    public Booking cancel(@RequestAttribute("user") User user, @PathVariable Long id) {
        return bookings.cancel(user, id);
    }

    @PostMapping("/bookings/{id}/pay")
    public Booking pay(@RequestAttribute("user") User user, @PathVariable Long id) {
        return bookings.pay(user, id);
    }

    @PostMapping("/ratings")
    public Rating rate(@RequestAttribute("user") User user, @Valid @RequestBody RateReq r) {
        return ratings.rate(user, r.rideId(), r.toUserId(), r.score(), r.comment());
    }

    @GetMapping("/ratings/given")
    public List<RatingService.Given> given(@RequestAttribute("user") User user) {
        return ratings.given(user);
    }

    @GetMapping("/ratings/user/{id}")
    public List<Rating> userRatings(@PathVariable Long id) {
        return ratings.forUser(id);
    }
}
