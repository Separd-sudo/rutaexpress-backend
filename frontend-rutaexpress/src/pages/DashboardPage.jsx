import React, { useState, useEffect } from 'react';
import { useAuth } from '../auth/AuthContext';
import bffApi from '../services/bffApi';

export const DashboardPage = () => {
  const { user, role } = useAuth();

  // Estados comunes
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [successMsg, setSuccessMsg] = useState(null);

  // Datos
  const [services, setServices] = useState([]);
  const [shipments, setShipments] = useState([]);
  const [selectedTrace, setSelectedTrace] = useState(null);
  const [loadingTrace, setLoadingTrace] = useState(false);

  // Formulario nuevo envio (Cliente/Despachador)
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [newShipment, setNewShipment] = useState({
    serviceId: '',
    senderName: '',
    senderAddress: '',
    senderPhone: '',
    recipientName: '',
    recipientAddress: '',
    recipientEmail: '',
    recipientPhone: '',
    weightKg: 1.0,
    distanceKm: 10.0,
    declaredValue: 20000.0,
    notes: ''
  });

  const loadData = async () => {
    setLoading(true);
    setError(null);
    try {
      const [svcData, shipData] = await Promise.all([
        bffApi.getCatalogServices().catch(() => []),
        bffApi.getShipments().catch(() => [])
      ]);
      setServices(svcData || []);
      setShipments(shipData || []);
      if (svcData && svcData.length > 0 && !newShipment.serviceId) {
        setNewShipment(prev => ({ ...prev, serviceId: svcData[0].id }));
      }
    } catch (err) {
      setError('Error al sincronizar con el backend (BFF). Verifique que los servicios esten activos.');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  // Acciones de Despachador: Cambiar estado
  const handleStatusChange = async (shipmentId, nextStatus) => {
    setError(null);
    setSuccessMsg(null);
    try {
      await bffApi.updateShipmentStatus(
        shipmentId,
        nextStatus,
        user?.email || 'operador@rutaexpress.cl',
        role || 'Despachador',
        `Transicion a ${nextStatus} ejecutada desde Portal Web`
      );
      setSuccessMsg(`Estado actualizado correctamente a ${nextStatus}.`);
      await loadData();
      if (selectedTrace?.shipment?.id === shipmentId) {
        handleViewTrace(shipmentId);
      }
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Error al cambiar estado';
      setError(msg);
    }
  };

  // Accion DELETE: Eliminar o cancelar envio (Cliente y Admin)
  const handleDeleteShipment = async (id, tracking) => {
    if (!window.confirm(`¿Está seguro de eliminar el envío ${tracking}? Esta acción ejecutará una petición HTTP DELETE hacia el BFF.`)) {
      return;
    }
    setError(null);
    setSuccessMsg(null);
    try {
      await bffApi.deleteShipment(id);
      setSuccessMsg(`Envío ${tracking} eliminado exitosamente (HTTP DELETE procesado por el BFF).`);
      if (selectedTrace?.shipment?.id === id) {
        setSelectedTrace(null);
      }
      await loadData();
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Error al eliminar el envío';
      setError(msg);
    }
  };

  // Ver agregacion completa y timeline de auditoria
  const handleViewTrace = async (shipmentId) => {
    setLoadingTrace(true);
    try {
      const trace = await bffApi.getFullTrace(shipmentId);
      setSelectedTrace(trace);
    } catch (err) {
      setError('No fue posible consultar el timeline de auditoria para este envio.');
    } finally {
      setLoadingTrace(false);
    }
  };

  // Enviar formulario de creacion
  const handleCreateSubmit = async (e) => {
    e.preventDefault();
    setError(null);
    setSuccessMsg(null);
    try {
      const payload = {
        ...newShipment,
        serviceId: Number(newShipment.serviceId),
        weightKg: Number(newShipment.weightKg),
        distanceKm: Number(newShipment.distanceKm),
        declaredValue: Number(newShipment.declaredValue),
        createdBy: user?.email || 'cliente@correo.cl'
      };
      const created = await bffApi.createShipment(payload);
      setSuccessMsg(`Envio creado exitosamente con tracking ${created.trackingNumber}.`);
      setShowCreateModal(false);
      await loadData();
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Error al crear envio';
      setError(msg);
    }
  };

  // Metricas para rol Admin
  const totalShipments = shipments.length;
  const inTransitCount = shipments.filter(s => s.status === 'EN_RUTA').length;
  const inWarehouseCount = shipments.filter(s => s.status === 'EN_BODEGA').length;
  const deliveredCount = shipments.filter(s => s.status === 'ENTREGADO').length;

  return (
    <div className="container" style={{ paddingBottom: '3rem' }}>
      {/* Encabezado */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', margin: '1.5rem 0', flexWrap: 'wrap', gap: '1rem' }}>
        <div>
          <h1 style={{ fontSize: '1.5rem', fontWeight: 700, color: '#0f172a' }}>
            Panel de Operaciones
          </h1>
          <p style={{ fontSize: '0.875rem', color: '#64748b' }}>
            Sesion activa: <strong>{user?.name}</strong> | Rol asignado: <span className="badge badge-creado">{role}</span>
          </p>
        </div>

        <div style={{ display: 'flex', gap: '0.75rem' }}>
          {(role === 'Cliente' || role === 'Despachador' || role === 'Admin') && (
            <button
              onClick={() => setShowCreateModal(true)}
              className="btn btn-accent"
              style={{ fontSize: '0.8125rem' }}
            >
              Nuevo Envio
            </button>
          )}
          <button
            onClick={loadData}
            className="btn btn-outline"
            style={{ fontSize: '0.8125rem' }}
          >
            Actualizar Datos
          </button>
        </div>
      </div>

      {/* Alertas */}
      {error && (
        <div style={{ backgroundColor: '#fee2e2', border: '1px solid #f87171', color: '#991b1b', padding: '0.875rem', borderRadius: '0.375rem', fontSize: '0.875rem', marginBottom: '1.5rem' }}>
          <strong>Alerta:</strong> {error}
        </div>
      )}
      {successMsg && (
        <div style={{ backgroundColor: '#dcfce7', border: '1px solid #86efac', color: '#166534', padding: '0.875rem', borderRadius: '0.375rem', fontSize: '0.875rem', marginBottom: '1.5rem' }}>
          {successMsg}
        </div>
      )}

      {/* VISTA SEGUN RUBRICA - ROL ADMIN: KPIS DE RED */}
      {role === 'Admin' && (
        <section style={{ marginBottom: '2rem' }}>
          <h2 style={{ fontSize: '1.125rem', fontWeight: 600, color: '#0f172a', marginBottom: '1rem' }}>
            KPIs de la Red Logistica
          </h2>
          <div className="grid-4">
            <div className="card" style={{ marginBottom: 0 }}>
              <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600 }}>Total de Envios</div>
              <div style={{ fontSize: '1.75rem', fontWeight: 700, color: '#0f172a', marginTop: '0.25rem' }}>{totalShipments}</div>
            </div>
            <div className="card" style={{ marginBottom: 0 }}>
              <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600 }}>En Bodega</div>
              <div style={{ fontSize: '1.75rem', fontWeight: 700, color: '#6d28d9', marginTop: '0.25rem' }}>{inWarehouseCount}</div>
            </div>
            <div className="card" style={{ marginBottom: 0 }}>
              <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600 }}>En Ruta (Activos)</div>
              <div style={{ fontSize: '1.75rem', fontWeight: 700, color: '#c2410c', marginTop: '0.25rem' }}>{inTransitCount}</div>
            </div>
            <div className="card" style={{ marginBottom: 0 }}>
              <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600 }}>Entregados</div>
              <div style={{ fontSize: '1.75rem', fontWeight: 700, color: '#15803d', marginTop: '0.25rem' }}>{deliveredCount}</div>
            </div>
          </div>

          <div className="card" style={{ marginTop: '1.5rem' }}>
            <h3 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>Capacidad de Flota por Servicio (Catalogo)</h3>
            <div className="table-wrapper">
              <table>
                <thead>
                  <tr>
                    <th>Codigo</th>
                    <th>Servicio</th>
                    <th>Tarifa Base</th>
                    <th>Por Km</th>
                    <th>Capacidad Maxima</th>
                    <th>Cupos Disponibles</th>
                    <th>Estado</th>
                  </tr>
                </thead>
                <tbody>
                  {services.map(s => (
                    <tr key={s.id}>
                      <td><code>{s.code}</code></td>
                      <td><strong>{s.name}</strong></td>
                      <td>${s.basePrice}</td>
                      <td>${s.pricePerKm}</td>
                      <td>{s.maxDailyCapacity} vehiculos/dia</td>
                      <td>
                        <span style={{ fontWeight: 700, color: s.availableCapacity <= 5 ? '#dc2626' : '#16a34a' }}>
                          {s.availableCapacity} disponibles
                        </span>
                      </td>
                      <td>
                        <span className={`badge ${s.active ? 'badge-entregado' : 'badge-cancelado'}`}>
                          {s.active ? 'Activo' : 'Inactivo'}
                        </span>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </section>
      )}

      {/* VISTA SEGUN RUBRICA - ROL DESPACHADOR: ENVIOS EN BODEGA Y EN RUTA */}
      {role === 'Despachador' && (
        <section style={{ marginBottom: '2rem' }}>
          <div className="grid-2" style={{ marginBottom: '1.5rem' }}>
            <div className="card" style={{ marginBottom: 0, borderLeft: '4px solid #6d28d9' }}>
              <div style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>ENVIOS EN BODEGA</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700, marginTop: '0.25rem' }}>{inWarehouseCount} pendientes de salida</div>
            </div>
            <div className="card" style={{ marginBottom: 0, borderLeft: '4px solid #c2410c' }}>
              <div style={{ fontSize: '0.75rem', color: '#64748b', fontWeight: 600 }}>ENVIOS EN RUTA</div>
              <div style={{ fontSize: '1.5rem', fontWeight: 700, marginTop: '0.25rem' }}>{inTransitCount} en transito de entrega</div>
            </div>
          </div>
        </section>
      )}

      {/* TABLA PRINCIPAL DE ENVIOS (ACCESIBLE PARA TODOS) */}
      <section className="card">
        <div className="card-header">
          <div>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 600, color: '#0f172a' }}>
              {role === 'Cliente' ? 'Mis Solicitudes de Envio' : 'Listado Maestro de Envios'}
            </h2>
            <p style={{ fontSize: '0.8125rem', color: '#64748b' }}>
              Gestion del ciclo de vida y trazabilidad en tiempo real a traves del BFF.
            </p>
          </div>
          <span style={{ fontSize: '0.8125rem', color: '#64748b' }}>
            Total: {shipments.length} registro(s)
          </span>
        </div>

        {loading ? (
          <div style={{ padding: '2rem', textAlign: 'center', color: '#64748b' }}>Cargando datos desde el BFF...</div>
        ) : shipments.length === 0 ? (
          <div style={{ padding: '2rem', textAlign: 'center', color: '#64748b' }}>No hay envios registrados en el sistema.</div>
        ) : (
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Tracking</th>
                  <th>Servicio</th>
                  <th>Remitente</th>
                  <th>Destinatario</th>
                  <th>Costo</th>
                  <th>Estado Actual</th>
                  <th>Acciones</th>
                </tr>
              </thead>
              <tbody>
                {shipments.map(s => {
                  const badgeClass = `badge-${s.status.toLowerCase()}`;
                  return (
                    <tr key={s.id}>
                      <td>
                        <strong><code>{s.trackingNumber}</code></strong>
                      </td>
                      <td>{s.serviceCode}</td>
                      <td>
                        <div>{s.senderName}</div>
                        <small style={{ color: '#64748b' }}>{s.senderAddress}</small>
                      </td>
                      <td>
                        <div>{s.recipientName}</div>
                        <small style={{ color: '#64748b' }}>{s.recipientAddress}</small>
                      </td>
                      <td><strong>${s.shippingCost}</strong></td>
                      <td>
                        <span className={`badge ${badgeClass}`}>{s.status}</span>
                      </td>
                      <td>
                        <div style={{ display: 'flex', gap: '0.375rem', flexWrap: 'wrap' }}>
                          {/* Transiciones operativas para Despachador y Admin */}
                          {(role === 'Despachador' || role === 'Admin') && (
                            <>
                              {s.status === 'CREADO' && (
                                <button
                                  onClick={() => handleStatusChange(s.id, 'ACEPTADO')}
                                  className="btn btn-outline"
                                  style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                                >
                                  Aceptar
                                </button>
                              )}
                              {s.status === 'ACEPTADO' && (
                                <button
                                  onClick={() => handleStatusChange(s.id, 'EN_BODEGA')}
                                  className="btn btn-outline"
                                  style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                                >
                                  A Bodega
                                </button>
                              )}
                              {s.status === 'EN_BODEGA' && (
                                <button
                                  onClick={() => handleStatusChange(s.id, 'EN_RUTA')}
                                  className="btn btn-outline"
                                  style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem', borderColor: '#fdba74', color: '#c2410c' }}
                                >
                                  Despachar
                                </button>
                              )}
                              {s.status === 'EN_RUTA' && (
                                <button
                                  onClick={() => handleStatusChange(s.id, 'ENTREGADO')}
                                  className="btn btn-outline"
                                  style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem', borderColor: '#86efac', color: '#15803d' }}
                                >
                                  Entregar
                                </button>
                              )}
                              {s.status !== 'ENTREGADO' && s.status !== 'CANCELADO' && (
                                <button
                                  onClick={() => handleStatusChange(s.id, 'CANCELADO')}
                                  className="btn btn-outline"
                                  style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem', color: '#b91c1c' }}
                                >
                                  Cancelar
                                </button>
                              )}
                            </>
                          )}

                          {/* Ver Timeline de Auditoria (Todos, especialmente Auditor) */}
                          <button
                            onClick={() => handleViewTrace(s.id)}
                            className="btn btn-outline"
                            style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem' }}
                          >
                            Timeline
                          </button>

                          {/* Accion DELETE para Cliente y Admin (envios en CREADO o CANCELADO) */}
                          {(role === 'Cliente' || role === 'Admin') && (s.status === 'CREADO' || s.status === 'CANCELADO') && (
                            <button
                              onClick={() => handleDeleteShipment(s.id, s.trackingNumber)}
                              className="btn btn-outline"
                              style={{ padding: '0.25rem 0.5rem', fontSize: '0.75rem', borderColor: '#fca5a5', color: '#dc2626' }}
                              title="Eliminar registro mediante HTTP DELETE hacia el BFF"
                            >
                              Eliminar
                            </button>
                          )}
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* PANEL / MODAL DE TRAZABILIDAD Y AUDITORIA (FULL TRACE DEL BFF) */}
      {selectedTrace && (
        <section className="card" style={{ borderColor: '#38bdf8', backgroundColor: '#f0f9ff' }}>
          <div className="card-header" style={{ borderColor: '#bae6fd' }}>
            <div>
              <h3 style={{ fontSize: '1.125rem', fontWeight: 600, color: '#0369a1' }}>
                Timeline de Auditoria e Integracion: Envio {selectedTrace.shipment?.trackingNumber}
              </h3>
              <p style={{ fontSize: '0.8125rem', color: '#0284c7' }}>
                Consulta unificada provista por el endpoint agregador del BFF (/api/bff/shipments/{'{id}'}/full-trace).
              </p>
            </div>
            <button
              onClick={() => setSelectedTrace(null)}
              className="btn btn-outline"
              style={{ fontSize: '0.75rem', padding: '0.25rem 0.5rem' }}
            >
              Cerrar Vista
            </button>
          </div>

          <div className="grid-2" style={{ marginBottom: '1.5rem' }}>
            <div style={{ backgroundColor: '#ffffff', padding: '1rem', borderRadius: '0.375rem', border: '1px solid #e0f2fe' }}>
              <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#64748b' }}>DATOS DEL ENVIO</div>
              <div style={{ fontSize: '0.875rem', marginTop: '0.5rem' }}>
                <div><strong>Destinatario:</strong> {selectedTrace.shipment?.recipientName} ({selectedTrace.shipment?.recipientAddress})</div>
                <div><strong>Estado Actual:</strong> <span className="badge badge-creado">{selectedTrace.shipment?.status}</span></div>
                <div><strong>Tarifa Total:</strong> ${selectedTrace.shipment?.shippingCost}</div>
              </div>
            </div>

            <div style={{ backgroundColor: '#ffffff', padding: '1rem', borderRadius: '0.375rem', border: '1px solid #e0f2fe' }}>
              <div style={{ fontSize: '0.75rem', fontWeight: 600, color: '#64748b' }}>SERVICIO ASOCIADO (CATALOGO)</div>
              <div style={{ fontSize: '0.875rem', marginTop: '0.5rem' }}>
                <div><strong>Nombre:</strong> {selectedTrace.catalogService?.name || 'Servicio General'}</div>
                <div><strong>Tarifa Base:</strong> ${selectedTrace.catalogService?.basePrice}</div>
                <div><strong>Capacidad Disponible:</strong> {selectedTrace.catalogService?.availableCapacity} vehiculos</div>
              </div>
            </div>
          </div>

          <h4 style={{ fontSize: '0.875rem', fontWeight: 600, color: '#0369a1', marginBottom: '0.75rem' }}>
            Historial Cronologico de Eventos (Auditoria Inmutable):
          </h4>

          {selectedTrace.timeline?.length === 0 ? (
            <p style={{ fontSize: '0.8125rem', color: '#64748b' }}>No hay eventos registrados para este envio.</p>
          ) : (
            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
              {selectedTrace.timeline.map((evt, idx) => (
                <div key={idx} style={{ backgroundColor: '#ffffff', border: '1px solid #e2e8f0', borderRadius: '0.375rem', padding: '0.75rem', fontSize: '0.8125rem' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', color: '#64748b', fontSize: '0.75rem', marginBottom: '0.25rem' }}>
                    <span><strong>{evt.eventType}</strong> - {evt.timestamp}</span>
                    <span>Ejecutado por: <strong>{evt.performedBy}</strong> ({evt.userRole})</span>
                  </div>
                  <div style={{ color: '#1e293b' }}>{evt.details}</div>
                </div>
              ))}
            </div>
          )}
        </section>
      )}

      {/* MODAL / FORMULARIO CREAR ENVIO */}
      {showCreateModal && (
        <div style={{
          position: 'fixed',
          top: 0, left: 0, right: 0, bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000,
          padding: '1rem'
        }}>
          <div className="card" style={{ maxWidth: '600px', width: '100%', maxHeight: '90vh', overflowY: 'auto' }}>
            <div className="card-header">
              <h3 style={{ fontSize: '1.125rem', fontWeight: 600 }}>Registrar Nueva Solicitud de Envio</h3>
              <button onClick={() => setShowCreateModal(false)} className="btn btn-outline" style={{ padding: '0.25rem 0.5rem' }}>X</button>
            </div>

            <form onSubmit={handleCreateSubmit}>
              <div className="form-group">
                <label className="form-label">Tipo de Servicio (Catalogo):</label>
                <select
                  className="form-control"
                  value={newShipment.serviceId}
                  onChange={e => setNewShipment({ ...newShipment, serviceId: e.target.value })}
                  required
                >
                  {services.map(s => (
                    <option key={s.id} value={s.id}>
                      {s.name} (${s.basePrice} base + ${s.pricePerKm}/km) - Cupos: {s.availableCapacity}
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid-2">
                <div className="form-group">
                  <label className="form-label">Remitente:</label>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="Nombre remitente"
                    value={newShipment.senderName}
                    onChange={e => setNewShipment({ ...newShipment, senderName: e.target.value })}
                    required
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Direccion Origen:</label>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="Av. Providencia 1234"
                    value={newShipment.senderAddress}
                    onChange={e => setNewShipment({ ...newShipment, senderAddress: e.target.value })}
                    required
                  />
                </div>
              </div>

              <div className="grid-2">
                <div className="form-group">
                  <label className="form-label">Destinatario:</label>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="Nombre destinatario"
                    value={newShipment.recipientName}
                    onChange={e => setNewShipment({ ...newShipment, recipientName: e.target.value })}
                    required
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Direccion Destino:</label>
                  <input
                    type="text"
                    className="form-control"
                    placeholder="Calle Valparaiso 567"
                    value={newShipment.recipientAddress}
                    onChange={e => setNewShipment({ ...newShipment, recipientAddress: e.target.value })}
                    required
                  />
                </div>
              </div>

              <div className="grid-3">
                <div className="form-group">
                  <label className="form-label">Peso (Kg):</label>
                  <input
                    type="number"
                    step="0.1"
                    className="form-control"
                    value={newShipment.weightKg}
                    onChange={e => setNewShipment({ ...newShipment, weightKg: e.target.value })}
                    required
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Distancia (Km):</label>
                  <input
                    type="number"
                    step="1"
                    className="form-control"
                    value={newShipment.distanceKm}
                    onChange={e => setNewShipment({ ...newShipment, distanceKm: e.target.value })}
                    required
                  />
                </div>
                <div className="form-group">
                  <label className="form-label">Valor Declarado ($):</label>
                  <input
                    type="number"
                    step="100"
                    className="form-control"
                    value={newShipment.declaredValue}
                    onChange={e => setNewShipment({ ...newShipment, declaredValue: e.target.value })}
                    required
                  />
                </div>
              </div>

              <div className="form-group">
                <label className="form-label">Notas de Despacho:</label>
                <input
                  type="text"
                  className="form-control"
                  placeholder="Instrucciones especiales para entrega"
                  value={newShipment.notes}
                  onChange={e => setNewShipment({ ...newShipment, notes: e.target.value })}
                />
              </div>

              <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem', marginTop: '1rem' }}>
                <button
                  type="button"
                  onClick={() => setShowCreateModal(false)}
                  className="btn btn-outline"
                >
                  Cancelar
                </button>
                <button type="submit" className="btn btn-accent">
                  Crear Solicitud de Envio
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default DashboardPage;