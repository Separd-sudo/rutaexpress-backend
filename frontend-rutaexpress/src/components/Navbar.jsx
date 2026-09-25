import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import bffApi from '../services/bffApi';

export const Navbar = () => {
  const { logout, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [bffStatus, setBffStatus] = useState('Verificando');

  useEffect(() => {
    let isMounted = true;
    const checkBff = async () => {
      try {
        const health = await bffApi.getHealth();
        if (isMounted) setBffStatus(health?.status === 'UP' ? 'Conectado' : 'Degradado');
      } catch (e) {
        if (isMounted) setBffStatus('Desconectado');
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
    <header style={{ backgroundColor: '#111827', color: '#f9fafb', borderBottom: '1px solid #374151' }}>
      <div className="container" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.75rem 1.5rem' }}>
        <Link to="/" style={{ color: '#ffffff', fontWeight: 700, fontSize: '1rem', textDecoration: 'none' }}>
          RutaExpress
        </Link>

        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
          <span style={{ fontSize: '0.75rem', color: '#9ca3af' }}>
            BFF: {bffStatus}
          </span>
          {isAuthenticated && (
            <button
              onClick={handleLogout}
              style={{
                backgroundColor: 'transparent',
                border: '1px solid #4b5563',
                color: '#d1d5db',
                padding: '0.25rem 0.6rem',
                fontSize: '0.75rem',
                borderRadius: '3px',
                cursor: 'pointer'
              }}
            >
              Salir
            </button>
          )}
        </div>
      </div>
    </header>
  );
};

export default Navbar;