# RentNest web

The web version of RentNest, the Android rental app in `../app`. Customers browse gear, pick dates and send a rental request. The store admin approves requests, hands items over, closes rentals with late, damage, cleaning and transport charges, and manages inventory, stock audits and earnings.

It's a Next.js app with no backend. All data lives in the browser:

- **IndexedDB** (via [Dexie](https://dexie.org)) holds the catalogue, bookings and everything else. It is seeded with demo data on first load.
- **localStorage** holds the session: onboarding, sign-in, chosen mode, theme and recently viewed items.
- **sessionStorage** holds the admin unlock, so the dashboard locks again in a new tab.

Each browser has its own copy of the data. The customer and admin flows share data only within one browser, and changes show up live across tabs.

## Run it

```bash
npm install
npm run dev        # http://localhost:3000
```

Demo sign-in: any valid Indian mobile number (for example 98450 12001) and any 4-digit code. Admin PIN: `1234`. The PIN can be changed from Admin → Change admin PIN.

To start over, go to Admin → Reset demo data. Seed dates are relative to the day you reset, so the dashboard always has rentals due today and one overdue.

## Test it

```bash
npm test           # unit tests (Vitest), run with TZ=Asia/Kolkata to catch time-zone bugs
npm run e2e        # browser smoke tests (Playwright); builds and serves on port 3100
```

Before the first `npm run e2e`, install the browser with `npx playwright install chromium`.

## How it's organised

| Folder | What's there |
|---|---|
| `src/domain` | Pure rules ported from the Android `domain` package: pricing, availability, booking states, late fees, stock alerts, item validation, money/date/phone formatting. |
| `src/data` | The Dexie schema, demo seed, and repositories (catalog, inventory, bookings, notifications, session). Writes return `Outcome<T>` with typed errors. |
| `src/lib` | Screen logic that is worth testing on its own: search, dashboard, inventory rows, audit, earnings, bills, item form. |
| `src/components` | The design system: buttons, fields, dialogs, item art, status pills, calendar, bill. |
| `src/app` | Routes. Customer screens are in `(customer)`, admin screens in `admin` (PIN required). |

Money is always integer paise. Dates are `"YYYY-MM-DD"` strings in the user's local calendar.

## Differences from the Android app

- **Overdue SMS.** A browser can't send texts, so the admin opens the reminder, copies it or opens their phone's SMS app, then taps "Mark as sent".
- **Photos** are resized to at most 1600 px and stored in the browser.
- **Bills** print or save as PDF through the browser's print dialog, and "Share" uses the system share sheet where available.
- **No sync between devices.** Every browser has its own demo store.

## Deploy to Vercel

The app needs no environment variables.

**From GitHub (recommended):** in Vercel, choose Add New → Project and import the repository. Set **Root Directory** to `web`, keep the detected Next.js settings, and deploy. Every push to `main` redeploys.

**From the command line,** run these in this folder:

```bash
npx vercel login
npx vercel --prod
```
