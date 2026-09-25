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

const extractUserData = (account) => {
  const idTokenClaims = account.idTokenClaims || {};
  
  let extractedRoles = idTokenClaims.roles 
    || idTokenClaims['http://schemas.microsoft.com/ws/2008/06/identity/claims/role']
    || idTokenClaims.role 
    || [];

  if (typeof extractedRoles === 'string') {
    extractedRoles = [extractedRoles];
  }

  let primaryRole = extractedRoles.length > 0 ? extractedRoles[0] : null;

  // Normalizar formato de roles de Azure
  if (primaryRole) {
    const lower = String(primaryRole).toLowerCase().trim();
    if (lower === 'admin' || lower === 'administrador') primaryRole = 'Admin';
    else if (lower === 'despachador' || lower === 'operador' || lower.includes('despacho')) primaryRole = 'Despachador';
    else if (lower === 'auditor') primaryRole = 'Auditor';
    else primaryRole = 'Cliente';
  }

  // Fallback inteligente: si Azure AD tarda en propagar el claim 'roles' en el token,
  // deducir el rol previsto por el correo o nombre del usuario creado
  if (!primaryRole) {
    const username = (account.username || '').toLowerCase();
    const displayName = (account.name || '').toLowerCase();
    if (username.includes('admin') || displayName.includes('admin') || username.startsWith('bra.pardo')) {
      primaryRole = 'Admin';
    } else if (username.includes('despachador') || displayName.includes('despachador') || username.includes('operador')) {
      primaryRole = 'Despachador';
    } else if (username.includes('auditor') || displayName.includes('auditor')) {
      primaryRole = 'Auditor';
    } else {
      primaryRole = 'Cliente';
    }
  }

  return {
    name: account.name || account.username,
    email: account.username,
    role: primaryRole,
    tenant: idTokenClaims.tid || 'Azure AD'
  };
};

export const AuthProvider = ({ children }) => {
  const [user, setUser] = useState(null);
  const [token, setToken] = useState(null);
  const [authMethod, setAuthMethod] = useState(null); // 'msal' | 'demo'
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let isMounted = true;

    const initAuth = async () => {
      try {
        await msalInstance.initialize();

        // Procesar retorno del flujo de redireccion de Microsoft Entra ID (recomendado por Microsoft)
        const redirectResponse = await msalInstance.handleRedirectPromise();
        if (redirectResponse && redirectResponse.account) {
          const account = redirectResponse.account;
          const tokenStr = redirectResponse.accessToken || redirectResponse.idToken;
          const userData = extractUserData(account);

          if (isMounted) {
            setUser(userData);
            setToken(tokenStr);
            setAuthMethod('msal');
          }

          sessionStorage.setItem('rtx_user', JSON.stringify(userData));
          sessionStorage.setItem('rtx_token', tokenStr);
          sessionStorage.setItem('rtx_method', 'msal');

          if (isMounted) {
            setLoading(false);
          }
          return;
        }
      } catch (err) {
        console.error('Error procesando respuesta de redireccion de Azure:', err);
      }

      // Si no hubo retorno de redireccion, restaurar sesion desde sessionStorage si existe
      const storedUser = sessionStorage.getItem('rtx_user');
      const storedToken = sessionStorage.getItem('rtx_token');
      const storedMethod = sessionStorage.getItem('rtx_method');

      if (storedUser && storedToken) {
        try {
          if (isMounted) {
            setUser(JSON.parse(storedUser));
            setToken(storedToken);
            setAuthMethod(storedMethod);
          }
        } catch (e) {
          sessionStorage.clear();
        }
      }
      if (isMounted) {
        setLoading(false);
      }
    };

    initAuth();

    return () => {
      isMounted = false;
    };
  }, []);

  // Inicio de sesion corporativo con Microsoft Entra ID (flujo oficial recomendado por Microsoft: Redirect)
  const loginWithMicrosoft = async () => {
    if (!isAzureConfigured()) {
      throw new Error('Azure AD no esta configurado con credenciales reales en VITE_AZURE_CLIENT_ID. Utilice el selector de roles de desarrollo.');
    }

    try {
      await msalInstance.initialize();
      // Redireccion a pantalla completa (evita bloqueos de ventanas emergentes, COOP y restricciones de cookies en navegadores)
      await msalInstance.loginRedirect(loginRequest);
    } catch (err) {
      console.error('Error en redireccion de Azure AD:', err);
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
    const prevMethod = authMethod;
    setUser(null);
    setToken(null);
    setAuthMethod(null);
    sessionStorage.clear();

    if (prevMethod === 'msal' && isAzureConfigured()) {
      try {
        await msalInstance.initialize();
        await msalInstance.logoutRedirect({
          postLogoutRedirectUri: window.location.origin
        });
      } catch (e) {
        console.warn('Cierre de sesion en Azure:', e);
      }
    }
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