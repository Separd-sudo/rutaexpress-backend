import React, { useState } from 'react';
import { useAuth } from '../auth/AuthContext';

export const TokenInspectorModal = ({ isOpen, onClose }) => {
  const { token, user } = useAuth();
  const [copied, setCopied] = useState(false);

  if (!isOpen) return null;

  const handleCopy = () => {
    if (token) {
      navigator.clipboard.writeText(token);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  return (
    <div style={{
      position: 'fixed',
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
      backgroundColor: 'rgba(15, 23, 42, 0.55)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 9999,
      padding: '1rem'
    }}>
      <div style={{
        backgroundColor: '#ffffff',
        borderRadius: '0.625rem',
        width: '100%',
        maxWidth: '480px',
        boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.2)',
        overflow: 'hidden',
        border: '1px solid #e2e8f0'
      }}>
        {/* Cabecera */}
        <div style={{
          padding: '1rem 1.25rem',
          borderBottom: '1px solid #e2e8f0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#f8fafc'
        }}>
          <div>
            <h3 style={{ fontSize: '1rem', fontWeight: 700, color: '#0f172a', margin: 0 }}>
              Token Activo de Sesión
            </h3>
            <p style={{ fontSize: '0.8rem', color: '#64748b', margin: '0.15rem 0 0 0' }}>
              {user?.name} · <strong style={{ color: '#2563eb' }}>Rol: {user?.role}</strong>
            </p>
          </div>
          <button
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              fontSize: '1.4rem',
              color: '#94a3b8',
              cursor: 'pointer',
              lineHeight: 1
            }}
          >
            &times;
          </button>
        </div>

        {/* Contenido: solo el token de sesion */}
        <div style={{ padding: '1.25rem' }}>
          <label style={{
            display: 'block',
            fontSize: '0.75rem',
            fontWeight: 600,
            color: '#475569',
            marginBottom: '0.375rem',
            textTransform: 'uppercase'
          }}>
            Bearer Token (JWT):
          </label>

          <textarea
            readOnly
            value={token || 'No hay token generado'}
            rows={7}
            style={{
              width: '100%',
              backgroundColor: '#f8fafc',
              border: '1px solid #cbd5e1',
              borderRadius: '0.375rem',
              fontSize: '0.75rem',
              fontFamily: 'Consolas, monospace',
              padding: '0.625rem',
              color: '#0f172a',
              resize: 'none',
              boxSizing: 'border-box',
              lineHeight: 1.4,
              wordBreak: 'break-all'
            }}
            onClick={(e) => e.target.select()}
          />

          <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
            <button
              onClick={handleCopy}
              className="btn btn-accent"
              style={{
                flex: 1,
                padding: '0.625rem 1rem',
                fontSize: '0.85rem',
                backgroundColor: copied ? '#16a34a' : '#2563eb',
                borderColor: copied ? '#16a34a' : '#2563eb',
                color: '#ffffff',
                fontWeight: 600,
                cursor: 'pointer'
              }}
            >
              {copied ? '✓ Token Copiado al Portapapeles' : 'Copiar Token'}
            </button>
            <button
              onClick={onClose}
              className="btn btn-outline"
              style={{ padding: '0.625rem 1rem', fontSize: '0.85rem' }}
            >
              Cerrar
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default TokenInspectorModal;
