import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import bffApi from '../services/bffApi';
import TokenInspectorModal from './TokenInspectorModal';

export const Navbar = () => {
  const { user, role, logout, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [bffStatus, setBffStatus] = useState('Verificando');
  const [isTokenModalOpen, setIsTokenModalOpen] = useState(false);

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
    <>
      <header style={{ backgroundColor: '#111827', color: '#f9fafb', borderBottom: '1px solid #374151' }}>
        <div className="container" style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '0.75rem 1.5rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '1.5rem' }}>
            <Link to="/" style={{ color: '#ffffff', fontWeight: 700, fontSize: '1rem', textDecoration: 'none' }}>
              RutaExpress
            </Link>
            {isAuthenticated && (
              <nav style={{ display: 'flex', gap: '1rem', fontSize: '0.85rem' }}>
                <Link to="/dashboard" style={{ color: '#d1d5db', textDecoration: 'none' }}>Dashboard</Link>
              </nav>
            )}
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            {/* Estado del BFF en texto plano y sobrio */}
            <div style={{ fontSize: '0.75rem', color: '#9ca3af' }}>
              BFF: {bffStatus}
            </div>

            {isAuthenticated ? (
              <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
                <div style={{ fontSize: '0.85rem', color: '#e5e7eb' }}>
                  <span>{user?.name}</span>
                  <span style={{
                    marginLeft: '0.5rem',
                    padding: '0.15rem 0.45rem',
                    border: '1px solid #4b5563',
                    borderRadius: '3px',
                    fontSize: '0.75rem',
                    color: '#d1d5db'
                  }}>
                    {role}
                  </span>
                </div>

                <button
                  onClick={() => setIsTokenModalOpen(true)}
                  style={{
                    backgroundColor: 'transparent',
                    border: '1px solid #6b7280',
                    color: '#ffffff',
                    padding: '0.3rem 0.65rem',
                    fontSize: '0.75rem',
                    borderRadius: '3px',
                    cursor: 'pointer'
                  }}
                >
                  Token Activo
                </button>

                <button
                  onClick={handleLogout}
                  style={{
                    backgroundColor: 'transparent',
                    border: '1px solid #4b5563',
                    color: '#9ca3af',
                    padding: '0.3rem 0.65rem',
                    fontSize: '0.75rem',
                    borderRadius: '3px',
                    cursor: 'pointer'
                  }}
                >
                  Cerrar Sesion
                </button>
              </div>
            ) : (
              <Link
                to="/login"
                style={{
                  backgroundColor: '#ffffff',
                  color: '#111827',
                  padding: '0.35rem 0.75rem',
                  fontSize: '0.8rem',
                  borderRadius: '3px',
                  textDecoration: 'none',
                  fontWeight: 600
                }}
              >
                Iniciar Sesion
              </Link>
            )}
          </div>
        </div>
      </header>

      {/* Mini pantalla del token */}
      <TokenInspectorModal
        isOpen={isTokenModalOpen}
        onClose={() => setIsTokenModalOpen(false)}
      />
    </>
  );
};

export default Navbar;