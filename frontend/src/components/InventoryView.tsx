import { useEffect, useState } from 'react';
import { api } from '../api';
import type { Product } from '../types';

export function InventoryView({ notify }: { notify: (message: string, kind?: 'error' | 'success') => void }) {
  const [products, setProducts] = useState<Product[]>([]);
  const [productId, setProductId] = useState('');
  const [direction, setDirection] = useState<'in' | 'out'>('in');
  useEffect(() => { api.products().then((page) => setProducts(page.content)).catch((error: Error) => notify(error.message, 'error')); }, []);
  const submit = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault(); const values = new FormData(event.currentTarget);
    try { await api.moveStock(Number(productId), direction, { quantity: Number(values.get('quantity')), reason: String(values.get('reason')) }); event.currentTarget.reset(); notify(direction === 'in' ? 'Entrada registrada.' : 'Salida registrada.', 'success'); } catch (error) { notify((error as Error).message, 'error'); }
  };
  return <section><header className="page-header"><p className="eyebrow">Control de existencias</p><h1>Inventario</h1><p>Registrá cada ajuste para mantener el historial al día.</p></header>
    <form className="panel inventory-form" onSubmit={submit}><div className="segmented" role="group" aria-label="Tipo de movimiento"><button type="button" className={direction === 'in' ? 'selected in' : ''} onClick={() => setDirection('in')}>Entrada</button><button type="button" className={direction === 'out' ? 'selected out' : ''} onClick={() => setDirection('out')}>Salida</button></div><label>Producto<select value={productId} onChange={(e) => setProductId(e.target.value)} required><option value="">Elegí un producto</option>{products.map((p) => <option key={p.id} value={p.id}>{p.name} · disponible {p.stock}</option>)}</select></label><label>Cantidad<input name="quantity" type="number" min="1" step="1" required /></label><label>Motivo<textarea name="reason" maxLength={255} required placeholder={direction === 'in' ? 'Ej.: recepción de proveedor' : 'Ej.: merma o ajuste'} rows={3} /></label><button className="primary" disabled={!productId}>Registrar {direction === 'in' ? 'entrada' : 'salida'}</button></form>
  </section>;
}
