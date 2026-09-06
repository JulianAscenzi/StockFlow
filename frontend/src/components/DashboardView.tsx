import type { Dashboard } from '../types';

const money = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' });

export function DashboardView({ data, loading }: { data: Dashboard | null; loading: boolean }) {
  if (loading) return <p className="loading">Cargando el resumen de hoy…</p>;
  if (!data) return <p className="empty">No se pudo cargar el resumen. Verificá que el backend esté en ejecución.</p>;
  const cards = [
    ['Ventas de hoy', String(data.saleCount), 'operaciones'],
    ['Facturación', money.format(data.revenue), 'ventas confirmadas'],
    ['Unidades vendidas', String(data.unitsSold), 'productos'],
    ['Margen bruto', money.format(data.grossProfit), 'estimado']
  ];
  return <section><header className="page-header"><div><p className="eyebrow">{data.date}</p><h1>Buen día</h1><p>Este es el pulso de tu negocio hoy.</p></div></header>
    <div className="metrics">{cards.map(([label, value, hint]) => <article className="metric" key={label}><p>{label}</p><strong>{value}</strong><small>{hint}</small></article>)}</div>
    <section className="panel"><div className="panel-header"><div><h2>Productos para reponer</h2><p>Stock igual o por debajo del mínimo.</p></div><span className="count">{data.lowStockProducts.totalElements}</span></div>
      {data.lowStockProducts.content.length === 0 ? <p className="empty">Todo el inventario está por encima de su mínimo.</p> : <div className="table-wrap"><table><thead><tr><th>Producto</th><th>SKU</th><th>Disponible</th><th>Mínimo</th></tr></thead><tbody>{data.lowStockProducts.content.map((product) => <tr key={product.id}><td>{product.name}</td><td>{product.sku}</td><td><span className="stock-low">{product.stock}</span></td><td>{product.minimumStock}</td></tr>)}</tbody></table></div>}
    </section></section>;
}
