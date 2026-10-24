#!/usr/bin/env python3
"""Prepara fuentes, manuales, frontend y (si existe) JAR verificado, sin tocar Git.

Ejecutar después de mvn test/package y npm test/build. Este script verifica los
artefactos locales; no descarga dependencias ni sustituye la demo en navegador.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from zipfile import BadZipFile, ZIP_DEFLATED, ZipFile, ZipInfo

RAIZ = Path(__file__).resolve().parents[1]
DESTINO = RAIZ / 'output' / 'entrega'
EXTENSIONES = {'.java', '.xml', '.properties', '.json', '.ts', '.tsx', '.css', '.html',
               '.md', '.pdf', '.svg', '.pic', '.png', '.jpg', '.jpeg', '.py', '.sh', '.txt', '.cjs'}


def pruebas_java():
    """Extrae contadores JUnit sin publicar propiedades ni rutas privadas del proceso."""
    informes = sorted((RAIZ / 'backend/target/surefire-reports').glob('TEST-*.xml'))
    if not informes:
        raise ValueError('Faltan informes JUnit; ejecutar mvn test en backend')
    resumen = {k: 0 for k in ('tests', 'failures', 'errors', 'skipped')}
    suites = []
    for archivo in informes:
        suite = ET.parse(archivo).getroot()
        datos = {k: int(suite.get(k, '0')) for k in resumen}
        for k, v in datos.items(): resumen[k] += v
        suites.append({'suite': suite.get('name'), **datos})
    if not resumen['tests'] or any(resumen[k] for k in ('failures', 'errors', 'skipped')):
        raise ValueError(f'Los informes no están completamente aprobados: {resumen}')
    return {**resumen, 'suites': suites}


def verificar_pdf(archivo):
    contenido = archivo.read_bytes()
    if not contenido.startswith(b'%PDF-') or b'%%EOF' not in contenido[-1024:]:
        raise ValueError(f'PDF ausente o incompleto: {archivo.name}')


def verificar_frontend():
    carpeta = RAIZ / 'frontend/dist'
    pagina = carpeta / 'index.html'
    if not pagina.is_file(): raise ValueError('Falta frontend/dist; ejecutar npm run build')
    recursos = re.findall(r'(?:src|href)="(/assets/[^\"]+)"', pagina.read_text())
    if not any(recurso.endswith('.js') for recurso in recursos):
        raise ValueError('El index del frontend no referencia su aplicación JavaScript compilada')
    for recurso in recursos:
        if not (carpeta / recurso.lstrip('/')).is_file():
            raise ValueError(f'El build referencia un recurso ausente: {recurso}')
    return sorted(p for p in carpeta.rglob('*') if p.is_file())


def verificar_jar(archivo):
    """No acepta un JAR simple de clases como si fuera ejecutable Spring Boot."""
    if not archivo.exists(): return False
    with ZipFile(archivo) as jar:
        if jar.testzip() is not None: raise ValueError('El JAR está corrupto')
        nombres = set(jar.namelist())
        manifiesto = jar.read('META-INF/MANIFEST.MF').decode()
        if ('Start-Class: gt.usac.redxela.RedxelaApplication' not in manifiesto
                or 'Main-Class: org.springframework.boot.loader.launch.JarLauncher' not in manifiesto
                or 'BOOT-INF/classes/gt/usac/redxela/RedxelaApplication.class' not in nombres
                or not any(n.startswith('BOOT-INF/lib/') and n.endswith('.jar') for n in nombres)):
            raise ValueError('El JAR no contiene el lanzador, aplicación y dependencias esperados')
    return True


def fuentes():
    """Lista permitida: nunca recorre .git, cachés, credenciales ni node_modules."""
    archivos = [RAIZ / x for x in ('README.md', '.gitignore', 'backend/pom.xml', 'frontend/package.json',
        'frontend/package-lock.json', 'frontend/index.html', 'frontend/tsconfig.json',
        'frontend/tsconfig.node.json', 'frontend/vite.config.ts')]
    for carpeta in ('backend/src', 'frontend/src', 'frontend/tests', 'docs', 'scripts'):
        for archivo in (RAIZ / carpeta).rglob('*'):
            if not archivo.is_file() or archivo.is_symlink(): continue
            if '__pycache__' in archivo.parts: continue
            if archivo.suffix in EXTENSIONES or archivo.name == 'org.mockito.plugins.MockMaker':
                archivos.append(archivo)
    return sorted(set(archivos))


def serializar(datos):
    return (json.dumps(datos, ensure_ascii=False, indent=2, sort_keys=True) + '\n').encode()


def escribir_zip(archivo, contenidos):
    """Metadatos fijos permiten repetir el ZIP con las mismas fuentes y artefactos."""
    with ZipFile(archivo, 'w', compression=ZIP_DEFLATED) as paquete:
        for ruta, datos in sorted(contenidos.items()):
            entrada = ZipInfo(ruta, date_time=(1980, 1, 1, 0, 0, 0))
            entrada.compress_type = ZIP_DEFLATED
            entrada.external_attr = 0o100644 << 16
            paquete.writestr(entrada, datos)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--exigir-jar', action='store_true', help='Rechazar un paquete sin JAR ejecutable Spring Boot')
    args = parser.parse_args()
    try:
        resumen = pruebas_java()
        for nombre in ('manual_usuario', 'manual_tecnico'):
            verificar_pdf(RAIZ / 'docs' / (nombre + '.pdf'))
        frontend = verificar_frontend()
        jar = RAIZ / 'backend/target/redxela-1.0.0.jar'
        incluye_jar = verificar_jar(jar)
        if args.exigir_jar and not incluye_jar:
            raise ValueError('Falta el JAR ejecutable. Completar mvn package con sus dependencias disponibles')
        try:
            head = subprocess.run(['git', 'rev-parse', 'HEAD'], cwd=RAIZ, text=True, capture_output=True, check=True).stdout.strip()
        except (OSError, subprocess.CalledProcessError):
            head = None  # El árbol extraído puede usarse sin metadatos Git.
        archivos = fuentes() + frontend + ([jar] if incluye_jar else [])
        contenidos = {}
        for archivo in archivos:
            # Aun una ruta permitida debe permanecer dentro del repositorio al resolverse.
            archivo.resolve().relative_to(RAIZ.resolve())
            relativo = archivo.relative_to(RAIZ).as_posix()
            if archivo == jar: relativo = 'backend/redxela-1.0.0.jar'
            contenidos['REDXela/' + relativo] = archivo.read_bytes()
        plan = RAIZ / 'PLAN_AGENTE.md'
        if not plan.exists(): plan = RAIZ.parent / 'PLAN_AGENTE.md'
        if plan.is_file() and not plan.is_symlink(): contenidos['REDXela/PLAN_AGENTE.md'] = plan.read_bytes()
        estado = {'commit_base': head, 'snapshot': 'Archivos de trabajo actuales; no crea commits',
                  'jar_incluido': incluye_jar, 'pruebas_java': resumen,
                  'validacion_visual': 'No certificada por este script; consultar docs/verificacion_fase9.md',
                  'frontend': 'Build incluido; requiere servir /api hacia el backend',
                  'tipo': 'fuentes-manuales-frontend' + ('-jar' if incluye_jar else '-sin-jar')}
        contenidos['REDXela/ESTADO_ENTREGA.json'] = serializar(estado)
        hashes = {ruta: hashlib.sha256(datos).hexdigest() for ruta, datos in contenidos.items()}
        contenidos['REDXela/SHA256SUMS.txt'] = ''.join(f'{valor}  {ruta}\n' for ruta, valor in sorted(hashes.items())).encode()
        DESTINO.mkdir(parents=True, exist_ok=True)
        nombre = 'REDXela-fase9' + ('' if incluye_jar else '-fuentes-sin-jar') + '.zip'
        salida = DESTINO / nombre
        escribir_zip(salida, contenidos)
        with ZipFile(salida) as paquete:
            if paquete.testzip() is not None: raise ValueError('Falló el CRC del paquete')
            for ruta, valor in hashes.items():
                if hashlib.sha256(paquete.read(ruta)).hexdigest() != valor:
                    raise ValueError(f'Hash incorrecto: {ruta}')
        sha = hashlib.sha256(salida.read_bytes()).hexdigest()
        salida.with_suffix('.zip.sha256').write_text(f'{sha}  {salida.name}\n')
        (DESTINO / 'estado_entrega.json').write_bytes(serializar(estado))
        print(f'Paquete: {salida.relative_to(RAIZ)}')
        print(f'{len(contenidos)} entradas; {salida.stat().st_size} bytes; SHA-256 {sha}')
        print('JAR ejecutable incluido' if incluye_jar else 'Paquete de fuentes: JAR pendiente, no se declara entrega final verificada')
        print('CRC y hashes individuales verificados. No se ejecutó git add, commit ni push.')
    except (OSError, ValueError, KeyError, BadZipFile, ET.ParseError, subprocess.CalledProcessError) as error:
        sys.exit(f'No se preparó la entrega: {error}')


if __name__ == '__main__':
    main()
