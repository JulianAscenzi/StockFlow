import { useCallback, useEffect, useState } from 'react';
import { accessToken, api, clearAccessToken, onSessionExpired } from './api';
import { DashboardView } from './components/DashboardView';
import { InventoryView } from './components/InventoryView';
import { Navigation, type Section } from './components/Navigation';
import { ProductsView } from './components/ProductsView';
import { SaleView } from './components/SaleView';
import { SalesHistoryView } from './components/SalesHistoryView';
import { LoginView } from './components/LoginView';
import type { Dashboard } from './types';

export default function App() {
  const [section, setSection] = useState<Section>('dashboard'); const [dashboard, setDashboard] = useState<Dashboard | null>(null); const [loading, setLoading] = useState(true); const [notice, setNotice] = useState<{ message: string; kind: 'error' | 'success' } | null>(null);
  // The published portfolio demo is public by default. Private installations
  // must opt in explicitly with VITE_AUTH_ENABLED=true.
  const authenticationRequired = import.meta.env.VITE_AUTH_ENABLED === 'true';
  const [authenticated, setAuthenticated] = useState(() => !authenticationRequired || Boolean(accessToken()));
  const [loginMessage, setLoginMessage] = useState('');
  const expireSession = useCallback(() => {
    clearAccessToken(); setDashboard(null); setNotice(null); setLoading(true);
    setLoginMessage('Tu sesión venció. Volvé a ingresar'); setAuthenticated(false);
  }, []);
  useEffect(() => {
    if (authenticationRequired) return onSessionExpired(expireSession);
  }, [authenticationRequired, expireSession]);
  const notify = useCallback((message: string, kind: 'error' | 'success' = 'success') => setNotice({ message, kind }), []);
  useEffect(() => {
    if (!authenticated || section !== 'dashboard') return;
    let current = true;
    setLoading(true);
    setDashboard(null);
    api.dashboard().then((data) => { if (current) setDashboard(data); })
      .catch((error: Error) => { if (current) notify(error.message, 'error'); })
      .finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [authenticated, section, notify]);
  if (!authenticated) return <LoginView message={loginMessage} onLogin={() => { setNotice(null); setLoginMessage(''); setLoading(true); setAuthenticated(true); }} />;
  const content = section === 'dashboard' ? <DashboardView data={dashboard} loading={loading} /> : section === 'products' ? <ProductsView notify={notify} /> : section === 'inventory' ? <InventoryView notify={notify} /> : section === 'history' ? <SalesHistoryView /> : <SaleView notify={notify} onHistory={() => setSection('history')} onLoginRequired={expireSession} />;
  return <div className="app-shell"><Navigation section={section} onChange={setSection} showLogout={authenticationRequired} onLogout={() => { clearAccessToken(); setDashboard(null); setNotice(null); setLoginMessage(''); setAuthenticated(false); }} /><main>{notice && <div className={`notice ${notice.kind}`} role="status">{notice.message}<button aria-label="Cerrar aviso" onClick={() => setNotice(null)}>×</button></div>}{content}</main></div>;
}
