package com.hopin.service;

import com.hopin.exception.*;
import com.hopin.model.*;
import com.hopin.repository.BookingRepository;
import com.hopin.repository.RideRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {
    private final BookingRepository bookings;
    private final RideRepository rides;
    private final SafetyService safety;
    private final NotificationService notifications;

    public BookingService(BookingRepository bookings, RideRepository rides, SafetyService safety,
                          NotificationService notifications) {
        this.bookings = bookings;
        this.rides = rides;
        this.safety = safety;
        this.notifications = notifications;
    }

    private String stretch(Booking b) {
        return b.pickupName + " → " + b.dropName + " on " + b.ride.departTime.toLocalDate();
    }

    @Transactional
    public Booking request(User rider, Long rideId, int seats, Integer pickupIdx, Integer dropIdx,
                           Double offeredPrice) {
        if (rider.role != Role.RIDER) {
            throw new UnauthorizedActionException("Only riders can book seats");
        }
        Ride ride = rides.findById(rideId).orElseThrow(() -> new RideNotFoundException(rideId));
        if (ride.status != RideStatus.OPEN || ride.departTime.isBefore(LocalDateTime.now())) {
            throw new RideException("This ride is not open for booking", 409);
        }
        if (safety.blockedEitherWay(rider.id, ride.driver.id)) {
            throw new BlockedException();
        }
        if (bookings.existsByRideIdAndRiderIdAndStatusIn(rideId, rider.id,
                List.of(BookingStatus.PENDING, BookingStatus.COUNTERED, BookingStatus.CONFIRMED))) {
            throw new DuplicateBookingException();
        }
        if (seats > ride.seatsLeft) {
            throw new NoSeatsAvailableException(ride.seatsLeft);
        }
        List<RouteUtil.Point> pts = RouteUtil.points(ride);
        int p = pickupIdx == null ? 0 : pickupIdx;
        int d = dropIdx == null ? pts.size() - 1 : dropIdx;
        if (p < 0 || d >= pts.size() || p >= d) {
            throw new RideException("Invalid pickup or drop-off point", 400);
        }
        double list = RouteUtil.price(ride.pricePerSeat, pts, p, d);
        double offer = list;
        if (ride.negotiable && offeredPrice != null) {
            if (!RouteUtil.multipleOf10(offeredPrice)) {
                throw new InvalidPriceException("Your offer must be a multiple of ₹10");
            }
            offer = offeredPrice;
        }
        Booking b = new Booking();
        b.ride = ride;
        b.rider = rider;
        b.seats = seats;
        b.pickupIdx = p;
        b.dropIdx = d;
        b.pickupName = pts.get(p).name();
        b.dropName = pts.get(d).name();
        b.listPrice = list;
        b.offeredPrice = offer;
        bookings.save(b);
        notifications.notify(ride.driver, rider.name + " requested " + seats + " seat(s) for " + stretch(b)
                + " at ₹" + Math.round(offer) + "/seat", "requests");
        return b;
    }

    @Transactional
    public Booking respond(User driver, Long bookingId, boolean accept) {
        Booking b = bookings.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
        Ride ride = rides.findByIdForUpdate(b.ride.id).orElseThrow(() -> new RideNotFoundException(b.ride.id));
        if (!ride.driver.id.equals(driver.id)) {
            throw new UnauthorizedActionException("This is not your ride");
        }
        if (b.status != BookingStatus.PENDING) {
            throw new BookingException("Booking is already " + b.status, 409);
        }
        if (ride.status != RideStatus.OPEN) {
            throw new RideException("Ride is no longer open", 409);
        }
        if (accept) {
            if (b.seats > ride.seatsLeft) {
                throw new NoSeatsAvailableException(ride.seatsLeft);
            }
            ride.seatsLeft -= b.seats;
            b.agreedPrice = b.offeredPrice;
            b.status = BookingStatus.CONFIRMED;
            notifications.notify(b.rider, driver.name + " accepted your booking for " + stretch(b), "bookings");
        } else {
            b.status = BookingStatus.REJECTED;
            notifications.notify(b.rider, driver.name + " declined your request for " + stretch(b), "bookings");
        }
        return b;
    }

    @Transactional
    public Booking counter(User driver, Long bookingId, double price) {
        Booking b = bookings.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
        Ride ride = rides.findByIdForUpdate(b.ride.id).orElseThrow(() -> new RideNotFoundException(b.ride.id));
        if (!ride.driver.id.equals(driver.id)) {
            throw new UnauthorizedActionException("This is not your ride");
        }
        if (!ride.negotiable) {
            throw new BookingException("This ride has a fixed price", 409);
        }
        if (b.status != BookingStatus.PENDING) {
            throw new BookingException("Booking is already " + b.status, 409);
        }
        if (!RouteUtil.multipleOf10(price)) {
            throw new InvalidPriceException("Your counter offer must be a multiple of ₹10");
        }
        b.counterPrice = price;
        b.status = BookingStatus.COUNTERED;
        notifications.notify(b.rider, driver.name + " countered with ₹" + Math.round(price) + "/seat for " + stretch(b), "bookings");
        return b;
    }

    @Transactional
    public Booking acceptCounter(User rider, Long bookingId) {
        Booking b = bookings.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (!b.rider.id.equals(rider.id)) {
            throw new UnauthorizedActionException("This is not your booking");
        }
        if (b.status != BookingStatus.COUNTERED) {
            throw new BookingException("There is no counter offer to accept", 409);
        }
        Ride ride = rides.findByIdForUpdate(b.ride.id).orElseThrow(() -> new RideNotFoundException(b.ride.id));
        if (ride.status != RideStatus.OPEN) {
            throw new RideException("Ride is no longer open", 409);
        }
        if (b.seats > ride.seatsLeft) {
            throw new NoSeatsAvailableException(ride.seatsLeft);
        }
        ride.seatsLeft -= b.seats;
        b.agreedPrice = b.counterPrice;
        b.status = BookingStatus.CONFIRMED;
        notifications.notify(ride.driver, rider.name + " accepted your counter offer for " + stretch(b), "requests");
        return b;
    }

    @Transactional
    public Booking cancel(User user, Long bookingId) {
        Booking b = bookings.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (!b.rider.id.equals(user.id)) {
            throw new UnauthorizedActionException("This is not your booking");
        }
        if (b.status != BookingStatus.PENDING && b.status != BookingStatus.COUNTERED
                && b.status != BookingStatus.CONFIRMED) {
            throw new BookingCancelledException();
        }
        Ride ride = rides.findByIdForUpdate(b.ride.id).orElseThrow(() -> new RideNotFoundException(b.ride.id));
        if (ride.status == RideStatus.COMPLETED) {
            throw new BookingException("Cannot cancel after the ride is completed", 409);
        }
        if (b.status == BookingStatus.CONFIRMED) {
            ride.seatsLeft += b.seats;
            notifications.notify(ride.driver, user.name + " cancelled their seat(s) for " + stretch(b), "myrides");
        }
        b.status = BookingStatus.CANCELLED;
        return b;
    }

    @Transactional
    public Booking pay(User rider, Long bookingId) {
        Booking b = bookings.findById(bookingId).orElseThrow(() -> new BookingNotFoundException(bookingId));
        if (!b.rider.id.equals(rider.id)) {
            throw new UnauthorizedActionException("This is not your booking");
        }
        if (b.status != BookingStatus.CONFIRMED) {
            throw new BookingCancelledException();
        }
        if (b.ride.status != RideStatus.COMPLETED) {
            throw new BookingException("You can pay once the driver completes the ride", 409);
        }
        if (b.paid) {
            throw new BookingException("Already paid", 409);
        }
        b.amountPaid = Math.round(b.agreedPrice * b.seats * 100.0) / 100.0;
        b.paid = true;
        return b;
    }

    public long badge(User u) {
        if (u.role == Role.DRIVER) {
            return bookings.countByRide_Driver_IdAndStatusAndRide_Status(u.id, BookingStatus.PENDING, RideStatus.OPEN);
        }
        if (u.role == Role.RIDER) {
            return bookings.countByRider_IdAndStatusAndRide_Status(u.id, BookingStatus.COUNTERED, RideStatus.OPEN);
        }
        return 0;
    }

    public List<Booking> forRider(User rider) {
        return bookings.findByRiderIdOrderByCreatedAtDesc(rider.id);
    }

    public List<Booking> forDriver(User driver) {
        return bookings.findByRideDriverIdOrderByCreatedAtDesc(driver.id);
    }
}
