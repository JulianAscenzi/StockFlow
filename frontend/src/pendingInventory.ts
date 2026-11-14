export const pendingInventoryKey = 'stockflow.pending-inventory.v1';

export interface PendingInventory {
  version: 1;
  key: string;
  productId: number;
  productName: string;
  direction: 'in' | 'out';
  payload: { quantity: number; reason: string };
}

export function readPendingInventory(): { pending: PendingInventory | null; error: string } {
  try {
    const raw = sessionStorage.getItem(pendingInventoryKey);
    if (!raw) return { pending: null, error: '' };
    const value = JSON.parse(raw) as PendingInventory;
    if (value.version !== 1 || typeof value.key !== 'string'
      || !/^[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}$/i.test(value.key)
      || !Number.isSafeInteger(value.productId) || value.productId <= 0
      || typeof value.productName !== 'string' || !['in', 'out'].includes(value.direction)
      || !value.payload || !Number.isInteger(value.payload.quantity)
      || value.payload.quantity <= 0 || value.payload.quantity > 2147483647
      || typeof value.payload.reason !== 'string' || !value.payload.reason.trim() || value.payload.reason.length > 255) {
      throw new Error('Invalid pending movement');
    }
    return { pending: value, error: '' };
  } catch {
    return { pending: null, error: 'No se pudo leer el ajuste guardado. Verificá los movimientos registrados antes de cerrar la recuperación.' };
  }
}

export function savePendingInventory(operation: PendingInventory) {
  const raw = JSON.stringify(operation);
  sessionStorage.setItem(pendingInventoryKey, raw);
  if (sessionStorage.getItem(pendingInventoryKey) !== raw) throw new Error('Movement was not stored');
}

export function clearPendingInventory() {
  sessionStorage.removeItem(pendingInventoryKey);
  if (sessionStorage.getItem(pendingInventoryKey) !== null) throw new Error('Movement was not cleared');
}

export function pendingInventoryMatches(key: string) {
  return readPendingInventory().pending?.key === key;
}
