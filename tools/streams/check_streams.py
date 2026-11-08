#!/usr/bin/env python3
"""Sonda de red para las emisoras de ZenRadio.

Para cada emisora hace lo mismo que el reproductor de la app en el móvil:
conecta solo por HTTPS (la app bloquea http y no sigue redirecciones de https
a http), pide los metadatos ICY, escucha unos segundos y comprueba con ffprobe
que lo que llega es audio que la app sabe reproducir. Repite la prueba en
varias rondas separadas para distinguir una emisora caída de un corte puntual.

Uso:
    python3 tools/streams/check_streams.py --rounds 3 --gap 60 \
        --json stream-report.json \
        --android-candidates app/src/androidTest/assets/candidates.txt

Termina con error si alguna emisora del catálogo no responde bien en todas
las rondas.
"""

from __future__ import annotations

import argparse
import concurrent.futures
import json
import os
import re
import subprocess
import sys
import tempfile
import time
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
CATALOG = ROOT / "app/src/main/java/com/zenradio/app/data/Station.kt"
CANDIDATES = Path(__file__).with_name("candidates.txt")

# Lo que envía el reproductor de Android (HttpURLConnection) al conectar.
USER_AGENT = "Dalvik/2.1.0 (Linux; U; Android 14; Pixel 8 Build/AP2A.240805.005)"
# Formatos que ExoPlayer reproduce sin módulos extra.
PLAYABLE_CODECS = {"mp3", "aac", "vorbis", "opus", "flac"}
MIN_AUDIO_BYTES = 16 * 1024
MIN_PACKETS = 20

CURL_ERRORS = {
    1: "redirige a una dirección http (la app solo admite https)",
    6: "el dominio ya no existe (DNS)",
    7: "el servidor no acepta la conexión",
    35: "error de conexión segura (TLS)",
    47: "demasiadas redirecciones",
    51: "certificado no válido",
    52: "el servidor responde vacío",
    56: "el servidor cortó la conexión",
    58: "error de certificado",
    60: "certificado no válido",
}


def load_catalog() -> list[dict]:
    text = CATALOG.read_text(encoding="utf-8")
    pattern = re.compile(
        r'Station\(\s*id = "([^"]+)",\s*name = "([^"]+)",\s*genre = "([^"]*)",\s*streamUrl = "([^"]+)"'
    )
    stations = [
        {"group": "catalogo", "id": sid, "name": name, "url": url}
        for sid, name, _genre, url in pattern.findall(text)
    ]
    if not stations:
        sys.exit(f"No encontré emisoras en {CATALOG}")
    return stations


def load_candidates() -> list[dict]:
    if not CANDIDATES.exists():
        return []
    stations = []
    for line in CANDIDATES.read_text(encoding="utf-8").splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        sid, name, url = (part.strip() for part in line.split("|", 2))
        stations.append({"group": "candidata", "id": sid, "name": name, "url": url})
    return stations


def strip_icy(data: bytes, metaint: int) -> bytes:
    """Quita los bloques de metadatos ICY intercalados en el audio."""
    audio = bytearray()
    i = 0
    while i < len(data):
        audio += data[i : i + metaint]
        i += metaint
        if i >= len(data):
            break
        i += 1 + data[i] * 16
    return bytes(audio)


def parse_headers(raw: str) -> tuple[str, dict, list[str]]:
    """Devuelve la línea de estado y las cabeceras de la última respuesta, y las redirecciones."""
    blocks = [b for b in re.split(r"\r?\n\r?\n", raw) if b.strip()]
    redirects = []
    for block in blocks:
        for line in block.splitlines():
            if line.lower().startswith("location:"):
                redirects.append(line.split(":", 1)[1].strip())
    last = blocks[-1].splitlines() if blocks else [""]
    headers = {}
    for line in last[1:]:
        if ":" in line:
            key, value = line.split(":", 1)
            headers[key.strip().lower()] = value.strip()
    return last[0].strip(), headers, redirects


def ffprobe(path: Path) -> dict:
    proc = subprocess.run(
        [
            "ffprobe", "-v", "error", "-count_packets",
            "-show_entries",
            "stream=codec_type,codec_name,sample_rate,channels,bit_rate,nb_read_packets:format=format_name",
            "-of", "json", str(path),
        ],
        capture_output=True,
        text=True,
        timeout=60,
    )
    try:
        data = json.loads(proc.stdout or "{}")
    except json.JSONDecodeError:
        data = {}
    stream = next((s for s in data.get("streams", []) if s.get("codec_type") == "audio"), None)
    return {"format": data.get("format", {}).get("format_name"), "stream": stream}


def decode_errors(path: Path) -> int:
    proc = subprocess.run(
        ["ffmpeg", "-v", "error", "-i", str(path), "-f", "null", "-"],
        capture_output=True,
        text=True,
        timeout=120,
    )
    return len([line for line in proc.stderr.splitlines() if line.strip()])


def probe(url: str, listen_seconds: int) -> dict:
    result: dict = {"ok": False, "reason": ""}
    with tempfile.TemporaryDirectory() as tmp:
        body = Path(tmp) / "body"
        headers_file = Path(tmp) / "headers"
        cmd = [
            "curl", "--silent", "--show-error",
            "--location", "--max-redirs", "20",
            "--proto", "=https", "--proto-redir", "=https",
            "--user-agent", USER_AGENT,
            "--header", "Icy-MetaData: 1",
            "--header", "Accept-Encoding: identity",
            "--connect-timeout", "10",
            "--max-time", str(listen_seconds),
            "--dump-header", str(headers_file),
            "--output", str(body),
            "--write-out", "%{http_code}\\t%{content_type}\\t%{url_effective}\\t%{time_starttransfer}\\t%{time_total}",
            url,
        ]
        proc = subprocess.run(cmd, capture_output=True, text=True)
        code, ctype, final_url, ttfb, total = (proc.stdout.split("\t") + [""] * 5)[:5]
        raw_headers = headers_file.read_text(errors="replace") if headers_file.exists() else ""
        status_line, headers, redirects = parse_headers(raw_headers)
        data = body.read_bytes() if body.exists() else b""
        result.update(
            http=code,
            status_line=status_line,
            content_type=ctype or headers.get("content-type", ""),
            final_url=final_url or url,
            redirects=redirects,
            icy_name=headers.get("icy-name"),
            icy_br=headers.get("icy-br"),
            bytes=len(data),
        )

        rc = proc.returncode
        if rc not in (0, 28):
            target = next((r for r in redirects if r.startswith("http://")), None)
            reason = CURL_ERRORS.get(rc, f"error de red (curl {rc}): {proc.stderr.strip()[:120]}")
            if rc == 1 and target:
                reason += f": {target}"
            result["reason"] = reason
            return result
        if rc == 28 and not data:
            result["reason"] = "no responde a tiempo"
            return result
        if code not in ("200", "206"):
            result["reason"] = f"responde con error HTTP {code}"
            return result

        lowered = result["content_type"].lower()
        if "mpegurl" in lowered:
            result["reason"] = "es una lista HLS/M3U, no un stream de audio directo"
            return result
        if "scpls" in lowered:
            result["reason"] = "es una lista .pls, no un stream de audio directo"
            return result
        if lowered.startswith("text/html"):
            result["reason"] = "devuelve una página web, no audio"
            return result

        try:
            seconds = max(0.5, float(total) - float(ttfb))
        except ValueError:
            seconds = float(listen_seconds)
        result["measured_kbps"] = round(len(data) * 8 / 1000 / seconds)

        metaint = int(headers.get("icy-metaint", "0") or 0)
        audio = strip_icy(data, metaint) if metaint else data
        if len(audio) < MIN_AUDIO_BYTES:
            result["reason"] = f"casi no llega audio ({len(audio) // 1024} KB en {listen_seconds} s)"
            return result

        audio_path = Path(tmp) / "audio"
        audio_path.write_bytes(audio)
        info = ffprobe(audio_path)
        stream = info["stream"] or {}
        codec = stream.get("codec_name")
        packets = int(stream.get("nb_read_packets") or 0)
        result.update(
            container=info["format"],
            codec=codec,
            sample_rate=stream.get("sample_rate"),
            channels=stream.get("channels"),
            bitrate_kbps=round(int(stream["bit_rate"]) / 1000) if stream.get("bit_rate") else None,
            packets=packets,
        )
        if not codec:
            result["reason"] = "lo que llega no es audio reconocible"
            return result
        if codec not in PLAYABLE_CODECS:
            result["reason"] = f"formato {codec} que la app no reproduce"
            return result
        if packets < MIN_PACKETS:
            result["reason"] = f"llegan muy pocos fragmentos de audio ({packets})"
            return result
        result["decode_errors"] = decode_errors(audio_path)
        result["ok"] = True
        return result


def describe_format(detail: dict) -> str:
    if not detail.get("codec"):
        return ""
    parts = [detail["codec"].upper()]
    if detail.get("sample_rate"):
        parts.append(f"{int(detail['sample_rate']) / 1000:g} kHz")
    if detail.get("channels"):
        parts.append("estéreo" if int(detail["channels"]) == 2 else "mono")
    return " ".join(parts)


def summarize(report: list[dict]) -> str:
    icons = {"estable": "✅ funciona", "intermitente": "⚠️ intermitente", "caida": "❌ no funciona"}
    lines = [
        "| Emisora | Grupo | Resultado | Rondas OK | Formato | kbps | Detalle |",
        "|---|---|---|---|---|---|---|",
    ]
    for entry in report:
        detail = entry["detail"]
        kbps = detail.get("bitrate_kbps") or detail.get("icy_br") or detail.get("measured_kbps") or ""
        if detail.get("ok"):
            notes = []
            if detail.get("final_url") and detail["final_url"] != entry["url"]:
                notes.append(f"redirige a {detail['final_url']}")
            if detail.get("icy_name"):
                notes.append(f"ICY: {detail['icy_name']}")
            if detail.get("decode_errors"):
                notes.append(f"{detail['decode_errors']} avisos al decodificar")
            note = "; ".join(notes)
        else:
            note = detail.get("reason", "")
        lines.append(
            f"| {entry['name']} (`{entry['id']}`) | {entry['group']} | {icons[entry['verdict']]} "
            f"| {entry['ok_rounds']}/{entry['rounds']} | {describe_format(detail)} | {kbps} "
            f"| {note.replace('|', '/')} |"
        )
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--gap", type=int, default=60, help="segundos entre rondas")
    parser.add_argument("--listen", type=int, default=12, help="segundos de escucha por emisora")
    parser.add_argument("--workers", type=int, default=10)
    parser.add_argument("--json", type=Path, help="guarda el informe completo en JSON")
    parser.add_argument(
        "--android-candidates",
        type=Path,
        help="escribe aquí las candidatas que funcionaron en todas las rondas (para la prueba en Android)",
    )
    args = parser.parse_args()

    stations = load_catalog() + load_candidates()
    print(f"Probando {len(stations)} emisoras en {args.rounds} rondas…", flush=True)
    rounds = []
    for number in range(args.rounds):
        if number:
            time.sleep(args.gap)
        with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
            results = list(pool.map(lambda s: probe(s["url"], args.listen), stations))
        rounds.append(results)
        good = sum(r["ok"] for r in results)
        print(f"Ronda {number + 1}/{args.rounds}: {good}/{len(results)} emisoras bien", flush=True)

    report = []
    for index, station in enumerate(stations):
        attempts = [results[index] for results in rounds]
        oks = sum(a["ok"] for a in attempts)
        verdict = "estable" if oks == len(attempts) else ("intermitente" if oks else "caida")
        detail = next((a for a in reversed(attempts) if a["ok"]), attempts[-1])
        report.append(
            {**station, "verdict": verdict, "ok_rounds": oks, "rounds": len(attempts), "detail": detail, "attempts": attempts}
        )

    table = summarize(report)
    print("\n" + table, flush=True)
    summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_file:
        with open(summary_file, "a", encoding="utf-8") as handle:
            handle.write("## Sonda de red\n\n" + table + "\n")
    if args.json:
        args.json.write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    if args.android_candidates:
        args.android_candidates.parent.mkdir(parents=True, exist_ok=True)
        stable = [e for e in report if e["group"] == "candidata" and e["verdict"] == "estable"]
        args.android_candidates.write_text(
            "".join(f"{e['id']}|{e['name']}|{e['url']}\n" for e in stable), encoding="utf-8"
        )

    broken = [e for e in report if e["group"] == "catalogo" and e["verdict"] != "estable"]
    if broken:
        print("\nEmisoras del catálogo con problemas: " + ", ".join(e["id"] for e in broken))
        return 1
    print("\nTodas las emisoras del catálogo responden bien.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
