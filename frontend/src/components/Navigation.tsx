export type Section = 'dashboard' | 'products' | 'inventory' | 'sale';

const items: Array<{ id: Section; label: string; icon: string }> = [
  { id: 'dashboard', label: 'Resumen', icon: '◈' },
  { id: 'products', label: 'Productos', icon: '□' },
  { id: 'inventory', label: 'Inventario', icon: '↕' },
  { id: 'sale', label: 'Nueva venta', icon: '+' }
];

export function Navigation({ section, onChange }: { section: Section; onChange: (section: Section) => void }) {
  return <aside className="sidebar">
    <a className="brand" href="#inicio" onClick={() => onChange('dashboard')}><span>SF</span> StockFlow</a>
    <nav aria-label="Navegación principal">
      {items.map((item) => <button key={item.id} className={section === item.id ? 'nav-item active' : 'nav-item'} onClick={() => onChange(item.id)}>
        <span aria-hidden="true">{item.icon}</span>{item.label}
      </button>)}
    </nav>
    <p className="sidebar-note">Gestión simple para tu comercio.</p>
  </aside>;
}
