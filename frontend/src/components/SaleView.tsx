import { useMemo, useRef, useState } from 'react';
import { api } from '../api';
import { ProductSelector } from './ProductSelector';
import type { Product } from '../types';

interface Line { productId: number; quantity: number; }
const money = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' });

export function SaleView({ notify }: { notify: (message: string, kind?: 'error' | 'success') => void }) {
  const [products, setProducts] = useState<Product[]>([]); const [lines, setLines] = useState<Line[]>([]); const [chosen, setChosen] = useState<Product | null>(null); const [refresh, setRefresh] = useState(0); const [notes, setNotes] = useState(''); const [submitting, setSubmitting] = useState(false);
  const submittingRef = useRef(false);
  const productById = useMemo(() => new Map(products.map((p) => [p.id, p])), [products]);
  const total = lines.reduce((sum, line) => sum + (productById.get(line.productId)?.price ?? 0) * line.quantity, 0);
  const add = () => {
    if (!chosen || lines.some((line) => line.productId === chosen.id)) return;
    setProducts((current) => [...current.filter((product) => product.id !== chosen.id), chosen]);
    setLines((current) => [...current, { productId: chosen.id, quantity: 1 }]);
    setChosen(null);
  };
  const updateQuantity = (productId: number, quantity: number) => setLines((current) => current.map((line) => line.productId === productId ? { ...line, quantity: Math.max(1, quantity) } : line));
  const confirm = async () => {
    if (submittingRef.current) return;
    submittingRef.current = true;
    setSubmitting(true);
    try { await api.createSale({ notes: notes || undefined, items: lines }); setLines([]); setNotes(''); setChosen(null); setRefresh((value) => value + 1); notify('Venta confirmada y stock actualizado.', 'success'); } catch (error) { notify((error as Error).message, 'error'); } finally { submittingRef.current = false; setSubmitting(false); }
  };
  return <section><header className="page-header"><p className="eyebrow">Punto de venta</p><h1>Nueva venta</h1><p>Agregá productos y confirmá. El stock se descuenta automáticamente.</p></header>
    <div className="sale-layout"><section className="panel"><ProductSelector label="Agregar producto" sellable selected={chosen} onSelect={setChosen} disabled={submitting} refresh={refresh} /><button className="secondary wide" onClick={add} disabled={!chosen || submitting}>Agregar a la venta</button><hr />{lines.length === 0 ? <p className="empty">Todavía no agregaste productos.</p> : <div className="sale-lines">{lines.map((line) => { const product = productById.get(line.productId); if (!product) return null; return <article key={line.productId}><div><strong>{product.name}</strong><small>{money.format(product.price)} c/u · disponible {product.stock}</small></div><input aria-label={`Cantidad de ${product.name}`} type="number" min="1" max={product.stock} value={line.quantity} disabled={submitting} onChange={(e) => updateQuantity(line.productId, Number(e.target.value))} /><button className="icon-button" aria-label={`Quitar ${product.name}`} disabled={submitting} onClick={() => setLines((current) => current.filter((item) => item.productId !== line.productId))}>×</button></article>; })}</div>}</section>
      <aside className="sale-summary"><h2>Resumen</h2><p>{lines.length} {lines.length === 1 ? 'producto' : 'productos'}</p><strong>{money.format(total)}</strong><label>Nota opcional<textarea value={notes} disabled={submitting} onChange={(e) => setNotes(e.target.value)} maxLength={500} rows={3} placeholder="Ej.: pedido por teléfono" /></label><button className="primary wide" disabled={lines.length === 0 || submitting} onClick={confirm}>{submitting ? 'Confirmando venta…' : 'Confirmar venta'}</button></aside></div>
  </section>;
}
