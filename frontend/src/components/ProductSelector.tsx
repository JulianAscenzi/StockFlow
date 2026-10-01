import { useEffect, useState } from 'react';
import { api } from '../api';
import type { PageResponse, Product } from '../types';

interface Props {
  label: string;
  sellable: boolean;
  selected: Product | null;
  onSelect: (product: Product | null) => void;
  disabled?: boolean;
  refresh: number;
}

export function ProductSelector({ label, sellable, selected, onSelect, disabled = false, refresh }: Props) {
  const [draft, setDraft] = useState('');
  const [search, setSearch] = useState({ query: '', page: 0, retry: 0 });
  const [result, setResult] = useState<PageResponse<Product> | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    api.lookup(search.query, sellable, search.page, controller.signal)
      .then((data) => { if (!controller.signal.aborted) setResult(data); })
      .catch((failure: Error) => { if (!controller.signal.aborted) setError(failure.message); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [search, sellable, refresh]);
  const submitSearch = () => setSearch({ query: draft.trim(), page: 0, retry: search.retry + 1 });
  return <div>
    <label>Buscar por nombre o SKU<input value={draft} disabled={disabled} onChange={(event) => setDraft(event.target.value)} onKeyDown={(event) => { if (event.key === 'Enter') { event.preventDefault(); submitSearch(); } }} /></label>
    <button type="button" className="secondary" disabled={disabled} onClick={submitSearch}>Buscar productos</button>
    {loading ? <p aria-live="polite">Cargando productos…</p> : error ? <div role="alert"><p>{refresh > 0 ? 'La operación fue confirmada, pero no se pudieron actualizar los productos. ' : ''}{error}</p><button type="button" disabled={disabled} onClick={() => setSearch({ ...search, retry: search.retry + 1 })}>Reintentar búsqueda</button></div> : result && <>
      <label>{label}<select disabled={disabled} value={result.content.some((product) => product.id === selected?.id) ? selected!.id : ''} onChange={(event) => onSelect(result.content.find((product) => product.id === Number(event.target.value)) ?? null)}>
        <option value="">Elegí un producto</option>{result.content.map((product) => <option key={product.id} value={product.id}>{product.name} · {product.sku} · disponible {product.stock}</option>)}
      </select></label>
      {result.content.length === 0 && <p>No hay productos para esta búsqueda.</p>}
      <div className="pagination"><span>Página {result.page + 1} de {Math.max(1, result.totalPages)}</span><div>
        <button type="button" disabled={disabled || result.first} onClick={() => setSearch({ ...search, page: search.page - 1 })}>Anterior</button>
        <button type="button" disabled={disabled || result.last} onClick={() => setSearch({ ...search, page: search.page + 1 })}>Siguiente</button>
      </div></div>
    </>}
    {selected && <p>Seleccionado: {selected.name} · disponible {selected.stock}</p>}
  </div>;
}
