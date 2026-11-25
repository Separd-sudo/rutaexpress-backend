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

  createCatalogService: async (payload) => {
    const res = await bffClient.post('/api/bff/catalog/services', payload);
    return res.data;
  },

  deleteCatalogService: async (id) => {
    const res = await bffClient.delete(`/api/bff/catalog/services/${id}`);
    return res.data;
  },

  simulateDlq: async (payload = {}) => {
    const res = await bffClient.post('/api/bff/shipments/demo/dlq', payload);
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
  },

  // ---------------- Reporteria (consumidores Kafka de ms-rutaexpress-report) ----------------

  // KPIs calculados desde el topic shipments.events
  getReportKpis: async (range = 'last24h') => {
    const res = await bffClient.get('/api/bff/report/kpis', { params: { range } });
    return res.data;
  },

  // Servicios de envio mas usados
  getReportTopServices: async (range = 'last7d', limit = 5) => {
    const res = await bffClient.get('/api/bff/report/top-services', { params: { range, limit } });
    return res.data;
  },

  // Ultimos eventos consumidos de shipments.events (incluye particion y offset)
  getReportRecentEvents: async (limit = 20) => {
    const res = await bffClient.get('/api/bff/report/events/recent', { params: { limit } });
    return res.data;
  },

  // Actividad por usuario consumida desde el topic audit.timeline
  getReportActivity: async (range = 'last24h', limit = 10) => {
    const res = await bffClient.get('/api/bff/report/activity', { params: { range, limit } });
    return res.data;
  },

  // ---------------- Administracion Kafka (ms-rutaexpress-kafka) ----------------

  getKafkaCluster: async () => {
    const res = await bffClient.get('/api/bff/kafka/cluster');
    return res.data;
  },

  getKafkaTopics: async () => {
    const res = await bffClient.get('/api/bff/kafka/topics');
    return res.data;
  },

  getKafkaTopic: async (topic) => {
    const res = await bffClient.get(`/api/bff/kafka/topics/${encodeURIComponent(topic)}`);
    return res.data;
  },

  createKafkaTopic: async (payload) => {
    const res = await bffClient.post('/api/bff/kafka/topics', payload);
    return res.data;
  },

  // set: { clave: valor } para cambiar; reset: [claves] para volver al valor por defecto
  updateKafkaTopicConfigs: async (topic, set = {}, reset = []) => {
    const res = await bffClient.put(`/api/bff/kafka/topics/${encodeURIComponent(topic)}/configs`, { set, reset });
    return res.data;
  },

  increaseKafkaPartitions: async (topic, totalCount) => {
    const res = await bffClient.post(`/api/bff/kafka/topics/${encodeURIComponent(topic)}/partitions`, { totalCount });
    return res.data;
  },

  deleteKafkaTopic: async (topic, force = false) => {
    const res = await bffClient.delete(`/api/bff/kafka/topics/${encodeURIComponent(topic)}`, { params: { force } });
    return res.data;
  },

  getKafkaConsumerGroups: async () => {
    const res = await bffClient.get('/api/bff/kafka/consumer-groups');
    return res.data;
  },

  getKafkaConsumerLag: async (groupId) => {
    const res = await bffClient.get(`/api/bff/kafka/consumer-groups/${encodeURIComponent(groupId)}/lag`);
    return res.data;
  },

  getKafkaDltMessages: async (dltTopic, limit = 20) => {
    const res = await bffClient.get(`/api/bff/kafka/dlt/${encodeURIComponent(dltTopic)}/messages`, { params: { limit } });
    return res.data;
  },

  reprocessKafkaDlt: async (dltTopic, partition, offset) => {
    const res = await bffClient.post(`/api/bff/kafka/dlt/${encodeURIComponent(dltTopic)}/reprocess`, null, {
      params: { partition, offset }
    });
    return res.data;
  },

  // Publica un evento de prueba con key (misma key = misma particion)
  publishKafkaTestEvent: async (key) => {
    const res = await bffClient.post('/api/bff/kafka/lab/publish', null, { params: { key } });
    return res.data;
  },

  // tipo: transient-failure | poison-pill | invalid-payload
  simulateKafkaError: async (tipo) => {
    const res = await bffClient.post(`/api/bff/kafka/lab/simulate/${tipo}`);
    return res.data;
  }
};

export default bffApi;