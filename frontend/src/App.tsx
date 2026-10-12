import React from 'react';

export const App: React.FC = () => {
  return (
    <div style={{ padding: '2rem', maxWidth: '1200px', margin: '0 auto' }}>
      <header style={{ marginBottom: '2rem', borderBottom: '1px solid #334155', paddingBottom: '1rem' }}>
        <h1 style={{ fontSize: '2rem', fontWeight: 'bold', color: '#38bdf8' }}>
          REDXela — Simulador Concurrente
        </h1>
        <p style={{ color: '#94a3b8' }}>
          Proyecto Final Sistemas Operativos 2 | USAC 2026
        </p>
      </header>

      <main style={{ background: '#1e293b', padding: '1.5rem', borderRadius: '8px', border: '1px solid #334155' }}>
        <h2 style={{ fontSize: '1.25rem', marginBottom: '1rem', color: '#f8fafc' }}>
          Estado del Entorno (Fase 0 - Preparación)
        </h2>
        <ul style={{ listStyleType: 'disc', paddingLeft: '1.5rem', color: '#cbd5e1', lineHeight: '1.8' }}>
          <li>Backend: Spring Boot 3.3.4 + Java 21 configurado y verificado.</li>
          <li>Frontend: React 18 + Vite 5 + TypeScript inicializado y listo.</li>
          <li>Conectividad API: Proxy configurado hacia backend (puerto 8080).</li>
        </ul>
      </main>
    </div>
  );
};

export default App;
