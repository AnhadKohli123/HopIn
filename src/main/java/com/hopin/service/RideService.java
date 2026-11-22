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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RideService {
    private final RideRepository rides;
    private final VehicleRepository vehicles;
    private final BookingRepository bookings;
    private final ProfileService profiles;

    public RideService(RideRepository rides, VehicleRepository vehicles, BookingRepository bookings,
                       ProfileService profiles) {
        this.rides = rides;
        this.vehicles = vehicles;
        this.bookings = bookings;
        this.profiles = profiles;
    }

    public record StopData(@NotBlank String name, Double lat, Double lng) {
    }

    public record NewRide(@NotBlank String source, Double srcLat, Double srcLng,
                          @NotBlank String destination, Double dstLat, Double dstLng,
                          @Valid List<StopData> stops,
                          @NotNull LocalDateTime departTime,
                          @Min(1) int seats,
                          @Positive double pricePerSeat,
                          boolean negotiable,
                          Long vehicleId) {
    }

    public record CoPassenger(Long id, String name, double rating, int ratingCount, int seats,
                              String pickup, String drop, ProfileService.ReviewView lastReview) {
    }

    public record RideView(Ride ride, int pickupIdx, int dropIdx, double listPrice, double score,
                           List<ProfileService.ReviewView> driverReviews, List<CoPassenger> coPassengers) {
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
    public Ride postRide(User driver, NewRide n) {
        requireDriver(driver);
        if (n.source().trim().equalsIgnoreCase(n.destination().trim())) {
            throw new RideException("Source and destination cannot be the same", 400);
        }
        if (n.departTime().isBefore(LocalDateTime.now())) {
            throw new RideException("Departure time must be in the future", 400);
        }
        if (!RouteUtil.multipleOf10(n.pricePerSeat())) {
            throw new RideException("Price per seat must be a multiple of ₹10", 400);
        }
        List<StopData> stops = n.stops() == null ? List.of() : n.stops();
        if (stops.size() > 5) {
            throw new RideException("A ride can have at most 5 in-between stops", 400);
        }
        Ride r = new Ride();
        r.driver = driver;
        if (n.vehicleId() != null) {
            Vehicle v = vehicles.findById(n.vehicleId())
                    .orElseThrow(() -> new RideException("Vehicle not found", 404));
            if (!v.driver.id.equals(driver.id)) {
                throw new UnauthorizedActionException("This vehicle is not yours");
            }
            if (n.seats() > v.seats) {
                throw new RideException("Vehicle only has " + v.seats + " seats", 400);
            }
            r.vehicle = v;
        }
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
        r.departTime = n.departTime();
        r.seatsTotal = n.seats();
        r.seatsLeft = n.seats();
        r.pricePerSeat = n.pricePerSeat();
        r.negotiable = n.negotiable();
        r.totalCost = n.pricePerSeat() * n.seats();
        return rides.save(r);
    }

    public List<RideView> search(String source, String destination, LocalDate date, int seats) {
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
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.plusDays(1).atStartOfDay();
        if (from.isBefore(now)) {
            from = now;
        }
        List<Ride> found = rides.findByStatusAndSeatsLeftGreaterThanEqualAndDepartTimeBetween(
                RideStatus.OPEN, seats, from, to);

        List<RideView> matched = new ArrayList<>();
        List<Double> prices = new ArrayList<>();
        List<Ride> keep = new ArrayList<>();
        List<int[]> idx = new ArrayList<>();
        for (Ride r : found) {
            List<RouteUtil.Point> pts = RouteUtil.points(r);
            int[] pair = matchSegment(pts, source, destination);
            if (pair == null) {
                continue;
            }
            keep.add(r);
            idx.add(pair);
            prices.add(RouteUtil.price(r.pricePerSeat, pts, pair[0], pair[1]));
        }
        double max = prices.stream().mapToDouble(Double::doubleValue).max().orElse(1);
        if (max <= 0) {
            max = 1;
        }
        for (int i = 0; i < keep.size(); i++) {
            Ride r = keep.get(i);
            double price = prices.get(i);
            double ratingPart = r.driver.ratingCount == 0 ? 0.6 : r.driver.rating / 5.0;
            double pricePart = 1.0 - (price / max);
            double score = Math.round((0.6 * ratingPart + 0.4 * pricePart) * 100.0) / 100.0;
            matched.add(new RideView(r, idx.get(i)[0], idx.get(i)[1], price, score,
                    profiles.reviewsFor(r.driver.id), coPassengers(r)));
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

    private int[] matchSegment(List<RouteUtil.Point> pts, String source, String destination) {
        int last = pts.size() - 1;
        for (int i = 0; i < last; i++) {
            if (!nameMatches(pts.get(i).name(), source)) {
                continue;
            }
            for (int j = i + 1; j <= last; j++) {
                if (nameMatches(pts.get(j).name(), destination)) {
                    return new int[]{i, j};
                }
            }
        }
        return null;
    }

    private boolean nameMatches(String name, String query) {
        if (query == null || query.isBlank()) {
            return true;
        }
        return name.toLowerCase().contains(query.trim().toLowerCase());
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
