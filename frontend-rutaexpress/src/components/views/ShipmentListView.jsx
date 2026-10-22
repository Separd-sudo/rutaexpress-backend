import React, { useState, useEffect } from 'react';
import bffApi from '../../services/bffApi';
import { useAuth } from '../../auth/AuthContext';

export const ShipmentListView = () => {
  const { role } = useAuth();
  const [shipments, setShipments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [statusFilter, setStatusFilter] = useState('');
  const [trackingSearch, setTrackingSearch] = useState('');
  const [selectedShipment, setSelectedShipment] = useState(null);
  const [deletingId, setDeletingId] = useState(null);

  const fetchShipments = async (status = statusFilter) => {
    setLoading(true);
    setError(null);
    try {
      const data = await bffApi.getShipments(status || undefined);
      setShipments(data || []);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Error al obtener envios');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchShipments();
  }, []);

  const handleSearchTracking = async (e) => {
    e.preventDefault();
    if (!trackingSearch.trim()) {
      fetchShipments();
      return;
    }
    setLoading(true);
    setError(null);
    try {
      const result = await bffApi.getShipmentByTracking(trackingSearch.trim());
      if (result) {
        setShipments([result]);
      } else {
        setShipments([]);
      }
    } catch (err) {
      setError('No se encontro ningun envio con el codigo ingresado');
      setShipments([]);
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async (id) => {
    if (!window.confirm(`Confirma la cancelacion/eliminacion del envio ID ${id}?`)) {
      return;
    }
    setDeletingId(id);
    try {
      await bffApi.deleteShipment(id);
      fetchShipments();
    } catch (err) {
      alert(err.response?.data?.message || 'Error al eliminar envio');
    } finally {
      setDeletingId(null);
    }
  };

  const getBadgeClass = (st) => {
    if (!st) return 'badge';
    const s = String(st).toLowerCase();
    return `badge badge-${s}`;
  };

  return (
    <div style={{ maxWidth: '1100px', margin: '0 auto' }}>
      <div className="card">
        <div className="card-header">
          <div>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 600, color: '#0f172a' }}>
              Listado y Seguimiento de Envios
            </h2>
            <p style={{ fontSize: '0.8125rem', color: '#64748b', marginTop: '0.2rem' }}>
              Consulta de despachos registrados en el sistema central.
            </p>
          </div>
          <button
            onClick={() => fetchShipments()}
            className="btn btn-outline"
            style={{ fontSize: '0.75rem', padding: '0.35rem 0.75rem' }}
          >
            Actualizar Lista
          </button>
        </div>

        {/* Barra de Filtros y Busqueda */}
        <div style={{
          display: 'flex',
          gap: '1rem',
          alignItems: 'center',
          flexWrap: 'wrap',
          marginBottom: '1.25rem',
          backgroundColor: '#f8fafc',
          padding: '0.875rem',
          borderRadius: '0.375rem',
          border: '1px solid #e2e8f0'
        }}>
          <form onSubmit={handleSearchTracking} style={{ display: 'flex', gap: '0.5rem', flex: 1, minWidth: '260px' }}>
            <input
              type="text"
              placeholder="Buscar por Codigo de Tracking (ej: RTX-20261022-...)"
              value={trackingSearch}
              onChange={(e) => setTrackingSearch(e.target.value)}
              className="form-control"
              style={{ fontSize: '0.8125rem' }}
            />
            <button type="submit" className="btn btn-primary" style={{ fontSize: '0.8125rem', whiteSpace: 'nowrap' }}>
              Buscar
            </button>
          </form>

          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <span style={{ fontSize: '0.75rem', fontWeight: 600, color: '#64748b', textTransform: 'uppercase' }}>
              Filtrar por Estado:
            </span>
            <select
              value={statusFilter}
              onChange={(e) => {
                setStatusFilter(e.target.value);
                fetchShipments(e.target.value);
              }}
              className="form-control"
              style={{ width: 'auto', fontSize: '0.8125rem', padding: '0.4rem 0.75rem' }}
            >
              <option value="">Todos los Estados</option>
              <option value="CREADO">CREADO</option>
              <option value="ACEPTADO">ACEPTADO</option>
              <option value="EN_BODEGA">EN_BODEGA</option>
              <option value="EN_RUTA">EN_RUTA</option>
              <option value="ENTREGADO">ENTREGADO</option>
              <option value="CANCELADO">CANCELADO</option>
            </select>
          </div>
        </div>

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

        {/* Tabla de Envios */}
        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Tracking</th>
                <th>Destinatario</th>
                <th>Direccion</th>
                <th>Peso</th>
                <th>Estado</th>
                <th>Fecha Creacion</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="8" style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>
                    Cargando envios...
                  </td>
                </tr>
              ) : shipments.length === 0 ? (
                <tr>
                  <td colSpan="8" style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>
                    No se encontraron envios registrados.
                  </td>
                </tr>
              ) : (
                shipments.map((s) => (
                  <tr key={s.id}>
                    <td style={{ fontWeight: 600 }}>{s.id}</td>
                    <td style={{ fontFamily: 'monospace', fontSize: '0.8rem', color: '#0f172a' }}>
                      {s.trackingNumber || s.trackingCode}
                    </td>
                    <td>{s.recipientName}</td>
                    <td style={{ maxWidth: '200px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {s.recipientAddress}
                    </td>
                    <td>{s.weightKg} Kg</td>
                    <td>
                      <span className={getBadgeClass(s.status)}>
                        {s.status}
                      </span>
                    </td>
                    <td style={{ fontSize: '0.75rem', color: '#64748b' }}>
                      {s.createdAt ? new Date(s.createdAt).toLocaleString('es-CL') : '-'}
                    </td>
                    <td>
                      <div style={{ display: 'flex', gap: '0.4rem' }}>
                        <button
                          onClick={() => setSelectedShipment(s)}
                          className="btn btn-outline"
                          style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem' }}
                        >
                          Detalle
                        </button>
                        {role === 'Admin' && (
                          <button
                            onClick={() => handleDelete(s.id)}
                            disabled={deletingId === s.id}
                            className="btn btn-outline"
                            style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem', color: '#b91c1c', borderColor: '#fca5a5' }}
                          >
                            {deletingId === s.id ? '...' : 'Anular'}
                          </button>
                        )}
                      </div>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>

      {/* Modal / Vista de Detalle */}
      {selectedShipment && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          right: 0,
          bottom: 0,
          backgroundColor: 'rgba(15, 23, 42, 0.6)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          padding: '1rem',
          zIndex: 50
        }}>
          <div style={{
            backgroundColor: '#ffffff',
            borderRadius: '0.5rem',
            maxWidth: '560px',
            width: '100%',
            boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1)',
            overflow: 'hidden'
          }}>
            <div style={{
              padding: '1rem 1.25rem',
              borderBottom: '1px solid #e2e8f0',
              backgroundColor: '#f8fafc',
              display: 'flex',
              justifyContent: 'space-between',
              alignItems: 'center'
            }}>
              <h3 style={{ fontSize: '1rem', fontWeight: 600, color: '#0f172a' }}>
                Detalle del Envio #{selectedShipment.id}
              </h3>
              <button
                onClick={() => setSelectedShipment(null)}
                style={{ background: 'transparent', border: 'none', cursor: 'pointer', fontSize: '1.25rem', color: '#64748b' }}
              >
                ×
              </button>
            </div>

            <div style={{ padding: '1.25rem', fontSize: '0.8125rem' }}>
              <div style={{ marginBottom: '0.75rem' }}>
                <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem', textTransform: 'uppercase' }}>Tracking:</span>
                <strong style={{ fontFamily: 'monospace', fontSize: '0.9rem' }}>{selectedShipment.trackingNumber || selectedShipment.trackingCode}</strong>
              </div>
              <div className="grid-2" style={{ marginBottom: '0.75rem' }}>
                <div>
                  <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem', textTransform: 'uppercase' }}>Remitente:</span>
                  <strong>{selectedShipment.senderName}</strong>
                  <div style={{ color: '#475569', fontSize: '0.75rem' }}>{selectedShipment.senderAddress}</div>
                </div>
                <div>
                  <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem', textTransform: 'uppercase' }}>Destinatario:</span>
                  <strong>{selectedShipment.recipientName}</strong>
                  <div style={{ color: '#475569', fontSize: '0.75rem' }}>{selectedShipment.recipientAddress}</div>
                </div>
              </div>
              <div className="grid-3" style={{ marginBottom: '0.75rem' }}>
                <div>
                  <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem', textTransform: 'uppercase' }}>Peso:</span>
                  <strong>{selectedShipment.weightKg} Kg</strong>
                </div>
                <div>
                  <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem', textTransform: 'uppercase' }}>Estado:</span>
                  <span className={getBadgeClass(selectedShipment.status)}>{selectedShipment.status}</span>
                </div>
                <div>
                  <span style={{ color: '#64748b', display: 'block', fontSize: '0.75rem', textTransform: 'uppercase' }}>Total Tarifa:</span>
                  <strong>${Number(selectedShipment.totalCost || selectedShipment.calculatedPrice || 0).toLocaleString('es-CL')}</strong>
                </div>
              </div>
            </div>

            <div style={{
              padding: '0.75rem 1.25rem',
              backgroundColor: '#f8fafc',
              borderTop: '1px solid #e2e8f0',
              display: 'flex',
              justifyContent: 'flex-end'
            }}>
              <button onClick={() => setSelectedShipment(null)} className="btn btn-outline" style={{ fontSize: '0.8125rem' }}>
                Cerrar
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default ShipmentListView;
