import React, { useCallback, useEffect, useState } from 'react';
import bffApi from '../../services/bffApi';

/**
 * Vista "Kafka Admin": consola de administracion del cluster Kafka a traves de ms-rutaexpress-kafka.
 *  - Cluster: brokers y controller.
 *  - Topics: listar, ver detalle (lider / replicas / ISR / offsets), crear, modificar retencion,
 *    aumentar particiones y eliminar.
 *  - Consumer groups: estado, miembros y lag por particion.
 *  - Laboratorio: publicar con key, simular errores y revisar / reprocesar las DLT.
 */

const DLT_TOPICS = [
  'shipments.events.audit.DLT',
  'shipments.events.report.DLT',
  'audit.timeline.report.DLT'
];

const HOUR_MS = 3600000;

// Extrae el mensaje de error que devuelve el backend
const errorText = (err) =>
  err.response?.data?.error || err.response?.data?.message || err.message || 'Error inesperado';

const sectionTitle = { fontSize: '0.875rem', fontWeight: 600, color: '#334155', textTransform: 'uppercase', marginBottom: '0.75rem' };
const box = { border: '1px solid #e2e8f0', borderRadius: '0.375rem', padding: '1rem', backgroundColor: '#ffffff', marginBottom: '1.25rem' };
const mono = { fontFamily: 'monospace' };

// Consola negra donde se muestra la respuesta JSON de cada accion
const ResultConsole = ({ result }) => {
  if (!result) return null;
  const ok = result.status === 'ok';
  return (
    <div style={{ marginTop: '0.75rem', border: '1px solid #374151', borderRadius: '4px', backgroundColor: '#111827', overflow: 'hidden' }}>
      <div style={{ padding: '0.4rem 0.75rem', backgroundColor: '#1f2937', fontSize: '0.7rem', color: ok ? '#86efac' : '#fca5a5', display: 'flex', justifyContent: 'space-between' }}>
        <span>{result.title}</span>
        <span>{ok ? 'OK' : 'ERROR'}</span>
      </div>
      <pre style={{ margin: 0, padding: '0.75rem', fontSize: '0.75rem', color: '#e5e7eb', maxHeight: '260px', overflow: 'auto', whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
        {typeof result.data === 'string' ? result.data : JSON.stringify(result.data, null, 2)}
      </pre>
    </div>
  );
};

export const KafkaAdminView = () => {
  const [cluster, setCluster] = useState(null);
  const [topics, setTopics] = useState([]);
  const [groups, setGroups] = useState([]);
  const [selectedTopic, setSelectedTopic] = useState(null);
  const [topicDetail, setTopicDetail] = useState(null);
  const [groupLag, setGroupLag] = useState(null);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(null);
  const [result, setResult] = useState(null);
  const [busy, setBusy] = useState(false);

  // Formularios
  const [newTopic, setNewTopic] = useState({ name: '', partitions: 3, replicationFactor: 3, retentionHours: 24, cleanupPolicy: 'delete' });
  const [retentionHours, setRetentionHours] = useState('');
  const [partitionCount, setPartitionCount] = useState('');
  const [publishKey, setPublishKey] = useState('RTX-DEMO-001');
  const [dltTopic, setDltTopic] = useState(DLT_TOPICS[0]);
  const [dltMessages, setDltMessages] = useState(null);

  // Carga cluster, topics y consumer groups
  const loadOverview = useCallback(async () => {
    setLoading(true);
    try {
      const [c, t, g] = await Promise.all([
        bffApi.getKafkaCluster(),
        bffApi.getKafkaTopics(),
        bffApi.getKafkaConsumerGroups()
      ]);
      setCluster(c);
      setTopics(t || []);
      setGroups(g || []);
      setLoadError(null);
    } catch (err) {
      setLoadError(errorText(err));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadOverview();
  }, [loadOverview]);

  // Ejecuta una accion, muestra su resultado y refresca la vista general
  const runAction = async (title, action, afterSuccess) => {
    setBusy(true);
    setResult(null);
    try {
      const data = await action();
      setResult({ status: 'ok', title, data });
      if (afterSuccess) await afterSuccess(data);
      await loadOverview();
    } catch (err) {
      setResult({ status: 'error', title, data: errorText(err) });
    } finally {
      setBusy(false);
    }
  };

  const openTopic = async (name) => {
    setSelectedTopic(name);
    setTopicDetail(null);
    setRetentionHours('');
    setPartitionCount('');
    try {
      setTopicDetail(await bffApi.getKafkaTopic(name));
    } catch (err) {
      setResult({ status: 'error', title: `Detalle de ${name}`, data: errorText(err) });
    }
  };

  const openGroupLag = async (groupId) => {
    try {
      setGroupLag(await bffApi.getKafkaConsumerLag(groupId));
    } catch (err) {
      setResult({ status: 'error', title: `Lag de ${groupId}`, data: errorText(err) });
    }
  };

  // ---------------- Acciones sobre topics ----------------

  const handleCreateTopic = (e) => {
    e.preventDefault();
    const payload = {
      name: newTopic.name.trim(),
      partitions: Number(newTopic.partitions),
      replicationFactor: Number(newTopic.replicationFactor),
      configs: {
        'retention.ms': String(Number(newTopic.retentionHours) * HOUR_MS),
        'cleanup.policy': newTopic.cleanupPolicy
      }
    };
    runAction(`Crear topic ${payload.name}`, () => bffApi.createKafkaTopic(payload), () => openTopic(payload.name));
  };

  const handleUpdateRetention = () => {
    const ms = String(Number(retentionHours) * HOUR_MS);
    runAction(`Modificar retencion de ${selectedTopic}`,
      () => bffApi.updateKafkaTopicConfigs(selectedTopic, { 'retention.ms': ms }),
      (data) => setTopicDetail(data));
  };

  const handleIncreasePartitions = () => {
    runAction(`Aumentar particiones de ${selectedTopic}`,
      () => bffApi.increaseKafkaPartitions(selectedTopic, Number(partitionCount)),
      (data) => setTopicDetail(data));
  };

  const handleDeleteTopic = () => {
    if (!window.confirm(`¿Eliminar el topic "${selectedTopic}"? Se perderan sus mensajes.`)) return;
    const name = selectedTopic;
    runAction(`Eliminar topic ${name}`, () => bffApi.deleteKafkaTopic(name), () => {
      setSelectedTopic(null);
      setTopicDetail(null);
    });
  };

  // ---------------- Laboratorio ----------------

  const handlePublish = () => {
    runAction(`Publicar evento con key ${publishKey}`, () => bffApi.publishKafkaTestEvent(publishKey));
  };

  const handleSimulate = (tipo) => {
    runAction(`Simulacion: ${tipo}`, () => bffApi.simulateKafkaError(tipo));
  };

  const handleLoadDlt = async () => {
    setDltMessages(null);
    try {
      setDltMessages(await bffApi.getKafkaDltMessages(dltTopic, 20));
    } catch (err) {
      setResult({ status: 'error', title: `Leer ${dltTopic}`, data: errorText(err) });
    }
  };

  const handleReprocess = (m) => {
    runAction(`Reprocesar ${dltTopic} p${m.partition}/o${m.offset}`,
      () => bffApi.reprocessKafkaDlt(dltTopic, m.partition, m.offset));
  };

  return (
    <div style={{ maxWidth: '1100px', margin: '0 auto' }}>
      <div className="card">
        <div className="card-header">
          <div>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 600, color: '#0f172a' }}>
              Administracion del Cluster Kafka
            </h2>
            <p style={{ fontSize: '0.8125rem', color: '#64748b', marginTop: '0.2rem' }}>
              Gestion de brokers, topics y consumer groups mediante el microservicio ms-rutaexpress-kafka.
            </p>
          </div>
          <button onClick={loadOverview} disabled={loading} className="btn btn-outline" style={{ fontSize: '0.8125rem' }}>
            {loading ? 'Cargando...' : 'Actualizar'}
          </button>
        </div>

        {loadError && (
          <div style={{ backgroundColor: '#fee2e2', border: '1px solid #f87171', color: '#991b1b', padding: '0.75rem', borderRadius: '0.375rem', fontSize: '0.8125rem', marginBottom: '1rem' }}>
            {loadError}
          </div>
        )}

        {/* Resultado de la ultima accion */}
        <ResultConsole result={result} />

        {/* ---------------- Brokers ---------------- */}
        <div style={{ ...box, marginTop: '1rem' }}>
          <h3 style={sectionTitle}>Brokers del cluster ({cluster?.brokerCount ?? 0})</h3>
          <div className="grid-3">
            {cluster?.brokers?.map((b) => (
              <div key={b.id} style={{ border: '1px solid #e2e8f0', borderRadius: '0.375rem', padding: '0.75rem', backgroundColor: '#f8fafc' }}>
                <div style={{ fontWeight: 700, color: '#0f172a' }}>Broker {b.id}</div>
                <div style={{ fontSize: '0.8125rem', color: '#475569', ...mono }}>{b.host}:{b.port}</div>
                <div style={{ marginTop: '0.4rem' }}>
                  <span style={{ fontSize: '0.7rem', padding: '0.15rem 0.5rem', borderRadius: '3px', fontWeight: 600, backgroundColor: b.controller ? '#dbeafe' : '#dcfce7', color: b.controller ? '#1d4ed8' : '#15803d' }}>
                    {b.controller ? 'CONTROLLER' : 'ACTIVO'}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>

        {/* ---------------- Topics ---------------- */}
        <div style={box}>
          <h3 style={sectionTitle}>Topics ({topics.length})</h3>
          <div className="table-wrapper">
            <table>
              <thead>
                <tr>
                  <th>Nombre</th>
                  <th>Particiones</th>
                  <th>Replicas</th>
                  <th>Sub-replicadas</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {topics.filter((t) => !t.name.startsWith('__')).map((t) => (
                  <tr key={t.name} style={selectedTopic === t.name ? { backgroundColor: '#eef2ff' } : undefined}>
                    <td style={mono}>{t.name}</td>
                    <td>{t.partitions}</td>
                    <td>{t.replicationFactor}</td>
                    <td style={{ color: t.underReplicatedPartitions > 0 ? '#b91c1c' : '#15803d', fontWeight: 600 }}>{t.underReplicatedPartitions}</td>
                    <td><button className="btn btn-outline" style={{ fontSize: '0.75rem', padding: '0.25rem 0.6rem' }} onClick={() => openTopic(t.name)}>Ver detalle</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {/* Detalle del topic seleccionado */}
          {selectedTopic && (
            <div style={{ marginTop: '1rem', border: '1px solid #c7d2fe', borderRadius: '0.375rem', padding: '1rem', backgroundColor: '#f8fafc' }}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '0.75rem' }}>
                <strong style={mono}>{selectedTopic}</strong>
                <span style={{ fontSize: '0.8125rem', color: '#475569' }}>
                  Mensajes retenidos: <strong>{topicDetail?.retainedMessages ?? '-'}</strong>
                </span>
              </div>

              {topicDetail && (
                <>
                  <div className="table-wrapper" style={{ marginBottom: '0.75rem' }}>
                    <table>
                      <thead>
                        <tr><th>Particion</th><th>Lider</th><th>Replicas</th><th>ISR</th><th>Offset inicial</th><th>Offset final</th><th>Mensajes</th></tr>
                      </thead>
                      <tbody>
                        {topicDetail.partitions.map((p) => (
                          <tr key={p.partition}>
                            <td>{p.partition}</td>
                            <td>Broker {p.leader ?? '-'}</td>
                            <td style={mono}>[{p.replicas.join(', ')}]</td>
                            <td style={{ ...mono, color: p.isr.length < p.replicas.length ? '#b91c1c' : '#15803d' }}>[{p.isr.join(', ')}]</td>
                            <td style={mono}>{p.earliestOffset}</td>
                            <td style={mono}>{p.logEndOffset}</td>
                            <td>{p.messages}</td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>

                  <div style={{ fontSize: '0.8125rem', color: '#475569', marginBottom: '0.75rem' }}>
                    {Object.entries(topicDetail.configs || {}).map(([k, v]) => (
                      <span key={k} style={{ display: 'inline-block', marginRight: '1rem' }}><span style={mono}>{k}</span>: <strong>{v}</strong></span>
                    ))}
                  </div>

                  {/* Acciones de mantenimiento */}
                  <div style={{ display: 'flex', gap: '1rem', flexWrap: 'wrap', alignItems: 'flex-end' }}>
                    <div>
                      <label className="form-label">Nueva retencion (horas)</label>
                      <div style={{ display: 'flex', gap: '0.5rem' }}>
                        <input type="number" min="1" className="form-control" style={{ width: '110px' }} value={retentionHours} onChange={(e) => setRetentionHours(e.target.value)} />
                        <button className="btn btn-primary" style={{ fontSize: '0.8rem' }} disabled={busy || !retentionHours} onClick={handleUpdateRetention}>Modificar</button>
                      </div>
                    </div>
                    <div>
                      <label className="form-label">Total de particiones</label>
                      <div style={{ display: 'flex', gap: '0.5rem' }}>
                        <input type="number" min="1" className="form-control" style={{ width: '110px' }} value={partitionCount} onChange={(e) => setPartitionCount(e.target.value)} />
                        <button className="btn btn-primary" style={{ fontSize: '0.8rem' }} disabled={busy || !partitionCount} onClick={handleIncreasePartitions}>Aumentar</button>
                      </div>
                    </div>
                    <button className="btn" style={{ fontSize: '0.8rem', backgroundColor: '#b91c1c', color: '#ffffff', border: '1px solid #b91c1c' }} disabled={busy} onClick={handleDeleteTopic}>
                      Eliminar topic
                    </button>
                  </div>
                </>
              )}
            </div>
          )}
        </div>

        {/* ---------------- Crear topic ---------------- */}
        <div style={box}>
          <h3 style={sectionTitle}>Crear topic</h3>
          <form onSubmit={handleCreateTopic} style={{ display: 'flex', gap: '0.75rem', flexWrap: 'wrap', alignItems: 'flex-end' }}>
            <div>
              <label className="form-label">Nombre</label>
              <input required className="form-control" placeholder="rutaexpress.demo" value={newTopic.name} onChange={(e) => setNewTopic({ ...newTopic, name: e.target.value })} />
            </div>
            <div>
              <label className="form-label">Particiones</label>
              <input type="number" min="1" className="form-control" style={{ width: '90px' }} value={newTopic.partitions} onChange={(e) => setNewTopic({ ...newTopic, partitions: e.target.value })} />
            </div>
            <div>
              <label className="form-label">Replicas</label>
              <input type="number" min="1" max="3" className="form-control" style={{ width: '90px' }} value={newTopic.replicationFactor} onChange={(e) => setNewTopic({ ...newTopic, replicationFactor: e.target.value })} />
            </div>
            <div>
              <label className="form-label">Retencion (horas)</label>
              <input type="number" min="1" className="form-control" style={{ width: '110px' }} value={newTopic.retentionHours} onChange={(e) => setNewTopic({ ...newTopic, retentionHours: e.target.value })} />
            </div>
            <div>
              <label className="form-label">Cleanup policy</label>
              <select className="form-control" value={newTopic.cleanupPolicy} onChange={(e) => setNewTopic({ ...newTopic, cleanupPolicy: e.target.value })}>
                <option value="delete">delete</option>
                <option value="compact">compact</option>
                <option value="compact,delete">compact,delete</option>
              </select>
            </div>
            <button type="submit" className="btn btn-primary" disabled={busy} style={{ fontSize: '0.8rem' }}>Crear topic</button>
          </form>
        </div>

        {/* ---------------- Consumer groups ---------------- */}
        <div style={box}>
          <h3 style={sectionTitle}>Consumer groups</h3>
          <div className="table-wrapper">
            <table>
              <thead>
                <tr><th>Grupo</th><th>Estado</th><th>Miembros</th><th>Lag total</th><th></th></tr>
              </thead>
              <tbody>
                {groups.map((g) => (
                  <tr key={g.groupId}>
                    <td style={mono}>{g.groupId}</td>
                    <td>{g.state}</td>
                    <td>{g.members}</td>
                    <td style={{ fontWeight: 600, color: g.totalLag > 0 ? '#b45309' : '#15803d' }}>{g.totalLag}</td>
                    <td><button className="btn btn-outline" style={{ fontSize: '0.75rem', padding: '0.25rem 0.6rem' }} onClick={() => openGroupLag(g.groupId)}>Ver lag</button></td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {groupLag && (
            <div style={{ marginTop: '1rem', border: '1px solid #e2e8f0', borderRadius: '0.375rem', padding: '1rem', backgroundColor: '#f8fafc' }}>
              <div style={{ marginBottom: '0.5rem', fontSize: '0.8125rem' }}>
                <strong style={mono}>{groupLag.groupId}</strong> · {groupLag.health} · lag total <strong>{groupLag.totalLag}</strong>
              </div>
              <div className="table-wrapper">
                <table>
                  <thead>
                    <tr><th>Topic</th><th>Particion</th><th>Offset actual</th><th>Offset final</th><th>Lag</th><th>Consumidor</th></tr>
                  </thead>
                  <tbody>
                    {groupLag.partitions.map((p) => (
                      <tr key={`${p.topic}-${p.partition}`}>
                        <td style={mono}>{p.topic}</td>
                        <td>{p.partition}</td>
                        <td style={mono}>{p.currentOffset}</td>
                        <td style={mono}>{p.logEndOffset}</td>
                        <td style={{ fontWeight: 600, color: p.lag > 0 ? '#b45309' : '#15803d' }}>{p.lag}</td>
                        <td style={{ fontSize: '0.75rem' }}>{p.consumer}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </div>
          )}
        </div>

        {/* ---------------- Laboratorio ---------------- */}
        <div style={box}>
          <h3 style={sectionTitle}>Laboratorio: publicar y simular errores</h3>
          <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'flex-end', flexWrap: 'wrap', marginBottom: '1rem' }}>
            <div>
              <label className="form-label">Key del mensaje</label>
              <input className="form-control" value={publishKey} onChange={(e) => setPublishKey(e.target.value)} />
            </div>
            <button className="btn btn-primary" style={{ fontSize: '0.8rem' }} disabled={busy || !publishKey} onClick={handlePublish}>
              Publicar en shipments.events
            </button>
            <span style={{ fontSize: '0.75rem', color: '#64748b' }}>Misma key = misma particion (orden garantizado)</span>
          </div>
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap' }}>
            <button className="btn btn-outline" style={{ fontSize: '0.8rem' }} disabled={busy} onClick={() => handleSimulate('transient-failure')}>Fallo transitorio (reintentos → DLT)</button>
            <button className="btn btn-outline" style={{ fontSize: '0.8rem' }} disabled={busy} onClick={() => handleSimulate('poison-pill')}>Poison pill (JSON roto → DLT)</button>
            <button className="btn btn-outline" style={{ fontSize: '0.8rem' }} disabled={busy} onClick={() => handleSimulate('invalid-payload')}>Payload invalido (@Valid → DLT)</button>
          </div>
        </div>

        {/* ---------------- Dead Letter Topics ---------------- */}
        <div style={{ ...box, marginBottom: 0 }}>
          <h3 style={sectionTitle}>Dead Letter Topics (mensajes fallidos)</h3>
          <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.75rem', flexWrap: 'wrap' }}>
            <select className="form-control" style={{ width: 'auto' }} value={dltTopic} onChange={(e) => setDltTopic(e.target.value)}>
              {DLT_TOPICS.map((t) => <option key={t} value={t}>{t}</option>)}
            </select>
            <button className="btn btn-outline" style={{ fontSize: '0.8rem' }} onClick={handleLoadDlt}>Ver mensajes</button>
          </div>
          {dltMessages && (
            <div className="table-wrapper">
              <table>
                <thead>
                  <tr><th>Part.</th><th>Offset</th><th>Key</th><th>Origen</th><th>Error</th><th></th></tr>
                </thead>
                <tbody>
                  {dltMessages.length === 0 && (
                    <tr><td colSpan={6} style={{ color: '#94a3b8', textAlign: 'center' }}>La DLT esta vacia</td></tr>
                  )}
                  {dltMessages.map((m) => (
                    <tr key={`${m.partition}-${m.offset}`}>
                      <td>{m.partition}</td>
                      <td style={mono}>{m.offset}</td>
                      <td style={mono}>{m.key || '-'}</td>
                      <td style={{ fontSize: '0.75rem' }}>{m.originalTopic} (p{m.originalPartition}/o{m.originalOffset})</td>
                      <td style={{ fontSize: '0.75rem', color: '#b91c1c', maxWidth: '320px' }}>{m.exceptionMessage}</td>
                      <td><button className="btn btn-outline" style={{ fontSize: '0.75rem', padding: '0.25rem 0.6rem' }} disabled={busy} onClick={() => handleReprocess(m)}>Reprocesar</button></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default KafkaAdminView;
