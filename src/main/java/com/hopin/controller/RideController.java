package com.hopin.controller;

import com.hopin.model.Ride;
import com.hopin.model.User;
import com.hopin.model.Vehicle;
import com.hopin.service.RideService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class RideController {
    private final RideService rides;

    public RideController(RideService rides) {
        this.rides = rides;
    }

    public record VehicleReq(@NotBlank String model, @NotBlank String plate, @Min(1) int seats) {
    }

    @PostMapping("/vehicles")
    public Vehicle addVehicle(@RequestAttribute("user") User user, @Valid @RequestBody VehicleReq r) {
        return rides.addVehicle(user, r.model(), r.plate(), r.seats());
    }

    @GetMapping("/vehicles")
    public List<Vehicle> myVehicles(@RequestAttribute("user") User user) {
        return rides.myVehicles(user);
    }

    @PostMapping("/rides")
    public Ride post(@RequestAttribute("user") User user, @Valid @RequestBody RideService.NewRide r) {
        return rides.postRide(user, r);
    }

    @GetMapping("/rides/search")
    public List<RideService.RideView> search(@RequestParam(required = false) String source,
                                             @RequestParam(required = false) String destination,
                                             @RequestParam(required = false)
                                             @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                             @RequestParam(defaultValue = "1") int seats) {
        return rides.search(source, destination, date, seats);
    }

    @GetMapping("/rides/mine")
    public List<Ride> mine(@RequestAttribute("user") User user) {
        return rides.myRides(user);
    }

    @GetMapping("/rides/{id}")
    public Ride get(@PathVariable Long id) {
        return rides.get(id);
    }

    @PostMapping("/rides/{id}/cancel")
    public Ride cancel(@RequestAttribute("user") User user, @PathVariable Long id) {
        return rides.cancelRide(user, id);
    }

    @PostMapping("/rides/{id}/complete")
    public Ride complete(@RequestAttribute("user") User user, @PathVariable Long id) {
        return rides.completeRide(user, id);
    }
}
