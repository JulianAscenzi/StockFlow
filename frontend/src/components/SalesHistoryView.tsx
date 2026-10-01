import { useEffect, useState } from 'react';
import { api } from '../api';
import type { PageResponse, Sale, SaleSummary } from '../types';

const money = new Intl.NumberFormat('es-AR', { style: 'currency', currency: 'ARS' });
const date = new Intl.DateTimeFormat('es-AR', { dateStyle: 'short', timeStyle: 'short', timeZone: 'America/Argentina/Buenos_Aires' });

export function SalesHistoryView() {
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<number | null>(null);
  const [list, setList] = useState<PageResponse<SaleSummary> | null>(null);
  const [detail, setDetail] = useState<Sale | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [retry, setRetry] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    const load = selected === null
      ? api.sales(page, controller.signal).then((data) => { if (!controller.signal.aborted) setList(data); })
      : api.sale(selected, controller.signal).then((data) => { if (!controller.signal.aborted) setDetail(data); });
    load.catch((failure: Error) => { if (!controller.signal.aborted) setError(failure.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [page, selected, retry]);
  return <section>
    <header className="page-header"><h1>Historial de ventas</h1><p>Importes en pesos argentinos y horario de Argentina.</p></header>
    {selected !== null && <button className="secondary" onClick={() => setSelected(null)}>Volver al historial</button>}
    {loading ? <p role="status">Cargando ventas…</p> : error ? <div role="alert"><p>{error}</p><button onClick={() => setRetry((value) => value + 1)}>Reintentar</button></div> : selected !== null && detail ?
      <section className="panel">
        <h2>Venta #{detail.id}</h2><p>{date.format(new Date(detail.createdAt))}</p>
        <div className="table-wrap"><table><thead><tr><th>Producto</th><th>SKU</th><th>Cantidad</th><th>Precio</th><th>Subtotal</th></tr></thead>
          <tbody>{detail.items.map((item) => <tr key={item.id}><td>{item.productName}</td><td>{item.productSku}</td><td>{item.quantity}</td><td>{money.format(item.unitPrice)}</td><td>{money.format(item.subtotal)}</td></tr>)}</tbody></table></div>
        <p>Notas: {detail.notes || 'Sin notas'}</p><strong>Total: {money.format(detail.total)}</strong>
      </section> : list && <section className="panel">
        {list.content.length === 0 ? <p className="empty">Todavía no hay ventas.</p> : <div className="table-wrap"><table>
          <thead><tr><th>Venta</th><th>Fecha</th><th>Total</th><th>Detalle</th></tr></thead><tbody>{list.content.map((sale) => <tr key={sale.id}><td>#{sale.id}</td><td>{date.format(new Date(sale.createdAt))}</td><td>{money.format(sale.total)}</td><td><button aria-label={`Ver venta ${sale.id}`} onClick={() => setSelected(sale.id)}>Ver detalle</button></td></tr>)}</tbody>
        </table></div>}
        <div className="pagination"><span>Página {list.page + 1} de {Math.max(1, list.totalPages)}</span><div><button disabled={list.first} onClick={() => setPage(page - 1)}>Anterior</button><button disabled={list.last} onClick={() => setPage(page + 1)}>Siguiente</button></div></div>
      </section>}
  </section>;
}
