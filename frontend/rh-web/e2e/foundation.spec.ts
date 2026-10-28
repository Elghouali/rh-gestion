import { test, expect, Page } from '@playwright/test';
const origin = 'http://localhost:4300';
async function signIn(page: Page, user: string, passwordKey: string) {
  const password = process.env[passwordKey];
  if (!password) throw new Error('Run tools/init-local.ps1 first');
  await page.goto('/');
  await page.getByRole('link', { name: 'Se connecter', exact: true }).click();
  await expect(page).toHaveURL(/localhost:8180/);
  await page.locator('#username').fill(user);
  await page.locator('#password').fill(password);
  await page.locator('#kc-login').click();
  await expect(page).toHaveURL(origin + '/');
  await expect(page.getByRole('table')).toBeVisible();
}
test('anonymous API is protected and login uses PKCE', async ({ page, request }) => {
  const response = await request.get('/api/v1/branches');
  expect(response.status()).toBe(401);
  await page.goto('/');
  await expect(page.getByRole('link', { name: 'Se connecter', exact: true })).toBeVisible();
  const authorization = page.waitForRequest((r) =>
    r.url().includes('/protocol/openid-connect/auth'),
  );
  await page.getByRole('link', { name: 'Se connecter', exact: true }).click();
  const requestUrl = new URL((await authorization).url());
  expect(requestUrl.searchParams.get('code_challenge_method')).toBe('S256');
});
test('owner signs in, sees five sites, switches Arabic, prints and signs out', async ({ page }) => {
  await signIn(page, 'rachid.owner', 'RH_OWNER_PASSWORD');
  await expect(page.locator('.branch-row')).toHaveCount(5);
  await expect(page.locator('.account-banner')).toContainText('Rachid');
  const session = await page.request.get('/api/v1/session');
  const data = await session.json();
  expect(data.authenticated).toBe(true);
  expect(data.roles).toContain('OWNER');
  expect(data.accessToken).toBeUndefined();
  await page.screenshot({ path: 'test-results/rh-workspace-fr.png', fullPage: true });
  await page.getByRole('button', { name: 'العربية', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await expect(page.getByRole('button', { name: /تسجيل الخروج/ })).toBeVisible();
  await page.screenshot({ path: 'test-results/rh-workspace-ar.png', fullPage: true });
  await page.pdf({
    path: 'test-results/rh-etablissements-ar.pdf',
    format: 'A4',
    printBackground: true,
  });
  await page.getByRole('button', { name: /تسجيل الخروج/ }).click();
  await expect(page).toHaveURL(origin + '/');
  await expect(page.getByRole('link', { name: /تسجيل الدخول/ })).toBeVisible();
  const loggedOut = await page.request.get('/api/v1/session');
  expect((await loggedOut.json()).authenticated).toBe(false);
});
test('cashier sees only assigned shop and cannot address another one', async ({ page }) => {
  await signIn(page, 'ibrahim', 'RH_CASHIER_PASSWORD');
  await expect(page.locator('.branch-row')).toHaveCount(1);
  await expect(page.getByRole('table')).toContainText('Magasin 1');
  const denied = await page.request.get('/api/v1/branches/10000000-0000-0000-0000-000000000003');
  expect(denied.status()).toBe(403);
  const forgery = await page.request.get('/api/v1/branches/10000000-0000-0000-0000-000000000003', {
    headers: {
      'X-User-Id': '20000000-0000-0000-0000-000000000001',
      'X-Branch-Id': '10000000-0000-0000-0000-000000000003',
    },
  });
  expect(forgery.status()).toBe(403);
  const missingCsrf = await page.request.post('/logout');
  expect(missingCsrf.status()).toBe(403);
});
test('stock manager sees only depot', async ({ page }) => {
  await signIn(page, 'rachid.stock', 'RH_STOCK_PASSWORD');
  await expect(page.locator('.branch-row')).toHaveCount(1);
  await expect(page.getByRole('table')).toContainText('Dépôt central');
});
