import { useEffect, useRef, useState } from 'react';
import { api } from '../api';
import { ProductSelector } from './ProductSelector';
import type { Product } from '../types';

export function InventoryView({ notify }: { notify: (message: string, kind?: 'error' | 'success') => void }) {
  const [selected, setSelected] = useState<Product | null>(null);
  const [refresh, setRefresh] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [direction, setDirection] = useState<'in' | 'out'>('in');
  const mountedRef = useRef(false);
  useEffect(() => {
    mountedRef.current = true;
    return () => { mountedRef.current = false; };
  }, []);
  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault(); const form = event.currentTarget; const values = new FormData(form);
    if (!selected || submitting) return;
    setSubmitting(true);
    try {
      await api.moveStock(selected.id, direction, { quantity: Number(values.get('quantity')), reason: String(values.get('reason')) });
      if (!mountedRef.current) return;
      form.reset();
      notify(direction === 'in' ? 'Entrada registrada.' : 'Salida registrada.', 'success');
      setRefresh((value) => value + 1);
      try {
        const product = await api.product(selected.id);
        if (mountedRef.current) setSelected(product);
      }
      catch { if (mountedRef.current) notify('Movimiento registrado. No se pudo actualizar el stock mostrado; volvé a buscar el producto.', 'error'); }
    } catch (error) { if (mountedRef.current) notify((error as Error).message, 'error'); }
    finally { if (mountedRef.current) setSubmitting(false); }
  };
  return <section><header className="page-header"><p className="eyebrow">Control de existencias</p><h1>Inventario</h1><p>Registrá cada ajuste para mantener el historial al día.</p></header>
    <form className="panel inventory-form" onSubmit={submit}><fieldset disabled={submitting}><div className="segmented" role="group" aria-label="Tipo de movimiento"><button type="button" className={direction === 'in' ? 'selected in' : ''} onClick={() => setDirection('in')}>Entrada</button><button type="button" className={direction === 'out' ? 'selected out' : ''} onClick={() => setDirection('out')}>Salida</button></div><ProductSelector label="Producto" sellable={false} selected={selected} onSelect={setSelected} disabled={submitting} refresh={refresh} /><label>Cantidad<input name="quantity" type="number" min="1" step="1" required /></label><label>Motivo<textarea name="reason" maxLength={255} required placeholder={direction === 'in' ? 'Ej.: recepción de proveedor' : 'Ej.: merma o ajuste'} rows={3} /></label><button className="primary" disabled={!selected || submitting}>Registrar {direction === 'in' ? 'entrada' : 'salida'}</button></fieldset></form>
  </section>;
}
