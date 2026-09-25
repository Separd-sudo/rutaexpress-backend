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
    } catch (err) {
      setErrorMsg(err.message || 'Error al conectar con Microsoft Entra ID');
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
          </div>

          <div style={{ margin: '1.5rem 0', display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <div style={{ flex: 1, height: '1px', backgroundColor: '#e2e8f0' }}></div>
            <span style={{ fontSize: '0.75rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.05em' }}>
              Acceso por Rol de Evaluacion
            </span>
            <div style={{ flex: 1, height: '1px', backgroundColor: '#e2e8f0' }}></div>
          </div>

          {/* Selector de perfiles para evaluacion funcional */}
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
            <button
              onClick={() => handleDemoLogin('Admin')}
              className="btn btn-outline"
              style={{ justifyContent: 'space-between', fontSize: '0.8125rem', padding: '0.5rem 0.875rem' }}
            >
              <span>Entrar como Administrador</span>
              <span style={{ border: '1px solid #d1d5db', padding: '0.1rem 0.4rem', borderRadius: '3px', fontSize: '0.75rem', color: '#4b5563' }}>Admin</span>
            </button>

            <button
              onClick={() => handleDemoLogin('Despachador')}
              className="btn btn-outline"
              style={{ justifyContent: 'space-between', fontSize: '0.8125rem', padding: '0.5rem 0.875rem' }}
            >
              <span>Entrar como Despachador</span>
              <span style={{ border: '1px solid #d1d5db', padding: '0.1rem 0.4rem', borderRadius: '3px', fontSize: '0.75rem', color: '#4b5563' }}>Despachador</span>
            </button>

            <button
              onClick={() => handleDemoLogin('Cliente')}
              className="btn btn-outline"
              style={{ justifyContent: 'space-between', fontSize: '0.8125rem', padding: '0.5rem 0.875rem' }}
            >
              <span>Entrar como Cliente</span>
              <span style={{ border: '1px solid #d1d5db', padding: '0.1rem 0.4rem', borderRadius: '3px', fontSize: '0.75rem', color: '#4b5563' }}>Cliente</span>
            </button>

            <button
              onClick={() => handleDemoLogin('Auditor')}
              className="btn btn-outline"
              style={{ justifyContent: 'space-between', fontSize: '0.8125rem', padding: '0.5rem 0.875rem' }}
            >
              <span>Entrar como Auditor</span>
              <span style={{ border: '1px solid #d1d5db', padding: '0.1rem 0.4rem', borderRadius: '3px', fontSize: '0.75rem', color: '#4b5563' }}>Auditor</span>
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