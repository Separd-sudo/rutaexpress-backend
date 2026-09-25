import React, { useState } from 'react';
import { useAuth } from '../auth/AuthContext';

export const TokenInspectorModal = ({ isOpen, onClose }) => {
  const { token, user, authMethod } = useAuth();
  const [copied, setCopied] = useState(false);

  if (!isOpen) return null;

  // Funcion utilitaria pura para decodificar JWT sin librerias externas
  const parseJwt = (jwtString) => {
    if (!jwtString || typeof jwtString !== 'string') return null;
    const parts = jwtString.split('.');
    if (parts.length !== 3) {
      return {
        isStandardJwt: false,
        raw: jwtString,
        header: { type: 'Token de sesion simulada (Demo)' },
        payload: {
          nota: 'Este es un token de evaluacion local para roles. Inicie sesion con Microsoft Entra ID o AWS Cognito para inspeccionar un JWT real con firma criptografica RSA256.',
          usuario: user?.email,
          rol: user?.role,
          tenant: user?.tenant
        },
        signature: 'N/A'
      };
    }

    try {
      const base64UrlDecode = (str) => {
        let base64 = str.replace(/-/g, '+').replace(/_/g, '/');
        while (base64.length % 4) {
          base64 += '=';
        }
        return JSON.parse(decodeURIComponent(escape(window.atob(base64))));
      };

      const header = base64UrlDecode(parts[0]);
      const payload = base64UrlDecode(parts[1]);
      const signature = parts[2];

      return {
        isStandardJwt: true,
        header,
        payload,
        signature,
        raw: jwtString
      };
    } catch (e) {
      return {
        isStandardJwt: false,
        raw: jwtString,
        header: { error: 'Error decodificando JWT' },
        payload: { error: e.message }
      };
    }
  };

  const parsed = parseJwt(token);

  const handleCopy = () => {
    if (token) {
      navigator.clipboard.writeText(token);
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }
  };

  const formatTimestamp = (ts) => {
    if (!ts) return 'N/A';
    try {
      const d = new Date(ts * 1000);
      return `${d.toLocaleDateString()} ${d.toLocaleTimeString()}`;
    } catch {
      return ts;
    }
  };

  const getIssuerBadge = () => {
    const iss = parsed?.payload?.iss || '';
    if (iss.includes('microsoftonline.com') || authMethod === 'msal') {
      return {
        label: 'Microsoft Entra ID (Azure AD)',
        color: '#0078d4',
        bg: '#eff6fc'
      };
    }
    if (iss.includes('amazoncognito.com') || iss.includes('cognito-idp')) {
      return {
        label: 'AWS Cognito User Pool',
        color: '#ff9900',
        bg: '#fff7ed'
      };
    }
    return {
      label: 'Sesión Local / Rol Demo',
      color: '#475569',
      bg: '#f1f5f9'
    };
  };

  const badge = getIssuerBadge();

  return (
    <div style={{
      position: 'fixed',
      top: 0,
      left: 0,
      right: 0,
      bottom: 0,
      backgroundColor: 'rgba(15, 23, 42, 0.65)',
      display: 'flex',
      alignItems: 'center',
      justifyContent: 'center',
      zIndex: 9999,
      padding: '1rem',
      backdropFilter: 'blur(3px)'
    }}>
      <div style={{
        backgroundColor: '#ffffff',
        borderRadius: '0.75rem',
        width: '100%',
        maxWidth: '760px',
        maxHeight: '90vh',
        display: 'flex',
        flexDirection: 'column',
        boxShadow: '0 25px 50px -12px rgba(0, 0, 0, 0.25)',
        overflow: 'hidden'
      }}>
        {/* Cabecera del modal */}
        <div style={{
          padding: '1.25rem 1.5rem',
          borderBottom: '1px solid #e2e8f0',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          backgroundColor: '#f8fafc'
        }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
              <h2 style={{ fontSize: '1.15rem', fontWeight: 700, color: '#0f172a', margin: 0 }}>
                Inspector de Token JWT & Validación de Identidad
              </h2>
              <span style={{
                fontSize: '0.75rem',
                fontWeight: 600,
                color: badge.color,
                backgroundColor: badge.bg,
                padding: '0.2rem 0.6rem',
                borderRadius: '9999px',
                border: `1px solid ${badge.color}30`
              }}>
                {badge.label}
              </span>
            </div>
            <p style={{ fontSize: '0.8rem', color: '#64748b', margin: '0.25rem 0 0 0' }}>
              Validado por el BFF en AWS EC2 contra los endpoints públicos JWKS (OAuth 2.0 / OIDC)
            </p>
          </div>
          <button
            onClick={onClose}
            style={{
              background: 'none',
              border: 'none',
              fontSize: '1.5rem',
              color: '#94a3b8',
              cursor: 'pointer',
              lineHeight: 1
            }}
          >
            &times;
          </button>
        </div>

        {/* Contenido con scroll */}
        <div style={{ padding: '1.5rem', overflowY: 'auto', display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
          
          {/* Ficha rápida de Claims principales */}
          <div style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '0.75rem',
            backgroundColor: '#f8fafc',
            padding: '1rem',
            borderRadius: '0.5rem',
            border: '1px solid #e2e8f0'
          }}>
            <div>
              <span style={{ fontSize: '0.7rem', color: '#64748b', textTransform: 'uppercase', fontWeight: 600 }}>Usuario / Principal</span>
              <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#0f172a', wordBreak: 'break-all' }}>
                {parsed?.payload?.preferred_username || parsed?.payload?.upn || parsed?.payload?.email || user?.email || 'N/A'}
              </div>
            </div>
            <div>
              <span style={{ fontSize: '0.7rem', color: '#64748b', textTransform: 'uppercase', fontWeight: 600 }}>Rol Extraído</span>
              <div style={{ fontSize: '0.85rem', fontWeight: 600, color: '#2563eb' }}>
                {user?.role || (parsed?.payload?.roles ? parsed.payload.roles.join(', ') : 'N/A')}
              </div>
            </div>
            <div>
              <span style={{ fontSize: '0.7rem', color: '#64748b', textTransform: 'uppercase', fontWeight: 600 }}>Tenant ID (Aislamiento)</span>
              <div style={{ fontSize: '0.8rem', fontFamily: 'monospace', color: '#0f172a' }}>
                {parsed?.payload?.tid || user?.tenant || 'N/A'}
              </div>
            </div>
            <div>
              <span style={{ fontSize: '0.7rem', color: '#64748b', textTransform: 'uppercase', fontWeight: 600 }}>Expiración (exp)</span>
              <div style={{ fontSize: '0.8rem', color: '#0f172a' }}>
                {formatTimestamp(parsed?.payload?.exp)}
              </div>
            </div>
          </div>

          {/* Header Decodificado */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.35rem' }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#dc2626', textTransform: 'uppercase' }}>
                1. Header (Algoritmo & Key ID)
              </span>
            </div>
            <pre style={{
              backgroundColor: '#fee2e220',
              border: '1px solid #fca5a5',
              color: '#991b1b',
              padding: '0.75rem',
              borderRadius: '0.375rem',
              fontSize: '0.78rem',
              margin: 0,
              overflowX: 'auto',
              fontFamily: 'Consolas, monospace'
            }}>
              {JSON.stringify(parsed?.header || {}, null, 2)}
            </pre>
          </div>

          {/* Payload Decodificado */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.35rem' }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#7c3aed', textTransform: 'uppercase' }}>
                2. Payload / Claims (Datos de Identidad y Roles)
              </span>
            </div>
            <pre style={{
              backgroundColor: '#f3e8ff25',
              border: '1px solid #d8b4fe',
              color: '#581c87',
              padding: '0.75rem',
              borderRadius: '0.375rem',
              fontSize: '0.78rem',
              margin: 0,
              maxHeight: '180px',
              overflowY: 'auto',
              fontFamily: 'Consolas, monospace'
            }}>
              {JSON.stringify(parsed?.payload || {}, null, 2)}
            </pre>
          </div>

          {/* Token Crudo (Raw Bearer Token) */}
          <div>
            <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.35rem' }}>
              <span style={{ fontSize: '0.75rem', fontWeight: 700, color: '#0284c7', textTransform: 'uppercase' }}>
                3. Token Bearer Crudo (Enviado al BFF en cabecera Authorization)
              </span>
              <button
                onClick={handleCopy}
                style={{
                  fontSize: '0.75rem',
                  padding: '0.2rem 0.6rem',
                  backgroundColor: copied ? '#16a34a' : '#0284c7',
                  color: '#ffffff',
                  border: 'none',
                  borderRadius: '0.25rem',
                  cursor: 'pointer',
                  fontWeight: 600
                }}
              >
                {copied ? '¡Copiado!' : 'Copiar Token'}
              </button>
            </div>
            <textarea
              readOnly
              value={token || 'No hay token disponible'}
              rows={3}
              style={{
                width: '100%',
                backgroundColor: '#f8fafc',
                border: '1px solid #cbd5e1',
                borderRadius: '0.375rem',
                fontSize: '0.72rem',
                fontFamily: 'Consolas, monospace',
                padding: '0.5rem',
                color: '#334155',
                resize: 'none',
                boxSizing: 'border-box'
              }}
            />
          </div>

          {/* Explicación Técnica para el Profesor */}
          <div style={{
            backgroundColor: '#f0fdf4',
            border: '1px solid #86efac',
            borderRadius: '0.5rem',
            padding: '0.75rem 1rem',
            fontSize: '0.78rem',
            color: '#166534',
            lineHeight: 1.45
          }}>
            <strong>Flujo de Validación en el Backend:</strong>
            <br />
            1. El frontend adjunta este token en cada solicitud: <code>Authorization: Bearer &lt;token&gt;</code>.
            <br />
            2. El microservicio <code>ms-rutaexpress-bff</code> en Spring Boot intercepta la petición con <code>NimbusJwtDecoder</code>.
            <br />
            3. El backend descarga la clave pública asimétrica desde el endpoint <code>JWKS</code> de Cognito/Azure y valida la firma sin necesidad de compartir secretos privados.
          </div>
        </div>

        {/* Pie del modal */}
        <div style={{
          padding: '0.85rem 1.5rem',
          borderTop: '1px solid #e2e8f0',
          display: 'flex',
          justifyContent: 'flex-end',
          backgroundColor: '#f8fafc'
        }}>
          <button
            onClick={onClose}
            className="btn btn-outline"
            style={{ padding: '0.5rem 1.25rem', fontSize: '0.85rem' }}
          >
            Cerrar
          </button>
        </div>
      </div>
    </div>
  );
};

export default TokenInspectorModal;
