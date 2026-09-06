import { useEffect, useState } from 'react';
import { api } from '../api';
import type { Category, Product } from '../types';

export function ProductsView({ notify }: { notify: (message: string, kind?: 'error' | 'success') => void }) {
  const [products, setProducts] = useState<Product[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [query, setQuery] = useState('');
  const [showForm, setShowForm] = useState(false);
  const [showCategoryForm, setShowCategoryForm] = useState(false);
  const refresh = () => Promise.all([api.products(query), api.categories()]).then(([p, c]) => { setProducts(p.content); setCategories(c.content); }).catch((error: Error) => notify(error.message, 'error'));
  useEffect(() => { void refresh(); }, []);
  const search = (event: React.FormEvent) => { event.preventDefault(); void refresh(); };
  const create = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault(); const form = event.currentTarget; const values = new FormData(form);
    try { await api.createProduct({ name: String(values.get('name')), sku: String(values.get('sku')), description: String(values.get('description')), price: Number(values.get('price')), cost: Number(values.get('cost')), minimumStock: Number(values.get('minimumStock')), categoryId: Number(values.get('categoryId')) }); form.reset(); setShowForm(false); notify('Producto creado.', 'success'); await refresh(); } catch (error) { notify((error as Error).message, 'error'); }
  };
  const createCategory = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault(); const form = event.currentTarget; const values = new FormData(form);
    try { await api.createCategory({ name: String(values.get('categoryName')), description: String(values.get('categoryDescription')) }); form.reset(); setShowCategoryForm(false); notify('Categoría creada.', 'success'); await refresh(); } catch (error) { notify((error as Error).message, 'error'); }
  };
  return <section><header className="page-header actions"><div><p className="eyebrow">Catálogo</p><h1>Productos</h1><p>Precios, costos y niveles mínimos en un solo lugar.</p></div><button className="primary" onClick={() => setShowForm(!showForm)}>+ Agregar producto</button></header>
    <section className="category-strip"><div><strong>Categorías</strong><span>{categories.length === 0 ? 'Todavía no hay categorías.' : categories.map((category) => category.name).join(' · ')}</span></div><button className="text-button" onClick={() => setShowCategoryForm(!showCategoryForm)}>+ Nueva categoría</button></section>
    {showCategoryForm && <form className="panel compact-form" onSubmit={createCategory}><label>Nombre<input name="categoryName" maxLength={100} required /></label><label>Descripción opcional<input name="categoryDescription" maxLength={255} /></label><div className="form-actions"><button className="primary">Guardar categoría</button><button type="button" className="secondary" onClick={() => setShowCategoryForm(false)}>Cancelar</button></div></form>}
    {showForm && <form className="panel form-grid" onSubmit={create}><label>Nombre<input name="name" required maxLength={150} /></label><label>SKU<input name="sku" required maxLength={50} /></label><label>Categoría<select name="categoryId" required defaultValue=""><option value="" disabled>Elegí una categoría</option>{categories.map((c) => <option key={c.id} value={c.id}>{c.name}</option>)}</select></label><label>Precio de venta<input name="price" type="number" min="0" step="0.01" required /></label><label>Costo<input name="cost" type="number" min="0" step="0.01" required /></label><label>Stock mínimo<input name="minimumStock" type="number" min="0" step="1" required defaultValue="0" /></label><label className="full">Descripción opcional<textarea name="description" maxLength={500} rows={2} /></label><div className="full form-actions"><button className="primary">Guardar producto</button><button type="button" className="secondary" onClick={() => setShowForm(false)}>Cancelar</button></div></form>}
    <form className="search" onSubmit={search}><label htmlFor="search">Buscar producto</label><input id="search" value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Nombre o parte del nombre" /><button className="secondary">Buscar</button></form>
    <section className="panel table-wrap"><table><thead><tr><th>Producto</th><th>SKU</th><th>Precio</th><th>Stock</th><th>Estado</th></tr></thead><tbody>{products.map((p) => <tr key={p.id}><td><strong>{p.name}</strong><small>{p.description}</small></td><td>{p.sku}</td><td>${p.price}</td><td>{p.stock} <small>/ mín. {p.minimumStock}</small></td><td><span className={p.active ? 'badge active' : 'badge'}>{p.active ? 'Activo' : 'Inactivo'}</span></td></tr>)}</tbody></table>{products.length === 0 && <p className="empty">No hay productos para mostrar.</p>}</section>
  </section>;
}
