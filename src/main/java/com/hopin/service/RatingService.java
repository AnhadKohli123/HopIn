package com.hopin.service;

import com.hopin.exception.RideException;
import com.hopin.exception.RideNotFoundException;
import com.hopin.exception.UnauthorizedActionException;
import com.hopin.model.*;
import com.hopin.repository.BookingRepository;
import com.hopin.repository.RatingRepository;
import com.hopin.repository.RideRepository;
import com.hopin.repository.UserRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RatingService {
    private final RatingRepository ratings;
    private final RideRepository rides;
    private final UserRepository users;
    private final BookingRepository bookings;

    public RatingService(RatingRepository ratings, RideRepository rides, UserRepository users,
                         BookingRepository bookings) {
        this.ratings = ratings;
        this.rides = rides;
        this.users = users;
        this.bookings = bookings;
    }

    public record Given(Long rideId, Long toUserId, int score) {
    }

    @Transactional
    public Rating rate(User from, Long rideId, Long toUserId, int score, String comment) {
        Ride ride = rides.findById(rideId).orElseThrow(() -> new RideNotFoundException(rideId));
        if (ride.status != RideStatus.COMPLETED) {
            throw new RideException("You can rate only after the ride is completed", 409);
        }
        if (from.id.equals(toUserId)) {
            throw new RideException("You cannot rate yourself", 400);
        }
        if (!isParticipant(ride, from.id) || !isParticipant(ride, toUserId)) {
            throw new UnauthorizedActionException("Only people on this ride can rate each other");
        }
        if (ratings.existsByRideIdAndFromUserIdAndToUserId(rideId, from.id, toUserId)) {
            throw new RideException("You already rated this person for this ride", 409);
        }
        String text = comment == null ? null : comment.trim();
        if (text != null && text.length() > 300) {
            throw new RideException("Comment can be at most 300 characters", 400);
        }
        User to = users.findById(toUserId).orElseThrow(() -> new RideException("User not found", 404));
        Rating r = new Rating();
        r.ride = ride;
        r.fromUser = from;
        r.toUser = to;
        r.score = score;
        r.comment = text == null || text.isEmpty() ? null : text;
        ratings.save(r);
        to.rating = Math.round(((to.rating * to.ratingCount) + score) / (to.ratingCount + 1) * 100.0) / 100.0;
        to.ratingCount += 1;
        users.save(to);
        return r;
    }

    public List<Rating> forUser(Long userId) {
        return ratings.findByToUserIdOrderByIdDesc(userId);
    }

    public List<Given> given(User from) {
        return ratings.findByFromUserId(from.id).stream()
                .map(r -> new Given(r.ride.id, r.toUser.id, r.score))
                .toList();
    }

    private boolean isParticipant(Ride ride, Long userId) {
        if (ride.driver.id.equals(userId)) {
            return true;
        }
        return bookings.findByRideId(ride.id).stream()
                .anyMatch(b -> b.rider.id.equals(userId) && b.status == BookingStatus.CONFIRMED);
    }
}
