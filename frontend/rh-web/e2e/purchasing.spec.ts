import { test, expect, Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';
const depot = '10000000-0000-0000-0000-000000000001';
async function login(page: Page, user: string, key: string) {
  await page.goto('/');
  await page.getByRole('link', { name: 'Se connecter', exact: true }).click();
  await page.locator('#username').fill(user);
  await page.locator('#password').fill(process.env[key]!);
  await page.locator('#kc-login').click();
  await expect(page.getByRole('table')).toBeVisible();
  await page.getByRole('button', { name: /Achats & réceptions/ }).click();
  await expect(page.locator('app-purchasing .loading')).toHaveCount(0);
}
async function save(page: Page, name: string) {
  await page.locator('app-purchasing .editor').getByRole('button', { name, exact: true }).click();
  await expect(page.locator('app-purchasing .editor')).toHaveCount(0);
  await expect(page.locator('app-purchasing .loading')).toHaveCount(0);
  await expect(page.locator('app-purchasing .success')).toBeVisible();
}
test('supplier order, physical quarantine, owner inspection and closed remainder', async ({
  page,
  browser,
}) => {
  test.setTimeout(120000);
  page.setDefaultTimeout(15000);
  const suffix = Date.now(),
    supplierName = '[DEMO E2E] Supplier ' + suffix,
    ref = 'PO-E2E-' + suffix,
    sku = 'BUY-E2E-' + suffix;
  await login(page, 'rachid.owner', 'RH_OWNER_PASSWORD');
  const csrf = await (await page.request.get('/api/v1/csrf')).json();
  const command = async (path: string, data: unknown) => {
    const r = await page.request.post(path, {
      data,
      headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': randomUUID() },
    });
    expect(r.status(), await r.text()).toBe(201);
    return r.json();
  };
  const model = await command('/api/v1/catalog/products', {
    name: '[DEMO E2E] Purchased phone ' + suffix,
    brand: 'RH Test',
    category: 'PHONE',
  });
  const variant = await command('/api/v1/catalog/variants', {
    productId: model.id,
    sku,
    color: 'Noir',
    ramGb: 8,
    storageGb: 128,
    wholesalePrice: '100.00',
    retailPrice: '120.00',
    minimumStock: 2,
  });
  await page.getByRole('button', { name: 'Nouveau fournisseur', exact: true }).click();
  let editor = page.locator('app-purchasing .editor');
  await editor.getByLabel('Nom du fournisseur', { exact: true }).fill(supplierName);
  await editor.getByLabel('Téléphone', { exact: true }).fill('0600000000');
  await save(page, 'Enregistrer');
  const suppliers = await (
    await page.request.get('/api/v1/suppliers', { params: { q: supplierName } })
  ).json();
  expect(suppliers.items).toHaveLength(1);
  await page.getByTestId('new-purchase').click();
  editor = page.locator('app-purchasing .editor');
  await editor
    .getByRole('combobox', { name: 'Fournisseur', exact: true })
    .selectOption(suppliers.items[0].id);
  await editor
    .getByRole('combobox', { name: 'Établissement de réception', exact: true })
    .selectOption(depot);
  await editor.getByLabel('Référence interne', { exact: true }).fill(ref);
  await editor
    .getByLabel('Motif / note', { exact: true })
    .fill('Commande approuvée par propriétaire');
  await editor.getByLabel('Rechercher SKU, modèle ou couleur', { exact: true }).fill(sku);
  await editor
    .locator('.model-search')
    .filter({ has: page.getByLabel('Rechercher SKU, modèle ou couleur', { exact: true }) })
    .getByRole('button', { name: 'Rechercher', exact: true })
    .click();
  await editor
    .getByRole('combobox', { name: 'Référence SKU', exact: true })
    .selectOption(variant.id);
  await editor.getByLabel('Quantité', { exact: true }).fill('5');
  await editor.getByLabel('Coût unitaire (DH)', { exact: true }).fill('70,1250');
  await save(page, 'Commander');
  const orders = await (await page.request.get('/api/v1/purchases', { params: { q: ref } })).json();
  expect(orders.items).toHaveLength(1);
  const order = orders.items[0];
  const stock = async () => {
    const r = await page.request.get('/api/v1/stock/balances', {
      params: { branchId: depot, q: sku },
    });
    expect(r.ok()).toBeTruthy();
    return (await r.json()).items.find((i: any) => i.variant.id === variant.id);
  };
  expect((await stock()).initialized).toBe(false);
  const managerContext = await browser.newContext();
  const manager = await managerContext.newPage();
  manager.setDefaultTimeout(15000);
  await login(manager, 'rachid.stock', 'RH_STOCK_PASSWORD');
  const card = manager.locator('[data-order-id="' + order.id + '"]');
  await card.getByRole('button', { name: 'Réception physique', exact: true }).click();
  let managerEditor = manager.locator('.editor');
  await managerEditor
    .getByLabel('Référence du bon fournisseur', { exact: true })
    .fill('BL-E2E-' + suffix);
  await managerEditor.getByLabel('Motif', { exact: true }).fill('Trois unités comptées');
  await managerEditor.getByLabel('Quantité', { exact: true }).fill('3');
  await save(manager, 'Réception physique');
  expect((await stock()).quarantine).toBe(3);
  expect((await stock()).quantity).toBe(0);
  await card.locator('summary').click();
  await expect(card.getByRole('button', { name: 'Contrôler', exact: true })).toHaveCount(0);
  const managerOrder = await (await manager.request.get('/api/v1/purchases/' + order.id)).json();
  expect(JSON.stringify(managerOrder)).not.toContain('unitCost');
  const receipt = managerOrder.receipts[0];
  const managerCsrf = await (await manager.request.get('/api/v1/csrf')).json();
  const denied = await manager.request.post(
    '/api/v1/purchases/' + order.id + '/receipts/' + receipt.id + '/inspections',
    {
      headers: { [managerCsrf.headerName]: managerCsrf.token, 'Idempotency-Key': randomUUID() },
      data: {
        reason: 'Not delegated',
        items: [{ variantId: variant.id, accepted: 3, rejected: 0 }],
      },
    },
  );
  expect(denied.status()).toBe(403);
  await page.reload();
  await page.getByRole('button', { name: /Achats & réceptions/ }).click();
  await expect(page.locator('app-purchasing .loading')).toHaveCount(0);
  const ownerCard = page.locator('[data-order-id="' + order.id + '"]');
  await ownerCard.locator('summary').click();
  await ownerCard.getByRole('button', { name: 'Contrôler', exact: true }).click();
  editor = page.locator('.editor');
  await editor.getByLabel('Motif', { exact: true }).fill('Deux conformes, une défectueuse');
  await editor.getByLabel('Accepté', { exact: true }).fill('2');
  await editor.getByLabel('Refusé', { exact: true }).fill('1');
  await save(page, 'Valider le contrôle');
  const after = await stock();
  expect(after.quantity).toBe(2);
  expect(after.quarantine).toBe(0);
  expect(after.rejected).toBe(1);
  await ownerCard.getByRole('button', { name: 'Clôturer le reliquat', exact: true }).click();
  editor = page.locator('.editor');
  await editor.getByLabel('Motif', { exact: true }).fill('Deux restantes annulées');
  await save(page, 'Clôturer le reliquat');
  await expect(
    ownerCard.getByRole('button', { name: 'Réception physique', exact: true }),
  ).toHaveCount(0);
  await page.screenshot({ path: 'test-results/rh-purchasing-fr.png', fullPage: true });
  await page.getByRole('button', { name: 'العربية', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await page.screenshot({ path: 'test-results/rh-purchasing-ar.png', fullPage: true });
  const audit = await (
    await page.request.get('/api/v1/stock/movements', {
      params: { branchId: depot, variantId: variant.id },
    })
  ).json();
  expect(audit.items.some((i: any) => i.kind === 'QUALITY_RELEASE')).toBe(true);
  expect(audit.items.some((i: any) => i.kind === 'QUALITY_REJECT')).toBe(true);
  await managerContext.close();
});
