#!/usr/bin/env python3
"""Convierte los manuales Markdown a PDF con Python estándar y groff/tbl/pic.

No requiere navegador, servidor, paquetes pip ni acceso a red. Los diagramas
SVG tienen una versión .pic para conservar vectores en el PDF de impresión.
"""
from pathlib import Path
import re
import shutil
import subprocess
import sys

RAIZ = Path(__file__).resolve().parents[1]
DOCS = RAIZ / 'docs'
SUSTITUCIONES = str.maketrans({'→': ' -> ', '×': 'x', '≤': '<=', '≥': '>=',
    '≈': '~', 'Σ': 'suma', 'ᵢ': 'i', 'ᵣ': 'r', '²': '2', '∅': 'vacio'})


def texto(valor):
    """Escapa controles roff y conserva el contenido de enlaces y código."""
    valor = valor.translate(SUSTITUCIONES)
    valor = re.sub(r'\[([^]]+)\]\([^)]+\)', r'\1', valor)
    valor = valor.replace('\\', r'\e')
    valor = re.sub(r'\*\*([^*]+)\*\*', lambda m: r'\f[B]' + m[1] + r'\f[]', valor)
    valor = re.sub(r'`([^`]+)`', lambda m: r'\f[CR]' + m[1] + r'\f[]', valor)
    if valor.startswith(('.', "'")):
        valor = r'\&' + valor
    return valor


def tabla(filas):
    """tbl reparte el ancho entre columnas y envuelve celdas extensas."""
    filas = [f for f in filas if not re.fullmatch(r'[|:\-\s]+', f)]
    datos = [[c.strip() for c in f.strip().strip('|').split('|')] for f in filas]
    columnas = len(datos[0])
    ancho = 5.8 / columnas
    salida = ['.LP', '.ps 8', '.vs 10', '.na', '.TS', 'allbox tab(|);',
              ' '.join(f'lw({ancho:.3f}i)' + ('1' if i < columnas - 1 else '') for i in range(columnas)) + '.']
    for i, fila in enumerate(datos):
        celdas = []
        for celda in fila:
            contenido = texto(celda)
            if i == 0:
                contenido = r'\f[B]' + contenido + r'\f[]'
            celdas.append('T{\n' + contenido + '\nT}')
        salida.append('|'.join(celdas))
    salida += ['.TE', '.ps 10', '.vs 13', '.ad b', '.LP']
    return salida


def convertir(fuente):
    """Reconoce los bloques utilizados en nuestros manuales, sin ejecutar Markdown."""
    lineas = fuente.read_text().splitlines()
    salida = ['.po .8i', '.ll 6.5i', '.nr PS 10', '.nr VS 13',
              '.ds LH REDXela | Sistemas Operativos 2', '.ds RH Fase 8',
              '.ds CF %', '.nr HY 0']
    i = 0
    while i < len(lineas):
        linea = lineas[i]
        if linea.startswith('```'):
            salida += ['.LP', '.nf', '.ft CR', '.ps 8', '.vs 10']
            i += 1
            while i < len(lineas) and not lineas[i].startswith('```'):
                salida.append(texto(lineas[i]))
                i += 1
            salida += ['.ft R', '.ps 10', '.vs 13', '.fi', '.LP']
        elif linea.startswith('|'):
            filas = []
            while i < len(lineas) and lineas[i].startswith('|'):
                filas.append(lineas[i]); i += 1
            salida += tabla(filas)
            continue
        elif re.fullmatch(r'!\[.*?\]\(.+?\)', linea):
            imagen = re.fullmatch(r'!\[(.*?)\]\((.+?)\)', linea)
            pic = (fuente.parent / imagen[2]).with_suffix('.pic')
            salida += ['.LP', r'\f[B]' + texto(imagen[1]) + r'\f[]']
            if pic.exists():
                salida += ['.PS', pic.read_text(), '.PE']
            else:
                salida.append(texto('Figura disponible en la versión Markdown: ' + imagen[2]))
        elif linea.startswith('# '):
            salida += ['.TL', texto(linea[2:]), '.AU', 'USAC | Proyecto Final | Octubre 2026', '.LP']
        elif linea.startswith('## '):
            siguiente = next((x for x in lineas[i+1:] if x.strip()), '')
            if siguiente.startswith('|'): salida.append('.ne 3i')
            salida += ['.SH 1', texto(linea[3:]), '.LP']
        elif linea.startswith('### '):
            siguiente = next((x for x in lineas[i+1:] if x.strip()), '')
            if siguiente.startswith('|'): salida.append('.ne 3i')
            salida += ['.SH 2', texto(linea[4:]), '.LP']
        elif not linea.strip():
            salida += ['.PP']
        elif linea.startswith('- '):
            salida += ['.IP \\(bu 2', texto(linea[2:])]
        elif re.match(r'\d+\. ', linea):
            numero, resto = linea.split('. ', 1)
            salida += [f'.IP "{numero}." 3', texto(resto)]
        else:
            salida.append(texto(linea))
        i += 1
    return '\n'.join(salida) + '\n'


def main():
    if not shutil.which('groff'):
        sys.exit('Se requiere groff con dispositivos PDF, tbl y pic. No se instalaron herramientas globales.')
    for nombre in ('manual_usuario', 'manual_tecnico'):
        archivo = DOCS / f'{nombre}.md'
        pdf = DOCS / f'{nombre}.pdf'
        proceso = subprocess.run(['groff', '-Kutf8', '-Tpdf', '-t', '-p', '-ms'],
                                 input=convertir(archivo).encode(), capture_output=True)
        if proceso.returncode or b'error:' in proceso.stderr:
            sys.exit(proceso.stderr.decode(errors='replace'))
        if proceso.stderr:
            print(proceso.stderr.decode(errors='replace'), file=sys.stderr)
        if not proceso.stdout.startswith(b'%PDF-'):
            sys.exit(f'No se generó un PDF válido para {nombre}')
        pdf.write_bytes(proceso.stdout)
        print(f'{pdf.relative_to(RAIZ)} ({pdf.stat().st_size} bytes)')


if __name__ == '__main__':
    main()
