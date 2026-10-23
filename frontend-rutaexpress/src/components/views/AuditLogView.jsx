import React, { useState, useEffect } from 'react';
import bffApi from '../../services/bffApi';

export const AuditLogView = () => {
  const [events, setEvents] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [userFilter, setUserFilter] = useState('');
  const [typeFilter, setTypeFilter] = useState('');

  const fetchEvents = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await bffApi.searchAuditEvents(
        userFilter.trim() || undefined,
        typeFilter.trim() || undefined
      );
      setEvents(data || []);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Error al obtener bitacora de auditoria');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchEvents();
  }, []);

  const handleFilterSubmit = (e) => {
    e.preventDefault();
    fetchEvents();
  };

  return (
    <div style={{ maxWidth: '1100px', margin: '0 auto' }}>
      <div className="card">
        <div className="card-header">
          <div>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 600, color: '#0f172a' }}>
              Bitacora Global de Auditoria
            </h2>
            <p style={{ fontSize: '0.8125rem', color: '#64748b', marginTop: '0.2rem' }}>
              Registro centralizado de operaciones y transacciones del sistema.
            </p>
          </div>
          <button
            onClick={fetchEvents}
            className="btn btn-outline"
            style={{ fontSize: '0.75rem', padding: '0.35rem 0.75rem' }}
          >
            Actualizar
          </button>
        </div>

        {/* Filtros */}
        <form onSubmit={handleFilterSubmit} style={{
          display: 'flex',
          gap: '1rem',
          flexWrap: 'wrap',
          marginBottom: '1.25rem',
          backgroundColor: '#f8fafc',
          padding: '0.875rem',
          borderRadius: '0.375rem',
          border: '1px solid #e2e8f0'
        }}>
          <div style={{ flex: 1, minWidth: '200px' }}>
            <label className="form-label" style={{ fontSize: '0.75rem' }}>Filtrar por Usuario / Correo</label>
            <input
              type="text"
              placeholder="ej: admin.red@rutaexpress.cl"
              value={userFilter}
              onChange={(e) => setUserFilter(e.target.value)}
              className="form-control"
              style={{ fontSize: '0.8125rem' }}
            />
          </div>

          <div style={{ flex: 1, minWidth: '200px' }}>
            <label className="form-label" style={{ fontSize: '0.75rem' }}>Tipo de Evento</label>
            <input
              type="text"
              placeholder="ej: SHIPMENT_CREATED, STATUS_CHANGED"
              value={typeFilter}
              onChange={(e) => setTypeFilter(e.target.value)}
              className="form-control"
              style={{ fontSize: '0.8125rem' }}
            />
          </div>

          <div style={{ display: 'flex', alignItems: 'flex-end' }}>
            <button type="submit" className="btn btn-primary" style={{ fontSize: '0.8125rem' }}>
              Aplicar Filtros
            </button>
          </div>
        </form>

        {error && (
          <div style={{
            backgroundColor: '#fee2e2',
            border: '1px solid #f87171',
            color: '#991b1b',
            padding: '0.75rem 1rem',
            borderRadius: '0.375rem',
            fontSize: '0.8125rem',
            marginBottom: '1rem'
          }}>
            {error}
          </div>
        )}

        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Tipo de Evento</th>
                <th>Usuario Responsable</th>
                <th>Rol</th>
                <th>Fecha y Hora</th>
                <th>Detalle</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="6" style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>
                    Cargando bitacora de auditoria...
                  </td>
                </tr>
              ) : events.length === 0 ? (
                <tr>
                  <td colSpan="6" style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>
                    No se encontraron registros de auditoria con los filtros seleccionados.
                  </td>
                </tr>
              ) : (
                events.map((evt) => (
                  <tr key={evt.id}>
                    <td style={{ fontWeight: 600 }}>{evt.id}</td>
                    <td>
                      <span style={{
                        display: 'inline-block',
                        padding: '0.2rem 0.5rem',
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        borderRadius: '3px',
                        backgroundColor: '#f1f5f9',
                        color: '#0f172a'
                      }}>
                        {evt.eventType}
                      </span>
                    </td>
                    <td>{evt.user || evt.performedBy || 'Sistema'}</td>
                    <td>
                      <span style={{ fontSize: '0.75rem', color: '#475569' }}>
                        {evt.userRole || '-'}
                      </span>
                    </td>
                    <td style={{ fontSize: '0.75rem', color: '#64748b' }}>
                      {evt.timestamp ? new Date(evt.timestamp).toLocaleString('es-CL') : '-'}
                    </td>
                    <td style={{ fontSize: '0.8125rem', color: '#334155' }}>
                      {evt.description || '-'}
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default AuditLogView;
