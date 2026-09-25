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
      backgroundColor: 'rgba(0, 0, 0, 0.5)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 9999,
      padding: '1rem'
    }}>
      <div style={{
        backgroundColor: '#ffffff',
        borderRadius: '4px',
        width: '100%',
        maxWidth: '520px',
        boxShadow: '0 4px 12px rgba(0, 0, 0, 0.15)',
        overflow: 'hidden',
        border: '1px solid #d1d5db',
        color: '#111827'
      }}>
        {/* Cabecera */}
        <div style={{
          padding: '0.875rem 1.25rem',
          borderBottom: '1px solid #e5e7eb',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#f9fafb'
        }}>
          <div>
            <h3 style={{ fontSize: '0.95rem', fontWeight: 700, margin: 0, color: '#111827' }}>
              Token de Sesion
            </h3>
            <p style={{ fontSize: '0.8rem', color: '#4b5563', margin: '0.15rem 0 0 0' }}>
              Usuario: {user?.name} | Rol: {user?.role}
            </p>
          </div>
          <button
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              fontSize: '1.25rem',
              color: '#6b7280',
              cursor: 'pointer',
              lineHeight: 1
            }}
          >
            X
          </button>
        </div>

        {/* Contenido */}
        <div style={{ padding: '1.25rem' }}>
          
          {/* Informacion de Creacion y Validacion del Token */}
          <div style={{
            border: '1px solid #e5e7eb',
            backgroundColor: '#f9fafb',
            borderRadius: '4px',
            padding: '0.75rem',
            marginBottom: '1rem',
            fontSize: '0.8rem',
            lineHeight: 1.5,
            color: '#374151'
          }}>
            <div><strong>Creacion:</strong> Emitido por Microsoft Entra ID (Algoritmo RS256 con clave asimetrica)</div>
            <div><strong>Validacion:</strong> Verificado por endpoint JWKS en el BFF de Spring Boot</div>
            <div><strong>Estado:</strong> Activo para peticiones Authorization: Bearer</div>
          </div>

          <label style={{
            display: 'block',
            fontSize: '0.75rem',
            fontWeight: 600,
            color: '#374151',
            marginBottom: '0.35rem',
            textTransform: 'uppercase'
          }}>
            Token Activo (JWT):
          </label>

          <textarea
            readOnly
            value={token || 'No hay token disponible'}
            rows={6}
            style={{
              width: '100%',
              backgroundColor: '#f9fafb',
              border: '1px solid #d1d5db',
              borderRadius: '4px',
              fontSize: '0.75rem',
              fontFamily: 'monospace',
              padding: '0.5rem',
              color: '#111827',
              resize: 'none',
              boxSizing: 'border-box',
              lineHeight: 1.35,
              wordBreak: 'break-all'
            }}
            onClick={(e) => e.target.select()}
          />

          <div style={{ display: 'flex', gap: '0.5rem', marginTop: '1rem' }}>
            <button
              onClick={handleCopy}
              style={{
                flex: 1,
                padding: '0.5rem 1rem',
                fontSize: '0.85rem',
                backgroundColor: '#111827',
                border: '1px solid #111827',
                color: '#ffffff',
                fontWeight: 600,
                borderRadius: '4px',
                cursor: 'pointer'
              }}
            >
              {copied ? 'Copiado' : 'Copiar Token'}
            </button>
            <button
              onClick={onClose}
              style={{
                padding: '0.5rem 1rem',
                fontSize: '0.85rem',
                backgroundColor: '#ffffff',
                border: '1px solid #d1d5db',
                color: '#374151',
                borderRadius: '4px',
                cursor: 'pointer'
              }}
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
