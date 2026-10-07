# RentNest — Equipment Rental Marketplace (Android) — Design Spec

Date: 2026-10-07
Status: Approved design, pending spec review

## 1. Intent

**What the user asked for:** An Android app serving both **customers** and **service providers** in one app, with **inventory management**, polished and easy to use ("world class").

**Agreed context:**
- Domain: **rental / equipment** marketplace: customers rent items (tools, cameras, camping gear, etc.); providers list items and manage their stock.
- Model: **marketplace**: many providers list items; customers browse across all providers.
- Goal of v1: **client demo / prototype**: a polished, fully clickable app with realistic seeded data. No real backend or payments.

**Success criteria:**
- A presenter can run the complete rental lifecycle live on one device: customer browses → books → provider accepts → checks out unit → customer sees active rental → provider processes return (with condition/damage) → customer leaves review.
- Inventory is unit-level: availability and double-booking prevention are correct.
- The app feels premium: consistent design system, motion, dark mode, no crashes, no blank screens.
- The architecture allows a real backend to be added later without rewriting UI.

**Out of scope (v1):** real payments, real authentication, chat, maps/GPS, push notifications (FCM), admin panel, iOS.

## 2. Tech Stack

- Kotlin, Jetpack Compose, Material 3
- MVVM: `ViewModel` + `StateFlow<UiState>`
- Compose Navigation (type-safe routes)
- Hilt for DI
- Room (local DB) with seed data on first launch; DataStore for preferences (current mode, theme, onboarding done)
- Coil for images (bundled drawable/asset placeholders, works offline)
- kotlinx-datetime or java.time (`LocalDate`) for date math
- Testing: JUnit, kotlinx-coroutines-test, Turbine, Room in-memory, Compose UI test
- minSdk 26, targetSdk/compileSdk latest installed stable
- Build: Gradle wrapper (Kotlin DSL) with a JDK compatible with the chosen AGP (install JDK 17/21 if Java 25 is unsupported)

## 3. Architecture

Single Gradle module `app`, packaged by layer/feature:

```
com.rentnest.app
 ├─ data/          Room entities, DAOs, AppDatabase, Room*Repository impls, SeedData
 ├─ domain/        Models, repository interfaces, rule engines (pure Kotlin)
 │    ├─ AvailabilityCalculator
 │    ├─ PricingEngine
 │    ├─ BookingStateMachine
 │    └─ StockAlerts
 ├─ di/            Hilt modules
 ├─ ui/theme/      Colors, typography, shapes, ModeTheme (customer/provider accents)
 ├─ ui/components/ Shared composables
 ├─ ui/common/     Onboarding, Login, ModeSwitch, Notifications, Profile
 ├─ ui/customer/   Home, Search, ItemDetails, Booking, Checkout, MyRentals, Saved
 └─ ui/provider/   Dashboard, Inventory, ItemEditor, Bookings, Handover, Earnings
```

Rules:
- UI depends only on domain interfaces; Room is an implementation detail behind repositories.
- Domain rule engines are pure functions/classes with no Android dependencies, so they are fully unit-testable.
- Each screen = `Screen` composable (stateless, takes `UiState` + callbacks) + `ViewModel`. Stateless screens enable previews and UI tests.

## 4. Data Model

| Entity | Fields |
|---|---|
| `User` | id, name, phone, avatarRes, isProvider (bool) |
| `Provider` | id, userId, shopName, rating, reviewCount, locationText, joinedDate |
| `Category` | id, name, iconRes |
| `Item` | id, providerId, categoryId, title, description, photos (list), dailyRate, weeklyRate, deposit, specs (map), lowStockThreshold (default 1), isActive |
| `ItemUnit` | id, itemId, tag (e.g. "CAM-003"), condition {NEW, GOOD, FAIR, DAMAGED}, status {AVAILABLE, MAINTENANCE, RETIRED} |
| `Booking` | id, itemId, unitId (nullable until accepted), customerId, startDate, endDate (inclusive), status, subtotal, deposit, damageFee, createdAt |
| `HandoverRecord` | id, bookingId, type {PICKUP, RETURN}, checklist (list of checked items), conditionAfter, notes, damageFee, timestamp |
| `Review` | id, itemId, bookingId, customerId, rating (1–5), text, createdAt |
| `Favourite` | userId, itemId |
| `Notification` | id, recipientUserId, audience {CUSTOMER, PROVIDER}, title, body, bookingId?, read, createdAt |

Note: the "rented" state of a unit is derived from ACTIVE bookings, not stored on the unit, which avoids conflicting state.

Booking status machine:
```
REQUESTED ──accept(unit)──► ACCEPTED ──checkOut──► ACTIVE ──return──► RETURNED ──review──► (reviewed flag)
    │                          │
    ├──decline──► DECLINED     └──cancel──► CANCELLED
    └──cancel───► CANCELLED
```
Invalid transitions return `BookingError.InvalidTransition`.

## 5. Domain Rules

**Availability** (`AvailabilityCalculator`):
- usableUnits(item) = units with status AVAILABLE (MAINTENANCE and RETIRED excluded).
- For date d: free(d) = usableUnits − count of bookings in {ACCEPTED, ACTIVE} overlapping d.
- REQUESTED bookings do not reserve stock, but providers see a conflict warning when accepting.
- A date range is bookable iff free(d) ≥ 1 for every d in range.
- `freeUnitsFor(range)` returns concrete units that have no overlapping ACCEPTED/ACTIVE booking (used by the accept unit picker).

**Pricing** (`PricingEngine`):
- days = endDate − startDate + 1 (min 1).
- subtotal = (days / 7) × weeklyRate + (days % 7) × dailyRate, capped so that the remainder never exceeds one more weekly rate: `min(that, ceil(days/7) × weeklyRate)`.
- total due now = subtotal + deposit. Output is an itemised `PriceBreakdown`.
- Amounts stored as `Long` paise; displayed as ₹ with Indian grouping.

**Stock alerts** (`StockAlerts`):
- Low stock: for any day in the next 7, free(d) ≤ item.lowStockThreshold.
- Maintenance: any unit in MAINTENANCE or condition DAMAGED.

**Errors** (sealed `DomainError`): `InvalidDateRange`, `DatesUnavailable`, `NoUnitFree`, `InvalidTransition`, `ValidationFailed(fields)`.

## 6. Screens & Flows

### Shared
- **Onboarding:** 3 illustrated slides → **Login** (phone number + simulated OTP, any 4 digits) → **Choose mode** (Customer / Provider).
- **Mode switch:** toggle in Profile; switches bottom nav and theme accent instantly; persisted in DataStore.
- **Notifications inbox:** cross-mode events (e.g. new booking → provider notification; accepted → customer notification). Badge on top bar bell.
- **Profile:** user info, mode switch, dark mode (system/light/dark), "Reset demo data".

The demo user is both a customer and a provider (owns one seeded provider shop) so a full flow can be shown on one device. Customer mode hides the user's own shop items from booking (the detail screen shows "This is your listing").

### Customer mode (bottom nav: Home · Search · Rentals · Saved · Profile)
1. **Home:** greeting, search field, category chips, "Popular near you" carousel, "Top providers", "Recently viewed".
2. **Search:** text query + filter sheet (category, price range, min rating, available on dates) + sort (relevance, price ↑/↓, rating).
3. **Item details:** photo pager, title, price card (day/week/deposit), provider card, specs, availability calendar (unavailable days greyed), reviews, favourite toggle, sticky "Select dates" CTA.
4. **Booking:** date range picker (unavailable dates disabled) → price breakdown → **Checkout** (simulated card / UPI / wallet selection, 1.5s processing) → animated **Success** screen → creates REQUESTED booking + provider notification.
5. **My Rentals:** tabs Requested / Upcoming (ACCEPTED) / Active / Past (RETURNED, DECLINED, CANCELLED); card with status timeline; actions: cancel (if REQUESTED/ACCEPTED), review (if RETURNED and not reviewed).
6. **Saved:** favourites grid.

### Provider mode (bottom nav: Dashboard · Inventory · Bookings · Earnings · Profile)
1. **Dashboard:** KPI tiles (today's pickups, today's returns, active rentals, pending requests, utilisation % = units in active rentals ÷ usable units), alerts list (low stock, maintenance), quick actions (Add item, View requests).
2. **Inventory:** searchable/filterable list of own items with "available / total units" badge and alert dot. **Item editor** (create/edit): photos (pick from bundled set), title, category, description, daily/weekly rate, deposit, specs key/values, low-stock threshold, active toggle. **Units tab:** list of units; add unit (auto-tag); change condition and status.
3. **Bookings:** tabs Requests / Upcoming / Active / Completed. Request card: accept (unit picker shows only free units) or decline. Upcoming → **Check-out** (pickup checklist). Active → **Return** (checklist, condition after, notes, damage fee; if condition DAMAGED, unit auto-set to MAINTENANCE).
4. **Earnings:** weekly/monthly toggle bar chart (from RETURNED bookings subtotal + damage fees), top-earning items, simulated payouts list.

## 7. Visual Design

- Material 3 with custom palette. **Customer accent: deep teal**; **Provider accent: warm amber**. Neutral surfaces shared. Full dark theme.
- Typography: Plus Jakarta Sans (headings), Inter (body), bundled as font resources.
- Shapes: 20dp cards, 14dp buttons/fields, pill chips.
- Motion: shared-element transition item card → details; animated status timeline; spring bottom-bar indicator; success check/confetti animation; skeleton shimmer while loading.
- Haptics on primary confirmations.
- Accessibility: ≥48dp targets, content descriptions on all icons/images, WCAG AA contrast in both themes, layouts tolerate 1.3× font scale.
- Name "RentNest" and currency ₹ INR live in a single config (`strings.xml`, `MoneyFormatter`) for easy change.

## 8. Error Handling

- Domain operations return `Result<T, DomainError>`; ViewModels map errors to user-facing messages shown inline or via snackbar.
- Item editor validation: title, category, dailyRate > 0 required; weeklyRate ≤ 7 × dailyRate; deposit ≥ 0.
- Every list has an illustrated empty state with a call to action.
- Seed is idempotent; "Reset demo data" clears and reseeds the DB, then restarts at the current mode's home.

## 9. Seed Data

- 8 categories: Cameras, Tools, Camping, Party & Events, Sports, Electronics, Vehicles, Music.
- 6 providers (one owned by the demo user) with ratings and locations (text).
- ~40 items, each with 1–5 units in varied conditions/statuses.
- ~20 bookings across all statuses and the demo user in both roles, relative to "today" so the dashboard always looks alive.
- Reviews on most items. Bundled placeholder images per category.

## 10. Testing

- **Unit (TDD):** `AvailabilityCalculator`, `PricingEngine`, `BookingStateMachine`, `StockAlerts`, item validation, money formatting.
- **Repository:** Room in-memory tests for booking creation/acceptance/return and availability queries.
- **ViewModel:** booking flow and provider accept flow with fake repositories (Turbine).
- **Compose UI smoke:** customer booking happy path; provider accepts request.
- **Final verification:** `./gradlew test assembleDebug`, install on emulator, manual run-through of the success-criteria flow in both modes, light and dark.

## 11. Future (not v1)

Swap Room repositories for Firebase/Supabase; real auth (OTP); payment gateway (Razorpay/Stripe); FCM push; chat; maps & pickup location; provider KYC & commission; admin panel.
