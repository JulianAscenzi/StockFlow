import { test, expect, type Page } from '@playwright/test';

async function signIn(page: Page) {
  await page.getByLabel('Correo electrónico').fill('browser@stockflow.test');
  await page.getByLabel('Contraseña').fill('browser-test-password');
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Productos', exact: true })).toBeVisible();
}

async function login(page: Page) {
  await page.goto('/');
  await signIn(page);
}

async function fixture(page: Page, suffix: string) {
  const token = await page.evaluate(() => localStorage.getItem('stockflow.access-token'));
  const headers = { Authorization: `Bearer ${token}` };
  const categoryResponse = await page.request.post('/api/categories', { headers, data: { name: `Category ${suffix}` } });
  expect(categoryResponse.ok()).toBeTruthy();
  const category = await categoryResponse.json();
  const response = await page.request.post('/api/products', { headers, data: {
    name: `Product ${suffix}`, sku: suffix, categoryId: category.id, price: 100, cost: 40, minimumStock: 0
  } });
  expect(response.ok()).toBeTruthy();
  const product = await response.json();
  expect((await page.request.post(`/api/products/${product.id}/stock/in`, { headers,
    data: { quantity: 5, reason: 'Fixture' } })).ok()).toBeTruthy();
  return { ...product, headers, category };
}

async function catalogSearch(page: Page, query: string) {
  await page.getByLabel('Buscar producto', { exact: true }).fill(query);
  await page.getByRole('button', { name: 'Buscar', exact: true }).click();
}

for (const failure of [false, true]) {
  test(`catalog ignores late ${failure ? 'errors' : 'results'}`, async ({ page }) => {
    await login(page);
    await fixture(page, `CAT_OLD_${failure}`);
    const recent = await fixture(page, `CAT_NEW_${failure}`);
    await page.getByRole('button', { name: 'Productos', exact: true }).click();
    await expect(page.getByText('Cargando catálogo…')).toHaveCount(0);
    let release!: () => void;
    const gate = new Promise<void>((resolve) => { release = resolve; });
    let captured!: () => void;
    const capture = new Promise<void>((resolve) => { captured = resolve; });
    let finished!: () => void;
    const finish = new Promise<void>((resolve) => { finished = resolve; });
    await page.route(`**/api/products/search?name=CAT_OLD_${failure}&*`, async (route) => {
      const response = await route.fetch();
      captured();
      await gate;
      try {
        if (failure) await route.fulfill({ status: 500, json: { message: 'Obsolete catalog error' } });
        else await route.fulfill({ response });
      } finally { finished(); }
    });
    try {
      await catalogSearch(page, `CAT_OLD_${failure}`);
      await capture;
      await catalogSearch(page, `CAT_NEW_${failure}`);
      await expect(page.getByRole('row').filter({ hasText: recent.sku })).toHaveCount(1);
      release();
      await finish;
      await expect(page.getByRole('row').filter({ hasText: `CAT_OLD_${failure}` })).toHaveCount(0);
      await expect(page.getByText('Obsolete catalog error')).toHaveCount(0);
      await expect(page.getByText('Cargando catálogo…')).toHaveCount(0);
    } finally { release(); }
  });
}

test('catalog pagination uses applied filter and discards an earlier page', async ({ page }) => {
  await login(page);
  const product = await fixture(page, 'CAT_PAGE');
  await page.getByRole('button', { name: 'Productos', exact: true }).click();
  await expect(page.getByText('Cargando catálogo…')).toHaveCount(0);
  let release!: () => void;
  const gate = new Promise<void>((resolve) => { release = resolve; });
  let captured!: () => void;
  const capture = new Promise<void>((resolve) => { captured = resolve; });
  let finished!: () => void;
  const finish = new Promise<void>((resolve) => { finished = resolve; });
  await page.route('**/api/products?page=1&*', async (route) => {
    const response = await route.fetch(); captured(); await gate;
    try { await route.fulfill({ response }); } finally { finished(); }
  });
  try {
    await page.getByLabel('Buscar producto', { exact: true }).fill('not-applied');
    await page.getByRole('button', { name: 'Siguiente', exact: true }).click();
    await capture;
    await catalogSearch(page, product.sku);
    await expect(page.getByRole('row').filter({ hasText: product.sku })).toHaveCount(1);
    release(); await finish;
    await expect(page.getByRole('row').filter({ hasText: product.sku })).toHaveCount(1);
    await expect(page.getByRole('row')).toHaveCount(2);
  } finally { release(); }
});
