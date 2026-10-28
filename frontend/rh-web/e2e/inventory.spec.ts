import { test, expect, Page, BrowserContext } from '@playwright/test';
const depot = '10000000-0000-0000-0000-000000000001';
const shop = '10000000-0000-0000-0000-000000000002';
async function login(page: Page, user: string, passwordKey: string) {
  await page.goto('/');
  await page.getByRole('link', { name: 'Se connecter', exact: true }).click();
  await page.locator('#username').fill(user);
  await page.locator('#password').fill(process.env[passwordKey]!);
  await page.locator('#kc-login').click();
  await expect(page.getByRole('table')).toBeVisible();
  await page.getByRole('button', { name: /Produits & stock/ }).click();
  await expect(page.locator('app-inventory .loading')).toHaveCount(0);
  await expect(page.locator('app-inventory [role="alert"]')).toHaveCount(0);
}
async function inventoryTab(page: Page, name: string) {
  await page.locator('app-inventory .tabs').getByRole('button', { name, exact: true }).click();
  await expect(page.locator('app-inventory .loading')).toHaveCount(0);
}
async function save(page: Page, label = 'Enregistrer') {
  await page.locator('.editor').getByRole('button', { name: label, exact: true }).click();
  await expect(page.locator('.editor')).toHaveCount(0);
  await expect(page.locator('app-inventory .loading')).toHaveCount(0);
  await expect(page.locator('app-inventory .success')).toBeVisible();
}
test('catalogue variants, all five locations, transit, partial receipts and scoped access', async ({
  page,
  browser,
}) => {
  test.setTimeout(150000);
  const suffix = Date.now().toString();
  const model = '[DEMO E2E] Phone ' + suffix;
  const sku = 'E2E-' + suffix + '-128';
  await login(page, 'rachid.owner', 'RH_OWNER_PASSWORD');
  await page.getByTestId('new-product').click();
  let editor = page.locator('.editor');
  await editor.getByLabel('Nom du modèle', { exact: true }).fill(model);
  await editor.getByLabel('Marque', { exact: true }).fill('RH Test');
  await save(page);
  const models = await (
    await page.request.get('/api/v1/catalog/products', { params: { q: model, size: 100 } })
  ).json();
  const product = models.items.find((p: any) => p.name === model);
  expect(product).toBeTruthy();
  for (const [reference, color, storage] of [
    [sku, 'Noir', 128],
    [sku + '-B', 'Bleu', 256],
  ] as const) {
    await page
      .locator('.inventory-heading')
      .getByRole('button', { name: 'Nouvelle variante', exact: true })
      .click();
    editor = page.locator('.editor');
    await editor.locator('.model-search input').fill(model);
    await editor
      .locator('.model-search')
      .getByRole('button', { name: 'Rechercher', exact: true })
      .click();
    await expect(
      editor.getByLabel('Modèle', { exact: true }).locator('option[value=' + JSON.stringify(product.id) + ']'),
    ).toHaveCount(1);
    await editor.getByLabel('Modèle', { exact: true }).selectOption(product.id);
    await editor.getByLabel('Référence SKU', { exact: true }).fill(reference);
    await editor.getByLabel('Couleur', { exact: true }).fill(color);
    await editor.getByLabel('RAM (Go)', { exact: true }).fill('8');
    await editor.getByLabel('Stockage (Go)', { exact: true }).fill(String(storage));
    await editor.getByLabel('Prix de gros (DH)', { exact: true }).fill('100.00');
    await editor.getByLabel('Prix détail (DH)', { exact: true }).fill('120.00');
    await editor.getByLabel('Seuil minimum', { exact: true }).fill('2');
    await save(page);
  }
  const variants = await (
    await page.request.get('/api/v1/catalog/variants', { params: { q: sku } })
  ).json();
  expect(variants.items).toHaveLength(2);
  const variant = variants.items.find((v: any) => v.sku === sku);
  expect(variant.storageGb).toBe(128);
  const sites = await (await page.request.get('/api/v1/branches')).json();
  expect(sites).toHaveLength(5);
  await inventoryTab(page, 'Stock par établissement');
  for (let index = 0; index < sites.length; index++) {
    const site = sites[index];
    await page
      .locator('.stock-toolbar')
      .getByRole('button', { name: 'Stock initial', exact: true })
      .click();
    editor = page.locator('.editor');
    await editor.getByLabel('Établissement', { exact: true }).selectOption(site.id);
    await editor.getByLabel('Référence SKU', { exact: true }).selectOption(variant.id);
    const qty = site.id === depot ? 10 : index;
    await editor.getByLabel('Quantité', { exact: true }).fill(String(qty));
    await editor.getByLabel(/Coût unitaire connu/).fill('70.00');
    await editor.getByLabel('Motif', { exact: true }).fill('Comptage de démonstration E2E');
    await save(page);
  }
  const quantity = async (branchId: string, bucket = 'quantity') => {
    const r = await page.request.get('/api/v1/stock/balances', { params: { branchId, q: sku } });
    expect(r.ok()).toBeTruthy();
    const data = await r.json();
    return data.items.find((s: any) => s.variant.id === variant.id)[bucket];
  };
  const initialShop = await quantity(shop);
  expect(await quantity(depot)).toBe(10);
  const stockContext = await browser.newContext();
  const stockPage = await stockContext.newPage();
  await login(stockPage, 'rachid.stock', 'RH_STOCK_PASSWORD');
  await inventoryTab(stockPage, 'Transferts');
  await stockPage.getByRole('button', { name: 'Nouveau transfert', exact: true }).click();
  let stockEditor = stockPage.locator('.editor');
  await stockEditor.locator('.model-search input').fill(sku);
  await stockEditor
    .locator('.model-search')
    .getByRole('button', { name: 'Rechercher', exact: true })
    .click();
  await expect(
    stockEditor
      .getByLabel('Référence SKU', { exact: true })
      .locator('option[value="' + variant.id + '"]'),
  ).toHaveCount(1);
  await stockEditor.getByLabel('Destination', { exact: true }).selectOption(shop);
  await stockEditor.getByLabel('Motif', { exact: true }).fill('Transfert de démonstration E2E');
  await stockEditor.getByLabel('Référence SKU', { exact: true }).selectOption(variant.id);
  await stockEditor.getByLabel('Quantité', { exact: true }).fill('4');
  await save(stockPage, 'Expédier');
  expect(await quantity(depot)).toBe(6);
  expect(await quantity(depot, 'inTransit')).toBe(4);
  expect(await quantity(shop)).toBe(initialShop);
  await expect(stockPage.getByRole('button', { name: 'Réceptionner', exact: true })).toHaveCount(0);
  await inventoryTab(page, 'Transferts');
  for (const qty of [2, 2]) {
    const card = page.locator('.transfer-card').filter({ hasText: sku });
    await card.getByRole('button', { name: 'Réceptionner', exact: true }).click();
    editor = page.locator('.editor');
    await editor.getByLabel('Motif', { exact: true }).fill('Réception de démonstration E2E');
    await editor.getByLabel('Quantité', { exact: true }).fill(String(qty));
    await save(page);
  }
  expect(await quantity(shop)).toBe(initialShop + 4);
  expect(await quantity(depot, 'inTransit')).toBe(0);
  await inventoryTab(page, 'Stock par établissement');
  await page.locator('.filters input').fill(sku);
  await page.locator('.filters select').selectOption('');
  await page.locator('.filters').getByRole('button', { name: 'Rechercher', exact: true }).click();
  await expect(page.locator('app-inventory .loading')).toHaveCount(0);
  await expect(page.locator('app-inventory tbody tr')).toHaveCount(10);
  await page.screenshot({ path: 'test-results/rh-stock-fr.png', fullPage: true });
  await page.getByRole('button', { name: 'العربية', exact: true }).click();
  await expect(page.locator('html')).toHaveAttribute('dir', 'rtl');
  await page.screenshot({ path: 'test-results/rh-stock-ar.png', fullPage: true });
  const cashierContext = await browser.newContext();
  const cashierPage = await cashierContext.newPage();
  await login(cashierPage, 'ibrahim', 'RH_CASHIER_PASSWORD');
  await expect(cashierPage.getByTestId('new-product')).toHaveCount(0);
  await inventoryTab(cashierPage, 'Stock par établissement');
  await expect(cashierPage.getByTestId('stock-write')).toHaveCount(0);
  const denied = await cashierPage.request.get('/api/v1/stock/balances', {
    params: { branchId: depot },
  });
  expect(denied.status()).toBe(403);
  const history = await (
    await cashierPage.request.get('/api/v1/stock/movements', {
      params: { branchId: shop, variantId: variant.id },
    })
  ).json();
  expect(JSON.stringify(history)).not.toContain('unitCost');
  await page.reload();
  await expect(page.getByRole('table')).toBeVisible();
  expect(await quantity(depot)).toBe(6);
  await stockContext.close();
  await cashierContext.close();
});
