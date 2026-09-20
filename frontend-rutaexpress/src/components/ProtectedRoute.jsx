import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';

export const ProtectedRoute = ({ children, allowedRoles }) => {
  const { isAuthenticated, role, loading } = useAuth();

  if (loading) {
    return (
      <div style={{ padding: '3rem', textAlign: 'center', color: '#64748b' }}>
        Verificando credenciales de acceso...
      </div>
    );
  }

  if (!isAuthenticated) {
    return <Navigate to="/login" replace />;
  }

  if (allowedRoles && !allowedRoles.includes(role)) {
    return (
      <div className="container" style={{ padding: '3rem 1.5rem' }}>
        <div className="card" style={{ borderColor: '#fca5a5', backgroundColor: '#fef2f2' }}>
          <h3 style={{ color: '#991b1b', marginBottom: '0.5rem' }}>Acceso No Autorizado</h3>
          <p style={{ color: '#7f1d1d', fontSize: '0.875rem' }}>
            Su rol actual (<strong>{role}</strong>) no dispone de privilegios para acceder a esta seccion.
          </p>
        </div>
      </div>
    );
  }

  return children;
};

export default ProtectedRoute;