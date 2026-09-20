import React, { createContext, useContext, useState, useEffect } from 'react';
import { msalInstance, loginRequest, isAzureConfigured } from './authConfig';

const AuthContext = createContext(null);

const DEMO_USERS = {
  Admin: {
    name: 'Administrador de Red',
    email: 'admin.red@rutaexpress.cl',
    role: 'Admin',
    tenant: 'RutaExpress Corp'
  },
  Despachador: {
    name: 'Operador de Despacho',
    email: 'despachador.central@rutaexpress.cl',
    role: 'Despachador',
    tenant: 'RutaExpress Corp'
  },
  Cliente: {
    name: 'Cliente PyME',
    email: 'contacto@cliente-pyme.cl',
    role: 'Cliente',
    tenant: 'Cliente Dominio'
  },
  Auditor: {
    name: 'Auditor Logístico',
    email: 'auditoria@rutaexpress.cl',
    role: 'Auditor',
    tenant: 'Auditoría Externa'
  }
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [authMethod, setAuthMethod] = useState(null); // 'msal' | 'demo'
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Restaurar sesion desde sessionStorage si existe
    const storedUser = sessionStorage.getItem('rtx_user');
    const storedToken = sessionStorage.getItem('rtx_token');
    const storedMethod = sessionStorage.getItem('rtx_method');

    if (storedUser && storedToken) {
      try {
        setUser(JSON.parse(storedUser));
        setToken(storedToken);
        setAuthMethod(storedMethod);
      } catch (e) {
        sessionStorage.clear();
      }
    }
    setLoading(false);
  }, []);

  // Inicio de sesion corporativo con Microsoft Entra ID (Azure AD)
  const loginWithMicrosoft = async () => {
    if (!isAzureConfigured()) {
      throw new Error('Azure AD no esta configurado con credenciales reales en VITE_AZURE_CLIENT_ID. Utilice el selector de roles de desarrollo.');
    }

    try {
      await msalInstance.initialize();
      const loginResponse = await msalInstance.loginPopup(loginRequest);
      const account = loginResponse.account;

      // Extraer roles de la app desde el token
      const idTokenClaims = account.idTokenClaims || {};
      const roles = idTokenClaims.roles || ['Cliente'];
      const primaryRole = roles[0] || 'Cliente';

      const userData = {
        name: account.name || account.username,
        email: account.username,
        role: primaryRole,
        tenant: idTokenClaims.tid || 'Azure AD'
      };

      const accessToken = loginResponse.accessToken || loginResponse.idToken;

      setUser(userData);
      setToken(accessToken);
      setAuthMethod('msal');

      sessionStorage.setItem('rtx_user', JSON.stringify(userData));
      sessionStorage.setItem('rtx_token', accessToken);
      sessionStorage.setItem('rtx_method', 'msal');

      return userData;
    } catch (err) {
      console.error('Error en autenticacion Azure AD:', err);
      throw err;
    }
  };

  // Inicio de sesion para evaluacion de roles en entorno de desarrollo
  const loginAsDemoRole = (roleKey) => {
    const demo = DEMO_USERS[roleKey] || DEMO_USERS.Cliente;
    // Generar un token simulado con estructura JWT legible
    const simulatedToken = `mock-bearer-token-${roleKey.toLowerCase()}-${Date.now()}`;

    setUser(demo);
    setToken(simulatedToken);
    setAuthMethod('demo');

    sessionStorage.setItem('rtx_user', JSON.stringify(demo));
    sessionStorage.setItem('rtx_token', simulatedToken);
    sessionStorage.setItem('rtx_method', 'demo');

    return demo;
  };

  const logout = async () => {
    if (authMethod === 'msal' && isAzureConfigured()) {
      try {
        await msalInstance.logoutPopup();
      } catch (e) {
        console.warn('Cierre de sesion en Azure:', e);
      }
    }
    setUser(null);
    setToken(null);
    setAuthMethod(null);
    sessionStorage.clear();
  };

  const value = {
    user,
    token,
    role: user?.role || null,
    isAuthenticated: !!user,
    authMethod,
    loading,
    loginWithMicrosoft,
    loginAsDemoRole,
    logout
  };

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
};

export const useAuth = () => {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth debe ser utilizado dentro de un AuthProvider');
  }
  return context;
};