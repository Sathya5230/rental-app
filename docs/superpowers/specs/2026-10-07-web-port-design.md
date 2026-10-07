# RentNest Web Port — Design

## Goal
Run RentNest on Vercel as a web app. Same customer and admin flows, rules and seed data as the Android app. The Android app is unchanged.

## Decisions
- **Approach:** full web port (not a landing page, not Compose for Web).
- **Stack:** Next.js (App Router) + TypeScript + Tailwind, client-rendered. Lives in `web/`; Vercel Root Directory = `web`.
- **Data:** browser-only. IndexedDB (Dexie) seeded on first load; session in localStorage. No backend, no accounts, no env vars.
- **Dropped/adapted:** background SMS gateway becomes "Copy message" and `sms:` link; native photo store becomes data URLs in IndexedDB; Bill PDF via `window.print()`.

## Structure
```
web/src/
  domain/      models, rules (pricing, availability, late fees, item validator, inventory metrics,
               stock alerts, overdue message), money + phone formatters, DomainError union
  data/        repository interfaces, Dexie implementations, seed data/catalog, session store
  app/         routes mirroring Routes.kt:
               onboarding, login, choose-mode, notifications, profile, admin-login
               customer: home, search, item/[id], book/[id], checkout, booking-success, rentals, saved
               admin: dashboard, inventory, bookings, earnings, item-editor, handover, bill, audit
  components/  design system ported from Theme.kt / Components.kt / ItemArt / AvailabilityCalendar
```
Customer UI is mobile-first in a phone-width column; admin UI is responsive with a wider desktop layout.

## Data flow
- First load seeds IndexedDB from SeedData/SeedCatalog; a version key triggers reseed on schema change; admin Profile has "Reset demo data".
- `CatalogRepository`, `BookingRepository`, `InventoryRepository`, `SessionRepository` are async interfaces; UI calls only these.
- Booking: dates screen uses AvailabilityCalculator, checkout uses PricingEngine; confirm writes booking + availability in one Dexie transaction. Handover/return change status; LateFees applied at return.
- Admin routes redirect to admin-login without a session.

## Errors
`DomainError` is a typed union. Repositories return `Result<T, DomainError>`; UI shows inline form errors or toasts. No thrown exceptions in UI code.

## Testing
- Vitest unit tests for every ported rule, translated from DomainRulesTest, RepositoryTest, CheckoutViewModelTest.
- Repository tests against `fake-indexeddb`.
- One Playwright smoke test of the customer booking happy path.
- Done = `next build` passes, tests pass, manual click-through of customer booking and admin handover/bill.

## Deployment
Standard Next.js project in `web/`. Deploy with `vercel --prod` from `web/` (requires user login) or import the GitHub repo in Vercel with Root Directory `web`.

## Delivery phases (commit after each)
1. Scaffold, domain rules + tests, data layer + seed
2. Customer flow
3. Admin flow
4. Polish, smoke test, deploy

## Out of scope
Real backend/auth, multi-device sync, real SMS, push notifications.
