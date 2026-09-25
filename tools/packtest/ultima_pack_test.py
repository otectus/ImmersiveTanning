#!/usr/bin/env python3
"""Full-modpack acceptance test for Immersive Tanning against the Ultima CurseForge instance.

Launches an isolated copy of the pack offline, using the launcher's installed libraries (same approach as
UltimaKingdoms/tools/client-test/production_client.py). The real instance is never modified: jars are hardlinked
read-only, everything else is copied into build/packtest/<label>/.

While the client runs, the game window's real on-screen pixels are captured with grim (Hyprland), so a window that
stops presenting (for example stuck on Forge's early loading screen) is detected even when the log shows the game
reaching the title screen. The immersive_tanning_smoketest agent (build/libs/*-smoketest.jar) then creates a flat world,
checks the tanning data and the pack integration (--expect groups, see src/smokeTest/.../PackChecks.java), stages real
kills, takes framebuffer screenshots and quits.

Usage:
  python3 tools/packtest/ultima_pack_test.py [--label NAME] [--without-mod] [--mod-jar PATH]
         [--early-window-control true|false] [--expect dragn,lycanites,alexsmobs,butchery,magic,bench,trades] [--timeout 900]
Exit code 0 = PASS.
"""
import argparse
import hashlib
import json
import os
import pathlib
import re
import shutil
import subprocess
import sys
import time
import uuid

ROOT = pathlib.Path(__file__).resolve().parents[2]
INSTALL = pathlib.Path('/home/otectus/Documents/curseforge/minecraft/Install')
PACK = pathlib.Path('/home/otectus/Documents/curseforge/minecraft/Instances/Ultima')
FORGE_VERSION = 'forge-47.4.23'
HEAP = '10624M'  # the instance's allocatedMemory
COPY_SKIP = {'mods', 'saves', 'logs', 'crash-reports', 'screenshots', 'downloads', 'modernfix', 'quickskin_cache'}
MOJANG_RED = (239, 50, 61)
# Installed builds of this mod are left out of the copy.
OWN_JAR_PREFIXES = ('immersive-tanning', 'immersive_tanning')


def allowed(rules):
    answer = not rules
    for rule in rules or []:
        os_rule = rule.get('os', {})
        matches = (os_rule.get('name', 'linux') == 'linux' and os_rule.get('arch', 'amd64') in ('amd64', 'x86_64')
                   and not rule.get('features'))
        if matches:
            answer = rule['action'] == 'allow'
    return answer


def build_instance(work, mod_jar, agent_jar, early_window_control):
    (work / 'mods').mkdir(parents=True)
    linked = 0
    for jar in sorted((PACK / 'mods').iterdir()):
        if not jar.name.endswith('.jar') or jar.name.startswith(OWN_JAR_PREFIXES):
            continue
        target = work / 'mods' / jar.name
        try:
            os.link(jar, target)
        except OSError:
            shutil.copy2(jar, target)
        linked += 1
    for extra in [mod_jar, agent_jar]:
        if extra:
            shutil.copy2(extra, work / 'mods' / extra.name)
    for entry in PACK.iterdir():
        if entry.name in COPY_SKIP or entry.name.startswith('mods-backup') or entry.name == 'minecraftinstance.json':
            continue
        if entry.is_dir():
            shutil.copytree(entry, work / entry.name, symlinks=True)
        else:
            shutil.copy2(entry, work / entry.name)
    options = work / 'options.txt'
    if options.exists():
        lines = options.read_text().splitlines()
        overrides = {'fullscreen': 'false', 'pauseOnLostFocus': 'false', 'soundCategory_master': '0.0',
                     'onboardAccessibility': 'false', 'skipMultiplayerWarning': 'true', 'joinedFirstServer': 'true'}
        seen = set()
        for i, line in enumerate(lines):
            key = line.split(':', 1)[0]
            if key in overrides:
                lines[i] = f'{key}:{overrides[key]}'
                seen.add(key)
        lines += [f'{k}:{v}' for k, v in overrides.items() if k not in seen]
        options.write_text('\n'.join(lines) + '\n')
    fml = work / 'config' / 'fml.toml'
    if early_window_control is not None and fml.exists():
        text = re.sub(r'earlyWindowControl\s*=\s*\w+', f'earlyWindowControl = {early_window_control}', fml.read_text())
        fml.write_text(text)
    # MCA's first-join destiny flow can teleport the player away from the stations the agent stages; the copy skips
    # it. (Origins' choice is made by the agent, as a player would.) Unlink first so a linked file is never written.
    mca = work / 'config' / 'mca.json'
    if mca.exists():
        text = re.sub(r'"launchIntoDestiny"\s*:\s*true', '"launchIntoDestiny": false', mca.read_text())
        mca.unlink()
        mca.write_text(text)
    return linked


def launch_command(work, props):
    vanilla = json.loads((INSTALL / 'versions/1.20.1/1.20.1.json').read_text())
    forge = json.loads((INSTALL / f'versions/{FORGE_VERSION}/{FORGE_VERSION}.json').read_text())
    libraries = {}
    for lib in vanilla['libraries'] + forge['libraries']:
        if allowed(lib.get('rules')):
            parts = lib['name'].split(':')
            libraries[':'.join(parts[:2]) + (':' + parts[3] if len(parts) > 3 else '')] = lib
    cp = []
    for lib in libraries.values():
        artifact = lib.get('downloads', {}).get('artifact')
        if artifact:
            path = INSTALL / 'libraries' / artifact['path']
            if not path.exists():
                raise SystemExit(f'Missing library {path}')
            cp.append(str(path))
    cp.append(str(INSTALL / 'versions/1.20.1/1.20.1.jar'))
    (work / 'natives').mkdir(exist_ok=True)
    values = {'auth_player_name': 'PackTest', 'version_name': FORGE_VERSION, 'game_directory': str(work),
              'assets_root': str(INSTALL / 'assets'), 'assets_index_name': vanilla['assetIndex']['id'],
              'auth_uuid': uuid.uuid3(uuid.NAMESPACE_DNS, 'OfflinePlayer:PackTest').hex, 'auth_access_token': '0',
              'clientid': '', 'auth_xuid': '', 'user_type': 'legacy', 'version_type': 'release',
              'natives_directory': str(work / 'natives'), 'launcher_name': 'ImmersiveTanningPackTest', 'launcher_version': '1',
              'classpath': ':'.join(cp), 'classpath_separator': ':', 'library_directory': str(INSTALL / 'libraries')}

    def expand(args):
        result = []
        for arg in args:
            if isinstance(arg, dict):
                if not allowed(arg.get('rules')):
                    continue
                arg = arg['value']
            for value in arg if isinstance(arg, list) else [arg]:
                expanded = re.sub(r'\$\{([^}]+)\}', lambda m: values[m[1]], value)
                if expanded.startswith('-DignoreList='):
                    expanded += ',1.20.1.jar'
                result.append(expanded)
        return result

    jvm = [f'-Xmx{HEAP}', '-Xms256M'] + [f'-D{k}={v}' for k, v in props.items()]
    return (['java'] + jvm + expand(vanilla['arguments']['jvm'] + forge['arguments']['jvm']) + [forge['mainClass']]
            + expand(vanilla['arguments']['game'] + forge['arguments']['game']) + ['--width', '1280', '--height', '800'])


def hypr(*args):
    try:
        return json.loads(subprocess.run(['hyprctl', '-j', *args], capture_output=True, text=True, timeout=5).stdout)
    except Exception:
        return None


def capture(pid, path):
    """Grab the window's on-screen pixels. Returns None when the window is not visible on the active workspace."""
    clients = hypr('clients') or []
    active = (hypr('activeworkspace') or {}).get('id')
    for c in clients:
        if c.get('pid') == pid and c.get('mapped') and not c.get('hidden') and c.get('workspace', {}).get('id') == active:
            (x, y), (w, h) = c['at'], c['size']
            if w < 50 or h < 50:
                return None
            if subprocess.run(['grim', '-g', f'{x},{y} {w}x{h}', str(path)], capture_output=True, timeout=10).returncode == 0:
                return path
    return None


def analyse(path):
    from PIL import Image
    image = Image.open(path).convert('RGB').resize((96, 54))
    raw = image.tobytes()
    pixels = [raw[i:i + 3] for i in range(0, len(raw), 3)]
    red = sum(1 for p in pixels if all(abs(p[i] - MOJANG_RED[i]) < 30 for i in range(3))) / len(pixels)
    digest = hashlib.md5(image.convert('L').resize((24, 14)).tobytes()).hexdigest()
    return red, digest


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--label', default=time.strftime('%Y%m%d-%H%M%S'))
    parser.add_argument('--mod-jar', type=pathlib.Path)
    parser.add_argument('--without-mod', action='store_true')
    parser.add_argument('--early-window-control', choices=['true', 'false'])
    parser.add_argument('--expect', default='')
    parser.add_argument('--timeout', type=int, default=900)
    parser.add_argument('--observe-after-title', type=int, default=30,
                        help='seconds to keep watching after the title screen when no agent runs')
    args = parser.parse_args()

    libs = ROOT / 'build' / 'libs'
    mod_jar = agent_jar = None
    if not args.without_mod:
        mod_jar = args.mod_jar or max((p for p in libs.glob('immersive-tanning-*.jar') if not p.stem.endswith('-smoketest')),
                                      key=lambda p: p.stat().st_mtime)
        agent_jar = max(libs.glob('immersive-tanning-*-smoketest.jar'), key=lambda p: p.stat().st_mtime)
    work = ROOT / 'build' / 'packtest' / args.label
    if work.exists():
        raise SystemExit(f'Refusing to reuse {work}')
    work.mkdir(parents=True)
    linked = build_instance(work, mod_jar, agent_jar, args.early_window_control)
    props = {}
    if agent_jar:
        props = {'immersive_tanning.smoketest.output': str(work), 'immersive_tanning.smoketest.expect': args.expect}
    cmd = launch_command(work, props)
    artifacts = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in [mod_jar, agent_jar] if p}
    (work / 'launch.json').write_text(json.dumps({'command': cmd, 'artifacts': artifacts, 'pack_jars': linked,
                                                  'early_window_control': args.early_window_control}, indent=2))
    print(f'[packtest] {work.name}: {linked} pack jars, mod={mod_jar.name if mod_jar else "none"}, '
          f'earlyWindowControl={args.early_window_control or "pack default"}', flush=True)

    captures = work / 'captures'
    captures.mkdir()
    log_path = work / 'client.log'
    start = time.time()
    title_at = None
    frames = []  # (seconds, red fraction, digest, file)
    with log_path.open('w') as log:
        proc = subprocess.Popen(cmd, cwd=work, stdout=log, stderr=subprocess.STDOUT)
        next_capture = 0.0
        while proc.poll() is None:
            now = time.time() - start
            if now > args.timeout:
                proc.terminate()
                try:
                    proc.wait(20)
                except subprocess.TimeoutExpired:
                    proc.kill()
                break
            if title_at is None and 'seconds to start' in log_path.read_text(errors='replace'):
                title_at = now
                print(f'[packtest] title screen logged at {now:.0f}s', flush=True)
            if now >= next_capture:
                next_capture = now + 5
                shot = capture(proc.pid, captures / f'{int(now):04d}.png')
                if shot:
                    red, digest = analyse(shot)
                    frames.append((round(now, 1), round(red, 3), digest, shot.name))
            if agent_jar is None and title_at is not None and now - title_at > args.observe_after_title:
                proc.terminate()
                try:
                    proc.wait(20)
                except subprocess.TimeoutExpired:
                    proc.kill()
                break
            time.sleep(1)
    elapsed = time.time() - start

    # Verdicts.
    text = log_path.read_text(errors='replace')
    after_title = [f for f in frames if title_at is not None and f[0] >= title_at + 20]
    if title_at is None:
        freeze = 'NO_TITLE'
    elif len(after_title) < 3:
        freeze = 'UNOBSERVED'
    elif all(f[1] > 0.5 for f in after_title):
        freeze = 'FROZEN'
    else:
        freeze = 'OK'
    handoff_error = 'trouble handing off the window' in text or 'ERROR DISPLAY' in text
    if handoff_error:
        freeze = 'EARLY_DISPLAY_HANDOFF_FAILED'
    own_frames = sorted(set(own_traces(text)))
    crashes = sorted(p.name for p in (work / 'crash-reports').glob('*.txt')) if (work / 'crash-reports').is_dir() else []
    own_errors = [line for line in text.splitlines()
                  if ('/ERROR]' in line or '/FATAL]' in line) and ('immersive_tanning' in line or 'immersivetanning' in line)]
    result = None
    if (work / 'smoketest-result.json').exists():
        result = json.loads((work / 'smoketest-result.json').read_text())
    passed = (freeze in ('OK', 'UNOBSERVED') and title_at is not None and not own_frames and not own_errors and not crashes
              and (agent_jar is None or (result is not None and result.get('pass'))))
    summary = {'pass': passed, 'freeze': freeze, 'title_seconds': title_at, 'elapsed_seconds': round(elapsed),
               'exit_code': proc.returncode, 'agent_result': result, 'own_stack_frames': own_frames[:20],
               'own_error_lines': own_errors[:20], 'crash_reports': crashes, 'frames': frames}
    (work / 'summary.json').write_text(json.dumps(summary, indent=2))
    print(f'[packtest] {"PASS" if passed else "FAIL"}: freeze={freeze}, title={title_at and round(title_at)}s, '
          f'agent={result and result.get("pass")}, own stack traces={len(own_frames)}, own errors={len(own_errors)}, '
          f'crash reports={len(crashes)}')
    print(f'[packtest] evidence: {work}/summary.json, {work}/client.log, {captures}/')
    sys.exit(0 if passed else 1)


# Frames from these packages never decide who threw: the first frame outside them names the responsible mod.
PLATFORM_PACKAGES = ('java.', 'javax.', 'jdk.', 'sun.', 'net.minecraft.', 'com.mojang.', 'net.minecraftforge.',
                     'cpw.mods.', 'org.spongepowered.', 'com.llamalad7.', 'com.google.', 'it.unimi.', 'io.netty.',
                     'org.apache.', 'org.lwjgl.')


# Our wrappers that only delegate to another mod's (or vanilla's) code: villager trade listings. When the exception was
# thrown deeper, in the listing they wrap (JustEnoughResources asks every listing for an offer with no trader), they
# are skipped like platform frames; as the innermost frame they still count.
PASS_THROUGH_FRAMES = ('com.otectus.immersivetanning.leather.BindingLeather.lambda$trade$',
                       'com.otectus.immersivetanning.compat.villager.TanningVillagerTrades.lambda$villageMade$')


def own_traces(text):
    """Innermost non-platform frame of every stack trace (and each Caused by) that belongs to Immersive Tanning.

    Our frames deeper in a stack do not count: the pack-test agent creating a world is on the stack whenever
    another mod's reload listener logs an exception, and that exception is not ours."""
    found, current = [], []
    for line in text.splitlines() + ['']:
        stripped = line.strip()
        if stripped.startswith('at '):
            current.append(stripped)
            continue
        if current:
            first = next((f for i, f in enumerate(current) if not f[3:].startswith(PLATFORM_PACKAGES)
                          and not (i > 0 and f[3:].startswith(PASS_THROUGH_FRAMES))), None)
            if first and first[3:].startswith('com.otectus.immersivetanning'):
                found.append(first)
            current = []
    return found


if __name__ == '__main__':
    main()
