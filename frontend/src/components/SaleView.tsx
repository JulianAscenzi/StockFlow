import { useEffect, useMemo, useState } from 'react';
import { api } from '../api';
import type { Product } from '../types';

interface Line { productId: number; quantity: number; }
const money = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' });

export function SaleView({ notify }: { notify: (message: string, kind?: 'error' | 'success') => void }) {
  const [products, setProducts] = useState<Product[]>([]); const [lines, setLines] = useState<Line[]>([]); const [chosen, setChosen] = useState(''); const [notes, setNotes] = useState('');
  useEffect(() => { api.activeProducts().then((page) => setProducts(page.content)).catch((error: Error) => notify(error.message, 'error')); }, []);
  const productById = useMemo(() => new Map(products.map((p) => [p.id, p])), [products]);
  const total = lines.reduce((sum, line) => sum + (productById.get(line.productId)?.price ?? 0) * line.quantity, 0);
  const add = () => { const id = Number(chosen); if (!id || lines.some((line) => line.productId === id)) return; setLines((current) => [...current, { productId: id, quantity: 1 }]); setChosen(''); };
  const updateQuantity = (productId: number, quantity: number) => setLines((current) => current.map((line) => line.productId === productId ? { ...line, quantity: Math.max(1, quantity) } : line));
  const confirm = async () => { try { await api.createSale({ notes: notes || undefined, items: lines }); setLines([]); setNotes(''); notify('Venta confirmada y stock actualizado.', 'success'); } catch (error) { notify((error as Error).message, 'error'); } };
  return <section><header className="page-header"><p className="eyebrow">Punto de venta</p><h1>Nueva venta</h1><p>Agregá productos y confirmá. El stock se descuenta automáticamente.</p></header>
    <div className="sale-layout"><section className="panel"><label>Agregar producto<select value={chosen} onChange={(e) => setChosen(e.target.value)}><option value="">Elegí un producto</option>{products.filter((p) => p.stock > 0).map((p) => <option key={p.id} value={p.id}>{p.name} · {money.format(p.price)} · stock {p.stock}</option>)}</select></label><button className="secondary wide" onClick={add} disabled={!chosen}>Agregar a la venta</button><hr />{lines.length === 0 ? <p className="empty">Todavía no agregaste productos.</p> : <div className="sale-lines">{lines.map((line) => { const product = productById.get(line.productId); if (!product) return null; return <article key={line.productId}><div><strong>{product.name}</strong><small>{money.format(product.price)} c/u · disponible {product.stock}</small></div><input aria-label={`Cantidad de ${product.name}`} type="number" min="1" max={product.stock} value={line.quantity} onChange={(e) => updateQuantity(line.productId, Number(e.target.value))} /><button className="icon-button" aria-label={`Quitar ${product.name}`} onClick={() => setLines((current) => current.filter((item) => item.productId !== line.productId))}>×</button></article>; })}</div>}</section>
      <aside className="sale-summary"><h2>Resumen</h2><p>{lines.length} {lines.length === 1 ? 'producto' : 'productos'}</p><strong>{money.format(total)}</strong><label>Nota opcional<textarea value={notes} onChange={(e) => setNotes(e.target.value)} maxLength={500} rows={3} placeholder="Ej.: pedido por teléfono" /></label><button className="primary wide" disabled={lines.length === 0} onClick={confirm}>Confirmar venta</button></aside></div>
  </section>;
}
