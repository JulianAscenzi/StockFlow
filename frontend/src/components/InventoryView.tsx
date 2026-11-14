import { useEffect, useRef, useState } from 'react';
import { api, HttpError } from '../api';
import { clearPendingInventory, pendingInventoryMatches, readPendingInventory, savePendingInventory, type PendingInventory } from '../pendingInventory';
import { ProductSelector } from './ProductSelector';
import type { Product } from '../types';

function definitelyRejected(error: unknown) {
  if (!(error instanceof HttpError)) return false;
  const codes: Record<number, string[]> = {
    400: ['VALIDATION_ERROR', 'INVALID_ARGUMENT', 'INVALID_PARAMETER', 'MALFORMED_REQUEST'],
    404: ['PRODUCT_NOT_FOUND'],
    409: ['INSUFFICIENT_STOCK', 'STOCK_LIMIT_EXCEEDED']
  };
  return error.status !== undefined && codes[error.status]?.includes(error.code ?? '') === true;
}

export function InventoryView({ notify }: { notify: (message: string, kind?: 'error' | 'success') => void }) {
  const [initial] = useState(readPendingInventory);
  const [pending, setPending] = useState<PendingInventory | null>(initial.pending);
  const [storageError, setStorageError] = useState(initial.error);
  const [recoveryMessage, setRecoveryMessage] = useState('');
  const [selected, setSelected] = useState<Product | null>(null);
  const [quantity, setQuantity] = useState(initial.pending ? String(initial.pending.payload.quantity) : '');
  const [reason, setReason] = useState(initial.pending?.payload.reason ?? '');
  const [refresh, setRefresh] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [direction, setDirection] = useState<'in' | 'out'>(initial.pending?.direction ?? 'in');
  const submittingRef = useRef(false);
  const mountedRef = useRef(false);
  const locked = submitting || pending !== null || storageError !== '';
  useEffect(() => {
    mountedRef.current = true;
    return () => { mountedRef.current = false; };
  }, []);

  const confirm = async () => {
    if (submittingRef.current || storageError || (!pending && !selected)) return;
    if (!pending && (!Number.isInteger(Number(quantity)) || Number(quantity) <= 0
      || Number(quantity) > 2147483647 || !reason.trim() || reason.length > 255)) {
      notify('Ingresá una cantidad entera positiva y un motivo de hasta 255 caracteres.', 'error');
      return;
    }
    submittingRef.current = true;
    setSubmitting(true);
    try {
      let operation = pending;
      try {
        operation ??= { version: 1, key: crypto.randomUUID(), productId: selected!.id, productName: selected!.name,
          direction, payload: { quantity: Number(quantity), reason } };
        savePendingInventory(operation);
      } catch {
        setRecoveryMessage('No se pudo guardar el ajuste en esta pestaña. No se envió la solicitud. Habilitá el almacenamiento y reintentá.');
        return;
      }
      setPending(operation);
      let movement;
      try {
        movement = await api.moveStock(operation.productId, operation.direction, operation.payload, operation.key);
      } catch (error) {
        if (!mountedRef.current || !pendingInventoryMatches(operation.key)) return;
        if (definitelyRejected(error)) {
          try { clearPendingInventory(); setPending(null); setRecoveryMessage(''); }
          catch { setRecoveryMessage('El ajuste fue rechazado, pero no se pudo limpiar la recuperación. Reintentá con la misma clave.'); }
          notify((error as Error).message, 'error');
        } else {
          setRecoveryMessage(error instanceof HttpError && error.code === 'IDEMPOTENCY_KEY_REUSED'
            ? 'Esta clave corresponde a otro ajuste. Verificá los movimientos registrados antes de cerrar la recuperación.'
            : 'No se pudo confirmar el resultado. El ajuste sigue guardado: reintentá con la misma clave para evitar duplicados.');
        }
        return;
      }
      if (!mountedRef.current || !pendingInventoryMatches(operation.key)) return;
      notify(operation.direction === 'in' ? 'Entrada registrada.' : 'Salida registrada.', 'success');
      try {
        clearPendingInventory();
        setPending(null); setQuantity(''); setReason(''); setRecoveryMessage('');
      } catch {
        setRecoveryMessage(`El movimiento #${movement.id} está confirmado. No se pudo limpiar la recuperación; reintentá para recuperar ese mismo movimiento.`);
        return;
      }
      setRefresh((value) => value + 1);
      try {
        const product = await api.product(operation.productId);
        if (mountedRef.current) setSelected(product);
      } catch {
        if (mountedRef.current) notify('Movimiento registrado. No se pudo actualizar el stock mostrado; volvé a buscar el producto.', 'error');
      }
    } finally {
      submittingRef.current = false;
      if (mountedRef.current) setSubmitting(false);
    }
  };

  const closeRecovery = () => {
    if (!window.confirm('Cerrar esta recuperación no cancela un movimiento registrado. Verificá los movimientos para evitar duplicados. ¿Querés cerrar la recuperación?')) return;
    try {
      clearPendingInventory(); setPending(null); setStorageError(''); setRecoveryMessage(''); setQuantity(''); setReason('');
    } catch { setRecoveryMessage('No se pudo limpiar la recuperación. Conservamos el ajuste para evitar duplicados.'); }
  };

  return <section><header className="page-header"><p className="eyebrow">Control de existencias</p><h1>Inventario</h1><p>Registrá cada ajuste para mantener el historial al día.</p></header>
    {(pending || storageError || recoveryMessage) && <section className="panel" aria-label="Recuperación de inventario">
      <p>{storageError || recoveryMessage || 'Hay un ajuste guardado en esta pestaña. Podés recuperar su resultado sin registrar otro movimiento.'}</p>
      {pending && <p>{pending.direction === 'in' ? 'Entrada' : 'Salida'} de {pending.payload.quantity} · {pending.productName} · {pending.payload.reason}</p>}
      <p>La recuperación se conserva al navegar o recargar esta pestaña; cerrar la pestaña o borrar su almacenamiento la elimina.</p>
      {pending && <button className="primary" disabled={submitting} onClick={confirm}>Reintentar ajuste</button>}
      {(pending || storageError) && <button className="secondary" disabled={submitting} onClick={closeRecovery}>Cerrar recuperación</button>}
    </section>}
    <form className="panel inventory-form" onSubmit={(event) => { event.preventDefault(); if (!locked) void confirm(); }}><fieldset disabled={locked}><div className="segmented" role="group" aria-label="Tipo de movimiento"><button type="button" className={direction === 'in' ? 'selected in' : ''} onClick={() => setDirection('in')}>Entrada</button><button type="button" className={direction === 'out' ? 'selected out' : ''} onClick={() => setDirection('out')}>Salida</button></div><ProductSelector label="Producto" sellable={false} selected={selected} onSelect={setSelected} disabled={locked} refresh={refresh} /><label>Cantidad<input name="quantity" type="number" min="1" max="2147483647" step="1" required value={quantity} onChange={(event) => setQuantity(event.target.value)} /></label><label>Motivo<textarea name="reason" maxLength={255} required value={reason} onChange={(event) => setReason(event.target.value)} placeholder={direction === 'in' ? 'Ej.: recepción de proveedor' : 'Ej.: merma o ajuste'} rows={3} /></label><button className="primary" disabled={!selected || locked}>Registrar {direction === 'in' ? 'entrada' : 'salida'}</button></fieldset></form>
  </section>;
}
