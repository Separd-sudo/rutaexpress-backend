import { PublicClientApplication } from '@azure/msal-browser';

const tenantId = import.meta.env.VITE_AZURE_TENANT_ID || 'common';
const clientId = import.meta.env.VITE_AZURE_CLIENT_ID || '00000000-0000-0000-0000-000000000000';
const apiClientId = import.meta.env.VITE_AZURE_API_CLIENT_ID || clientId;

export const msalConfig = {
  auth: {
    clientId: clientId,
    authority: `https://login.microsoftonline.com/${tenantId}`,
    redirectUri: typeof window !== 'undefined' ? window.location.origin : 'http://localhost:5173',
    postLogoutRedirectUri: typeof window !== 'undefined' ? window.location.origin : 'http://localhost:5173',
  },
  cache: {
    cacheLocation: 'sessionStorage',
    storeAuthStateInCookie: false,
  }
};

export const loginRequest = {
  scopes: [`api://${apiClientId}/access_as_user`]
};

export const isAzureConfigured = () => {
  return clientId !== '00000000-0000-0000-0000-000000000000';
};

export const msalInstance = new PublicClientApplication(msalConfig);

// Configuracion de AWS Cognito (Federacion OIDC)
export const cognitoConfig = {
  domain: import.meta.env.VITE_COGNITO_DOMAIN || '',
  clientId: import.meta.env.VITE_COGNITO_CLIENT_ID || '',
  redirectUri: import.meta.env.VITE_COGNITO_REDIRECT_URI || (typeof window !== 'undefined' ? `${window.location.origin}/` : 'http://localhost:5173/')
};

export const isCognitoConfigured = () => {
  return Boolean(cognitoConfig.domain && cognitoConfig.clientId);
};

export const getCognitoLoginUrl = () => {
  if (!isCognitoConfigured()) return '';
  const domain = cognitoConfig.domain.replace(/\/$/, '');
  const encodedRedirect = encodeURIComponent(cognitoConfig.redirectUri);
  return `${domain}/login?client_id=${cognitoConfig.clientId}&response_type=code&scope=email+openid+profile&redirect_uri=${encodedRedirect}`;
};