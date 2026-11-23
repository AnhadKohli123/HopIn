package com.hopin.service;

import com.hopin.model.Booking;
import com.hopin.model.BookingStatus;
import com.hopin.model.Ride;
import com.hopin.model.RideStatus;
import com.hopin.model.Role;
import com.hopin.model.User;
import com.hopin.repository.BookingRepository;
import com.hopin.repository.RideRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class HistoryService {
    public static final double SOLO_COST_PER_KM = 7.0;
    private final BookingRepository bookings;
    private final RideRepository rides;

    public HistoryService(BookingRepository bookings, RideRepository rides) {
        this.bookings = bookings;
        this.rides = rides;
    }

    public record Trip(Long bookingId, Long rideId, LocalDateTime date, String from, String to, double km, int seats,
                       double amount, boolean paid, String paymentMethod, String paidVia, Long otherId,
                       String otherName, int passengers, double co2Kg, List<Long> paidBookingIds) {
    }

    public record History(String role, List<Trip> trips, int tripCount, double km, double money, double moneySaved,
                          double co2Kg, int people, double soloCostPerKm, double co2PerKm) {
    }

    public History history(User me) {
        List<Trip> trips = new ArrayList<>();
        Set<Long> people = new HashSet<>();
        double km = 0, money = 0, saved = 0, co2 = 0;
        if (me.role == Role.DRIVER) {
            for (Ride r : rides.findByDriverIdOrderByDepartTimeDesc(me.id)) {
                if (r.status != RideStatus.COMPLETED) {
                    continue;
                }
                List<RouteUtil.Point> pts = RouteUtil.points(r);
                double rideKm = RouteUtil.roadKm(pts, 0, pts.size() - 1);
                int pax = 0;
                double earn = 0, rideCo2 = 0;
                boolean allPaid = true;
                List<Long> paidIds = new ArrayList<>();
                for (Booking b : bookings.findByRideId(r.id)) {
                    if (b.status != BookingStatus.CONFIRMED) {
                        continue;
                    }
                    pax += b.seats;
                    people.add(b.rider.id);
                    earn += total(b);
                    rideCo2 += RouteUtil.roadKm(pts, idx(b.pickupIdx, 0), idx(b.dropIdx, pts.size() - 1)) * PaymentService.CO2_KG_PER_KM * b.seats;
                    if (b.paid) {
                        paidIds.add(b.id);
                    } else {
                        allPaid = false;
                    }
                }
                rideCo2 = round1(rideCo2);
                trips.add(new Trip(null, r.id, r.departTime, r.source, r.destination, rideKm, pax, round2(earn),
                        pax > 0 && allPaid, null, null, null, null, pax, rideCo2, paidIds));
                km += rideKm;
                money += earn;
                saved += earn;
                co2 += rideCo2;
            }
        } else {
            for (Booking b : bookings.findByRiderIdOrderByCreatedAtDesc(me.id)) {
                if (b.status != BookingStatus.CONFIRMED || b.ride.status != RideStatus.COMPLETED) {
                    continue;
                }
                List<RouteUtil.Point> pts = RouteUtil.points(b.ride);
                double tripKm = RouteUtil.roadKm(pts, idx(b.pickupIdx, 0), idx(b.dropIdx, pts.size() - 1));
                double amount = total(b);
                double tripCo2 = round1(tripKm * PaymentService.CO2_KG_PER_KM * b.seats);
                people.add(b.ride.driver.id);
                trips.add(new Trip(b.id, b.ride.id, b.ride.departTime, b.pickupName, b.dropName, tripKm, b.seats,
                        amount, b.paid, b.paymentMethod, b.paidVia, b.ride.driver.id, b.ride.driver.name, b.seats, tripCo2, List.of()));
                km += tripKm;
                money += amount;
                saved += Math.max(0, tripKm * SOLO_COST_PER_KM - amount / Math.max(1, b.seats));
                co2 += tripCo2;
            }
        }
        trips.sort((a, b) -> b.date().compareTo(a.date()));
        return new History(me.role.name(), trips, trips.size(), round1(km), round2(money), Math.round(saved),
                round1(co2), people.size(), SOLO_COST_PER_KM, PaymentService.CO2_KG_PER_KM);
    }

    private double total(Booking b) {
        if (b.paid) {
            return b.amountPaid;
        }
        double per = b.agreedPrice == null ? 0 : b.agreedPrice;
        return round2(per * b.seats);
    }

    private int idx(Integer v, int fallback) {
        return v == null ? fallback : v;
    }

    private double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
