import { useMemo, useRef, useState } from 'react';
import { api, HttpError } from '../api';
import { clearPendingSale, pendingSaleMatches, readPendingSale, savePendingSale, type PendingSale } from '../pendingSale';
import { ProductSelector } from './ProductSelector';
import type { Product } from '../types';

type EditableLine = { productId: number; quantity: string };

function quantityError(quantity: string, stock?: number) {
  const value = Number(quantity);
  if (quantity.trim() === '' || !Number.isInteger(value) || value <= 0) return 'La cantidad debe ser un entero positivo.';
  if (value > 2147483647) return 'La cantidad supera el máximo permitido.';
  if (stock !== undefined && value > stock) return `La cantidad supera el stock mostrado (${stock}).`;
  return '';
}

const money = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' });
function definitelyRejected(error: unknown) {
  if (!(error instanceof HttpError)) return false;
  const codes: Record<number, string[]> = {
    400: ['VALIDATION_ERROR', 'EMPTY_SALE', 'INVALID_ARGUMENT', 'INVALID_PARAMETER', 'MALFORMED_REQUEST'],
    404: ['PRODUCT_NOT_FOUND'],
    409: ['INSUFFICIENT_STOCK', 'PRODUCT_INACTIVE']
  };
  return error.status !== undefined && codes[error.status]?.includes(error.code ?? '') === true;
}

export function SaleView({ notify, onHistory, onLoginRequired }: {
  notify: (message: string, kind?: 'error' | 'success') => void;
  onHistory: () => void;
  onLoginRequired: () => void;
}) {
  const [initial] = useState(readPendingSale);
  const [pending, setPending] = useState<PendingSale | null>(initial.pending);
  const [storageError, setStorageError] = useState(initial.error);
  const [products, setProducts] = useState<PendingSale['products']>(initial.pending?.products ?? []);
  const [lines, setLines] = useState<EditableLine[]>(initial.pending?.payload.items.map((line) => ({ ...line, quantity: String(line.quantity) })) ?? []);
  const [chosen, setChosen] = useState<Product | null>(null);
  const [refresh, setRefresh] = useState(0);
  const [notes, setNotes] = useState(initial.pending?.payload.notes ?? '');
  const [submitting, setSubmitting] = useState(false);
  const [recoveryMessage, setRecoveryMessage] = useState('');
  const [sessionExpired, setSessionExpired] = useState(false);
  const submittingRef = useRef(false);
  const locked = submitting || pending !== null || storageError !== '';
  const productById = useMemo(() => new Map(products.map((product) => [product.id, product])), [products]);
  const total = lines.reduce((sum, line) => {
    const quantity = Number(line.quantity);
    return sum + (productById.get(line.productId)?.price ?? 0) * (Number.isFinite(quantity) && quantity > 0 ? quantity : 0);
  }, 0);

  const add = () => {
    if (!chosen || locked || lines.some((line) => line.productId === chosen.id)) return;
    setProducts((current) => [...current.filter((product) => product.id !== chosen.id), chosen]);
    setLines((current) => [...current, { productId: chosen.id, quantity: '1' }]);
    setChosen(null);
  };
  const confirm = async () => {
    if (submittingRef.current || storageError || lines.length === 0) return;
    if (!pending && lines.some((line) => quantityError(line.quantity, productById.get(line.productId)?.stock))) {
      notify('Revisá las cantidades antes de confirmar la venta.', 'error');
      return;
    }
    submittingRef.current = true;
    setSubmitting(true);
    setSessionExpired(false);
    try {
      let operation = pending;
      try {
        operation ??= { version: 1, key: crypto.randomUUID(), payload: { notes: notes || undefined, items: lines.map((line) => ({ productId: line.productId, quantity: Number(line.quantity) })) }, products };
        savePendingSale(operation);
      } catch {
        setRecoveryMessage('No se pudo guardar la confirmación en esta pestaña. No se envió la solicitud. Habilitá el almacenamiento y reintentá.');
        return;
      }
      setPending(operation);
      let sale;
      try { sale = await api.createSale(operation.payload, operation.key); }
      catch (error) {
        if (definitelyRejected(error)) {
          try {
            if (pendingSaleMatches(operation.key)) {
              clearPendingSale(); setPending(null); setRecoveryMessage('');
            }
          }
          catch { setRecoveryMessage('La venta fue rechazada, pero no se pudo limpiar la recuperación. Reintentá con la misma clave.'); }
          notify(error instanceof HttpError && error.code === 'PRODUCT_INACTIVE'
            ? 'El producto está inactivo. Quitalo de la venta para continuar'
            : (error as Error).message, 'error');
        } else {
          setSessionExpired(error instanceof HttpError && error.status === 401);
          setRecoveryMessage(error instanceof HttpError && error.code === 'IDEMPOTENCY_KEY_REUSED'
            ? 'Esta clave corresponde a otra operación. Revisá el historial antes de cerrar la recuperación.'
            : 'No se pudo confirmar el resultado. La operación sigue guardada: reintentá con la misma clave para evitar duplicados.');
        }
        return;
      }
      if (!pendingSaleMatches(operation.key)) return;
      notify(`Venta confirmada y stock actualizado. Venta #${sale.id}.`, 'success');
      setRefresh((value) => value + 1);
      try {
        if (pendingSaleMatches(operation.key)) {
          clearPendingSale();
          setPending(null); setLines([]); setProducts([]); setNotes(''); setChosen(null); setRecoveryMessage('');
        }
      } catch {
        setRecoveryMessage(`La venta #${sale.id} está confirmada. No se pudo limpiar la recuperación local; reintentá para recuperar esa misma venta.`);
      }
    } finally { submittingRef.current = false; setSubmitting(false); }
  };
  const closeRecovery = () => {
    if (!window.confirm('Cerrar esta recuperación no cancela ninguna venta existente. Revisá el historial para evitar duplicados. ¿Querés cerrar la recuperación?')) return;
    try {
      clearPendingSale(); setPending(null); setStorageError(''); setRecoveryMessage(''); setLines([]); setProducts([]); setNotes(''); setSessionExpired(false);
    } catch { setRecoveryMessage('No se pudo limpiar la recuperación. Conservamos la operación para evitar duplicados.'); }
  };
  return <section><header className="page-header"><p className="eyebrow">Punto de venta</p><h1>Nueva venta</h1><p>Agregá productos y confirmá. El stock se descuenta automáticamente.</p></header>
    {(pending || storageError || recoveryMessage) && <section className="panel" aria-label="Recuperación de venta">
      <p>{storageError || recoveryMessage || 'Hay una confirmación guardada en esta pestaña. Podés recuperar su resultado sin crear otra venta.'}</p>
      <p>La recuperación se conserva al navegar o recargar esta pestaña; cerrar la pestaña o borrar su almacenamiento la elimina.</p>
      {pending && <button className="primary" disabled={submitting} onClick={confirm}>Reintentar confirmación</button>}
      <button className="secondary" disabled={submitting} onClick={onHistory}>Revisar historial</button>
      {sessionExpired && <button className="secondary" onClick={onLoginRequired}>Volver a iniciar sesión</button>}
      {(pending || storageError) && <button className="secondary" disabled={submitting} onClick={closeRecovery}>Cerrar recuperación</button>}
    </section>}
    <div className="sale-layout"><section className="panel">
      <ProductSelector label="Agregar producto" sellable selected={chosen} onSelect={setChosen} disabled={locked} refresh={refresh} />
      <button className="secondary wide" onClick={add} disabled={!chosen || locked}>Agregar a la venta</button><hr />
      {lines.length === 0 ? <p className="empty">Todavía no agregaste productos.</p> : <div className="sale-lines">{lines.map((line) => {
        const product = productById.get(line.productId);
        const name = product?.name ?? `Producto #${line.productId}`;
        const error = pending ? '' : quantityError(line.quantity, product?.stock);
        const errorId = `sale-quantity-error-${line.productId}`;
        return <article key={line.productId}><div><strong>{name}</strong>{product && <small>{money.format(product.price)} c/u · disponible {product.stock}</small>}</div>
          <input aria-label={`Cantidad de ${name}`} type="number" min="1" step="1" max={product?.stock} aria-invalid={Boolean(error)} aria-describedby={error ? errorId : undefined} value={line.quantity} disabled={locked}
            onChange={(event) => setLines((current) => current.map((item) => item.productId === line.productId ? { ...item, quantity: event.target.value } : item))} />
          <button className="icon-button" aria-label={`Quitar ${name}`} disabled={locked} onClick={() => setLines((current) => current.filter((item) => item.productId !== line.productId))}>×</button>
          {error && <p id={errorId} className="line-error" role="alert">{error}</p>}
        </article>;
      })}</div>}
    </section><aside className="sale-summary"><h2>Resumen</h2><p>{lines.length} {lines.length === 1 ? 'producto' : 'productos'}</p><strong>{money.format(total)}</strong>
      <label>Nota opcional<textarea value={notes} disabled={locked} onChange={(event) => setNotes(event.target.value)} maxLength={500} rows={3} placeholder="Ej.: pedido por teléfono" /></label>
      <button className="primary wide" disabled={lines.length === 0 || locked} onClick={confirm}>{submitting ? 'Confirmando venta…' : 'Confirmar venta'}</button>
    </aside></div>
  </section>;
}
