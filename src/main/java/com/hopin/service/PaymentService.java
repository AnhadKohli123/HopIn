package com.hopin.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hopin.exception.BookingNotFoundException;
import com.hopin.exception.PaymentException;
import com.hopin.exception.UnauthorizedActionException;
import com.hopin.model.*;
import com.hopin.repository.BookingRepository;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentService {
    public static final double CO2_KG_PER_KM = 0.12;
    private static final Set<String> SIM_METHODS = Set.of("UPI", "CARD", "NETBANKING");
    private final BookingRepository bookings;
    private final NotificationService notifications;
    private final ObjectMapper json;
    private final String keyId;
    private final String keySecret;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final SecureRandom random = new SecureRandom();

    public PaymentService(BookingRepository bookings, NotificationService notifications, ObjectMapper json,
                          @Value("${hopin.razorpay.key-id:}") String keyId,
                          @Value("${hopin.razorpay.key-secret:}") String keySecret) {
        this.bookings = bookings;
        this.notifications = notifications;
        this.json = json;
        this.keyId = keyId == null ? "" : keyId.trim();
        this.keySecret = keySecret == null ? "" : keySecret.trim();
    }

    public record OrderView(String provider, String keyId, String orderId, long amountPaise, String currency,
                            String name, String email, String description) {
    }

    public record Receipt(String receiptNo, Long bookingId, String riderName, String driverName, String vehicle,
                          String from, String to, LocalDateTime departTime, int seats, double pricePerSeat,
                          double amount, String paidVia, String paymentRef, LocalDateTime paidAt,
                          double km, double co2SavedKg) {
    }

    public boolean razorpayEnabled() {
        return !keyId.isEmpty() && !keySecret.isEmpty();
    }

    @Transactional
    public Booking setMethod(User rider, Long id, String method) {
        Booking b = own(rider, id);
        if (b.paid) {
            throw new PaymentException("This booking is already paid", 409);
        }
        if (b.status == BookingStatus.CANCELLED || b.status == BookingStatus.REJECTED) {
            throw new PaymentException("This booking is no longer active", 409);
        }
        b.paymentMethod = "ONLINE".equalsIgnoreCase(method) ? "ONLINE" : "CASH";
        return b;
    }

    @Transactional
    public OrderView createOrder(User rider, Long id) {
        Booking b = payable(rider, id);
        long paise = Math.round(amount(b) * 100);
        String desc = b.pickupName + " → " + b.dropName;
        if (!razorpayEnabled()) {
            return new OrderView("SIMULATED", null, null, paise, "INR", rider.name, null, desc);
        }
        try {
            String body = json.writeValueAsString(Map.of("amount", paise, "currency", "INR", "receipt", "hopin_" + b.id,
                    "notes", Map.of("bookingId", String.valueOf(b.id))));
            String auth = Base64.getEncoder().encodeToString((keyId + ":" + keySecret).getBytes(StandardCharsets.UTF_8));
            HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.razorpay.com/v1/orders"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Basic " + auth)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                throw new PaymentException("The payment gateway refused the order. Check the Razorpay keys", 502);
            }
            JsonNode node = json.readTree(res.body());
            b.paymentOrderId = node.get("id").asText();
            return new OrderView("RAZORPAY", keyId, b.paymentOrderId, paise, "INR", rider.name, null, desc);
        } catch (PaymentException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new PaymentException("Could not reach the payment gateway. Try again", 502);
        } catch (Exception e) {
            throw new PaymentException("Could not reach the payment gateway. Try again", 502);
        }
    }

    @Transactional
    public Booking verify(User rider, Long id, String paymentId, String signature) {
        Booking b = payable(rider, id);
        if (!razorpayEnabled() || b.paymentOrderId == null) {
            throw new PaymentException("Start the payment again", 409);
        }
        if (paymentId == null || signature == null) {
            throw new PaymentException("Payment details are missing", 400);
        }
        String expected = hmacHex(keySecret, b.paymentOrderId + "|" + paymentId);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8))) {
            throw new PaymentException("Payment could not be verified", 400);
        }
        return markPaid(b, "RAZORPAY", paymentId);
    }

    @Transactional
    public Booking simulate(User rider, Long id, String via) {
        if (razorpayEnabled()) {
            throw new PaymentException("Use the Razorpay checkout to pay", 409);
        }
        String v = via == null ? "" : via.trim().toUpperCase();
        if (!SIM_METHODS.contains(v)) {
            throw new PaymentException("Choose UPI, card or net banking", 400);
        }
        Booking b = payable(rider, id);
        return markPaid(b, v, "SIM" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmmss")) + (100 + random.nextInt(900)));
    }

    @Transactional
    public Booking cashReceived(User driver, Long id) {
        Booking b = bookings.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
        if (!b.ride.driver.id.equals(driver.id)) {
            throw new UnauthorizedActionException("This is not your ride");
        }
        if (b.status != BookingStatus.CONFIRMED) {
            throw new PaymentException("Only confirmed bookings can be paid", 409);
        }
        if (b.ride.status != RideStatus.COMPLETED) {
            throw new PaymentException("Mark the ride completed first", 409);
        }
        if (b.paid) {
            throw new PaymentException("This booking is already paid", 409);
        }
        b.paymentMethod = "CASH";
        return markPaid(b, "CASH", "CASH-" + b.id);
    }

    public Receipt receipt(User me, Long id) {
        Booking b = bookings.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
        if (!b.rider.id.equals(me.id) && !b.ride.driver.id.equals(me.id) && me.role != Role.ADMIN) {
            throw new UnauthorizedActionException("This is not your booking");
        }
        if (!b.paid) {
            throw new PaymentException("A receipt is available once the trip is paid", 409);
        }
        Ride r = b.ride;
        List<RouteUtil.Point> pts = RouteUtil.points(r);
        int from = b.pickupIdx == null ? 0 : b.pickupIdx;
        int to = b.dropIdx == null ? pts.size() - 1 : b.dropIdx;
        double km = RouteUtil.roadKm(pts, from, to);
        String no = "HOP-" + (b.paidAt == null ? "" : b.paidAt.format(DateTimeFormatter.ofPattern("yyyyMMdd")) + "-") + b.id;
        return new Receipt(no, b.id, b.rider.name, r.driver.name, r.vehicle == null ? null : r.vehicle.model + " · " + r.vehicle.plate,
                b.pickupName, b.dropName, r.departTime, b.seats, b.agreedPrice == null ? 0 : b.agreedPrice, b.amountPaid,
                b.paidVia, b.paymentRef, b.paidAt, km, Math.round(km * CO2_KG_PER_KM * b.seats * 10) / 10.0);
    }

    private Booking markPaid(Booking b, String via, String ref) {
        b.amountPaid = amount(b);
        b.paid = true;
        b.paidVia = via;
        b.paymentRef = ref;
        b.paidAt = LocalDateTime.now();
        if ("CASH".equals(via)) {
            notifications.notify(b.rider, b.ride.driver.name + " confirmed your cash payment of ₹" + Math.round(b.amountPaid)
                    + " for " + b.pickupName + " → " + b.dropName, "trips");
        } else {
            notifications.notify(b.ride.driver, b.rider.name + " paid ₹" + Math.round(b.amountPaid) + " online for "
                    + b.pickupName + " → " + b.dropName, "trips");
        }
        return b;
    }

    private double amount(Booking b) {
        double per = b.agreedPrice == null ? 0 : b.agreedPrice;
        return Math.round(per * b.seats * 100.0) / 100.0;
    }

    private Booking own(User rider, Long id) {
        Booking b = bookings.findById(id).orElseThrow(() -> new BookingNotFoundException(id));
        if (!b.rider.id.equals(rider.id)) {
            throw new UnauthorizedActionException("This is not your booking");
        }
        return b;
    }

    private Booking payable(User rider, Long id) {
        Booking b = own(rider, id);
        if (b.status != BookingStatus.CONFIRMED) {
            throw new PaymentException("Only confirmed bookings can be paid", 409);
        }
        if (b.ride.status != RideStatus.COMPLETED) {
            throw new PaymentException("You can pay once the driver completes the ride", 409);
        }
        if (b.paid) {
            throw new PaymentException("This booking is already paid", 409);
        }
        if (!"ONLINE".equals(b.paymentMethod)) {
            throw new PaymentException("This booking is set to cash. Switch it to online to pay here", 409);
        }
        return b;
    }

    static String hmacHex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] out = mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte x : out) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
