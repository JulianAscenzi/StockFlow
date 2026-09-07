import { FormEvent, useState } from 'react';
import { api, saveAccessToken } from '../api';

export function LoginView({ onLogin }: { onLogin: () => void }) {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setLoading(true);
    setError(null);
    try {
      const response = await api.login({ email, password });
      saveAccessToken(response.accessToken);
      onLogin();
    } catch (loginError) {
      setError(loginError instanceof Error ? loginError.message : 'No se pudo iniciar sesión.');
    } finally {
      setLoading(false);
    }
  }

  return <main className="login-page"><form className="login-card" onSubmit={submit}>
    <a className="login-brand" href="#inicio"><span>SF</span> StockFlow</a>
    <p className="eyebrow">Acceso de administrador</p>
    <h1>Bienvenido</h1>
    <p>Ingresá tus credenciales para administrar el comercio.</p>
    <label>Correo electrónico<input type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required /></label>
    <label>Contraseña<input type="password" autoComplete="current-password" value={password} onChange={(event) => setPassword(event.target.value)} required /></label>
    {error && <p className="login-error" role="alert">{error}</p>}
    <button className="primary wide" disabled={loading}>{loading ? 'Ingresando…' : 'Ingresar'}</button>
  </form></main>;
}
