# HopIn: A Smart Carpooling Platform with Route-Based Ride Matching

Spring Boot 3 + MySQL/Postgres + JWT, with a single-page HTML/JS frontend and OpenStreetMap.

## Run it

1. JDK 17+ and Maven installed.
2. Quick start with the built-in H2 database (no MySQL needed, data resets on restart):

```
mvn spring-boot:run -Dspring-boot.run.profiles=h2
```

3. Open http://localhost:8080. Admin login (seeded on first run): `admin@hopin.com` / `admin123` (or the `ADMIN_PASSWORD` env var if set)

### Using MySQL (data is kept)

1. Install **MySQL Community Server** (MySQL Installer from dev.mysql.com, choose "Server only") and set a root password during setup.
2. In Command Prompt, from the project folder:

```
set DB_USER=root
set DB_PASSWORD=your_mysql_password
set JWT_SECRET=any-long-random-string-at-least-32-characters
mvn spring-boot:run
```

The `hopin` database and all tables are created automatically on first run. Passwords come from environment variables, so they never end up on GitHub. To keep them across windows, use `setx DB_PASSWORD your_mysql_password` once and open a new window.

### Real verification emails (Gmail)

Without this, email codes are shown on screen as demo codes. To really send them:

1. Turn on **2-Step Verification** on the Gmail account (Google Account → Security).
2. Create an **App password** (Google Account → Security → App passwords) named `HopIn`. You get a 16-character password.
3. Before `mvn spring-boot:run`:

```
set MAIL_USER=yourname@gmail.com
set MAIL_PASSWORD=the16characterapppassword
```

Phone codes are always simulated (shown on screen and in the server console), because real SMS needs a paid provider.

## Deploy it online (free): Render + Neon

The app ships with a `Dockerfile`, so Render builds and runs it straight from GitHub. The database is a free Postgres on Neon (Render's own free Postgres is deleted after 30 days).

**1. Database (Neon)**
1. Sign up at https://neon.tech and create a project (region: Singapore or closest to you).
2. On the dashboard, open **Connect** and copy the connection string. It looks like
   `postgresql://USER:PASSWORD@ep-xxx.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`
3. Split it into three values:
   - `DB_URL` = `jdbc:postgresql://ep-xxx.ap-southeast-1.aws.neon.tech/neondb?sslmode=require`
   - `DB_USER` = the `USER` part
   - `DB_PASSWORD` = the `PASSWORD` part

**2. Web app (Render)**
1. Sign up at https://render.com with GitHub.
2. **New → Web Service**, pick the `HopIn` repo. Language: **Docker**. Instance type: **Free**.
3. Add environment variables:

| Key | Value |
|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | from Neon |
| `JWT_SECRET` | any long random string (40+ characters) |
| `ADMIN_PASSWORD` | your own admin password |
| `MAIL_USER`, `MAIL_PASSWORD` | optional, Gmail + app password |
| `RAZORPAY_KEY_ID`, `RAZORPAY_KEY_SECRET` | optional, test keys from Razorpay dashboard (Test mode → API Keys) |

4. **Create Web Service.** The first build takes about 5–8 minutes. Your app is live at `https://<name>.onrender.com`.

Every `git push` redeploys automatically. On the free plan the app sleeps after 15 minutes without visitors, and the next visit takes about a minute to wake it up, so open it a minute before a demo.

## Maps (free, no API key)

The map uses OpenStreetMap: Leaflet for the map, Nominatim for place search and OSRM for road routes. No key, card or billing is needed, but you need an internet connection.

- On the Post a ride page, type a place, press **Enter** or click **Search**, then pick it from the list.
- Nominatim and OSRM are free public servers meant for light use, which is fine for a college project.

## Features

**Rides and booking**
- **Map:** free OpenStreetMap with a Streets / Satellite / Classic switcher, road-style routes and pickup/drop-off pins.
- **Stops, pickups and drop-offs:** up to 5 stops; riders get in and out anywhere on the route and, after booking, only see their own stretch.
- **Recommended price range:** shows whether your price is below, within or above ₹2–₹3.5 per km for the route.
- **Instant booking or approval:** when posting, the driver picks "Approve each request" or "⚡ Instant booking". Riders tap **Book now** at the listed price; with instant booking that seat is confirmed straight away, and lower offers ("Send offer") still need the driver's approval.
- **Fares in tens and bargaining:** prices, offers and counters are multiples of ₹10; stretches always add up to the full price.
- **Meeting-point suggestions:** fuel stations, bus stands and highway exits near each point (from OpenStreetMap), with a one-tap "Use".
- **Rides near me:** 📍 uses your location, or Find picks an exact place; rides within 5/10/20/40 km are matched and the distance is shown. Typing a town name still works.
- **Ride alerts:** when a search is empty, save an alert; you get a notification (and an email, if Gmail is set up and your email is verified) when a matching ride is posted.

**Payments and trips**
- **Pay by cash or online:** riders choose when booking and can switch until they pay. Cash: the driver taps **Cash received** after the trip. Online: after the ride is completed the rider taps **Pay**.
- **Online payments:** with Razorpay test keys set, the real Razorpay checkout opens (test UPI `success@razorpay`, test cards from the Razorpay docs) and the server checks the payment signature. Without keys, a built-in simulated checkout (UPI / card / net banking) is used, so the demo always works.
- **Receipts:** every paid booking has a printable receipt.
- **Trips tab:** history of completed trips with totals. Drivers see rides, passengers, earnings and km driven; riders see trips, km, money spent, money saved compared with driving alone (₹7/km) and CO₂ saved (0.12 kg/km).

**Trust and safety**
- **Verified badge (✔):** email code, phone code and government ID. The ID is checked instantly with no admin step: Aadhaar numbers must pass the Verhoeff checksum, and PAN, driving licence, passport and voter ID numbers must match their official formats. Only the ID type and last 4 characters are stored, plus a one-way fingerprint so one ID can't verify two accounts. This checks that the number is well-formed, not who owns it.
- **Profile photos** (cropped and resized in the browser), **rating summary** on profiles, reviews of drivers and co-passengers before booking.
- **Report and block:** report a user to the admin team; blocking hides each other's rides and stops bookings and messages.
- **Live trip sharing:** start sharing from Trip tools; family get a link (`/track.html#…`, no login) showing your live position on the map. Keep the page open while travelling.
- **SOS:** one tap to call 112, and an SOS alert with your location goes to the admins.

**Everything else**
- **In-app chat** between rider and driver, before and after booking, with unread badges.
- **Notifications bell** for requests, accepts, counters, cancellations, ride alerts and ID decisions.
- **Admin:** overview stats, users (suspend/restore), reports and SOS alerts (with location), all rides.
- **Star ratings, cancellation badge, experience level, preferences, dark mode, mobile layout with bottom navigation, no overbooking** (row locks with `PESSIMISTIC_WRITE`).

Live sharing and "near me" need location permission, which browsers only allow on `localhost` or HTTPS. A tracking link made on localhost only opens on the same computer; once HopIn is hosted online, anyone with the link can open it.

## Booking flow

```
rider offers  -> PENDING --driver accepts--> CONFIRMED
                    |--driver counters--> COUNTERED --rider accepts--> CONFIRMED
                    |--driver rejects---> REJECTED       |--rider declines--> CANCELLED
CONFIRMED --ride completed--> rider pays agreedPrice * seats (online), or driver marks cash received
```

Seats are counted for the whole ride, not per stretch.

## Exception hierarchy

```
ApplicationException
 ├─ RideException:    RideNotFoundException, NoSeatsAvailableException
 ├─ BookingException: BookingNotFoundException, DuplicateBookingException,
 │                    BookingCancelledException, InvalidPriceException
 ├─ UserException:    InvalidLoginException, UnauthorizedActionException, UserAlreadyExistsException,
 │                    VerificationException, BlockedException
 ├─ ChatException
 └─ SafetyException
```

`GlobalExceptionHandler` turns each into JSON: `{"error": "<ClassName>", "message": "..."}`.

## REST API

Send `Authorization: Bearer <token>` on everything except `/api/auth/**` and `/api/public/**`.

| Area | Endpoints |
|---|---|
| Auth | `POST /api/auth/register`, `POST /api/auth/login` |
| Profile | `GET/PUT /api/users/me`, `PUT/DELETE /api/users/me/photo`, `GET /api/users/{id}/profile` |
| Verification | `POST /api/verify/email/send`, `/email/confirm`, `/phone/send`, `/phone/confirm`, `/id` |
| Vehicles | `GET/POST /api/vehicles` |
| Rides | `POST /api/rides`, `GET /api/rides/search?source&destination&date&seats[&fromLat&fromLng&toLat&toLng&radiusKm]`, `GET /api/rides/mine`, `POST /api/rides/{id}/complete`, `/cancel` |
| Bookings | `POST /api/rides/{id}/book`, `GET /api/bookings/mine`, `/requests`, `/badge`, `POST /api/bookings/{id}/accept`, `/reject`, `/counter`, `/accept-counter`, `/cancel` |
| Payments | `POST /api/bookings/{id}/payment-method`, `/pay/order`, `/pay/verify`, `/pay/simulate`, `/cash-received`, `GET /api/bookings/{id}/receipt`, `GET /api/history` |
| Ratings | `POST /api/ratings`, `GET /api/ratings/given`, `GET /api/ratings/user/{id}` |
| Chat | `GET /api/chats`, `GET/POST /api/chats/{rideId}/{riderId}` |
| Alerts | `GET/POST /api/alerts`, `DELETE /api/alerts/{id}`, `GET /api/notifications`, `POST /api/notifications/read` |
| Safety | `POST /api/users/{id}/report`, `POST/DELETE /api/users/{id}/block`, `GET /api/users/blocked`, `POST /api/sos`, `POST /api/live/start`, `POST /api/live/{token}/location`, `/stop`, `GET /api/live/active` |
| Public | `GET /api/public/photos/{id}`, `GET /api/public/live/{token}` |
| Admin | `GET /api/admin/stats`, `/users`, `/reports`, `/rides`, `POST /api/admin/users/{id}/block`, `/unblock`, `POST /api/admin/reports/{id}/resolve` |

## Project layout

```
src/main/java/com/hopin
 ├─ config/       DataSeeder (admin + password encoder)
 ├─ security/     JwtUtil, AuthInterceptor, WebConfig
 ├─ model/        User, Vehicle, Ride, RideStop, Booking, Rating, UserPhoto, VerificationCode,
 │                ChatMessage, RideAlert, Notification, Report, UserBlock, TripShare + enums
 ├─ repository/   Spring Data JPA repositories
 ├─ service/      Auth, Ride, Booking, Rating, Profile, Verification, Mail, Chat, Alert,
 │                Notification, Safety services, RouteUtil (matching + fares), Views
 ├─ controller/   Auth, Ride, Booking, Profile, Verification, Chat, Alert, Safety, Public, Admin
 └─ exception/    custom exception hierarchy + global handler
src/main/resources
 ├─ application.properties (MySQL, Gmail via env vars), application-h2.properties
 └─ static/       index.html (app), track.html (public live tracking), logo.svg
```

## Debugging for your assignment

Put breakpoints in `BookingService.respond`, `BookingService.counter` and `RouteUtil.fraction`, run `HopInApplication` in debug mode, and inspect `ride.seatsLeft`, `b.offeredPrice`, `b.counterPrice` and `b.status` in the Variables panel.
