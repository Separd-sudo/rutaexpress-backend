import React, { useState, useEffect } from 'react';
import bffApi from '../../services/bffApi';

export const CatalogAdminView = () => {
  const [services, setServices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [showAddForm, setShowAddForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [formData, setFormData] = useState({
    code: '',
    name: '',
    description: '',
    basePrice: 3500,
    pricePerKm: 250,
    maxDailyCapacity: 50
  });

  const fetchCatalog = async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await bffApi.getCatalogServices(false);
      setServices(data || []);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Error al obtener catalogo de tarifas');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchCatalog();
  }, []);

  const handleChange = (e) => {
    const { name, value } = e.target;
    setFormData((prev) => ({
      ...prev,
      [name]: name === 'basePrice' || name === 'pricePerKm' || name === 'maxDailyCapacity'
        ? Number(value)
        : value
    }));
  };

  const handleCreateService = async (e) => {
    e.preventDefault();
    setSubmitting(true);
    setError(null);
    try {
      await bffApi.createCatalogService(formData);
      setShowAddForm(false);
      setFormData({
        code: '',
        name: '',
        description: '',
        basePrice: 3500,
        pricePerKm: 250,
        maxDailyCapacity: 50
      });
      await fetchCatalog();
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Error al crear servicio tarifario');
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div style={{ maxWidth: '1100px', margin: '0 auto' }}>
      <div className="card">
        <div className="card-header">
          <div>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 600, color: '#0f172a' }}>
              Gestion de Catalogo y Tarifas
            </h2>
            <p style={{ fontSize: '0.8125rem', color: '#64748b', marginTop: '0.2rem' }}>
              Administracion de categorias de despacho, tarifas por distancia y capacidades de flota.
            </p>
          </div>
          <button
            onClick={() => setShowAddForm(!showAddForm)}
            className="btn btn-primary"
            style={{ fontSize: '0.75rem', padding: '0.35rem 0.75rem' }}
          >
            {showAddForm ? 'Cerrar Formulario' : '+ Nuevo Servicio'}
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

        {/* Formulario de Alta */}
        {showAddForm && (
          <form onSubmit={handleCreateService} style={{
            backgroundColor: '#f8fafc',
            border: '1px solid #e2e8f0',
            padding: '1.25rem',
            borderRadius: '0.375rem',
            marginBottom: '1.5rem'
          }}>
            <h3 style={{ fontSize: '0.875rem', fontWeight: 600, color: '#0f172a', marginBottom: '0.75rem', textTransform: 'uppercase' }}>
              Registrar Nueva Categoria Tarifaria
            </h3>
            <div className="grid-3">
              <div className="form-group">
                <label className="form-label">Codigo *</label>
                <input
                  type="text"
                  name="code"
                  required
                  placeholder="ej: CARGA_PESADA"
                  value={formData.code}
                  onChange={handleChange}
                  className="form-control"
                />
              </div>
              <div className="form-group">
                <label className="form-label">Nombre Comercial *</label>
                <input
                  type="text"
                  name="name"
                  required
                  placeholder="ej: Despacho Sobredimensionado"
                  value={formData.name}
                  onChange={handleChange}
                  className="form-control"
                />
              </div>
              <div className="form-group">
                <label className="form-label">Precio Base (CLP) *</label>
                <input
                  type="number"
                  name="basePrice"
                  required
                  value={formData.basePrice}
                  onChange={handleChange}
                  className="form-control"
                />
              </div>
            </div>

            <div className="grid-3">
              <div className="form-group">
                <label className="form-label">Tarifa por Km (CLP) *</label>
                <input
                  type="number"
                  name="pricePerKm"
                  required
                  value={formData.pricePerKm}
                  onChange={handleChange}
                  className="form-control"
                />
              </div>
              <div className="form-group">
                <label className="form-label">Capacidad Diaria (Cupos) *</label>
                <input
                  type="number"
                  name="maxDailyCapacity"
                  required
                  value={formData.maxDailyCapacity}
                  onChange={handleChange}
                  className="form-control"
                />
              </div>
              <div className="form-group">
                <label className="form-label">Descripcion</label>
                <input
                  type="text"
                  name="description"
                  placeholder="Detalle del servicio"
                  value={formData.description}
                  onChange={handleChange}
                  className="form-control"
                />
              </div>
            </div>

            <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '0.5rem', marginTop: '0.5rem' }}>
              <button type="button" onClick={() => setShowAddForm(false)} className="btn btn-outline" style={{ fontSize: '0.8125rem' }}>
                Cancelar
              </button>
              <button type="submit" disabled={submitting} className="btn btn-primary" style={{ fontSize: '0.8125rem' }}>
                {submitting ? 'Guardando...' : 'Guardar Servicio'}
              </button>
            </div>
          </form>
        )}

        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Codigo</th>
                <th>Nombre Servicio</th>
                <th>Precio Base</th>
                <th>Precio por Km</th>
                <th>Cupos (Disponibles / Total)</th>
                <th>Estado</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr>
                  <td colSpan="7" style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>
                    Cargando catalogo...
                  </td>
                </tr>
              ) : services.length === 0 ? (
                <tr>
                  <td colSpan="7" style={{ textAlign: 'center', padding: '2rem', color: '#64748b' }}>
                    No hay servicios tarifarios registrados en el catalogo.
                  </td>
                </tr>
              ) : (
                services.map((s) => (
                  <tr key={s.id}>
                    <td style={{ fontWeight: 600 }}>{s.id}</td>
                    <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{s.code}</td>
                    <td style={{ fontWeight: 500 }}>{s.name}</td>
                    <td>${Number(s.basePrice || 0).toLocaleString('es-CL')}</td>
                    <td>${Number(s.pricePerKm || 0).toLocaleString('es-CL')}</td>
                    <td>{s.availableCapacity ?? s.maxDailyCapacity} / {s.maxDailyCapacity}</td>
                    <td>
                      <span style={{
                        display: 'inline-block',
                        padding: '0.2rem 0.5rem',
                        fontSize: '0.75rem',
                        fontWeight: 600,
                        borderRadius: '3px',
                        backgroundColor: s.active !== false ? '#dcfce7' : '#fee2e2',
                        color: s.active !== false ? '#15803d' : '#b91c1c'
                      }}>
                        {s.active !== false ? 'ACTIVO' : 'INACTIVO'}
                      </span>
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

export default CatalogAdminView;
