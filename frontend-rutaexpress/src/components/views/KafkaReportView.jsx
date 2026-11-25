import React, { useCallback, useEffect, useState } from 'react';
import bffApi from '../../services/bffApi';

/**
 * Vista "Streaming Kafka": muestra en tiempo cuasi real lo que consumen los microservicios
 * desde los topics de Kafka.
 *  - shipments.events -> ms-rutaexpress-report (grupo report-svc): KPIs y ultimos eventos.
 *  - audit.timeline   -> ms-rutaexpress-report (grupo report-timeline-svc): actividad por usuario.
 * Con la actualizacion automatica activada, cada nuevo envio o cambio de estado aparece
 * aqui sin recargar la pagina.
 */

const REFRESH_MS = 5000;

const RANGES = [
  { value: 'last1h', label: 'Ultima hora' },
  { value: 'last24h', label: 'Ultimas 24 horas' },
  { value: 'last7d', label: 'Ultimos 7 dias' }
];

// Tarjeta de indicador numerico
const KpiCard = ({ label, value, hint }) => (
  <div style={{ border: '1px solid #e2e8f0', borderRadius: '0.375rem', padding: '1rem', backgroundColor: '#f8fafc' }}>
    <div style={{ fontSize: '0.7rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600 }}>{label}</div>
    <div style={{ fontSize: '1.6rem', fontWeight: 700, color: '#0f172a', marginTop: '0.25rem' }}>{value ?? '-'}</div>
    {hint && <div style={{ fontSize: '0.75rem', color: '#94a3b8', marginTop: '0.15rem' }}>{hint}</div>}
  </div>
);

// Etiqueta que indica de que topic proviene la informacion
const TopicTag = ({ topic, group }) => (
  <span style={{
    fontSize: '0.7rem', fontFamily: 'monospace', padding: '0.15rem 0.5rem', borderRadius: '3px',
    backgroundColor: '#eef2ff', color: '#3730a3', border: '1px solid #c7d2fe', whiteSpace: 'nowrap'
  }}>
    {topic}{group ? ` · grupo ${group}` : ''}
  </span>
);

/**
 * Grafico de barras "Envios creados por hora" (requerimiento del panel de operaciones del caso).
 * Una sola serie, un solo color; cada barra muestra hora y cantidad al pasar el mouse.
 */
const ShipmentsPerHourChart = ({ series }) => {
  const [hover, setHover] = useState(null);
  if (!series || series.length === 0) {
    return <div style={{ fontSize: '0.8125rem', color: '#94a3b8' }}>Sin datos</div>;
  }
  const max = Math.max(1, ...series.map((p) => p.count));
  const hourLabel = (h) => h.slice(11, 16);

  return (
    <div>
      <div style={{ position: 'relative', height: '140px', display: 'flex', alignItems: 'flex-end', gap: '2px', borderBottom: '1px solid #cbd5e1' }}>
        {series.map((p, i) => (
          <div
            key={p.hour}
            onMouseEnter={() => setHover(i)}
            onMouseLeave={() => setHover(null)}
            style={{ flex: 1, height: '100%', display: 'flex', alignItems: 'flex-end', cursor: 'default' }}
          >
            <div style={{
              width: '100%',
              height: p.count > 0 ? `${Math.max(4, (p.count / max) * 100)}%` : '0',
              backgroundColor: hover === i ? '#1e40af' : '#3b82f6',
              borderRadius: '4px 4px 0 0'
            }} />
          </div>
        ))}
        {hover !== null && (
          <div style={{
            position: 'absolute', top: 0, left: `${((hover + 0.5) / series.length) * 100}%`, transform: 'translateX(-50%)',
            backgroundColor: '#0f172a', color: '#ffffff', fontSize: '0.75rem', padding: '0.25rem 0.5rem', borderRadius: '4px', whiteSpace: 'nowrap', pointerEvents: 'none'
          }}>
            {series[hover].hour.replace('T', ' ')} · {series[hover].count} envios
          </div>
        )}
      </div>
      <div style={{ display: 'flex', justifyContent: 'space-between', fontSize: '0.7rem', color: '#64748b', marginTop: '0.25rem' }}>
        <span>{hourLabel(series[0].hour)}</span>
        <span>Maximo: {max} envios/hora</span>
        <span>{hourLabel(series[series.length - 1].hour)}</span>
      </div>
    </div>
  );
};

const formatTime = (value) => {
  if (!value) return '-';
  const d = new Date(value);
  return isNaN(d.getTime()) ? value : d.toLocaleString();
};

export const KafkaReportView = () => {
  const [range, setRange] = useState('last24h');
  const [autoRefresh, setAutoRefresh] = useState(true);
  const [kpis, setKpis] = useState(null);
  const [topServices, setTopServices] = useState([]);
  const [recentEvents, setRecentEvents] = useState([]);
  const [activity, setActivity] = useState(null);
  const [lastUpdate, setLastUpdate] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // Consulta en paralelo los 4 endpoints de reporteria
  const loadData = useCallback(async () => {
    setLoading(true);
    try {
      const [k, top, recent, act] = await Promise.all([
        bffApi.getReportKpis(range),
        bffApi.getReportTopServices(range, 5),
        bffApi.getReportRecentEvents(15),
        bffApi.getReportActivity(range, 10)
      ]);
      setKpis(k);
      setTopServices(top || []);
      setRecentEvents(recent || []);
      setActivity(act);
      setLastUpdate(new Date());
      setError(null);
    } catch (err) {
      setError(err.response?.data?.error || err.response?.data?.message || err.message || 'Error al consultar reporteria');
    } finally {
      setLoading(false);
    }
  }, [range]);

  // Carga inicial y cada vez que cambia el rango
  useEffect(() => {
    loadData();
  }, [loadData]);

  // Actualizacion automatica para ver los eventos llegar en tiempo cuasi real
  useEffect(() => {
    if (!autoRefresh) return undefined;
    const id = setInterval(loadData, REFRESH_MS);
    return () => clearInterval(id);
  }, [autoRefresh, loadData]);

  return (
    <div style={{ maxWidth: '1100px', margin: '0 auto' }}>
      <div className="card">
        <div className="card-header" style={{ flexWrap: 'wrap', gap: '0.75rem' }}>
          <div>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 600, color: '#0f172a' }}>
              Streaming Kafka: Reporteria y Auditoria en Tiempo Real
            </h2>
            <p style={{ fontSize: '0.8125rem', color: '#64748b', marginTop: '0.2rem' }}>
              Datos calculados por los consumidores Kafka a partir de los topics <strong>shipments.events</strong> y <strong>audit.timeline</strong>.
            </p>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', flexWrap: 'wrap' }}>
            <select value={range} onChange={(e) => setRange(e.target.value)} className="form-control" style={{ width: 'auto' }}>
              {RANGES.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
            </select>
            <label style={{ fontSize: '0.8125rem', color: '#334155', display: 'flex', alignItems: 'center', gap: '0.35rem' }}>
              <input type="checkbox" checked={autoRefresh} onChange={(e) => setAutoRefresh(e.target.checked)} />
              Auto (5 s)
            </label>
            <button onClick={loadData} disabled={loading} className="btn btn-outline" style={{ fontSize: '0.8125rem' }}>
              {loading ? 'Actualizando...' : 'Actualizar'}
            </button>
          </div>
        </div>

        <div style={{ fontSize: '0.75rem', color: '#64748b', marginBottom: '1rem' }}>
          {autoRefresh && <span style={{ color: '#15803d', fontWeight: 600 }}>● EN VIVO </span>}
          Ultima actualizacion: {lastUpdate ? lastUpdate.toLocaleTimeString() : '-'}
        </div>

        {error && (
          <div style={{ backgroundColor: '#fee2e2', border: '1px solid #f87171', color: '#991b1b', padding: '0.75rem', borderRadius: '0.375rem', fontSize: '0.8125rem', marginBottom: '1rem' }}>
            {error}
          </div>
        )}

        {/* KPIs desde shipments.events */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
          <h3 style={{ fontSize: '0.875rem', fontWeight: 600, color: '#334155', textTransform: 'uppercase' }}>Indicadores de envios</h3>
          <TopicTag topic="shipments.events" group="report-svc" />
        </div>
        <div className="grid-4" style={{ marginBottom: '1rem' }}>
          <KpiCard label="Eventos procesados" value={kpis?.totalEvents} hint="en el rango" />
          <KpiCard label="Envios creados" value={kpis?.shipmentsCreated} />
          <KpiCard label="Envios entregados" value={kpis?.shipmentsDelivered} hint={`Lead time prom.: ${kpis?.leadTime?.avgMinutes ?? 0} min`} />
          <KpiCard label="Envios activos" value={kpis?.activeTotal} hint="foto actual" />
        </div>

        {/* Envios por hora (serie calculada por report-svc) */}
        <div style={{ border: '1px solid #e2e8f0', borderRadius: '0.375rem', padding: '1rem', marginBottom: '1rem' }}>
          <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600, marginBottom: '0.75rem' }}>Envios creados por hora</div>
          <ShipmentsPerHourChart series={kpis?.shipmentsPerHour} />
        </div>

        <div className="grid-2" style={{ marginBottom: '1.5rem' }}>
          {/* Estados activos */}
          <div style={{ border: '1px solid #e2e8f0', borderRadius: '0.375rem', padding: '1rem' }}>
            <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600, marginBottom: '0.5rem' }}>Envios activos por estado</div>
            {kpis?.activeByStatus ? Object.entries(kpis.activeByStatus).map(([status, count]) => (
              <div key={status} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.35rem 0', borderBottom: '1px solid #f1f5f9', fontSize: '0.8125rem' }}>
                <span className={`badge badge-${status.toLowerCase()}`}>{status}</span>
                <strong>{count}</strong>
              </div>
            )) : <div style={{ fontSize: '0.8125rem', color: '#94a3b8' }}>Sin datos</div>}
          </div>

          {/* Servicios mas usados */}
          <div style={{ border: '1px solid #e2e8f0', borderRadius: '0.375rem', padding: '1rem' }}>
            <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600, marginBottom: '0.5rem' }}>Servicios mas usados</div>
            {topServices.length > 0 ? topServices.map((s) => (
              <div key={`${s.serviceCode}-${s.serviceId}`} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.35rem 0', borderBottom: '1px solid #f1f5f9', fontSize: '0.8125rem' }}>
                <span style={{ fontFamily: 'monospace' }}>{s.serviceCode}</span>
                <strong>{s.shipments} envios</strong>
              </div>
            )) : <div style={{ fontSize: '0.8125rem', color: '#94a3b8' }}>Sin envios en el rango</div>}
          </div>
        </div>

        {/* Ultimos eventos consumidos de shipments.events */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
          <h3 style={{ fontSize: '0.875rem', fontWeight: 600, color: '#334155', textTransform: 'uppercase' }}>Ultimos eventos consumidos</h3>
          <TopicTag topic="shipments.events" group="report-svc" />
        </div>
        <div className="table-wrapper" style={{ marginBottom: '1.5rem' }}>
          <table>
            <thead>
              <tr>
                <th>Hora</th>
                <th>Tracking (key)</th>
                <th>Evento</th>
                <th>Estado</th>
                <th>Servicio</th>
                <th>Particion</th>
                <th>Offset</th>
              </tr>
            </thead>
            <tbody>
              {recentEvents.length === 0 && (
                <tr><td colSpan={7} style={{ color: '#94a3b8', textAlign: 'center' }}>Aun no hay eventos. Crea un envio para verlo aparecer aqui.</td></tr>
              )}
              {recentEvents.map((e) => (
                <tr key={e.eventId}>
                  <td>{formatTime(e.eventTime)}</td>
                  <td style={{ fontFamily: 'monospace' }}>{e.trackingNumber}</td>
                  <td>{e.eventType}</td>
                  <td>{e.newStatus ? <span className={`badge badge-${e.newStatus.toLowerCase()}`}>{e.newStatus}</span> : '-'}</td>
                  <td>{e.serviceCode || '-'}</td>
                  <td style={{ fontFamily: 'monospace' }}>{e.partition}</td>
                  <td style={{ fontFamily: 'monospace' }}>{e.offset}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {/* Actividad por usuario desde audit.timeline */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.5rem' }}>
          <h3 style={{ fontSize: '0.875rem', fontWeight: 600, color: '#334155', textTransform: 'uppercase' }}>
            Auditoria: actividad por usuario ({activity?.totalActions ?? 0} acciones)
          </h3>
          <TopicTag topic="audit.timeline" group="report-timeline-svc" />
        </div>
        <div className="grid-2" style={{ marginBottom: '1rem' }}>
          <div style={{ border: '1px solid #e2e8f0', borderRadius: '0.375rem', padding: '1rem' }}>
            <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600, marginBottom: '0.5rem' }}>Usuarios mas activos</div>
            {activity?.byUser?.length > 0 ? activity.byUser.map((u) => (
              <div key={`${u.who}-${u.role}`} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.35rem 0', borderBottom: '1px solid #f1f5f9', fontSize: '0.8125rem' }}>
                <span>{u.who} <span style={{ color: '#94a3b8' }}>({u.role || 'sin rol'})</span></span>
                <strong>{u.actions}</strong>
              </div>
            )) : <div style={{ fontSize: '0.8125rem', color: '#94a3b8' }}>Sin actividad en el rango</div>}
          </div>
          <div style={{ border: '1px solid #e2e8f0', borderRadius: '0.375rem', padding: '1rem' }}>
            <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600, marginBottom: '0.5rem' }}>Acciones por tipo</div>
            {activity?.byAction && Object.keys(activity.byAction).length > 0 ? Object.entries(activity.byAction).map(([action, count]) => (
              <div key={action} style={{ display: 'flex', justifyContent: 'space-between', padding: '0.35rem 0', borderBottom: '1px solid #f1f5f9', fontSize: '0.8125rem' }}>
                <span style={{ fontFamily: 'monospace' }}>{action}</span>
                <strong>{count}</strong>
              </div>
            )) : <div style={{ fontSize: '0.8125rem', color: '#94a3b8' }}>Sin acciones en el rango</div>}
          </div>
        </div>

        <div className="table-wrapper">
          <table>
            <thead>
              <tr>
                <th>Hora</th>
                <th>Quien</th>
                <th>Rol</th>
                <th>Accion</th>
                <th>Tracking</th>
                <th>Origen</th>
                <th>Particion</th>
                <th>Offset</th>
              </tr>
            </thead>
            <tbody>
              {(!activity?.recent || activity.recent.length === 0) && (
                <tr><td colSpan={8} style={{ color: '#94a3b8', textAlign: 'center' }}>Sin acciones registradas</td></tr>
              )}
              {activity?.recent?.map((a) => (
                <tr key={a.eventId}>
                  <td>{formatTime(a.eventTime)}</td>
                  <td>{a.who}</td>
                  <td>{a.role || '-'}</td>
                  <td style={{ fontFamily: 'monospace', fontSize: '0.8rem' }}>{a.action}</td>
                  <td style={{ fontFamily: 'monospace' }}>{a.trackingNumber || '-'}</td>
                  <td>{a.from || '-'}</td>
                  <td style={{ fontFamily: 'monospace' }}>{a.partition}</td>
                  <td style={{ fontFamily: 'monospace' }}>{a.offset}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
};

export default KafkaReportView;
