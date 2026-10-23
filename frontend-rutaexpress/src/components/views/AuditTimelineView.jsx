import React, { useState } from 'react';
import bffApi from '../../services/bffApi';

export const AuditTimelineView = () => {
  const [searchId, setSearchId] = useState('');
  const [loading, setLoading] = useState(false);
  const [timeline, setTimeline] = useState(null);
  const [fullTrace, setFullTrace] = useState(null);
  const [error, setError] = useState(null);

  const handleSearch = async (e) => {
    e.preventDefault();
    if (!searchId.trim()) return;

    setLoading(true);
    setError(null);
    setTimeline(null);
    setFullTrace(null);

    try {
      let numericId = searchId.trim();
      // Si el usuario busco por codigo de tracking, primero obtenemos el ID
      if (isNaN(numericId)) {
        const shipment = await bffApi.getShipmentByTracking(numericId);
        if (shipment && shipment.id) {
          numericId = shipment.id;
        } else {
          throw new Error('No se encontro el envio con el codigo ingresado');
        }
      }

      // Consumir el endpoint de agregacion optimizado del BFF
      try {
        const traceData = await bffApi.getFullTrace(numericId);
        setFullTrace(traceData);
      } catch (errTrace) {
        console.warn('Endpoint full-trace no disponible, consultando timeline directo:', errTrace);
      }

      const timelineData = await bffApi.getShipmentTimeline(numericId);
      setTimeline(timelineData || []);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Error al obtener trazabilidad del envio');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={{ maxWidth: '900px', margin: '0 auto' }}>
      <div className="card">
        <div className="card-header">
          <div>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 600, color: '#0f172a' }}>
              Trazabilidad y Linea de Tiempo de Envios
            </h2>
            <p style={{ fontSize: '0.8125rem', color: '#64748b', marginTop: '0.2rem' }}>
              Inspeccione la cadena inmutable de eventos de auditoria registrados por el sistema.
            </p>
          </div>
        </div>

        {/* Buscador de Envio */}
        <form onSubmit={handleSearch} style={{ display: 'flex', gap: '0.75rem', marginBottom: '1.5rem' }}>
          <input
            type="text"
            required
            placeholder="Ingrese ID del Envio (ej: 1) o Codigo Tracking"
            value={searchId}
            onChange={(e) => setSearchId(e.target.value)}
            className="form-control"
            style={{ maxWidth: '420px' }}
          />
          <button type="submit" disabled={loading} className="btn btn-primary" style={{ whiteSpace: 'nowrap' }}>
            {loading ? 'Consultando...' : 'Buscar Trazabilidad'}
          </button>
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

        {/* Informacion Unificada del Envio (Full Trace) */}
        {fullTrace && (
          <div style={{
            border: '1px solid #e2e8f0',
            borderRadius: '0.375rem',
            padding: '1rem',
            backgroundColor: '#f8fafc',
            marginBottom: '1.5rem'
          }}>
            <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600, marginBottom: '0.5rem' }}>
              Resumen de Agregacion BFF (Envio + Tarifa + Estado)
            </div>
            <div className="grid-3" style={{ fontSize: '0.8125rem' }}>
              <div>
                <span style={{ color: '#64748b', display: 'block' }}>Tracking:</span>
                <strong style={{ fontFamily: 'monospace' }}>{fullTrace.trackingNumber || fullTrace.trackingCode}</strong>
              </div>
              <div>
                <span style={{ color: '#64748b', display: 'block' }}>Estado Actual:</span>
                <span className="badge badge-creado">{fullTrace.currentStatus || fullTrace.status}</span>
              </div>
              <div>
                <span style={{ color: '#64748b', display: 'block' }}>Destinatario:</span>
                <strong>{fullTrace.recipientName}</strong>
              </div>
            </div>
          </div>
        )}

        {/* Lista de Hitos / Timeline */}
        {timeline && (
          <div>
            <h3 style={{ fontSize: '0.875rem', fontWeight: 600, color: '#334155', textTransform: 'uppercase', marginBottom: '1rem', letterSpacing: '0.025em' }}>
              Eventos Registrados en Bitacora ({timeline.length})
            </h3>

            {timeline.length === 0 ? (
              <div style={{ padding: '2rem', textAlign: 'center', color: '#64748b', backgroundColor: '#f8fafc', borderRadius: '0.375rem' }}>
                No se registran eventos de auditoria para este envio.
              </div>
            ) : (
              <div style={{ display: 'flex', flexDirection: 'column', gap: '0.875rem' }}>
                {timeline.map((evt, idx) => (
                  <div
                    key={evt.id || idx}
                    style={{
                      border: '1px solid #e2e8f0',
                      borderLeft: '4px solid #0f172a',
                      borderRadius: '0.375rem',
                      padding: '1rem',
                      backgroundColor: '#ffffff'
                    }}
                  >
                    <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.35rem' }}>
                      <span style={{ fontWeight: 600, fontSize: '0.875rem', color: '#0f172a' }}>
                        {evt.eventType || 'EVENTO_AUDITORIA'}
                      </span>
                      <span style={{ fontSize: '0.75rem', color: '#64748b' }}>
                        {evt.timestamp ? new Date(evt.timestamp).toLocaleString('es-CL') : '-'}
                      </span>
                    </div>

                    <div style={{ fontSize: '0.8125rem', color: '#475569', marginBottom: '0.5rem' }}>
                      Usuario ejecutor: <strong>{evt.user || evt.performedBy || 'Sistema'}</strong>
                      {evt.userRole && (
                        <span style={{ marginLeft: '0.5rem', fontSize: '0.75rem', border: '1px solid #cbd5e1', padding: '0.1rem 0.4rem', borderRadius: '3px' }}>
                          {evt.userRole}
                        </span>
                      )}
                    </div>

                    {evt.description && (
                      <div style={{ fontSize: '0.8125rem', color: '#334155', marginBottom: '0.5rem' }}>
                        {evt.description}
                      </div>
                    )}

                    {evt.payload && (
                      <details style={{ fontSize: '0.75rem', color: '#64748b', marginTop: '0.5rem' }}>
                        <summary style={{ cursor: 'pointer', outline: 'none' }}>Ver Carga Util JSON</summary>
                        <pre style={{
                          backgroundColor: '#f1f5f9',
                          padding: '0.5rem',
                          borderRadius: '0.25rem',
                          marginTop: '0.35rem',
                          overflowX: 'auto',
                          fontFamily: 'monospace',
                          fontSize: '0.75rem'
                        }}>
                          {typeof evt.payload === 'object' ? JSON.stringify(evt.payload, null, 2) : String(evt.payload)}
                        </pre>
                      </details>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>
    </div>
  );
};

export default AuditTimelineView;
