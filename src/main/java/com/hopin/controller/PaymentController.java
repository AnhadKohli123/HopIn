package com.hopin.controller;

import com.hopin.model.Booking;
import com.hopin.model.User;
import com.hopin.service.HistoryService;
import com.hopin.service.PaymentService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class PaymentController {
    private final PaymentService payments;
    private final HistoryService history;

    public PaymentController(PaymentService payments, HistoryService history) {
        this.payments = payments;
        this.history = history;
    }

    public record MethodReq(String method) {
    }

    public record VerifyReq(String paymentId, String orderId, String signature) {
    }

    public record SimReq(String via) {
    }

    @PostMapping("/bookings/{id}/payment-method")
    public Booking method(@RequestAttribute("user") User user, @PathVariable Long id, @RequestBody MethodReq r) {
        return payments.setMethod(user, id, r.method());
    }

    @PostMapping("/bookings/{id}/pay/order")
    public PaymentService.OrderView order(@RequestAttribute("user") User user, @PathVariable Long id) {
        return payments.createOrder(user, id);
    }

    @PostMapping("/bookings/{id}/pay/verify")
    public Booking verify(@RequestAttribute("user") User user, @PathVariable Long id, @RequestBody VerifyReq r) {
        return payments.verify(user, id, r.paymentId(), r.signature());
    }

    @PostMapping("/bookings/{id}/pay/simulate")
    public Booking simulate(@RequestAttribute("user") User user, @PathVariable Long id, @RequestBody SimReq r) {
        return payments.simulate(user, id, r.via());
    }

    @PostMapping("/bookings/{id}/cash-received")
    public Booking cash(@RequestAttribute("user") User user, @PathVariable Long id) {
        return payments.cashReceived(user, id);
    }

    @GetMapping("/bookings/{id}/receipt")
    public PaymentService.Receipt receipt(@RequestAttribute("user") User user, @PathVariable Long id) {
        return payments.receipt(user, id);
    }

    @GetMapping("/history")
    public HistoryService.History history(@RequestAttribute("user") User user) {
        return history.history(user);
    }
}
