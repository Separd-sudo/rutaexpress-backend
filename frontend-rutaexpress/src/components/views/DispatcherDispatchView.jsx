import React, { useState, useEffect } from 'react';
import bffApi from '../../services/bffApi';
import { useAuth } from '../../auth/AuthContext';

export const DispatcherDispatchView = () => {
  const { user, role } = useAuth();
  const [shipments, setShipments] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [pendingStatus, setPendingStatus] = useState({});
  const [updatingId, setUpdatingId] = useState(null);

  const fetchShipments = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await bffApi.getShipments();
      setShipments(data || []);
      const initialMap = {};
      (data || []).forEach((s) => {
        initialMap[s.id] = s.status;
      });
      setPendingStatus(initialMap);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Error al obtener envios para despacho');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchShipments();
  }, []);

  const handleStatusChange = (id, newStatus) => {
    setPendingStatus((prev) => ({
      ...prev,
      [id]: newStatus
    }));
  };

  const handleSaveStatus = async (id) => {
    const nextStatus = pendingStatus[id];
    if (!nextStatus) return;

    setUpdatingId(id);
    setError(null);
    setSuccessMessage(null);

    try {
      const performedBy = user?.name || user?.email || 'Operador Logistico';
      const userRole = role || 'Despachador';
      const note = `Actualizacion de estado a ${nextStatus} realizada por ${performedBy}`;

      await bffApi.updateShipmentStatus(id, nextStatus, performedBy, userRole, note);
      setSuccessMessage(`Envio #${id} actualizado a ${nextStatus}. Evento publicado en RabbitMQ.`);
      await fetchShipments();
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Error al actualizar estado del envio');
    } finally {
      setUpdatingId(null);
    }
  };

  // Contadores metricas
  const countPending = shipments.filter((s) => s.status === 'CREADO' || s.status === 'ACEPTADO').length;
  const countInTransit = shipments.filter((s) => s.status === 'EN_BODEGA' || s.status === 'EN_RUTA').length;
  const countDelivered = shipments.filter((s) => s.status === 'ENTREGADO').length;

  const getBadgeClass = (st) => {
    if (!st) return 'badge';
    const s = String(st).toLowerCase();
    return `badge badge-${s}`;
  };

  return (
    <div style={{ maxWidth: '1100px', margin: '0 auto' }}>
      {/* Metricas de Despacho */}
      <div className="grid-3" style={{ marginBottom: '1.25rem' }}>
        <div className="card" style={{ padding: '1rem', borderLeft: '4px solid #0284c7' }}>
          <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600 }}>
            Pendientes por Despachar
          </div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a', marginTop: '0.25rem' }}>
            {countPending}
          </div>
        </div>

        <div className="card" style={{ padding: '1rem', borderLeft: '4px solid #f59e0b' }}>
          <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600 }}>
            En Bodega / En Ruta
          </div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a', marginTop: '0.25rem' }}>
            {countInTransit}
          </div>
        </div>

        <div className="card" style={{ padding: '1rem', borderLeft: '4px solid #16a34a' }}>
          <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600 }}>
            Entregados
          </div>
          <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a', marginTop: '0.25rem' }}>
            {countDelivered}
          </div>
        </div>
      </div>

      <div className="card">
        <div className="card-header">
          <div>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 600, color: '#0f172a' }}>
              Consola Operativa de Despacho
            </h2>
            <p style={{ fontSize: '0.8125rem', color: '#64748b', marginTop: '0.2rem' }}>
              Actualice el estado del paquete en tiempo real para activar los eventos asincronos en RabbitMQ.
            </p>
          </div>
          <button
            onClick={fetchShipments}
            className="btn btn-outline"
            style={{ fontSize: '0.75rem', padding: '0.35rem 0.75rem' }}
          >
            Refrescar
          </button>
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

        {successMessage && (
          <div style={{
            backgroundColor: '#f0fdf4',
            border: '1px solid #86efac',
            color: '#166534',
            padding: '0.75rem 1rem',
            borderRadius: '0.375rem',
            fontSize: '0.8125rem',
            marginBottom: '1rem'
          }}>
            {successMessage}
          </div>
        )}

        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Tracking</th>
                <th>Destinatario</th>
                <th>Direccion de Entrega</th>
                <th>Estado Actual</th>
                <th>Nuevo Estado Operativo</th>
                <th>Accion</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="7" style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>
                    Cargando consola de despacho...
                  </td>
                </tr>
              ) : shipments.length === 0 ? (
                <tr>
                  <td colSpan="7" style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>
                    No hay despachos registrados para operar.
                  </td>
                </tr>
              ) : (
                shipments.map((s) => (
                  <tr key={s.id}>
                    <td style={{ fontWeight: 600 }}>{s.id}</td>
                    <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>
                      {s.trackingNumber || s.trackingCode}
                    </td>
                    <td>{s.recipientName}</td>
                    <td style={{ maxWidth: '200px', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
                      {s.recipientAddress}
                    </td>
                    <td>
                      <span className={getBadgeClass(s.status)}>
                        {s.status}
                      </span>
                    </td>
                    <td>
                      <select
                        value={pendingStatus[s.id] || s.status}
                        onChange={(e) => handleStatusChange(s.id, e.target.value)}
                        disabled={s.status === 'ENTREGADO' || s.status === 'CANCELADO'}
                        className="form-control"
                        style={{ fontSize: '0.8125rem', padding: '0.3rem 0.5rem', width: 'auto' }}
                      >
                        <option value="CREADO">CREADO</option>
                        <option value="ACEPTADO">ACEPTADO</option>
                        <option value="EN_BODEGA">EN_BODEGA</option>
                        <option value="EN_RUTA">EN_RUTA</option>
                        <option value="ENTREGADO">ENTREGADO</option>
                        <option value="CANCELADO">CANCELADO</option>
                      </select>
                    </td>
                    <td>
                      <button
                        onClick={() => handleSaveStatus(s.id)}
                        disabled={updatingId === s.id || (pendingStatus[s.id] === s.status) || s.status === 'ENTREGADO' || s.status === 'CANCELADO'}
                        className="btn btn-primary"
                        style={{ fontSize: '0.75rem', padding: '0.3rem 0.7rem' }}
                      >
                        {updatingId === s.id ? 'Guardando...' : 'Actualizar'}
                      </button>
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

export default DispatcherDispatchView;
