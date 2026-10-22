import React, { useState, useEffect } from 'react';
import bffApi from '../../services/bffApi';
import { useAuth } from '../../auth/AuthContext';

export const NewShipmentView = ({ onShipmentCreated }) => {
  const { user } = useAuth();
  const [catalog, setCatalog] = useState([]);
  const [loadingCatalog, setLoadingCatalog] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [successData, setSuccessData] = useState(null);
  const [errorMessage, setErrorMessage] = useState(null);

  const [formData, setFormData] = useState({
    senderName: user?.name || '',
    senderAddress: '',
    senderPhone: '',
    recipientName: '',
    recipientAddress: '',
    recipientEmail: '',
    recipientPhone: '',
    serviceId: '',
    weightKg: 1.5,
    distanceKm: 15,
    declaredValue: 10000,
    specialInstructions: ''
  });

  useEffect(() => {
    const fetchServices = async () => {
      try {
        const services = await bffApi.getCatalogServices(true);
        setCatalog(services || []);
        if (services && services.length > 0) {
          setFormData((prev) => ({ ...prev, serviceId: services[0].id }));
        }
      } catch (err) {
        console.error('Error al cargar catalogo de servicios:', err);
      } finally {
        setLoadingCatalog(false);
      }
    };
    fetchServices();
  }, []);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: name === 'weightKg' || name === 'distanceKm' || name === 'declaredValue'
        ? Number(value)
        : value
    }));
  };

  const selectedService = catalog.find((s) => String(s.id) === String(formData.serviceId));

  const calculateEstimate = () => {
    if (!selectedService) return 0;
    const base = Number(selectedService.basePrice) || 0;
    const perKm = Number(selectedService.pricePerKm) || 0;
    const distance = Number(formData.distanceKm) || 0;
    return base + (perKm * distance);
  };

  const handleSubmit = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setErrorMessage(null);
    setSuccessData(null);

    try {
      const payload = {
        ...formData,
        serviceId: Number(formData.serviceId)
      };
      const response = await bffApi.createShipment(payload);
      setSuccessData(response);
      if (onShipmentCreated) {
        onShipmentCreated(response);
      }
    } catch (err) {
      const msg = err.response?.data?.message || err.message || 'Error al registrar solicitud de envio';
      setErrorMessage(msg);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: '840px', margin: '0 auto' }}>
      <div className="card">
        <div className="card-header">
          <div>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 600, color: '#0f172a' }}>
              Solicitud de Nuevo Envio
            </h2>
            <p style={{ fontSize: '0.8125rem', color: '#64748b', marginTop: '0.2rem' }}>
              Complete el formulario para registrar un despacho en la red RutaExpress.
            </p>
          </div>
        </div>

        {errorMessage && (
          <div style={{
            backgroundColor: '#fee2e2',
            border: '1px solid #f87171',
            color: '#991b1b',
            padding: '0.75rem 1rem',
            borderRadius: '0.375rem',
            fontSize: '0.8125rem',
            marginBottom: '1rem'
          }}>
            {errorMessage}
          </div>
        )}

        {successData && (
          <div style={{
            backgroundColor: '#f0fdf4',
            border: '1px solid #86efac',
            color: '#166534',
            padding: '1rem',
            borderRadius: '0.375rem',
            fontSize: '0.875rem',
            marginBottom: '1.25rem'
          }}>
            <div style={{ fontWeight: 600, marginBottom: '0.35rem' }}>
              Solicitud Registrada Exitosamente
            </div>
            <div>Codigo de Seguimiento (Tracking): <strong>{successData.trackingNumber || successData.trackingCode}</strong></div>
            <div>ID del Envio: <strong>{successData.id}</strong></div>
            <div>Estado Inicial: <span className="badge badge-creado">CREADO</span></div>
          </div>
        )}

        <form onSubmit={handleSubmit}>
          {/* Seccion Remitente */}
          <div style={{ marginBottom: '1.25rem' }}>
            <h3 style={{ fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.025em' }}>
              1. Datos del Remitente (Origen)
            </h3>
            <div className="grid-3">
              <div className="form-group">
                <label className="form-label">Nombre Remitente *</label>
                <input
                  type="text"
                  name="senderName"
                  required
                  value={formData.senderName}
                  onChange={handleChange}
                  className="form-control"
                />
              </div>
              <div className="form-group">
                <label className="form-label">Direccion de Retiro *</label>
                <input
                  type="text"
                  name="senderAddress"
                  required
                  placeholder="Av. Providencia 1234"
                  value={formData.senderAddress}
                  onChange={handleChange}
                  className="form-control"
                />
              </div>
              <div className="form-group">
                <label className="form-label">Telefono Remitente</label>
                <input
                  type="text"
                  name="senderPhone"
                  placeholder="+56 9 1234 5678"
                  value={formData.senderPhone}
                  onChange={handleChange}
                  className="form-control"
                />
              </div>
            </div>
          </div>

          {/* Seccion Destinatario */}
          <div style={{ marginBottom: '1.25rem' }}>
            <h3 style={{ fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.025em' }}>
              2. Datos del Destinatario (Destino)
            </h3>
            <div className="grid-2">
              <div className="form-group">
                <label className="form-label">Nombre Destinatario *</label>
                <input
                  type="text"
                  name="recipientName"
                  required
                  placeholder="Empresa o Persona Receptora"
                  value={formData.recipientName}
                  onChange={handleChange}
                  className="form-control"
                />
              </div>
              <div className="form-group">
                <label className="form-label">Direccion de Entrega *</label>
                <input
                  type="text"
                  name="recipientAddress"
                  required
                  placeholder="Calle Comercio 456, Piso 3"
                  value={formData.recipientAddress}
                  onChange={handleChange}
                  className="form-control"
                />
              </div>
            </div>

            <div className="grid-2">
              <div className="form-group">
                <label className="form-label">Correo Electronico</label>
                <input
                  type="email"
                  name="recipientEmail"
                  placeholder="contacto@destino.cl"
                  value={formData.recipientEmail}
                  onChange={handleChange}
                  className="form-control"
                />
              </div>
              <div className="form-group">
                <label className="form-label">Telefono Destinatario</label>
                <input
                  type="text"
                  name="recipientPhone"
                  placeholder="+56 9 8765 4321"
                  value={formData.recipientPhone}
                  onChange={handleChange}
                  className="form-control"
                />
              </div>
            </div>
          </div>

          {/* Seccion Servicio y Dimensiones */}
          <div style={{ marginBottom: '1.25rem' }}>
            <h3 style={{ fontSize: '0.875rem', fontWeight: 600, color: '#334155', marginBottom: '0.75rem', textTransform: 'uppercase', letterSpacing: '0.025em' }}>
              3. Tipo de Servicio y Paquete
            </h3>
            <div className="grid-3">
              <div className="form-group">
                <label className="form-label">Servicio de Catalogo *</label>
                {loadingCatalog ? (
                  <div style={{ fontSize: '0.8125rem', color: '#64748b', padding: '0.5rem 0' }}>Cargando catalogo...</div>
                ) : (
                  <select
                    name="serviceId"
                    value={formData.serviceId}
                    onChange={handleChange}
                    className="form-control"
                    required
                  >
                    {catalog.map((srv) => (
                      <option key={srv.id} value={srv.id}>
                        {srv.name} ({srv.code}) - Base: ${Number(srv.basePrice).toLocaleString('es-CL')}
                      </option>
                    ))}
                  </select>
                )}
              </div>

              <div className="form-group">
                <label className="form-label">Peso Estimado (Kg) *</label>
                <input
                  type="number"
                  name="weightKg"
                  step="0.1"
                  min="0.1"
                  required
                  value={formData.weightKg}
                  onChange={handleChange}
                  className="form-control"
                />
              </div>

              <div className="form-group">
                <label className="form-label">Distancia Estimada (Km)</label>
                <input
                  type="number"
                  name="distanceKm"
                  step="1"
                  min="1"
                  value={formData.distanceKm}
                  onChange={handleChange}
                  className="form-control"
                />
              </div>
            </div>

            <div className="form-group">
              <label className="form-label">Instrucciones Especiales</label>
              <textarea
                name="specialInstructions"
                rows={2}
                placeholder="Horario de recepcion, edificio con conserjeria, etc."
                value={formData.specialInstructions}
                onChange={handleChange}
                className="form-control"
              />
            </div>
          </div>

          {/* Resumen de Tarifa */}
          <div style={{
            border: '1px solid #e2e8f0',
            backgroundColor: '#f8fafc',
            padding: '1rem',
            borderRadius: '0.375rem',
            marginBottom: '1.5rem',
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'center'
          }}>
            <div>
              <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600 }}>
                Tarifa Estimada de Despacho
              </div>
              <div style={{ fontSize: '0.8125rem', color: '#475569', marginTop: '0.2rem' }}>
                Servicio: {selectedService?.name || 'Seleccionado'} | {formData.weightKg} Kg | {formData.distanceKm} Km
              </div>
            </div>
            <div style={{ fontSize: '1.25rem', fontWeight: 700, color: '#0f172a' }}>
              ${calculateEstimate().toLocaleString('es-CL')} CLP
            </div>
          </div>

          {/* Boton de envio */}
          <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.75rem' }}>
            <button
              type="submit"
              disabled={submitting || loadingCatalog}
              className="btn btn-primary"
            >
              {submitting ? 'Procesando Solicitud...' : 'Confirmar y Generar Envio'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default NewShipmentView;
