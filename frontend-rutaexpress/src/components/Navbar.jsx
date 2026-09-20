import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import bffApi from '../services/bffApi';

export const Navbar = () => {
  const { user, role, logout, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [bffStatus, setBffStatus] = useState('checking');

  useEffect(() => {
    let isMounted = true;
    const checkBff = async () => {
      try {
        const health = await bffApi.getHealth();
        if (isMounted) setBffStatus(health?.status === 'UP' ? 'online' : 'degraded');
      } catch (e) {
        if (isMounted) setBffStatus('offline');
      }
    };
    checkBff();
    const interval = setInterval(checkBff, 30000);
    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, []);

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <header style={{ backgroundColor: '#0f172a', color: '#ffffff', borderBottom: '1px solid #1e293b' }}>
      <div className="container" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.875rem 1.5rem' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
          <Link to="/" style={{ color: '#ffffff', fontWeight: 700, fontSize: '1.125rem', letterSpacing: '-0.025em' }}>
            RutaExpress <span style={{ color: '#94a3b8', fontWeight: 400, fontSize: '0.875rem' }}>| Portal Operativo</span>
          </Link>
          {isAuthenticated && (
            <nav style={{ display: 'flex', gap: '1rem', fontSize: '0.875rem' }}>
              <Link to="/dashboard" style={{ color: '#e2e8f0' }}>Dashboard</Link>
            </nav>
          )}
        </div>

        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          {/* Indicador de estado del BFF */}
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.375rem', fontSize: '0.75rem', color: '#94a3b8' }}>
            <span style={{
              width: '8px',
              height: '8px',
              borderRadius: '50%',
              backgroundColor: bffStatus === 'online' ? '#22c55e' : bffStatus === 'checking' ? '#eab308' : '#ef4444'
            }}></span>
            <span>BFF: {bffStatus}</span>
          </div>

          {isAuthenticated ? (
            <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
              <div style={{ textAlign: 'right' }}>
                <div style={{ fontSize: '0.875rem', fontWeight: 600 }}>{user?.name}</div>
                <div style={{ fontSize: '0.75rem', color: '#94a3b8' }}>
                  Rol: <strong style={{ color: '#38bdf8' }}>{role}</strong>
                </div>
              </div>
              <button onClick={handleLogout} className="btn btn-outline" style={{ color: '#ffffff', borderColor: '#334155', padding: '0.375rem 0.75rem', fontSize: '0.75rem' }}>
                Cerrar sesión
              </button>
            </div>
          ) : (
            <Link to="/login" className="btn btn-accent" style={{ padding: '0.375rem 0.875rem', fontSize: '0.8125rem' }}>
              Iniciar sesión
            </Link>
          )}
        </div>
      </div>
    </header>
  );
};

export default Navbar;