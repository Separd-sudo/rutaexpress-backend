import React, { useState } from 'react';
import bffApi from '../../services/bffApi';

export const RabbitAdminView = () => {
  const [simulating, setSimulating] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);

  const handleSimulateDlq = async () => {
    setSimulating(true);
    setResult(null);
    setError(null);
    try {
      const res = await bffApi.simulateDlq({
        reason: 'PRUEBA_RESILIENCIA_EVALUACION_DUOC_EP3',
        simulatedAt: new Date().toISOString()
      });
      setResult(res);
    } catch (err) {
      setError(err.response?.data?.message || err.message || 'Error al ejecutar simulacion DLQ');
    } finally {
      setSimulating(false);
    }
  };

  return (
    <div style={{ maxWidth: '960px', margin: '0 auto' }}>
      <div className="card">
        <div className="card-header">
          <div>
            <h2 style={{ fontSize: '1.125rem', fontWeight: 600, color: '#0f172a' }}>
              Infraestructura y Resiliencia RabbitMQ
            </h2>
            <p style={{ fontSize: '0.8125rem', color: '#64748b', marginTop: '0.2rem' }}>
              Estado del cluster de mensajeria asincrona de alta disponibilidad y manejo de mensajes muertos (DLQ).
            </p>
          </div>
        </div>

        {/* Informacion del Cluster */}
        <div className="grid-2" style={{ marginBottom: '1.5rem' }}>
          <div style={{
            border: '1px solid #e2e8f0',
            borderRadius: '0.375rem',
            padding: '1rem',
            backgroundColor: '#f8fafc'
          }}>
            <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600, marginBottom: '0.5rem' }}>
              Nodo Primario
            </div>
            <div style={{ fontSize: '1rem', fontWeight: 700, color: '#0f172a' }}>
              rabbitmq-1
            </div>
            <div style={{ fontSize: '0.8125rem', color: '#475569', marginTop: '0.25rem' }}>
              Puerto AMQP: 5672 | Administracion: 15672
            </div>
            <div style={{ marginTop: '0.5rem' }}>
              <span style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem', backgroundColor: '#dcfce7', color: '#15803d', borderRadius: '3px', fontWeight: 600 }}>
                ACTIVO / EN CLUSTER
              </span>
            </div>
          </div>

          <div style={{
            border: '1px solid #e2e8f0',
            borderRadius: '0.375rem',
            padding: '1rem',
            backgroundColor: '#f8fafc'
          }}>
            <div style={{ fontSize: '0.75rem', textTransform: 'uppercase', color: '#64748b', fontWeight: 600, marginBottom: '0.5rem' }}>
              Nodo Secundario (Clon / Replica)
            </div>
            <div style={{ fontSize: '1rem', fontWeight: 700, color: '#0f172a' }}>
              rabbitmq-2
            </div>
            <div style={{ fontSize: '0.8125rem', color: '#475569', marginTop: '0.25rem' }}>
              Puerto AMQP: 5673 | Administracion: 15673
            </div>
            <div style={{ marginTop: '0.5rem' }}>
              <span style={{ fontSize: '0.75rem', padding: '0.2rem 0.5rem', backgroundColor: '#dcfce7', color: '#15803d', borderRadius: '3px', fontWeight: 600 }}>
                ACTIVO / EN CLUSTER (ha-all)
              </span>
            </div>
          </div>
        </div>

        {/* Politica de Espejado y Colas */}
        <div style={{
          border: '1px solid #e2e8f0',
          borderRadius: '0.375rem',
          padding: '1rem',
          backgroundColor: '#ffffff',
          marginBottom: '1.5rem'
        }}>
          <h3 style={{ fontSize: '0.875rem', fontWeight: 600, color: '#334155', textTransform: 'uppercase', marginBottom: '0.75rem' }}>
            Colas de Negocio y Politica de Espejado
          </h3>
          <ul style={{ listStyle: 'none', padding: 0, margin: 0, fontSize: '0.8125rem', color: '#475569' }}>
            <li style={{ padding: '0.5rem 0', borderBottom: '1px solid #f1f5f9', display: 'flex', justifyContent: 'space-between' }}>
              <span>Cola de Despachos: <strong>shipments-queue</strong></span>
              <span style={{ fontFamily: 'monospace', color: '#0f172a' }}>ha-mode: all</span>
            </li>
            <li style={{ padding: '0.5rem 0', borderBottom: '1px solid #f1f5f9', display: 'flex', justifyContent: 'space-between' }}>
              <span>Cola de Eventos de Auditoria: <strong>audit-queue</strong></span>
              <span style={{ fontFamily: 'monospace', color: '#0f172a' }}>ha-mode: all</span>
            </li>
            <li style={{ padding: '0.5rem 0', display: 'flex', justifyContent: 'space-between' }}>
              <span>Cola de Mensajes Muertos: <strong>shipments-dlq</strong></span>
              <span style={{ fontFamily: 'monospace', color: '#b91c1c' }}>dead-letter-exchange</span>
            </li>
          </ul>
        </div>

        {/* Demostracion Resiliencia DLQ */}
        <div style={{
          border: '1px solid #cbd5e1',
          borderRadius: '0.375rem',
          padding: '1.25rem',
          backgroundColor: '#f8fafc'
        }}>
          <h3 style={{ fontSize: '0.9rem', fontWeight: 600, color: '#0f172a', marginBottom: '0.35rem' }}>
            Demostracion de Resiliencia: Simulacion de Fallo hacia DLQ
          </h3>
          <p style={{ fontSize: '0.8125rem', color: '#64748b', marginBottom: '1rem' }}>
            Publica un mensaje con formato invalido en el exchange para comprobar que el consumidor rechaza el mensaje y RabbitMQ lo enruta automaticamente hacia la cola de descarte (Dead Letter Queue).
          </p>

          <button
            onClick={handleSimulateDlq}
            disabled={simulating}
            className="btn btn-primary"
            style={{ fontSize: '0.8125rem' }}
          >
            {simulating ? 'Enviando Mensaje Fallido...' : 'Ejecutar Simulacion de Fallo a DLQ'}
          </button>

          {error && (
            <div style={{
              backgroundColor: '#fee2e2',
              border: '1px solid #f87171',
              color: '#991b1b',
              padding: '0.75rem',
              borderRadius: '0.375rem',
              fontSize: '0.8125rem',
              marginTop: '1rem'
            }}>
              {error}
            </div>
          )}

          {result && (
            <div style={{
              backgroundColor: '#f0fdf4',
              border: '1px solid #86efac',
              padding: '1rem',
              borderRadius: '0.375rem',
              marginTop: '1rem'
            }}>
              <div style={{ fontWeight: 600, color: '#166534', fontSize: '0.875rem', marginBottom: '0.35rem' }}>
                Simulacion Exitosa: Mensaje Procesado por Politica DLQ
              </div>
              <pre style={{
                backgroundColor: '#ffffff',
                border: '1px solid #bbf7d0',
                padding: '0.75rem',
                borderRadius: '0.25rem',
                fontSize: '0.75rem',
                fontFamily: 'monospace',
                overflowX: 'auto',
                color: '#1e293b'
              }}>
                {JSON.stringify(result, null, 2)}
              </pre>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

export default RabbitAdminView;
