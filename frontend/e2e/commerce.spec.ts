import { test, expect, type Page } from '@playwright/test';

async function login(page: Page) {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('browser@stockflow.test');
  await page.getByLabel('Contraseña').fill('browser-test-password');
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Buen día' })).toBeVisible();
}

async function seed(page: Page, suffix: string) {
  const token = await page.evaluate(() => localStorage.getItem('stockflow.access-token'));
  const headers = { Authorization: `Bearer ${token}` };
  const category = await page.request.post('/api/categories', { headers, data: { name: `Category ${suffix}` } });
  expect(category.ok()).toBeTruthy();
  const product = await page.request.post('/api/products', { headers, data: {
    name: `Product ${suffix}`, sku: suffix, categoryId: (await category.json()).id,
    price: 100, cost: 40, minimumStock: 0
  } });
  expect(product.ok()).toBeTruthy();
  const result = await product.json();
  const stock = await page.request.post(`/api/products/${result.id}/stock/in`, { headers, data: { quantity: 5, reason: 'Browser fixture' } });
  expect(stock.ok()).toBeTruthy();
  return { ...result, headers };
}

async function addToSale(page: Page, id: number) {
  await page.getByRole('button', { name: 'Nueva venta', exact: true }).click();
  await expect(page.getByLabel('Agregar producto').locator(`option[value="${id}"]`)).toHaveCount(1);
  await page.getByLabel('Agregar producto').selectOption(String(id));
  await page.getByRole('button', { name: 'Agregar a la venta' }).click();
}

test('commercial flow updates stock and dashboard without reload', async ({ page }) => {
  await login(page);
  await page.getByRole('button', { name: 'Productos', exact: true }).click();
  await page.getByRole('button', { name: '+ Nueva categoría', exact: true }).click();
  await page.getByLabel('Nombre', { exact: true }).fill('Almacén');
  await page.getByRole('button', { name: 'Guardar categoría' }).click();
  await expect(page.getByRole('status')).toContainText('Categoría creada');
  await page.getByRole('button', { name: '+ Agregar producto', exact: true }).click();
  await page.getByLabel('Nombre', { exact: true }).fill('Arroz');
  await page.getByLabel('SKU', { exact: true }).fill('ARROZ');
  await page.getByRole('combobox', { name: /^Categoría/ }).selectOption({ label: 'Almacén' });
  await page.getByLabel('Precio de venta').fill('100');
  await page.getByLabel('Costo', { exact: true }).fill('40');
  await page.getByRole('button', { name: 'Guardar producto' }).click();
  await expect(page.getByRole('status')).toContainText('Producto creado');
  await page.getByRole('button', { name: 'Inventario', exact: true }).click();
  await expect(page.getByRole('combobox', { name: /^Producto/ }).locator('option')).toHaveCount(2);
  await page.getByRole('combobox', { name: /^Producto/ }).selectOption({ index: 1 });
  await page.getByLabel('Cantidad', { exact: true }).fill('5');
  await page.getByLabel('Motivo').fill('Recepción');
  await page.getByRole('button', { name: 'Registrar entrada' }).click();
  await expect(page.getByRole('status')).toContainText('Entrada registrada');
  await page.getByRole('button', { name: 'Nueva venta', exact: true }).click();
  await expect(page.getByLabel('Agregar producto').locator('option')).toHaveCount(2);
  await page.getByLabel('Agregar producto').selectOption({ index: 1 });
  await page.getByRole('button', { name: 'Agregar a la venta' }).click();
  await page.getByLabel('Cantidad de Arroz').fill('2');
  await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Venta confirmada');
  await page.getByRole('button', { name: 'Productos', exact: true }).click();
  await expect(page.getByRole('row').filter({ hasText: 'ARROZ' }).getByRole('cell').nth(3)).toHaveText('3 / mín. 0');
  await page.getByRole('button', { name: 'Resumen', exact: true }).click();
  await expect(page.locator('.metric').filter({ hasText: 'Ventas de hoy' }).locator('strong')).toHaveText('1');
  await expect(page.locator('.metric').filter({ hasText: 'Unidades vendidas' }).locator('strong')).toHaveText('2');
  await expect(page.locator('.metric').filter({ hasText: 'Facturación' }).locator('strong')).toContainText('200');
  await expect(page.locator('.metric').filter({ hasText: 'Margen bruto' }).locator('strong')).toContainText('120');
});

test('confirmation locks controls and sends one request', async ({ page }) => {
  await login(page);
  const product = await seed(page, 'LOCK');
  await addToSale(page, product.id);
  let release!: () => void;
  const gate = new Promise<void>((resolve) => { release = resolve; });
  let requests = 0;
  await page.route('**/api/sales', async (route) => { requests++; await gate; await route.continue(); });
  try {
    await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
    await expect(page.getByRole('button', { name: 'Confirmando venta…' })).toBeDisabled();
    await expect(page.getByLabel('Agregar producto')).toBeDisabled();
    await expect(page.getByLabel('Cantidad de Product LOCK')).toBeDisabled();
    await expect(page.getByLabel('Nota opcional')).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Quitar Product LOCK' })).toBeDisabled();
    expect(requests).toBe(1);
  } finally { release(); }
  await expect(page.getByRole('status')).toContainText('Venta confirmada');
  expect(requests).toBe(1);
});

test('real stock conflict preserves cart for correction', async ({ page }) => {
  await login(page);
  const product = await seed(page, 'CONFLICT');
  await addToSale(page, product.id);
  await page.getByLabel('Cantidad de Product CONFLICT').fill('5');
  const reduction = await page.request.post(`/api/products/${product.id}/stock/out`, {
    headers: product.headers, data: { quantity: 3, reason: 'Concurrent operation' }
  });
  expect(reduction.ok()).toBeTruthy();
  const response = page.waitForResponse((r) => r.url().endsWith('/api/sales') && r.status() === 409);
  await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
  expect((await (await response).json()).code).toBe('INSUFFICIENT_STOCK');
  await expect(page.getByLabel('Cantidad de Product CONFLICT')).toHaveValue('5');
  await page.getByLabel('Cantidad de Product CONFLICT').fill('2');
  await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Venta confirmada');
});

test('invalid credentials, login and logout', async ({ page }) => {
  await page.goto('/');
  await page.getByLabel('Correo electrónico').fill('browser@stockflow.test');
  await page.getByLabel('Contraseña').fill('wrong');
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page.getByRole('alert')).toBeVisible();
  await page.getByLabel('Contraseña').fill('browser-test-password');
  await page.getByRole('button', { name: 'Ingresar', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Buen día' })).toBeVisible();
  await page.getByRole('button', { name: 'Cerrar sesión' }).click();
  await expect(page.getByRole('heading', { name: 'Bienvenido' })).toBeVisible();
  expect(await page.evaluate(() => localStorage.getItem('stockflow.access-token'))).toBeNull();
});

test('dashboard discards a response from an earlier visit', async ({ page }) => {
  await login(page);
  await page.getByRole('button', { name: 'Productos', exact: true }).click();
  let release!: () => void;
  const gate = new Promise<void>((resolve) => { release = resolve; });
  let captured!: () => void;
  const capture = new Promise<void>((resolve) => { captured = resolve; });
  let visits = 0;
  await page.route('**/api/dashboard?*', async (route) => {
    visits++;
    if (visits === 1) {
      const response = await route.fetch();
      const body = await response.json();
      captured();
      await gate;
      await route.fulfill({ response, json: { ...body, saleCount: 99999 } });
    } else await route.continue();
  });
  try {
    await page.getByRole('button', { name: 'Resumen', exact: true }).click();
    await capture;
    await expect(page.getByText('Cargando el resumen de hoy…')).toBeVisible();
    await page.getByRole('button', { name: 'Productos', exact: true }).click();
    await page.getByRole('button', { name: 'Resumen', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Buen día' })).toBeVisible();
    const stale = page.waitForResponse(async (response) => response.url().includes('/api/dashboard?') && (await response.json()).saleCount === 99999);
    release();
    await stale;
    await expect(page.locator('.metric').filter({ hasText: 'Ventas de hoy' }).locator('strong')).not.toHaveText('99999');
  } finally { release(); }
});
