# REDXela — Simulador Concurrente de Centro de Distribución
### Proyecto Final Sistemas Operativos 2 — USAC (Octubre 2026)

REDXela es un simulador concurrente de un centro logístico y de distribución de alta demanda, modelado como procesos y recursos compartidos del sistema operativo para evidenciar conceptos de concurrencia, sincronización, exclusión mutua, planificación de CPU, prevención/detección de interbloqueos (deadlock) y administración de almacenamiento en memoria no contigua.

---

## 1. Requisitos del Sistema

- **Java Development Kit (JDK):** Versión 21 (OpenJDK 21 recomendado).
- **Apache Maven:** Versión 3.9 o superior.
- **Node.js y npm:** Node.js v18+ (probado con v22+) y npm v10+.
- **Navegador Web:** Chrome, Firefox o Edge compatible con Server-Sent Events (SSE).

---

## 2. Estructura del Proyecto

```
proyecto_final/
├── PLAN_AGENTE.md                 # Contrato del proyecto, requisitos y diseño
├── README.md                      # Instrucciones de compilación y ejecución
├── docs/
│   ├── bitacora_decisiones.md     # Decisiones de diseño y fundamento teórico para la defensa
│   ├── manual_usuario.md          # Manual de usuario con capturas y guía de uso
│   ├── manual_tecnico.md          # Manual técnico de arquitectura y algoritmos
│   └── diagramas/                 # Diagramas de arquitectura y flujo
├── backend/                       # Servidor Spring Boot 3 + java.util.concurrent nativo
│   ├── pom.xml
│   └── src/
└── frontend/                      # Cliente web reactivo con React 18 + TypeScript + Vite
    ├── package.json
    └── src/
```

---

## 3. Compilación y Ejecución

### 3.1 Backend (Spring Boot)

1. Ingresar a la carpeta del backend:
   ```bash
   cd backend
   ```
2. Ejecutar pruebas unitarias y de concurrencia:
   ```bash
   mvn test
   ```
3. Iniciar el servidor en modo desarrollo:
   ```bash
   mvn spring-boot:run
   ```
   El backend iniciará en `http://localhost:8080`.

### 3.2 Frontend (React + Vite)

1. Ingresar a la carpeta del frontend:
   ```bash
   cd frontend
   ```
2. Instalar dependencias (solo la primera vez):
   ```bash
   npm install
   ```
3. Iniciar el servidor de desarrollo Vite:
   ```bash
   npm run dev
   ```
   La aplicación web estará disponible en `http://localhost:5173`. Las peticiones `/api` se redirigen automáticamente al backend.
