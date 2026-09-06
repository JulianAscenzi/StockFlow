import type { Category, Dashboard, PageResponse, Product } from './types';

async function request<T>(path: string, options?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options?.headers }
  });
  if (response.ok) {
    return response.status === 204 ? (undefined as T) : response.json() as Promise<T>;
  }
  const error = await response.json().catch(() => ({}));
  throw new Error(error.message ?? 'No se pudo completar la operación.');
}

export const api = {
  dashboard: () => request<Dashboard>('/api/dashboard?size=8'),
  products: (name = '') => request<PageResponse<Product>>(`/api/products/search?name=${encodeURIComponent(name)}&size=100`),
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
