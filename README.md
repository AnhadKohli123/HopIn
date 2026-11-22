# HopIn: A Smart Carpooling Platform with Route-Based Ride Matching

Spring Boot 3 + MySQL + JWT, with a single-page HTML/JS frontend and OpenStreetMap.

## Run it

1. JDK 17+ and Maven installed.
2. From the folder that contains `pom.xml`, run it with the built-in H2 database (no MySQL needed, data resets on restart):

```
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

3. Open http://localhost:8080. Admin login (seeded on first run): `admin@hopin.com` / `admin123`

### Using MySQL

Start MySQL, then set your credentials as environment variables (Windows Command Prompt shown) and run without the h2 profile:

```
set DB_USER=root
set DB_PASSWORD=your_mysql_password
set JWT_SECRET=any-long-random-string-at-least-32-characters
mvn spring-boot:run
```

The `hopin` database is created automatically. Passwords are read from environment variables so they never end up on GitHub.

## Maps (free, no API key)

The map uses OpenStreetMap: Leaflet for the map, Nominatim for place search and OSRM for road routes. No key, card or billing is needed, but you need an internet connection.

- On the Post a ride page, type a place, press **Enter** or click **Search**, then pick it from the list.
- Nominatim and OSRM are free public servers meant for light use, which is fine for a college project.

## Features

- **Map:** free OpenStreetMap with a Streets / Satellite / Classic switcher (top-right of the map), road-style routes and pickup and drop-off pins.
- **Stops:** the driver searches From, To and up to 5 stops and picks them from the results.
- **Pickups and drop-offs anywhere on the route:** riders choose where to get in and out. After booking, a rider only sees their own stretch (From → To), not the driver's full route.
- **Fares in tens:** price per seat must be a multiple of ₹10. Each stretch is rounded to ₹10 and all stretches still add up exactly to the full price. Offers and counters are also in tens.
- **Bargaining:** on negotiable rides the rider offers a price, and the driver accepts, counters or rejects.
- **Search rules:** From, To, Date and Seats are required, and missing fields are highlighted. Nothing is listed until you search, and "No rides for this route" shows when nothing matches.
- **Reviews before booking:** each search result shows reviews of the driver and the other passengers already on that trip.
- **Profiles:** click any name to see rating, experience, rides published, completed and cancelled, a cancellation badge (Never, Rarely or Frequently cancels rides), preferences (No smoking, No pets, AC, Music, Luggage) and special instructions. Everyone edits their own profile in the Profile tab.
- **Star ratings:** 1–5 stars with an optional comment, once per person per trip. Riders rate the driver, and drivers rate each passenger after completing a ride.
- **Badge:** a red count on Requests (driver: pending requests) or My bookings (rider: counter-offers waiting). It refreshes every 25 seconds.
- **My rides:** split into Active and Completed & cancelled. Requests for completed rides disappear from the Requests tab.
- **Landing page:** teal background, with the logo and name sliding down and "Welcome" fading in. Login and Register also work with Enter.
- **Dark mode:** follows your system setting by default; the ☀️/🌙 button in the header (or top-right on the login page) switches it and remembers your choice. The map switches to dark streets too.
- **No overbooking:** the ride row is locked (`PESSIMISTIC_WRITE`) when seats are confirmed or released.

## Booking flow

```
rider offers  -> PENDING --driver accepts--> CONFIRMED
                    |--driver counters--> COUNTERED --rider accepts--> CONFIRMED
                    |--driver rejects---> REJECTED       |--rider declines--> CANCELLED
CONFIRMED --ride completed--> rider pays agreedPrice * seats
```

Seats are counted for the whole ride, not per stretch.

## Exception hierarchy

```
ApplicationException
 ├─ RideException:    RideNotFoundException, NoSeatsAvailableException
 ├─ BookingException: BookingNotFoundException, DuplicateBookingException,
 │                    BookingCancelledException, InvalidPriceException
 └─ UserException:    InvalidLoginException, UnauthorizedActionException, UserAlreadyExistsException
```

`GlobalExceptionHandler` turns each into JSON: `{"error": "<ClassName>", "message": "..."}`.

## REST API

| Method | Path | Who |
|---|---|---|
| POST | /api/auth/register, /api/auth/login | all |
| POST/GET | /api/vehicles | driver |
| POST | /api/rides (with stops, pricePerSeat, negotiable) | driver |
| GET | /api/rides/search?source=&destination=&date=&seats= | all |
| GET | /api/rides/mine, /api/rides/{id} | driver / all |
| POST | /api/rides/{id}/complete, /api/rides/{id}/cancel | driver |
| POST | /api/rides/{id}/book (seats, pickupIdx, dropIdx, offeredPrice) | rider |
| GET | /api/bookings/mine | rider |
| GET | /api/bookings/requests | driver |
| POST | /api/bookings/{id}/accept, /reject, /counter | driver |
| POST | /api/bookings/{id}/accept-counter, /cancel, /pay | rider |
| POST | /api/ratings | participants |
| GET | /api/ratings/given | ratings you have given |
| GET | /api/bookings/badge | badge count |
| GET/PUT | /api/users/me | own profile |
| GET | /api/users/{id}/profile | public profile with stats and reviews |
| GET | /api/admin/users, /rides, /stats | admin |
| POST | /api/admin/users/{id}/block, /unblock | admin |

Send `Authorization: Bearer <token>` on everything except `/api/auth/**`.

## Project layout

```
src/main/java/com/hopin
 ├─ config/       DataSeeder (admin + password encoder)
 ├─ security/     JwtUtil, AuthInterceptor, WebConfig
 ├─ model/        User, Vehicle, Ride, RideStop, Booking, Rating + enums
 ├─ repository/   Spring Data JPA repositories
 ├─ service/      AuthService, RideService, BookingService, RatingService, ProfileService, RouteUtil
 ├─ controller/   Auth, Ride, Booking (+ratings), Profile, Admin
 └─ exception/    custom exception hierarchy + global handler
src/main/resources
 ├─ application.properties, application-h2.properties
 └─ static/       index.html (frontend), logo.svg
```

## Debugging for your assignment

Put breakpoints in `BookingService.respond`, `BookingService.counter` and `RouteUtil.fraction`, run `HopInApplication` in debug mode, and inspect `ride.seatsLeft`, `b.offeredPrice`, `b.counterPrice` and `b.status` in the Variables panel.
