import { useCallback, useEffect, useState } from 'react';
import { api } from './api';
import { DashboardView } from './components/DashboardView';
import { InventoryView } from './components/InventoryView';
import { Navigation, type Section } from './components/Navigation';
import { ProductsView } from './components/ProductsView';
import { SaleView } from './components/SaleView';
import type { Dashboard } from './types';

export default function App() {
  const [section, setSection] = useState<Section>('dashboard'); const [dashboard, setDashboard] = useState<Dashboard | null>(null); const [loading, setLoading] = useState(true); const [notice, setNotice] = useState<{ message: string; kind: 'error' | 'success' } | null>(null);
  const notify = useCallback((message: string, kind: 'error' | 'success' = 'success') => setNotice({ message, kind }), []);
  useEffect(() => { api.dashboard().then(setDashboard).catch((error: Error) => notify(error.message, 'error')).finally(() => setLoading(false)); }, [notify]);
  const content = section === 'dashboard' ? <DashboardView data={dashboard} loading={loading} /> : section === 'products' ? <ProductsView notify={notify} /> : section === 'inventory' ? <InventoryView notify={notify} /> : <SaleView notify={notify} />;
  return <div className="app-shell"><Navigation section={section} onChange={setSection} /><main>{notice && <div className={`notice ${notice.kind}`} role="status">{notice.message}<button aria-label="Cerrar aviso" onClick={() => setNotice(null)}>×</button></div>}{content}</main></div>;
}
