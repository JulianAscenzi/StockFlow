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

for (const [section, heading] of [['Resumen', 'Buen día'], ['Productos', 'Productos'],
  ['Inventario', 'Inventario'], ['Historial de ventas', 'Historial de ventas']]) {
  test(`expired session returns to ${section} and keeps sale recovery`, async ({ page }) => {
    await login(page);
    const product = await fixture(page, `SESSION_${section}`);
    const pending = await page.evaluate((product) => {
      const value = JSON.stringify({ version: 1, key: crypto.randomUUID(),
        payload: { items: [{ productId: product.id, quantity: 1 }] },
        products: [{ id: product.id, name: product.name, price: 100, stock: 5 }] });
      sessionStorage.setItem('stockflow.pending-sale.v1', value);
      return value;
    }, product);
    if (section === 'Resumen') {
      await page.getByRole('button', { name: 'Productos', exact: true }).click();
      await expect(page.getByText('Cargando catálogo…')).toHaveCount(0);
    }
    await page.evaluate(() => localStorage.setItem('stockflow.access-token', 'expired'));
    await page.getByRole('button', { name: section, exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Bienvenido', exact: true })).toBeVisible();
    await expect(page.getByText('Tu sesión venció. Volvé a ingresar')).toBeVisible();
    expect(await page.evaluate(() => localStorage.getItem('stockflow.access-token'))).toBeNull();
    await signIn(page);
    await expect(page.getByRole('heading', { name: heading, exact: true })).toBeVisible();
    expect(await page.evaluate(() => sessionStorage.getItem('stockflow.pending-sale.v1'))).toBe(pending);
  });
}

async function addFixtureToSale(page: Page, product: { id: number; sku: string }) {
  await page.getByRole('button', { name: 'Nueva venta', exact: true }).click();
  await page.getByLabel('Buscar por nombre o SKU').fill(product.sku);
  await page.getByRole('button', { name: 'Buscar productos', exact: true }).click();
  await expect(page.getByLabel('Agregar producto').locator(`option[value="${product.id}"]`)).toHaveCount(1);
  await page.getByLabel('Agregar producto').selectOption(String(product.id));
  await page.getByRole('button', { name: 'Agregar a la venta' }).click();
}

test('expired sale confirmation retains its key and can recover after login', async ({ page }) => {
  await login(page);
  const product = await fixture(page, 'SESSION_SALE');
  await addFixtureToSale(page, product);
  await page.evaluate(() => localStorage.setItem('stockflow.access-token', 'expired'));
  await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Bienvenido' })).toBeVisible();
  const pending = await page.evaluate(() => sessionStorage.getItem('stockflow.pending-sale.v1'));
  expect(pending).not.toBeNull();
  await signIn(page);
  await expect(page.getByRole('heading', { name: 'Nueva venta', exact: true })).toBeVisible();
  expect(await page.evaluate(() => sessionStorage.getItem('stockflow.pending-sale.v1'))).toBe(pending);
  const request = page.waitForRequest((request) => request.url().endsWith('/api/sales') && request.method() === 'POST');
  await page.getByRole('button', { name: 'Reintentar confirmación' }).click();
  expect((await request).headers()['idempotency-key']).toBe(JSON.parse(pending!).key);
  await expect(page.getByRole('status')).toContainText('Venta confirmada');
  expect(await page.evaluate(() => sessionStorage.getItem('stockflow.pending-sale.v1'))).toBeNull();
});

test('a late unauthorized response cannot close a new session with the same token', async ({ page }) => {
  await login(page);
  const product = await fixture(page, 'SESSION_LATE');
  const token = await page.evaluate(() => localStorage.getItem('stockflow.access-token'));
  await page.getByRole('button', { name: 'Inventario', exact: true }).click();
  await page.getByLabel('Buscar por nombre o SKU').fill(product.sku);
  await page.getByRole('button', { name: 'Buscar productos', exact: true }).click();
  await expect(page.getByRole('combobox', { name: /^Producto/ }).locator(`option[value="${product.id}"]`)).toHaveCount(1);
  await page.getByRole('combobox', { name: /^Producto/ }).selectOption(String(product.id));
  let release!: () => void;
  const gate = new Promise<void>((resolve) => { release = resolve; });
  let captured!: () => void;
  const capture = new Promise<void>((resolve) => { captured = resolve; });
  let finished!: () => void;
  const finish = new Promise<void>((resolve) => { finished = resolve; });
  await page.route(`**/api/products/${product.id}`, async (route) => {
    captured(); await gate;
    try { await route.fulfill({ status: 401, json: { message: 'Old session' } }); } finally { finished(); }
  });
  try {
    await page.getByLabel('Cantidad', { exact: true }).fill('1');
    await page.getByLabel('Motivo').fill('New delivery');
    await page.getByRole('button', { name: 'Registrar entrada' }).click();
    await capture;
    await page.getByRole('button', { name: 'Cerrar sesión' }).click();
    await page.route('**/api/auth/login', (route) => route.fulfill({ json: { accessToken: token, tokenType: 'Bearer' } }));
    await signIn(page);
    release(); await finish;
    await expect(page.getByRole('heading', { name: 'Inventario', exact: true })).toBeVisible();
    expect(await page.evaluate(() => localStorage.getItem('stockflow.access-token'))).toBe(token);
  } finally { release(); }
});

test('invalid sale quantities never send requests or create a recovery', async ({ page }) => {
  await login(page);
  const product = await fixture(page, 'QUANTITY');
  await addFixtureToSale(page, product);
  let requests = 0;
  page.on('request', (request) => { if (request.url().endsWith('/api/sales') && request.method() === 'POST') requests++; });
  const quantity = page.getByLabel(`Cantidad de ${product.name}`);
  for (const value of ['', '0', '-1', '1.5', '6', '2147483648']) {
    await quantity.fill(value);
    await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
    await expect(quantity).toHaveValue(value);
    await expect(quantity).toHaveAttribute('aria-invalid', 'true');
    await expect(page.getByRole('alert')).toBeVisible();
    expect(requests).toBe(0);
    expect(await page.evaluate(() => sessionStorage.getItem('stockflow.pending-sale.v1'))).toBeNull();
    await expect(page.getByLabel('Nota opcional')).toBeEnabled();
  }
  await quantity.fill('2');
  await expect(quantity).toHaveAttribute('aria-invalid', 'false');
  await page.getByRole('button', { name: 'Confirmar venta', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Venta confirmada');
  expect(requests).toBe(1);
});

test('sale recovery replays its original payload despite the displayed stock', async ({ page }) => {
  await login(page);
  const product = await fixture(page, 'QUANTITY_REPLAY');
  const key = await page.evaluate(() => crypto.randomUUID());
  const payload = { items: [{ productId: product.id, quantity: 2 }] };
  const response = await page.request.post('/api/sales', { headers: { ...product.headers, 'Idempotency-Key': key }, data: payload });
  expect(response.status()).toBe(201);
  const sale = await response.json();
  await page.evaluate(({ key, payload, product }) => sessionStorage.setItem('stockflow.pending-sale.v1',
    JSON.stringify({ version: 1, key, payload, products: [{ id: product.id, name: product.name, price: 100, stock: 0 }] })),
  { key, payload, product });
  await page.getByRole('button', { name: 'Nueva venta', exact: true }).click();
  await page.getByRole('button', { name: 'Reintentar confirmación' }).click();
  await expect(page.getByRole('status')).toContainText(`Venta #${sale.id}.`);
  expect((await (await page.request.get(`/api/products/${product.id}`, { headers: product.headers })).json()).stock).toBe(3);
  expect(await page.evaluate(() => sessionStorage.getItem('stockflow.pending-sale.v1'))).toBeNull();
});

for (const kind of ['product', 'category'] as const) {
  test(`${kind} creation blocks duplicate submissions and preserves rejected values`, async ({ page }) => {
    await login(page);
    const product = await fixture(page, `WRITE_${kind}`);
    await page.getByRole('button', { name: 'Productos', exact: true }).click();
    await expect(page.getByText('Cargando catálogo…')).toHaveCount(0);
    await page.getByRole('button', { name: kind === 'product' ? '+ Agregar producto' : '+ Nueva categoría', exact: true }).click();
    const form = page.locator('form.catalog-form');
    const name = form.getByLabel('Nombre', { exact: true });
    await name.fill(`Created ${kind}`);
    if (kind === 'product') {
      await form.getByLabel('SKU', { exact: true }).fill('CREATED_WRITE');
      await form.getByRole('combobox', { name: 'Categoría', exact: true }).selectOption(String(product.category.id));
      await form.getByLabel('Precio de venta').fill('12');
      await form.getByLabel('Costo', { exact: true }).fill('6');
    }
    let release!: () => void;
    const gate = new Promise<void>((resolve) => { release = resolve; });
    let requests = 0;
    await page.route(`**/api/${kind === 'product' ? 'products' : 'categories'}`, async (route) => {
      if (route.request().method() !== 'POST') { await route.continue(); return; }
      requests++;
      if (requests === 1) {
        await gate;
        await route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ message: 'Creación rechazada', code: 'CONFLICT' }) });
      } else await route.continue();
    });
    await form.getByRole('button', { name: kind === 'product' ? 'Guardar producto' : 'Guardar categoría', exact: true }).click();
    await expect(name).toBeDisabled();
    await expect(form.getByRole('button', { name: 'Cancelar', exact: true })).toBeDisabled();
    await form.evaluate((element: HTMLFormElement) => { element.requestSubmit(); element.requestSubmit(); });
    expect(requests).toBe(1);
    release();
    await expect(name).toBeEnabled();
    await expect(name).toHaveValue(`Created ${kind}`);
    await form.getByRole('button', { name: kind === 'product' ? 'Guardar producto' : 'Guardar categoría', exact: true }).click();
    await expect(form).toHaveCount(0);
    await expect(page.getByRole('status')).toContainText(kind === 'product' ? 'Producto creado.' : 'Categoría creada.');
    expect(requests).toBe(2);
  });

  test(`${kind} creation reports persistence when catalog refresh fails`, async ({ page }) => {
    await login(page);
    const product = await fixture(page, `REFRESH_${kind}`);
    await page.getByRole('button', { name: 'Productos', exact: true }).click();
    await expect(page.getByText('Cargando catálogo…')).toHaveCount(0);
    await page.getByRole('button', { name: kind === 'product' ? '+ Agregar producto' : '+ Nueva categoría', exact: true }).click();
    const form = page.locator('form.catalog-form');
    await form.getByLabel('Nombre', { exact: true }).fill(`Refresh ${kind}`);
    if (kind === 'product') {
      await form.getByLabel('SKU', { exact: true }).fill('REFRESH_CREATED');
      await form.getByRole('combobox', { name: 'Categoría', exact: true }).selectOption(String(product.category.id));
      await form.getByLabel('Precio de venta').fill('12');
      await form.getByLabel('Costo', { exact: true }).fill('6');
    }
    let created = false;
    await page.route('**/api/categories**', async (route) => {
      if (route.request().method() === 'GET' && created) await route.fulfill({ status: 500, contentType: 'application/json', body: '{}' });
      else if (kind === 'category' && route.request().method() === 'POST') {
        const response = await route.fetch(); expect(response.status()).toBe(201); created = true; await route.fulfill({ response });
      } else await route.continue();
    });
    if (kind === 'product') await page.route('**/api/products', async (route) => {
      if (route.request().method() !== 'POST') { await route.continue(); return; }
      const response = await route.fetch(); expect(response.status()).toBe(201); created = true; await route.fulfill({ response });
    });
    await form.getByRole('button', { name: kind === 'product' ? 'Guardar producto' : 'Guardar categoría', exact: true }).click();
    await expect(form).toHaveCount(0);
    await expect(page.getByRole('status')).toContainText(`${kind === 'product' ? 'Producto creado.' : 'Categoría creada.'} No se pudo actualizar el catálogo.`);
    expect(created).toBe(true);
  });
}

for (const rejected of [false, true]) {
  test(`late catalog creation ${rejected ? 'rejection' : 'success'} cannot affect another section`, async ({ page }) => {
    await login(page);
    await page.getByRole('button', { name: 'Productos', exact: true }).click();
    await expect(page.getByText('Cargando catálogo…')).toHaveCount(0);
    await page.getByRole('button', { name: '+ Nueva categoría', exact: true }).click();
    await page.locator('form.catalog-form').getByLabel('Nombre', { exact: true }).fill(`Late creation ${rejected}`);
    await page.evaluate(() => {
      const originalFetch = window.fetch;
      window.fetch = async (...args) => {
        const response = await originalFetch(...args);
        if (String(args[0]).endsWith('/api/categories') && args[1]?.method === 'POST') {
          const originalJson = response.json.bind(response);
          response.json = async () => {
            const body = await originalJson();
            // Signal after the response consumers have run their promise continuations.
            setTimeout(() => { document.documentElement.dataset.creationHandled = 'true'; }, 0);
            return body;
          };
        }
        return response;
      };
    });
    let release!: () => void;
    const gate = new Promise<void>((resolve) => { release = resolve; });
    let captured!: () => void;
    const capturedRequest = new Promise<void>((resolve) => { captured = resolve; });
    await page.route('**/api/categories', async (route) => {
      if (route.request().method() !== 'POST') { await route.continue(); return; }
      const response = rejected ? undefined : await route.fetch();
      captured(); await gate;
      if (response) await route.fulfill({ response });
      else await route.fulfill({ status: 409, contentType: 'application/json', body: JSON.stringify({ message: 'Late rejection' }) });
    });
    await page.getByRole('button', { name: 'Guardar categoría', exact: true }).click();
    await capturedRequest;
    await page.getByRole('button', { name: 'Resumen', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Buen día', exact: true })).toBeVisible();
    let refreshes = 0;
    page.on('request', (request) => {
      if (request.method() === 'GET' && /\/api\/(products|categories)/.test(request.url())) refreshes++;
    });
    release();
    await page.waitForFunction(() => document.documentElement.dataset.creationHandled === 'true');
    await expect(page.getByRole('status')).toHaveCount(0);
    expect(refreshes).toBe(0);
  });
}
