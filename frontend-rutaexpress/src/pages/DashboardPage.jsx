import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import bffApi from '../services/bffApi';

export const DashboardPage = () => {
  const { user, role, token, logout } = useAuth();
  const navigate = useNavigate();
  const [bffStatus, setBffStatus] = useState('Verificando');
  const [copied, setCopied] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [verifyResult, setVerifyResult] = useState(null);

  useEffect(() => {
    let isMounted = true;
    const checkBff = async () => {
      try {
        const health = await bffApi.getHealth();
        if (isMounted) {
          setBffStatus(health?.status === 'UP' ? 'Conectado (UP)' : 'Degradado');
        }
      } catch (e) {
        if (isMounted) {
          setBffStatus('Desconectado');
        }
      }
    };
    checkBff();
    const interval = setInterval(checkBff, 15000);
    return () => {
      isMounted = false;
      clearInterval(interval);
    };
  }, []);

  const handleCopy = () => {
    if (token) {
      navigator.clipboard.writeText(token);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const handleVerifyToken = async () => {
    setVerifying(true);
    setVerifyResult(null);
    try {
      const responseData = await bffApi.getShipments();
      setVerifyResult({
        status: 'success',
        code: 200,
        endpoint: 'GET /api/bff/shipments',
        data: responseData
      });
    } catch (err) {
      const status = err.response?.status || 500;
      const data = err.response?.data || { error: err.message };
      setVerifyResult({
        status: 'error',
        code: status,
        endpoint: 'GET /api/bff/shipments',
        data: data
      });
    } finally {
      setVerifying(false);
    }
  };

  const handleLogout = async () => {
    await logout();
    navigate('/login');
  };

  return (
    <div style={{
      minHeight: 'calc(100vh - 70px)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '2rem 1rem'
    }}>
      <div style={{
        width: '100%',
        maxWidth: '580px',
        backgroundColor: '#ffffff',
        border: '1px solid #d1d5db',
        borderRadius: '6px',
        boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.05)',
        overflow: 'hidden'
      }}>
        {/* Cabecera */}
        <div style={{
          padding: '1.25rem 1.5rem',
          borderBottom: '1px solid #e5e7eb',
          backgroundColor: '#f9fafb',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <div>
            <h1 style={{ fontSize: '1.15rem', fontWeight: 700, margin: 0, color: '#111827' }}>
              RutaExpress
            </h1>
            <p style={{ fontSize: '0.8rem', color: '#6b7280', margin: '0.2rem 0 0 0' }}>
              Portal de Identidad y Sesion
            </p>
          </div>
          <div style={{
            fontSize: '0.75rem',
            padding: '0.25rem 0.6rem',
            border: '1px solid #d1d5db',
            borderRadius: '4px',
            color: '#374151',
            backgroundColor: '#ffffff'
          }}>
            BFF: {bffStatus}
          </div>
        </div>

        {/* Contenido centrado */}
        <div style={{ padding: '1.5rem' }}>
          
          {/* Datos del Usuario y Rol */}
          <div style={{
            border: '1px solid #e5e7eb',
            borderRadius: '4px',
            padding: '1rem',
            backgroundColor: '#f9fafb',
            marginBottom: '1.25rem'
          }}>
            <div style={{ marginBottom: '0.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#6b7280', fontWeight: 600 }}>Usuario</span>
              <strong style={{ fontSize: '0.9rem', color: '#111827' }}>{user?.name || user?.email}</strong>
            </div>

            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
              <span style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#6b7280', fontWeight: 600 }}>Rol Asignado</span>
              <span style={{
                fontSize: '0.8rem',
                fontWeight: 600,
                padding: '0.2rem 0.6rem',
                border: '1px solid #9ca3af',
                borderRadius: '4px',
                color: '#111827',
                backgroundColor: '#ffffff'
              }}>
                {role || user?.role || 'Cliente'}
              </span>
            </div>
          </div>

          {/* Token Activo */}
          <div style={{ marginBottom: '1.25rem' }}>
            <label style={{
              display: 'block',
              fontSize: '0.75rem',
              fontWeight: 600,
              color: '#374151',
              marginBottom: '0.35rem',
              textTransform: 'uppercase'
            }}>
              Token Activo de Sesion (JWT):
            </label>
            <textarea
              readOnly
              value={token || 'No hay token generado'}
              rows={5}
              style={{
                width: '100%',
                backgroundColor: '#f9fafb',
                border: '1px solid #d1d5db',
                borderRadius: '4px',
                fontSize: '0.75rem',
                fontFamily: 'monospace',
                padding: '0.625rem',
                color: '#111827',
                resize: 'none',
                boxSizing: 'border-box',
                lineHeight: 1.4,
                wordBreak: 'break-all'
              }}
              onClick={(e) => e.target.select()}
            />
          </div>

          {/* Accion de Verificacion con BFF */}
          <div style={{ marginBottom: '1.25rem' }}>
            <button
              type="button"
              onClick={handleVerifyToken}
              disabled={verifying || !token}
              style={{
                width: '100%',
                padding: '0.65rem 1rem',
                fontSize: '0.85rem',
                fontWeight: 600,
                backgroundColor: '#ffffff',
                border: '1px solid #111827',
                color: '#111827',
                borderRadius: '4px',
                cursor: verifying ? 'wait' : 'pointer'
              }}
            >
              {verifying ? 'Verificando con BFF...' : 'Verificar Token con BFF'}
            </button>

            {verifyResult && (
              <div style={{
                marginTop: '0.75rem',
                border: '1px solid #374151',
                borderRadius: '4px',
                backgroundColor: '#111827',
                color: '#f3f4f6',
                overflow: 'hidden',
                fontSize: '0.75rem',
                fontFamily: 'Consolas, Monaco, "Courier New", monospace'
              }}>
                <div style={{
                  padding: '0.4rem 0.75rem',
                  backgroundColor: '#1f2937',
                  borderBottom: '1px solid #374151',
                  display: 'flex',
                  justifyContent: 'space-between',
                  alignItems: 'center',
                  fontSize: '0.7rem',
                  color: '#9ca3af'
                }}>
                  <span>Consola de Respuesta BFF</span>
                  <span>HTTP {verifyResult.code}</span>
                </div>
                <div style={{ padding: '0.75rem' }}>
                  <div style={{ color: '#9ca3af', marginBottom: '0.35rem' }}>
                    &gt; {verifyResult.endpoint}
                  </div>
                  <pre style={{
                    margin: 0,
                    fontFamily: 'inherit',
                    fontSize: '0.75rem',
                    lineHeight: 1.4,
                    color: '#e5e7eb',
                    maxHeight: '180px',
                    overflowY: 'auto',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-all'
                  }}>
                    {JSON.stringify(verifyResult.data, null, 2)}
                  </pre>
                </div>
              </div>
            )}
          </div>

          {/* Acciones Principales */}
          <div style={{ display: 'flex', gap: '0.75rem' }}>
            <button
              onClick={handleCopy}
              style={{
                flex: 1,
                padding: '0.65rem 1rem',
                fontSize: '0.85rem',
                fontWeight: 600,
                backgroundColor: '#111827',
                border: '1px solid #111827',
                color: '#ffffff',
                borderRadius: '4px',
                cursor: 'pointer'
              }}
            >
              {copied ? 'Copiado' : 'Copiar Token'}
            </button>
            <button
              onClick={handleLogout}
              style={{
                padding: '0.65rem 1.25rem',
                fontSize: '0.85rem',
                fontWeight: 600,
                backgroundColor: '#ffffff',
                border: '1px solid #d1d5db',
                color: '#374151',
                borderRadius: '4px',
                cursor: 'pointer'
              }}
            >
              Cerrar Sesion
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};

export default DashboardPage;