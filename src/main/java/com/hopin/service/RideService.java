package com.hopin.service;

import com.hopin.exception.RideException;
import com.hopin.exception.RideNotFoundException;
import com.hopin.exception.UnauthorizedActionException;
import com.hopin.model.*;
import com.hopin.repository.BookingRepository;
import com.hopin.repository.RideRepository;
import com.hopin.repository.VehicleRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RideService {
    private final RideRepository rides;
    private final VehicleRepository vehicles;
    private final BookingRepository bookings;
    private final ProfileService profiles;
    private final AlertService alerts;
    private final SafetyService safety;
    private final NotificationService notifications;

    public RideService(RideRepository rides, VehicleRepository vehicles, BookingRepository bookings,
                       ProfileService profiles, AlertService alerts, SafetyService safety,
                       NotificationService notifications) {
        this.rides = rides;
        this.vehicles = vehicles;
        this.bookings = bookings;
        this.profiles = profiles;
        this.alerts = alerts;
        this.safety = safety;
        this.notifications = notifications;
    }

    public record StopData(@NotBlank String name, Double lat, Double lng) {
    }

    public record NewRide(@NotBlank String source, Double srcLat, Double srcLng,
                          @NotBlank String destination, Double dstLat, Double dstLng,
                          @Valid List<StopData> stops,
                          @NotNull LocalDateTime departTime,
                          List<LocalDateTime> extraTimes,
                          @Min(1) int seats,
                          @Positive double pricePerSeat,
                          boolean negotiable,
                          Long vehicleId) {
    }

    public record GeoQuery(Double fromLat, Double fromLng, Double toLat, Double toLng, Double radiusKm) {
    }

    public record CoPassenger(Long id, String name, double rating, int ratingCount, int seats,
                              String pickup, String drop, ProfileService.ReviewView lastReview) {
    }

    public record RideView(Ride ride, int pickupIdx, int dropIdx, double listPrice, double score,
                           List<ProfileService.ReviewView> driverReviews, List<CoPassenger> coPassengers,
                           Double pickupKm, Double dropKm, String driverSummary) {
    }

    @Transactional
    public Vehicle addVehicle(User driver, String model, String plate, int seats) {
        requireDriver(driver);
        Vehicle v = new Vehicle();
        v.driver = driver;
        v.model = model;
        v.plate = plate;
        v.seats = seats;
        return vehicles.save(v);
    }

    public List<Vehicle> myVehicles(User driver) {
        return vehicles.findByDriverId(driver.id);
    }

    @Transactional
    public List<Ride> postRide(User driver, NewRide n) {
        requireDriver(driver);
        if (n.source().trim().equalsIgnoreCase(n.destination().trim())) {
            throw new RideException("Source and destination cannot be the same", 400);
        }
        if (!RouteUtil.multipleOf10(n.pricePerSeat())) {
            throw new RideException("Price per seat must be a multiple of ₹10", 400);
        }
        List<StopData> stops = n.stops() == null ? List.of() : n.stops();
        if (stops.size() > 5) {
            throw new RideException("A ride can have at most 5 in-between stops", 400);
        }
        TreeSet<LocalDateTime> times = new TreeSet<>();
        times.add(n.departTime());
        if (n.extraTimes() != null) {
            for (LocalDateTime t : n.extraTimes()) {
                if (t != null) {
                    times.add(t);
                }
            }
        }
        if (times.size() > 14) {
            throw new RideException("You can post for at most 14 dates at once", 400);
        }
        LocalDateTime now = LocalDateTime.now();
        for (LocalDateTime t : times) {
            if (t.isBefore(now)) {
                throw new RideException("Every departure time must be in the future", 400);
            }
        }
        Vehicle vehicle = null;
        if (n.vehicleId() != null) {
            vehicle = vehicles.findById(n.vehicleId())
                    .orElseThrow(() -> new RideException("Vehicle not found", 404));
            if (!vehicle.driver.id.equals(driver.id)) {
                throw new UnauthorizedActionException("This vehicle is not yours");
            }
            if (n.seats() > vehicle.seats) {
                throw new RideException("Vehicle only has " + vehicle.seats + " seats", 400);
            }
        }
        List<Ride> saved = new ArrayList<>();
        for (LocalDateTime t : times) {
            Ride r = new Ride();
            r.driver = driver;
            r.vehicle = vehicle;
            r.source = n.source().trim();
            r.srcLat = n.srcLat();
            r.srcLng = n.srcLng();
            r.destination = n.destination().trim();
            r.dstLat = n.dstLat();
            r.dstLng = n.dstLng();
            int seq = 0;
            for (StopData s : stops) {
                RideStop rs = new RideStop();
                rs.ride = r;
                rs.name = s.name().trim();
                rs.lat = s.lat();
                rs.lng = s.lng();
                rs.seq = seq++;
                r.stops.add(rs);
            }
            r.departTime = t;
            r.seatsTotal = n.seats();
            r.seatsLeft = n.seats();
            r.pricePerSeat = n.pricePerSeat();
            r.negotiable = n.negotiable();
            r.totalCost = n.pricePerSeat() * n.seats();
            saved.add(rides.save(r));
        }
        for (Ride r : saved) {
            alerts.onRidePosted(r);
        }
        return saved;
    }

    public List<RideView> search(User me, String source, String destination, LocalDate date, int seats, GeoQuery geo) {
        if (source == null || source.isBlank() || destination == null || destination.isBlank()) {
            throw new RideException("Enter both From and To", 400);
        }
        if (date == null) {
            throw new RideException("Pick a date", 400);
        }
        if (date.isBefore(LocalDate.now())) {
            throw new RideException("Date cannot be in the past", 400);
        }
        if (seats < 1) {
            throw new RideException("Seats must be at least 1", 400);
        }
        double radius = geo == null || geo.radiusKm() == null ? 10 : Math.max(1, Math.min(50, geo.radiusKm()));
        RouteUtil.End a = new RouteUtil.End(source, geo == null ? null : geo.fromLat(), geo == null ? null : geo.fromLng(), radius);
        RouteUtil.End b = new RouteUtil.End(destination, geo == null ? null : geo.toLat(), geo == null ? null : geo.toLng(), radius);
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        if (from.isBefore(now)) {
            from = now;
        }
        List<Ride> found = rides.findByStatusAndSeatsLeftGreaterThanEqualAndDepartTimeBetween(
                RideStatus.OPEN, seats, from, to);

        List<Ride> keep = new ArrayList<>();
        List<RouteUtil.Match> matches = new ArrayList<>();
        List<Double> prices = new ArrayList<>();
        for (Ride r : found) {
            if (me != null && (r.driver.id.equals(me.id) || safety.blockedEitherWay(me.id, r.driver.id))) {
                continue;
            }
            List<RouteUtil.Point> pts = RouteUtil.points(r);
            RouteUtil.Match m = RouteUtil.match(pts, a, b);
            if (m == null) {
                continue;
            }
            keep.add(r);
            matches.add(m);
            prices.add(RouteUtil.price(r.pricePerSeat, pts, m.from(), m.to()));
        }
        double max = prices.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (max <= 0) {
            max = 1;
        }
        List<RideView> matched = new ArrayList<>();
        for (int i = 0; i < keep.size(); i++) {
            Ride r = keep.get(i);
            RouteUtil.Match m = matches.get(i);
            double price = prices.get(i);
            double ratingPart = r.driver.ratingCount == 0 ? 0.6 : r.driver.rating / 5.0;
            double pricePart = 1.0 - (price / max);
            double score;
            if (m.fromKm() != null || m.toKm() != null) {
                double walk = (m.fromKm() == null ? 0 : m.fromKm()) + (m.toKm() == null ? 0 : m.toKm());
                double near = 1.0 - Math.min(1.0, walk / (2 * radius));
                score = 0.5 * ratingPart + 0.3 * pricePart + 0.2 * near;
            } else {
                score = 0.6 * ratingPart + 0.4 * pricePart;
            }
            score = Math.round(score * 100.0) / 100.0;
            matched.add(new RideView(r, m.from(), m.to(), price, score,
                    profiles.reviewsFor(r.driver.id), coPassengers(r), m.fromKm(), m.toKm(),
                    profiles.summary(r.driver.id)));
        }
        matched.sort(Comparator.comparingDouble(RideView::score).reversed());
        return matched;
    }

    private List<CoPassenger> coPassengers(Ride r) {
        List<CoPassenger> list = new ArrayList<>();
        for (Booking b : bookings.findByRideId(r.id)) {
            if (b.status == BookingStatus.CONFIRMED) {
                list.add(new CoPassenger(b.rider.id, b.rider.name, b.rider.rating, b.rider.ratingCount, b.seats,
                        b.pickupName, b.dropName, profiles.latestReview(b.rider.id)));
            }
        }
        return list;
    }

    public Ride get(Long id) {
        return rides.findById(id).orElseThrow(() -> new RideNotFoundException(id));
    }

    public List<Ride> myRides(User driver) {
        return rides.findByDriverIdOrderByDepartTimeDesc(driver.id);
    }

    @Transactional
    public Ride cancelRide(User driver, Long rideId) {
        Ride r = rides.findByIdForUpdate(rideId).orElseThrow(() -> new RideNotFoundException(rideId));
        ownRide(driver, r);
        if (r.status != RideStatus.OPEN) {
            throw new RideException("Only open rides can be cancelled", 409);
        }
        r.status = RideStatus.CANCELLED;
        for (Booking b : bookings.findByRideId(rideId)) {
            if (b.status == BookingStatus.PENDING || b.status == BookingStatus.COUNTERED
                    || b.status == BookingStatus.CONFIRMED) {
                b.status = BookingStatus.CANCELLED;
                notifications.notify(b.rider, driver.name + " cancelled the ride " + b.pickupName + " → " + b.dropName
                        + " on " + r.departTime.toLocalDate() + ". Search again to find another ride.", "bookings");
            }
        }
        return r;
    }

    @Transactional
    public Ride completeRide(User driver, Long rideId) {
        Ride r = rides.findByIdForUpdate(rideId).orElseThrow(() -> new RideNotFoundException(rideId));
        ownRide(driver, r);
        if (r.status != RideStatus.OPEN) {
            throw new RideException("Only open rides can be completed", 409);
        }
        r.status = RideStatus.COMPLETED;
        for (Booking b : bookings.findByRideId(rideId)) {
            if (b.status == BookingStatus.PENDING || b.status == BookingStatus.COUNTERED) {
                b.status = BookingStatus.REJECTED;
            }
        }
        return r;
    }

    private void requireDriver(User u) {
        if (u.role != Role.DRIVER) {
            throw new UnauthorizedActionException("Only drivers can do this");
        }
    }

    private void ownRide(User driver, Ride r) {
        if (!r.driver.id.equals(driver.id)) {
            throw new UnauthorizedActionException("This is not your ride");
        }
    }
}
