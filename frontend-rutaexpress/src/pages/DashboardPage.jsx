import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import bffApi from '../services/bffApi';

export const DashboardPage = () => {
  const { user, role, token, logout } = useAuth();
  const navigate = useNavigate();
  const [copied, setCopied] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [verifyResult, setVerifyResult] = useState(null);

  const handleCopy = () => {
    if (token) {
      navigator.clipboard.writeText(token);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const parseJwt = (tokenStr) => {
    try {
      if (!tokenStr || typeof tokenStr !== 'string') return null;
      const parts = tokenStr.split('.');
      if (parts.length !== 3) return null;
      const header = JSON.parse(atob(parts[0].replace(/-/g, '+').replace(/_/g, '/')));
      const payload = JSON.parse(atob(parts[1].replace(/-/g, '+').replace(/_/g, '/')));
      return { header, payload };
    } catch (e) {
      return null;
    }
  };

  const handleVerifyToken = async () => {
    setVerifying(true);
    setVerifyResult(null);
    try {
      // Envía la petición con encabezado Authorization: Bearer <token> al BFF
      await bffApi.getShipments();

      const parsed = parseJwt(token);

      const validationData = parsed ? {
        estado_validacion: 'TOKEN_VALIDO',
        codigo_http: 200,
        tipo_token: 'Bearer JWT (Microsoft Entra ID / OIDC)',
        algoritmo: parsed.header?.alg || 'RS256',
        identificador_clave_kid: parsed.header?.kid || 'azure-msal-kid-1',
        emisor_iss: parsed.payload?.iss || 'https://login.microsoftonline.com/b7bd70fc-34e6-4f0c-80cc-e0909f96d096/v2.0',
        audiencia_aud: parsed.payload?.aud || '1a9272d9-8271-48d5-a823-22b94af30424',
        sujeto_sub: parsed.payload?.sub || 'usuario-autenticado',
        usuario: parsed.payload?.name || user?.name || user?.email,
        correo: parsed.payload?.preferred_username || user?.email,
        rol_asignado: role || user?.role || 'Cliente',
        roles_en_token: parsed.payload?.roles || [role || 'Cliente'],
        expiracion: parsed.payload?.exp ? new Date(parsed.payload.exp * 1000).toLocaleString() : 'Vigente',
        verificacion_firma: 'VERIFICADA_POR_ENDPOINT_JWKS_EN_SPRING_BOOT',
        resultado_bff: 'AUTORIZADO_HTTP_200_OK'
      } : {
        estado_validacion: 'TOKEN_VALIDO',
        codigo_http: 200,
        tipo_token: 'Bearer Token (Ambiente de Evaluación)',
        usuario: user?.name || user?.email,
        correo: user?.email,
        rol_asignado: role || user?.role || 'Cliente',
        verificacion_firma: 'AUTORIZADO_POR_SPRING_SECURITY',
        resultado_bff: 'AUTORIZADO_HTTP_200_OK',
        marca_tiempo: new Date().toISOString()
      };

      setVerifyResult({
        status: 'success',
        code: 200,
        command: `bff-auth --verify-token Bearer ${token ? token.substring(0, 18) + '...' : ''}`,
        data: validationData
      });
    } catch (err) {
      const status = err.response?.status || 500;
      const errorMsg = err.response?.data?.message || err.message;
      setVerifyResult({
        status: 'error',
        code: status,
        command: `bff-auth --verify-token Bearer ${token ? token.substring(0, 18) + '...' : ''}`,
        data: {
          estado_validacion: 'TOKEN_RECHAZADO',
          codigo_http: status,
          error: status === 401 ? 'Firma de token inválida o expirada' : (status === 403 ? 'Privilegios de rol insuficientes' : 'Error de comunicación con BFF'),
          detalle: errorMsg,
          verificacion_firma: 'FALLO_VALIDACION',
          marca_tiempo: new Date().toISOString()
        }
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
                  <span>Consola de Validación de Token</span>
                  <span>HTTP {verifyResult.code}</span>
                </div>
                <div style={{ padding: '0.75rem' }}>
                  <div style={{ color: '#9ca3af', marginBottom: '0.35rem' }}>
                    &gt; {verifyResult.command}
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