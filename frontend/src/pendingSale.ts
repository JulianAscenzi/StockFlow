import type { SaleRequest } from './types';

export const pendingSaleKey = 'stockflow.pending-sale.v1';
export interface PendingSale {
  version: 1;
  key: string;
  payload: SaleRequest;
  products: Array<{ id: number; name: string; price: number; stock: number }>;
}

export function readPendingSale(): { pending: PendingSale | null; error: string } {
  try {
    const raw = sessionStorage.getItem(pendingSaleKey);
    if (!raw) return { pending: null, error: '' };
    const value = JSON.parse(raw) as PendingSale;
    if (value.version !== 1 || typeof value.key !== 'string' || !/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(value.key)
      || !value.payload || (value.payload.notes !== undefined && typeof value.payload.notes !== 'string')
      || !Array.isArray(value.payload.items) || value.payload.items.length === 0
      || !value.payload.items.every((item) => Number.isSafeInteger(item.productId) && item.productId > 0 && Number.isSafeInteger(item.quantity) && item.quantity > 0)
      || !Array.isArray(value.products) || !value.products.every((product) => Number.isSafeInteger(product.id) && typeof product.name === 'string' && Number.isFinite(product.price) && Number.isSafeInteger(product.stock))) {
      throw new Error('Invalid pending operation');
    }
    return { pending: value, error: '' };
  } catch {
    return { pending: null, error: 'No se pudo leer la confirmación guardada. Revisá el historial antes de cerrar la recuperación.' };
  }
}

export function savePendingSale(pending: PendingSale) {
  const value = JSON.stringify(pending);
  sessionStorage.setItem(pendingSaleKey, value);
  if (sessionStorage.getItem(pendingSaleKey) !== value) throw new Error('Pending sale was not stored');
}

export function clearPendingSale() {
  sessionStorage.removeItem(pendingSaleKey);
  if (sessionStorage.getItem(pendingSaleKey) !== null) throw new Error('Pending sale was not cleared');
}

export function pendingSaleMatches(key: string) {
  try {
    const raw = sessionStorage.getItem(pendingSaleKey);
    if (!raw) return false;
    const value = JSON.parse(raw) as Partial<PendingSale>;
    return value.version === 1 && value.key === key;
  } catch {
    return false;
  }
}
