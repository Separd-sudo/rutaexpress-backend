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
      await bffApi.getShipments();
      setVerifyResult({
        status: 'success',
        message: 'HTTP 200 OK - Petición autorizada con éxito por el BFF'
      });
    } catch (err) {
      const status = err.response?.status;
      const msg = err.response?.data?.message || err.message;
      setVerifyResult({
        status: 'error',
        message: status ? `HTTP ${status} - ${msg}` : `Error de conexión: ${msg}`
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
        maxWidth: '560px',
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

          {/* Validacion y Creacion del Token */}
          <div style={{
            border: '1px solid #e5e7eb',
            borderRadius: '4px',
            padding: '0.85rem 1rem',
            fontSize: '0.8rem',
            lineHeight: 1.5,
            color: '#374151',
            marginBottom: '1.25rem',
            backgroundColor: '#ffffff'
          }}>
            <div><strong>Creacion:</strong> Emitido por Microsoft Entra ID con firma criptografica asimetrica (RS256)</div>
            <div><strong>Validacion:</strong> Verificado por el endpoint JWKS en el BFF de Spring Boot</div>
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
                padding: '0.75rem 1rem',
                fontSize: '0.8rem',
                borderRadius: '4px',
                border: '1px solid #d1d5db',
                backgroundColor: '#f9fafb',
                color: '#111827',
                lineHeight: 1.4
              }}>
                <div><strong>Estado:</strong> {verifyResult.status === 'success' ? 'Autorizado' : 'Rechazado'}</div>
                <div><strong>Respuesta:</strong> {verifyResult.message}</div>
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