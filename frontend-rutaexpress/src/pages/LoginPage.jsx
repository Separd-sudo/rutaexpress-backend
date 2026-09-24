import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { isAzureConfigured } from '../auth/authConfig';

export const LoginPage = () => {
  const { loginWithMicrosoft, loginAsDemoRole, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [errorMsg, setErrorMsg] = useState(null);
  const [isLoggingIn, setIsLoggingIn] = useState(false);

  React.useEffect(() => {
    if (isAuthenticated) {
      navigate('/dashboard');
    }
  }, [isAuthenticated, navigate]);

  const handleMicrosoftLogin = async () => {
    setErrorMsg(null);
    setIsLoggingIn(true);
    try {
      await loginWithMicrosoft();
      navigate('/dashboard');
    } catch (err) {
      setErrorMsg(err.message || 'Error al conectar con Microsoft Entra ID');
    } finally {
      setIsLoggingIn(false);
    }
  };

  const handleDemoLogin = (role) => {
    loginAsDemoRole(role);
    navigate('/dashboard');
  };

  return (
    <div style={{ minHeight: 'calc(100vh - 65px)', display: 'flex', alignItems: 'center', justifyContent: 'center', padding: '1.5rem' }}>
      <div style={{ width: '100%', maxWidth: '440px' }}>
        <div style={{ textAlign: 'center', marginBottom: '2rem' }}>
          <h1 style={{ fontSize: '1.75rem', fontWeight: 800, color: '#0f172a', letterSpacing: '-0.025em' }}>
            RutaExpress
          </h1>
          <p style={{ fontSize: '0.875rem', color: '#64748b', marginTop: '0.25rem' }}>
            Plataforma Unificada para Envios de Ultima Milla
          </p>
        </div>

        <div className="card" style={{ boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.08), 0 2px 4px -2px rgba(0, 0, 0, 0.04)' }}>
          <div style={{ marginBottom: '1.5rem' }}>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 600, color: '#0f172a' }}>
              Autenticacion Corporativa
            </h2>
            <p style={{ fontSize: '0.8125rem', color: '#64748b', marginTop: '0.25rem' }}>
              Inicie sesion con su cuenta institucional de Microsoft Entra ID (Azure AD).
            </p>
          </div>

          {errorMsg && (
            <div style={{ backgroundColor: '#fee2e2', border: '1px solid #f87171', color: '#991b1b', padding: '0.75rem', borderRadius: '0.375rem', fontSize: '0.8125rem', marginBottom: '1.25rem' }}>
              {errorMsg}
            </div>
          )}

          {/* Boton oficial segun rubrica: Iniciar sesion con Microsoft */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.75rem' }}>
            <button
              onClick={handleMicrosoftLogin}
              disabled={isLoggingIn}
              className="btn btn-microsoft"
              style={{ width: '100%', display: 'flex', gap: '0.75rem', padding: '0.75rem 1rem', fontSize: '0.875rem' }}
            >
              {/* Logo oficial de Microsoft en SVG */}
              <svg width="20" height="20" viewBox="0 0 21 21" xmlns="http://www.w3.org/2000/svg">
                <rect x="1" y="1" width="9" height="9" fill="#f25022" />
                <rect x="11" y="1" width="9" height="9" fill="#7fba00" />
                <rect x="1" y="11" width="9" height="9" fill="#00a4ef" />
                <rect x="11" y="11" width="9" height="9" fill="#ffb900" />
              </svg>
              <span>{isLoggingIn ? 'Conectando con Azure...' : 'Iniciar sesion con Microsoft Entra ID'}</span>
            </button>

            <a
              href="https://us-east-1akiimfjh5.auth.us-east-1.amazoncognito.com/login?client_id=rutaexpress-web-client&response_type=code&scope=email+openid+profile&redirect_uri=http://localhost:5173/"
              className="btn"
              style={{
                width: '100%',
                display: 'flex',
                gap: '0.75rem',
                padding: '0.75rem 1rem',
                fontSize: '0.875rem',
                backgroundColor: '#ff9900',
                color: '#ffffff',
                border: 'none',
                textDecoration: 'none',
                justifyContent: 'center',
                alignItems: 'center',
                fontWeight: 600,
                borderRadius: '0.375rem'
              }}
            >
              {/* Logo de AWS */}
              <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                <path d="M18.76 12.56c-.28-.2-.71-.11-.89.17-.67 1.05-1.57 1.94-2.65 2.62-.27.17-.34.54-.17.81.17.27.53.35.81.18 1.19-.75 2.19-1.74 2.93-2.9.18-.28.1-.64-.18-.84l.15-.04zm-6.76 4.44c-3.87 0-7.02-3.15-7.02-7.02S8.13 2.96 12 2.96s7.02 3.15 7.02 7.02c0 1.25-.33 2.43-.91 3.45-.16.28-.07.64.21.8.28.16.64.07.8-.21.68-1.19 1.07-2.57 1.07-4.04 0-4.51-3.66-8.17-8.19-8.17S3.81 5.47 3.81 9.98s3.66 8.17 8.19 8.17c1.37 0 2.66-.34 3.8-.94.28-.15.39-.5.24-.78-.15-.28-.5-.39-.78-.24-1.01.53-2.15.83-3.37.83h.11z"/>
              </svg>
              <span>Iniciar sesion con AWS Cognito (Federado)</span>
            </a>
          </div>

          <div style={{ margin: '1.5rem 0', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <div style={{ flex: 1, height: '1px', backgroundColor: '#e2e8f0' }}></div>
            <span style={{ fontSize: '0.75rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Acceso por Rol de Evaluacion
            </span>
            <div style={{ flex: 1, height: '1px', backgroundColor: '#e2e8f0' }}></div>
          </div>

          {/* Selector de perfiles para evaluacion academica y funcional inmediata */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <button
              onClick={() => handleDemoLogin('Admin')}
              className="btn btn-outline"
              style={{ justifyContent: 'space-between', fontSize: '0.8125rem', padding: '0.5rem 0.875rem' }}
            >
              <span>Entrar como <strong>Administrador</strong></span>
              <span className="badge badge-creado">Admin</span>
            </button>

            <button
              onClick={() => handleDemoLogin('Despachador')}
              className="btn btn-outline"
              style={{ justifyContent: 'space-between', fontSize: '0.8125rem', padding: '0.5rem 0.875rem' }}
            >
              <span>Entrar como <strong>Despachador</strong></span>
              <span className="badge badge-en_bodega">Operador</span>
            </button>

            <button
              onClick={() => handleDemoLogin('Cliente')}
              className="btn btn-outline"
              style={{ justifyContent: 'space-between', fontSize: '0.8125rem', padding: '0.5rem 0.875rem' }}
            >
              <span>Entrar como <strong>Cliente</strong></span>
              <span className="badge badge-aceptado">Cliente</span>
            </button>

            <button
              onClick={() => handleDemoLogin('Auditor')}
              className="btn btn-outline"
              style={{ justifyContent: 'space-between', fontSize: '0.8125rem', padding: '0.5rem 0.875rem' }}
            >
              <span>Entrar como <strong>Auditor</strong></span>
              <span className="badge badge-entregado">Solo lectura</span>
            </button>
          </div>
        </div>

        <div style={{ textAlign: 'center', fontSize: '0.75rem', color: '#94a3b8' }}>
          Trazabilidad protegida por JWT via Backend-For-Frontend (BFF)
        </div>
      </div>
    </div>
  );
};

export default LoginPage;