import type { Category, Dashboard, PageResponse, Product, Sale, SaleSummary, SaleRequest } from './types';

const apiBaseUrl = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/$/, '');
const tokenStorageKey = 'stockflow.access-token';

export function accessToken() {
  return localStorage.getItem(tokenStorageKey);
}

export function saveAccessToken(token: string) {
  localStorage.setItem(tokenStorageKey, token);
}

export function clearAccessToken() {
  localStorage.removeItem(tokenStorageKey);
}

export class HttpError extends Error {
  constructor(message: string, readonly status?: number, readonly code?: string) {
    super(message);
    this.name = 'HttpError';
  }
}

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  let response: Response;
  try {
    response = await fetch(`${apiBaseUrl}${path}`, {
      ...options,
      headers: {
        'Content-Type': 'application/json',
        ...(accessToken() ? { Authorization: `Bearer ${accessToken()}` } : {}),
        ...options?.headers
      }
    });
  } catch {
    throw new HttpError('No se pudo conectar con el backend. Verificá que la API esté activa y que VITE_API_BASE_URL apunte a su URL.', 0, 'BACKEND_UNAVAILABLE');
  }
  if (response.ok) {
    if (response.status === 204) return undefined as T;
    try { return await response.json() as T; }
    catch { throw new HttpError('La respuesta del servidor no se pudo interpretar.', response.status); }
  }
  const error = await response.json().catch(() => ({}));
  throw new HttpError(typeof error?.message === 'string' ? error.message : 'No se pudo completar la operación.', response.status, typeof error?.code === 'string' ? error.code : undefined);
}

export const api = {
  sales: (page = 0, signal?: AbortSignal) => request<PageResponse<SaleSummary>>(`/api/sales?page=${page}&size=20`, { signal }),
  sale: (id: number, signal?: AbortSignal) => request<Sale>(`/api/sales/${id}`, { signal }),
  login: (body: { email: string; password: string }) =>
    request<{ accessToken: string; tokenType: string }>('/api/auth/login', {
      method: 'POST', body: JSON.stringify(body)
    }),
  dashboard: () => request<Dashboard>('/api/dashboard?size=8'),
  products: (name = '', page = 0, signal?: AbortSignal) => {
    const trimmedName = name.trim();
    const path = trimmedName
      ? `/api/products/search?name=${encodeURIComponent(trimmedName)}&page=${page}&size=20`
      : `/api/products?page=${page}&size=20`;
    return request<PageResponse<Product>>(path, { signal });
  },
  product: (id: number) => request<Product>(`/api/products/${id}`),
  lookup: (q: string, sellable: boolean, page: number, signal?: AbortSignal) =>
    request<PageResponse<Product>>(`/api/products/lookup?q=${encodeURIComponent(q)}&sellable=${sellable}&page=${page}&size=20`, { signal }),
  categories: (signal?: AbortSignal) => loadAllPages<Category>((page) => request<PageResponse<Category>>(`/api/categories?page=${page}&size=100`, { signal })),
  createCategory: (body: { name: string; description: string }) =>
    request<Category>('/api/categories', { method: 'POST', body: JSON.stringify(body) }),
  createProduct: (body: Omit<Product, 'id' | 'stock' | 'active'>) =>
    request<Product>('/api/products', { method: 'POST', body: JSON.stringify(body) }),
  moveStock: (productId: number, direction: 'in' | 'out', body: { quantity: number; reason: string }) =>
    request(`/api/products/${productId}/stock/${direction}`, { method: 'POST', body: JSON.stringify(body) }),
  createSale: async (body: SaleRequest, key?: string) => {
    const sale = await request<Sale>('/api/sales', { method: 'POST', body: JSON.stringify(body),
      headers: key ? { 'Idempotency-Key': key } : undefined });
    if (!sale || !Number.isSafeInteger(sale.id) || sale.id <= 0 || !Number.isFinite(sale.total)
      || typeof sale.createdAt !== 'string' || !Number.isFinite(Date.parse(sale.createdAt))
      || !Array.isArray(sale.items) || sale.items.length !== body.items.length) {
      throw new HttpError('La respuesta de la venta no se pudo interpretar.', 201);
    }
    return sale;
  }
};

async function loadAllPages<T>(loadPage: (page: number) => Promise<PageResponse<T>>): Promise<T[]> {
  const firstPage = await loadPage(0);
  const pages = await Promise.all(
    Array.from({ length: Math.max(0, firstPage.totalPages - 1) }, (_, index) => loadPage(index + 1))
  );
  return [
    ...firstPage.content,
    ...pages.flatMap((page) => page.content)
  ];
}
