import { expect, test, type Page } from '@playwright/test';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

/** Accessible name of a calendar day [n] days from today, e.g. "17 Oct 2026". */
function dayLabel(n: number) {
  const t = new Date();
  const d = new Date(t.getFullYear(), t.getMonth(), t.getDate() + n);
  return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
}

async function signIn(page: Page) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Skip' }).click();
  await page.getByLabel('Mobile number').fill('9845012001');
  await page.getByRole('button', { name: 'Send OTP' }).click();
  await page.getByLabel('One-time code').fill('1234');
  await page.getByRole('button', { name: 'Verify & continue' }).click();
  await page.getByRole('button', { name: /I want to rent/ }).click();
  await expect(page).toHaveURL(/\/home$/);
}

async function unlockAdmin(page: Page, pin = '1234') {
  await page.getByLabel('Admin PIN').fill(pin);
  await page.getByRole('button', { name: 'Unlock dashboard' }).click();
}

test('a customer requests a booking and the admin approves it', async ({ page }) => {
  await signIn(page);
  await page.goto('/item/12');
  await page.getByRole('button', { name: 'Select dates' }).click();
  await page.getByRole('button', { name: dayLabel(20), exact: true }).click();
  await page.getByRole('button', { name: dayLabel(22), exact: true }).click();
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.getByRole('button', { name: 'Send request' }).dblclick();
  // Headings, not getByText: Next's route announcer repeats the page heading.
  await expect(page.getByRole('heading', { name: 'Request sent to the store' })).toBeVisible();
  await page.getByRole('button', { name: 'View my rentals' }).click();
  await page.getByRole('tab', { name: /Requested/ }).click();
  await expect(page.getByRole('article').filter({ hasText: 'Sleeping Bag (-5°C)' })).toHaveCount(1);

  await page.goto('/admin');
  await expect(page).toHaveURL(/\/admin-login$/);
  await unlockAdmin(page);
  await expect(page).toHaveURL(/\/admin$/);
  await page.goto('/admin/bookings');
  const card = page.getByRole('article').filter({ hasText: 'Sleeping Bag (-5°C)' }).filter({ hasText: 'Arjun Mehta' });
  await card.getByRole('button', { name: 'Approve' }).click();
  await page.getByRole('button', { name: 'Confirm' }).click();
  await page.getByRole('tab', { name: /Upcoming/ }).click();
  await expect(page.getByRole('article').filter({ hasText: 'Sleeping Bag (-5°C)' }).getByText('Approved')).toBeVisible();
});

test('admin pages need the PIN', async ({ page }) => {
  await signIn(page);
  await page.goto('/admin/inventory');
  await expect(page).toHaveURL(/\/admin-login$/);
  await unlockAdmin(page, '0000');
  await expect(page.getByText(/Wrong PIN/)).toBeVisible();
  await expect(page).toHaveURL(/\/admin-login$/);
  await unlockAdmin(page);
  await expect(page).toHaveURL(/\/admin$/);
});

test('deep links survive reloads and unknown ids show not found', async ({ page }) => {
  await page.goto('/item/11');
  await expect(page).toHaveURL(/\/onboarding$/);
  await signIn(page);
  await page.goto('/item/11');
  await expect(page.getByRole('heading', { name: '4-Person Dome Tent' })).toBeVisible();
  await page.reload();
  await expect(page.getByRole('heading', { name: '4-Person Dome Tent' })).toBeVisible();
  await page.goto('/item/9999');
  await expect(page.getByRole('heading', { name: 'Item not found' })).toBeVisible();
});

test('malformed and unknown ids show a not-found state instead of crashing', async ({ page }) => {
  await signIn(page);
  await page.goto('/item/abc');
  await expect(page.getByRole('heading', { name: 'Item not found' })).toBeVisible();
  await page.goto('/item/12abc');
  await expect(page.getByRole('heading', { name: 'Item not found' })).toBeVisible();
  await page.goto('/booking-success/9999');
  await expect(page.getByRole('heading', { name: 'Booking not found' })).toBeVisible();
  await page.goto('/admin-login');
  await unlockAdmin(page);
  await expect(page).toHaveURL(/\/admin$/);
  await page.goto('/admin/bill/abc');
  await expect(page.getByText("We couldn't find that anymore.")).toBeVisible();
  await page.goto('/admin/handover/abc');
  await expect(page.getByRole('heading', { name: 'Booking not found' })).toBeVisible();
});

test('the item editor saves every change and keeps edits across tabs', async ({ page }) => {
  await signIn(page);
  await page.goto('/admin-login');
  await unlockAdmin(page);
  await expect(page).toHaveURL(/\/admin$/);
  await page.goto('/admin/items/12');
  const daily = page.getByLabel('Per day ₹', { exact: true });
  await daily.fill('180');
  await page.getByRole('tab', { name: /Units/ }).click();
  await page.getByRole('tab', { name: 'Details' }).click();
  await expect(daily).toHaveValue('180');
  await page.getByRole('button', { name: 'Save changes' }).click();
  const saved = page.getByRole('status').getByText('Changes saved');
  await expect(saved).toBeVisible();
  await expect(saved).toBeHidden({ timeout: 6_000 }); // so the next check sees the second save's toast
  await daily.fill('200');
  await page.getByRole('button', { name: 'Save changes' }).click();
  await expect(saved).toBeVisible();
  await page.reload();
  await expect(page.getByLabel('Per day ₹', { exact: true })).toHaveValue('200');
});
