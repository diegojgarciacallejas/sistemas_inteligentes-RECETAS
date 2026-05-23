"""
compute_idf_from_recipenlg.py

Script de preprocesamiento (se ejecuta UNA SOLA VEZ offline).
Calcula el IDF sobre el corpus RecipeNLG y guarda el resultado
en idf_corpus.json, que TextMiningBehaviour carga en runtime.

Requisitos:
    pip install tqdm

Descarga del corpus:
    https://recipenlg.cs.put.poznan.pl/
    Archivo: full_dataset.csv (unzip primero)

Uso:
    python compute_idf_from_recipenlg.py
    python compute_idf_from_recipenlg.py --input ruta/full_dataset.csv --output idf_corpus.json
    python compute_idf_from_recipenlg.py --max-docs 500000   # para pruebas rapidas

El fichero idf_corpus.json resultante debe colocarse en:
    src/main/resources/idf_corpus.json
"""

import argparse
import csv
import json
import math
import re
import sys
from collections import defaultdict

# ── Stopwords: DEBEN ser identicas a las del TextMiningBehaviour.java ─────────
STOPWORDS = {
    "a", "an", "the", "and", "or", "but", "in", "on", "at", "to", "for",
    "of", "with", "by", "from", "is", "are", "was", "were", "be", "been",
    "have", "has", "had", "do", "does", "did", "will", "would", "could",
    "should", "it", "its", "this", "that", "i", "you", "he", "she", "we",
    "they", "as", "up", "out", "into", "over", "if", "so", "than", "then",
    "per", "also", "very", "just", "your", "about", "some", "more", "all",
    "not", "no", "can", "our", "their", "each", "both", "such", "these",
    "those", "get", "got", "one", "two", "three", "four", "five", "six"
}


def tokenize(text: str) -> set[str]:
    """
    Tokenizacion identica a TextMiningBehaviour.tokenize() en Java:
      - Minusculas
      - Eliminar caracteres no alfanumericos
      - Colapsar espacios
      - Filtrar stopwords y tokens < 3 chars
    Devuelve un SET (terminos unicos) porque para IDF solo importa
    si el termino aparece en el documento, no cuantas veces.
    """
    text = text.lower()
    text = re.sub(r'[^a-z0-9\s]', ' ', text)
    text = re.sub(r'\s+', ' ', text).strip()
    return {t for t in text.split() if len(t) >= 3 and t not in STOPWORDS}


def extract_text(row: dict) -> str:
    """
    Extrae el texto relevante de una fila del CSV de RecipeNLG.
    Usa 'title' y 'directions' (instrucciones de preparacion),
    que son los campos equivalentes a summary + instructions de Spoonacular.
    """
    title = row.get('title', '')

    # 'directions' es un array JSON dentro del CSV: ["step1", "step2", ...]
    directions_raw = row.get('directions', '[]')
    try:
        directions_list = json.loads(directions_raw)
        directions = ' '.join(directions_list)
    except (json.JSONDecodeError, TypeError):
        directions = directions_raw

    return title + ' ' + directions


def compute_idf(input_path: str, output_path: str, max_docs: int | None = None) -> None:
    """
    Lee el CSV de RecipeNLG, calcula el IDF de cada termino y
    guarda el resultado en un fichero JSON.

    Formula IDF con suavizado (igual que en Java):
        IDF(t) = log( (1 + N) / (1 + df(t)) ) + 1

    Donde:
        N     = numero total de documentos procesados
        df(t) = numero de documentos que contienen el termino t
    """
    df: dict[str, int] = defaultdict(int)
    N = 0

    print(f"Leyendo corpus desde: {input_path}")
    if max_docs:
        print(f"Limite: {max_docs:,} documentos")

    try:
        # Intentar importar tqdm para barra de progreso (opcional)
        from tqdm import tqdm
        use_tqdm = True
    except ImportError:
        use_tqdm = False
        print("(instala tqdm para ver progreso: pip install tqdm)")

    with open(input_path, 'r', encoding='utf-8', errors='replace') as f:
        reader = csv.DictReader(f)

        rows = reader
        if use_tqdm:
            rows = tqdm(reader, desc="Procesando recetas", unit=" recetas")

        for row in rows:
            if max_docs and N >= max_docs:
                break

            text = extract_text(row)
            unique_terms = tokenize(text)

            for term in unique_terms:
                df[term] += 1

            N += 1

            if not use_tqdm and N % 100_000 == 0:
                print(f"  Procesados {N:,} documentos, {len(df):,} terminos unicos...")

    print(f"\nCorpus procesado: {N:,} documentos, {len(df):,} terminos unicos")

    # Calcular IDF con la misma formula que usa Java
    idf: dict[str, float] = {}
    for term, freq in df.items():
        idf[term] = math.log((1.0 + N) / (1.0 + freq)) + 1.0

    # Guardar como JSON
    with open(output_path, 'w', encoding='utf-8') as f:
        json.dump(idf, f, ensure_ascii=False, separators=(',', ':'))

    size_mb = len(json.dumps(idf)) / (1024 * 1024)
    print(f"IDF guardado en: {output_path}  ({size_mb:.1f} MB)")
    print(f"\nPaso siguiente:")
    print(f"  Copia {output_path} a src/main/resources/idf_corpus.json")


def main():
    parser = argparse.ArgumentParser(
        description='Calcula IDF del corpus RecipeNLG para TextMiningBehaviour'
    )
    parser.add_argument(
        '--input', default='full_dataset.csv',
        help='Ruta al fichero full_dataset.csv de RecipeNLG (default: full_dataset.csv)'
    )
    parser.add_argument(
        '--output', default='idf_corpus.json',
        help='Fichero de salida con los valores IDF (default: idf_corpus.json)'
    )
    parser.add_argument(
        '--max-docs', type=int, default=None,
        help='Limitar a N documentos (util para pruebas rapidas, ej: --max-docs 100000)'
    )
    args = parser.parse_args()

    compute_idf(args.input, args.output, args.max_docs)


if __name__ == '__main__':
    main()
