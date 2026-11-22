package com.hopin.service;

import com.hopin.exception.UserException;
import com.hopin.model.BookingStatus;
import com.hopin.model.Rating;
import com.hopin.model.Role;
import com.hopin.model.RideStatus;
import com.hopin.model.User;
import com.hopin.model.UserPhoto;
import com.hopin.repository.BookingRepository;
import com.hopin.repository.RatingRepository;
import com.hopin.repository.RideRepository;
import com.hopin.repository.UserPhotoRepository;
import com.hopin.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProfileService {
    private static final Set<String> STOP = Set.of("this", "that", "with", "have", "from", "they", "were", "will",
            "would", "there", "their", "about", "really", "just", "ride", "trip", "driver", "passenger", "also",
            "been", "when", "what", "your", "more", "very", "much", "make", "made", "into", "than", "then", "them",
            "only", "even", "some", "like", "well", "here", "over", "after", "before", "hopin");
    private static final int MAX_PHOTO_CHARS = 400_000;
    private final UserRepository users;
    private final RideRepository rides;
    private final BookingRepository bookings;
    private final RatingRepository ratings;
    private final UserPhotoRepository photos;

    public ProfileService(UserRepository users, RideRepository rides, BookingRepository bookings,
                          RatingRepository ratings, UserPhotoRepository photos) {
        this.users = users;
        this.rides = rides;
        this.bookings = bookings;
        this.ratings = ratings;
        this.photos = photos;
    }

    public record ReviewView(String from, int score, String comment, LocalDateTime at) {
    }

    public record Profile(Long id, String name, Role role, double rating, int ratingCount,
                          Boolean noSmoking, Boolean noPets, Boolean ac, Boolean music, Boolean luggage,
                          String note, long ridesPublished, long ridesCompleted, long ridesCancelled,
                          long tripsTaken, String reliability, String experience, List<ReviewView> reviews,
                          String summary, boolean emailVerified, boolean phoneVerified, boolean idVerified,
                          boolean verified, boolean hasPhoto, int photoVersion) {
    }

    public record ProfileUpdate(String name, Boolean noSmoking, Boolean noPets, Boolean ac, Boolean music,
                                Boolean luggage, String note) {
    }

    public record Photo(byte[] bytes, String type) {
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
                reviewsFor(u.id), summary(u.id),
                Boolean.TRUE.equals(u.emailVerified), Boolean.TRUE.equals(u.phoneVerified), u.idVerified(), u.verified(),
                Boolean.TRUE.equals(u.hasPhoto), u.photoVersion == null ? 0 : u.photoVersion);
    }

    public String summary(Long userId) {
        List<Rating> all = ratings.findByToUserIdOrderByIdDesc(userId);
        if (all.isEmpty()) {
            return null;
        }
        int n = all.size();
        double avg = all.stream().mapToInt(r -> r.score).average().orElse(0);
        long five = all.stream().filter(r -> r.score == 5).count();
        String tone = avg >= 4.5 ? "Excellent feedback" : avg >= 4 ? "Mostly positive feedback"
                : avg >= 3 ? "Mixed feedback" : "Mostly negative feedback";
        StringBuilder sb = new StringBuilder(tone).append(": ")
                .append(String.format(Locale.ROOT, "%.1f", avg)).append("★ average from ")
                .append(n).append(n == 1 ? " review" : " reviews").append(", ")
                .append(Math.round(five * 100.0 / n)).append("% five-star.");
        Map<String, Integer> counts = new HashMap<>();
        for (Rating r : all) {
            if (r.comment == null) {
                continue;
            }
            Set<String> seen = new HashSet<>();
            for (String w : r.comment.toLowerCase(Locale.ROOT).split("[^a-z]+")) {
                if (w.length() >= 4 && !STOP.contains(w) && seen.add(w)) {
                    counts.merge(w, 1, Integer::sum);
                }
            }
        }
        List<String> top = new ArrayList<>(counts.entrySet().stream()
                .filter(e -> e.getValue() >= 2)
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .limit(3).map(Map.Entry::getKey).toList());
        if (!top.isEmpty()) {
            sb.append(" People often mention: ").append(String.join(", ", top)).append(".");
        }
        return sb.toString();
    }

    @Transactional
    public Map<String, Object> update(User me, ProfileUpdate p) {
        User u = fresh(me);
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
        return Views.self(users.save(u));
    }

    public Map<String, Object> me(User me) {
        return Views.self(fresh(me));
    }

    @Transactional
    public Map<String, Object> setPhoto(User me, String dataUrl) {
        if (dataUrl == null || !dataUrl.matches("^data:image/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$")) {
            throw new UserException("Upload a JPG, PNG or WebP image", 400);
        }
        if (dataUrl.length() > MAX_PHOTO_CHARS) {
            throw new UserException("Photo is too large. Try a smaller image", 400);
        }
        try {
            Base64.getDecoder().decode(dataUrl.substring(dataUrl.indexOf(',') + 1));
        } catch (IllegalArgumentException e) {
            throw new UserException("That image could not be read", 400);
        }
        User u = fresh(me);
        UserPhoto p = photos.findById(u.id).orElseGet(UserPhoto::new);
        p.userId = u.id;
        p.data = dataUrl;
        photos.save(p);
        u.hasPhoto = true;
        u.photoVersion = (u.photoVersion == null ? 0 : u.photoVersion) + 1;
        return Views.self(users.save(u));
    }

    @Transactional
    public Map<String, Object> removePhoto(User me) {
        User u = fresh(me);
        photos.findById(u.id).ifPresent(photos::delete);
        u.hasPhoto = false;
        u.photoVersion = (u.photoVersion == null ? 0 : u.photoVersion) + 1;
        return Views.self(users.save(u));
    }

    public Photo photo(Long userId) {
        UserPhoto p = photos.findById(userId).orElseThrow(() -> new UserException("No photo", 404));
        String type = p.data.substring(5, p.data.indexOf(';'));
        return new Photo(Base64.getDecoder().decode(p.data.substring(p.data.indexOf(',') + 1)), type);
    }

    private User fresh(User me) {
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
