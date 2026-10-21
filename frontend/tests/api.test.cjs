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
