#!/usr/bin/env python3
"""Resume la prueba de reproducción en Android a partir de logcat.

StationPlaybackTest deja una línea por emisora con la etiqueta ZenStreamTest:
    RESULT|grupo|id|nombre|OK o FALLA|ms hasta sonar|formato|detalle|url

Uso: python3 tools/streams/android_report.py android-stream.log
Termina con error si falla alguna emisora del catálogo o si no hay resultados.
"""

from __future__ import annotations

import os
import sys
from pathlib import Path


def main() -> int:
    path = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("android-stream.log")
    log = path.read_text(encoding="utf-8", errors="replace") if path.exists() else ""
    rows = {}
    for line in log.splitlines():
        if "RESULT|" not in line:
            continue
        fields = line.split("RESULT|", 1)[1].split("|")
        if len(fields) < 8:
            continue
        group, sid, name, result, start_ms, fmt, detail, url = fields[:8]
        rows[(group, sid)] = (group, sid, name, result, start_ms, fmt, detail, url)

    if not rows:
        print(f"No hay resultados de la prueba en {path}.")
        return 1

    lines = [
        "| Emisora | Grupo | Resultado | Empieza a sonar | Formato | Detalle |",
        "|---|---|---|---|---|---|",
    ]
    for group, sid, name, result, start_ms, fmt, detail, _url in rows.values():
        icon = "✅ suena" if result == "OK" else "❌ falla"
        start = f"{int(start_ms) / 1000:.1f} s" if start_ms.lstrip("-").isdigit() and int(start_ms) >= 0 else ""
        lines.append(f"| {name} (`{sid}`) | {group} | {icon} | {start} | {fmt} | {detail} |")
    table = "\n".join(lines)
    print(table)
    summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_file:
        with open(summary_file, "a", encoding="utf-8") as handle:
            handle.write("## Reproducción en Android\n\n" + table + "\n")

    broken = [sid for (group, sid), row in rows.items() if group == "catalogo" and row[3] != "OK"]
    if broken:
        print("\nEmisoras del catálogo que no suenan en Android: " + ", ".join(broken))
        return 1
    print("\nTodas las emisoras del catálogo suenan en Android.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
