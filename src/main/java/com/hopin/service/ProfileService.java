package com.hopin.service;

import com.hopin.exception.UserException;
import com.hopin.model.BookingStatus;
import com.hopin.model.Rating;
import com.hopin.model.Role;
import com.hopin.model.RideStatus;
import com.hopin.model.User;
import com.hopin.repository.BookingRepository;
import com.hopin.repository.RatingRepository;
import com.hopin.repository.RideRepository;
import com.hopin.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private final UserRepository users;
    private final RideRepository rides;
    private final BookingRepository bookings;
    private final RatingRepository ratings;

    public ProfileService(UserRepository users, RideRepository rides, BookingRepository bookings,
                          RatingRepository ratings) {
        this.users = users;
        this.rides = rides;
        this.bookings = bookings;
        this.ratings = ratings;
    }

    public record ReviewView(String from, int score, String comment, LocalDateTime at) {
    }

    public record Profile(Long id, String name, Role role, double rating, int ratingCount,
                          Boolean noSmoking, Boolean noPets, Boolean ac, Boolean music, Boolean luggage,
                          String note, long ridesPublished, long ridesCompleted, long ridesCancelled,
                          long tripsTaken, String reliability, String experience, List<ReviewView> reviews) {
    }

    public record ProfileUpdate(String name, Boolean noSmoking, Boolean noPets, Boolean ac, Boolean music,
                                Boolean luggage, String note) {
    }

    public List<ReviewView> reviewsFor(Long userId) {
        return ratings.findTop5ByToUserIdOrderByIdDesc(userId).stream().map(this::view).toList();
    }

    public ReviewView latestReview(Long userId) {
        List<Rating> list = ratings.findTop5ByToUserIdOrderByIdDesc(userId);
        return list.isEmpty() ? null : view(list.get(0));
    }

    public Profile profile(Long userId) {
        User u = users.findById(userId).orElseThrow(() -> new UserException("User not found", 404));
        long published = rides.countByDriverId(u.id);
        long completed = rides.countByDriverIdAndStatus(u.id, RideStatus.COMPLETED);
        long cancelled = rides.countByDriverIdAndStatus(u.id, RideStatus.CANCELLED);
        long trips = bookings.countByRider_IdAndStatusAndRide_Status(u.id, BookingStatus.CONFIRMED, RideStatus.COMPLETED);
        return new Profile(u.id, u.name, u.role, u.rating, u.ratingCount,
                u.noSmoking, u.noPets, u.ac, u.music, u.luggage, u.note,
                published, completed, cancelled, trips,
                reliability(published, cancelled), experience(u.role == Role.DRIVER ? completed : trips),
                reviewsFor(u.id));
    }

    @Transactional
    public User update(User me, ProfileUpdate p) {
        User u = users.findById(me.id).orElseThrow(() -> new UserException("User not found", 404));
        if (p.name() != null) {
            if (p.name().isBlank()) {
                throw new UserException("Name cannot be empty", 400);
            }
            u.name = p.name().trim();
        }
        if (p.note() != null) {
            String n = p.note().trim();
            if (n.length() > 300) {
                throw new UserException("Instructions can be at most 300 characters", 400);
            }
            u.note = n;
        }
        u.noSmoking = Boolean.TRUE.equals(p.noSmoking());
        u.noPets = Boolean.TRUE.equals(p.noPets());
        u.ac = Boolean.TRUE.equals(p.ac());
        u.music = Boolean.TRUE.equals(p.music());
        u.luggage = Boolean.TRUE.equals(p.luggage());
        return users.save(u);
    }

    public User me(User me) {
        return users.findById(me.id).orElseThrow(() -> new UserException("User not found", 404));
    }

    private String reliability(long published, long cancelled) {
        if (published == 0) {
            return "No rides yet";
        }
        if (cancelled == 0) {
            return "Never cancels rides";
        }
        if ((double) cancelled / published <= 0.2) {
            return "Rarely cancels rides";
        }
        return "Frequently cancels rides";
    }

    private String experience(long done) {
        if (done == 0) {
            return "New";
        }
        if (done < 5) {
            return "Beginner";
        }
        if (done < 20) {
            return "Regular";
        }
        return "Experienced";
    }

    private ReviewView view(Rating r) {
        return new ReviewView(r.fromUser.name, r.score, r.comment, r.createdAt);
    }
}
