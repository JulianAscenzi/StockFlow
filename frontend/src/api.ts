import type { Category, Dashboard, PageResponse, Product } from './types';

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

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(`${apiBaseUrl}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...(accessToken() ? { Authorization: `Bearer ${accessToken()}` } : {}),
      ...options?.headers
    }
  });
  if (response.ok) {
    return response.status === 204 ? (undefined as T) : response.json() as Promise<T>;
  }
  const error = await response.json().catch(() => ({}));
  throw new Error(error.message ?? 'No se pudo completar la operación.');
}

export const api = {
  login: (body: { email: string; password: string }) =>
    request<{ accessToken: string; tokenType: string }>('/api/auth/login', {
      method: 'POST', body: JSON.stringify(body)
    }),
  dashboard: () => request<Dashboard>('/api/dashboard?size=8'),
  products: (name = '', page = 0) => {
    const trimmedName = name.trim();
    const path = trimmedName
      ? `/api/products/search?name=${encodeURIComponent(trimmedName)}&page=${page}&size=20`
      : `/api/products?page=${page}&size=20`;
    return request<PageResponse<Product>>(path);
  },
  allProducts: () => loadAllPages<Product>((page) => request<PageResponse<Product>>(`/api/products?page=${page}&size=100`)),
  activeProducts: () => loadAllPages<Product>((page) => request<PageResponse<Product>>(`/api/products/active?page=${page}&size=100`)),
  categories: () => loadAllPages<Category>((page) => request<PageResponse<Category>>(`/api/categories?page=${page}&size=100`)),
  createCategory: (body: { name: string; description: string }) =>
    request<Category>('/api/categories', { method: 'POST', body: JSON.stringify(body) }),
  createProduct: (body: Omit<Product, 'id' | 'stock' | 'active'>) =>
    request<Product>('/api/products', { method: 'POST', body: JSON.stringify(body) }),
  moveStock: (productId: number, direction: 'in' | 'out', body: { quantity: number; reason: string }) =>
    request(`/api/products/${productId}/stock/${direction}`, { method: 'POST', body: JSON.stringify(body) }),
  createSale: (body: { notes?: string; items: Array<{ productId: number; quantity: number }> }) =>
    request('/api/sales', { method: 'POST', body: JSON.stringify(body) })
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
