// Pruebas del transporte sin servidor: simulan fallos HTTP y reconexión del navegador.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const Module = require('node:module');
const path = require('node:path');
const ts = require('typescript');
const archivo = path.resolve(__dirname, '../src/services/api.ts');
const modulo = new Module(archivo, module);
modulo.filename = archivo;
modulo.paths = module.paths;
modulo._compile(ts.transpileModule(fs.readFileSync(archivo, 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
}).outputText, archivo);
const { api } = modulo.exports;
const drenarPromesas = () => new Promise(resolve => setImmediate(resolve));

class StreamFalso {
  static ultimo;
  constructor() { StreamFalso.ultimo = this; this.cerrado = false; }
  addEventListener(_, escuchar) { this.escuchar = escuchar; }
  close() { this.cerrado = true; }
  enviar(estado) { this.escuchar({ data: JSON.stringify(estado) }); }
}

test('Los comandos rechazan errores HTTP con el mensaje del backend', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({ mensaje: 'Resuelva primero el conflicto activo' }), { status: 409 }));
  await assert.rejects(api.alternarPrevencion(true), /Resuelva primero el conflicto activo/);
});

test('SSE suspende el sondeo; al desconectarse vuelve a sondear y limpia sus tareas', async t => {
  t.mock.timers.enable({ apis: ['setTimeout', 'setInterval'] });
  const previo = globalThis.EventSource;
  globalThis.EventSource = StreamFalso;
  t.after(() => { globalThis.EventSource = previo; });
  let consultas = 0;
  t.mock.method(globalThis, 'fetch', async () => {
    consultas++;
    return new Response(JSON.stringify({ tiempoSimuladoSegundos: consultas }));
  });
  const modos = [], estados = [];
  const cancelar = api.conectarStreamSSE(e => estados.push(e), undefined, m => modos.push(m));
  t.after(cancelar);
  await drenarPromesas();
  assert.equal(modos.at(-1), 'polling');
  StreamFalso.ultimo.enviar({ tiempoSimuladoSegundos: 100 });
  assert.equal(modos.at(-1), 'sse');
  t.mock.timers.tick(3000);
  await drenarPromesas();
  assert.equal(consultas, 1);
  StreamFalso.ultimo.onerror(new Event('error'));
  t.mock.timers.tick(1500);
  t.mock.timers.tick(1);
  await drenarPromesas();
  assert.equal(modos.at(-1), 'polling');
  assert.equal(consultas, 2);
  cancelar();
  t.mock.timers.tick(5000);
  await drenarPromesas();
  assert.equal(consultas, 2);
  assert.ok(StreamFalso.ultimo.cerrado);
  assert.equal(estados.length, 3);
});

test('Desmontar ignora una respuesta de sondeo pendiente', async t => {
  const previo = globalThis.EventSource;
  globalThis.EventSource = StreamFalso;
  t.after(() => { globalThis.EventSource = previo; });
  let responder;
  t.mock.method(globalThis, 'fetch', () => new Promise(resolve => { responder = resolve; }));
  let recibidos = 0;
  const cancelar = api.conectarStreamSSE(() => recibidos++);
  cancelar();
  responder(new Response('{}'));
  await drenarPromesas();
  assert.equal(recibidos, 0);
  assert.ok(StreamFalso.ultimo.cerrado);
});

test('Comparación envía la carga configurable y consulta el trabajo con cancelación', async t => {
  const configuracion = { semilla: 42, cantidadPedidos: 300, tasaLlegadasPorMinuto: 4 };
  const llamadas = [];
  t.mock.method(globalThis, 'fetch', async (url, opciones) => {
    llamadas.push({ url, opciones });
    return new Response(JSON.stringify({ id: 1, estado: 'EJECUTANDO', configuracion }), { status: opciones.method === 'POST' ? 202 : 200 });
  });
  assert.equal((await api.ejecutarComparacion(configuracion)).estado, 'EJECUTANDO');
  const abortador = new AbortController();
  await api.obtenerComparacion(abortador.signal);
  assert.equal(llamadas[0].url, '/api/comparacion');
  assert.deepEqual(JSON.parse(llamadas[0].opciones.body), configuracion);
  assert.equal(llamadas[1].opciones.signal, abortador.signal);
});

test('Comparación propaga el rechazo de un trabajo solapado', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response(JSON.stringify({ mensaje: 'Ya hay una comparación en ejecución' }), { status: 409 }));
  await assert.rejects(api.ejecutarComparacion({ semilla: 1, cantidadPedidos: 30, tasaLlegadasPorMinuto: 4 }), /Ya hay una comparación/);
});

test('Un error HTML del proxy conserva su estado HTTP en pantalla', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response('<html>sin backend</html>', { status: 502, statusText: 'Bad Gateway' }));
  await assert.rejects(api.obtenerClientes(), /Error 502: Bad Gateway/);
});

test('Una respuesta exitosa que no contiene JSON válido se rechaza', async t => {
  t.mock.method(globalThis, 'fetch', async () => new Response('no es JSON', { status: 200 }));
  await assert.rejects(api.obtenerEstado(), /respuesta JSON inválida/);
});
