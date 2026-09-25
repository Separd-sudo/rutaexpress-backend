import axios from 'axios';

const BFF_BASE_URL = import.meta.env.VITE_BFF_URL || 'http://localhost:8080';

const bffClient = axios.create({
  baseURL: BFF_BASE_URL,
  headers: {
    'Content-Type': 'application/json'
  },
  timeout: 10000
});

// Interceptor para inyectar token Bearer de Azure AD o desarrollo
bffClient.interceptors.request.use((config) => {
  const token = sessionStorage.getItem('rtx_token');
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
}, (error) => {
  return Promise.reject(error);
});

export const bffApi = {
  // Comprobacion de salud del BFF
  getHealth: async () => {
    const res = await bffClient.get('/api/bff/health');
    return res.data;
  },

  // Catalogo de servicios
  getCatalogServices: async (activeOnly = true) => {
    const res = await bffClient.get('/api/bff/catalog/services', {
      params: { activeOnly }
    });
    return res.data;
  },

  // Envios
  getShipments: async (status, from, to) => {
    const params = {};
    if (status) params.status = status;
    if (from) params.from = from;
    if (to) params.to = to;

    const res = await bffClient.get('/api/bff/shipments', { params });
    return res.data;
  },

  getShipmentById: async (id) => {
    const res = await bffClient.get(`/api/bff/shipments/${id}`);
    return res.data;
  },

  getShipmentByTracking: async (trackingNumber) => {
    const res = await bffClient.get(`/api/bff/shipments/tracking/${trackingNumber}`);
    return res.data;
  },

  createShipment: async (payload) => {
    const res = await bffClient.post('/api/bff/shipments', payload);
    return res.data;
  },

  updateShipmentStatus: async (id, status, performedBy, userRole, note) => {
    const res = await bffClient.put(`/api/bff/shipments/${id}/status`, {
      status,
      performedBy,
      userRole,
      note
    });
    return res.data;
  },

  deleteShipment: async (id) => {
    const res = await bffClient.delete(`/api/bff/shipments/${id}`);
    return res.data;
  },

  deleteCatalogService: async (id) => {
    const res = await bffClient.delete(`/api/bff/catalog/services/${id}`);
    return res.data;
  },

  // Endpoint de agregacion optimizado
  getFullTrace: async (id) => {
    const res = await bffClient.get(`/api/bff/shipments/${id}/full-trace`);
    return res.data;
  },

  // Auditoria
  getShipmentTimeline: async (shipmentId) => {
    const res = await bffClient.get(`/api/bff/audit/shipments/${shipmentId}`);
    return res.data;
  },

  searchAuditEvents: async (user, eventType, from, to) => {
    const params = {};
    if (user) params.user = user;
    if (eventType) params.eventType = eventType;
    if (from) params.from = from;
    if (to) params.to = to;

    const res = await bffClient.get('/api/bff/audit', { params });
    return res.data;
  }
};

export default bffApi;