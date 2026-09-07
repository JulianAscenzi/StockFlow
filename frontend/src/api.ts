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
  products: (name = '') => {
    const trimmedName = name.trim();
    const path = trimmedName
      ? `/api/products/search?name=${encodeURIComponent(trimmedName)}&size=100`
      : '/api/products?size=100';
    return request<PageResponse<Product>>(path);
  },
  activeProducts: () => request<PageResponse<Product>>('/api/products/active?size=100'),
  categories: () => request<PageResponse<Category>>('/api/categories?size=100'),
  createCategory: (body: { name: string; description: string }) =>
    request<Category>('/api/categories', { method: 'POST', body: JSON.stringify(body) }),
  createProduct: (body: Omit<Product, 'id' | 'stock' | 'active'>) =>
    request<Product>('/api/products', { method: 'POST', body: JSON.stringify(body) }),
  moveStock: (productId: number, direction: 'in' | 'out', body: { quantity: number; reason: string }) =>
    request(`/api/products/${productId}/stock/${direction}`, { method: 'POST', body: JSON.stringify(body) }),
  createSale: (body: { notes?: string; items: Array<{ productId: number; quantity: number }> }) =>
    request('/api/sales', { method: 'POST', body: JSON.stringify(body) })
};
