#!/usr/bin/env python3
"""Exports or verifies the sounds defined in art/tanning_sounds.py.

  python3 tools/generate_sounds.py            # --check (default): the exports match the source, write nothing
  python3 tools/generate_sounds.py --write    # synthesize every sound and encode it to OGG Vorbis (needs ffmpeg)

The check compares a SHA-1 of each sound's synthesized 16-bit PCM with art/tanning_sounds.manifest.json (written by
--write) and requires every OGG to exist; Vorbis files themselves are not byte-compared (encoders differ).
"""
import argparse
import hashlib
import json
import subprocess
import sys
import tempfile
import wave
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / 'art'))
import tanning_sounds  # noqa: E402

SOUNDS = ROOT / 'src/main/resources/assets/immersive_tanning/sounds'
MANIFEST = ROOT / 'art/tanning_sounds.manifest.json'


def pcm(samples):
    return (np.clip(samples, -1.0, 1.0) * 32767).astype('<i2').tobytes()


def main():
    parser = argparse.ArgumentParser()
    group = parser.add_mutually_exclusive_group()
    group.add_argument('--write', action='store_true')
    group.add_argument('--check', action='store_true')
    args = parser.parse_args()
    sounds = tanning_sounds.sounds()
    hashes = {name: hashlib.sha1(pcm(s)).hexdigest() for name, s in sounds.items()}

    if args.write:
        with tempfile.TemporaryDirectory() as tmp:
            for name, samples in sounds.items():
                wav = Path(tmp) / (name.replace('/', '_') + '.wav')
                with wave.open(str(wav), 'wb') as w:
                    w.setnchannels(1)
                    w.setsampwidth(2)
                    w.setframerate(tanning_sounds.RATE)
                    w.writeframes(pcm(samples))
                out = SOUNDS / (name + '.ogg')
                out.parent.mkdir(parents=True, exist_ok=True)
                subprocess.run(['ffmpeg', '-loglevel', 'error', '-y', '-i', str(wav), '-map_metadata', '-1', '-fflags', '+bitexact',
                                '-c:a', 'libvorbis', '-q:a', '5', str(out)], check=True)
        MANIFEST.write_text(json.dumps({'rate': tanning_sounds.RATE, 'pcm_sha1': hashes}, indent=2, sort_keys=True) + '\n')
        print(f'wrote {len(sounds)} sounds')
        return 0

    problems = []
    recorded = json.loads(MANIFEST.read_text())['pcm_sha1'] if MANIFEST.exists() else {}
    for name, digest in hashes.items():
        if not (SOUNDS / (name + '.ogg')).exists():
            problems.append(f'missing {name}.ogg')
        if recorded.get(name) != digest:
            problems.append(f'{name}: source changed since the last --write')
    for name in recorded:
        if name not in hashes:
            problems.append(f'{name}: in the manifest but not in the source')
    if problems:
        print('FAIL\n  ' + '\n  '.join(problems))
        return 1
    print('PASS')
    return 0


if __name__ == '__main__':
    sys.exit(main())
