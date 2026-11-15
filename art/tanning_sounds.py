"""Editable source of Immersive Tanning's own sounds (1.3). Deterministic synthesis: fixed seeds, no samples.

Every sound here is generated from noise, filters and envelopes by this file alone; nothing is recorded or taken
from another work, so there is nothing to attribute. OGG files are delivery exports: change this source, then run
tools/generate_sounds.py --write (needs ffmpeg with libvorbis). tools/generate_sounds.py --check verifies the
exports against this source.

Loops (scrape, sweep) end where they begin, so they repeat without a click.
"""
import math

import numpy as np

RATE = 44100


def _rng(seed):
    return np.random.default_rng(seed)


def _t(seconds):
    return np.arange(int(seconds * RATE)) / RATE


def _biquad(x, b0, b1, b2, a1, a2):
    y = np.zeros_like(x)
    x1 = x2 = y1 = y2 = 0.0
    for i in range(len(x)):
        xi = x[i]
        yi = b0 * xi + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, xi
        y2, y1 = y1, yi
        y[i] = yi
    return y


def bandpass(x, freq, q):
    """RBJ band-pass (constant 0 dB peak gain); {@code freq} may be an array for a sweep (coarse, per block)."""
    if np.ndim(freq) == 0:
        w = 2 * math.pi * freq / RATE
        alpha = math.sin(w) / (2 * q)
        a0 = 1 + alpha
        return _biquad(x, alpha / a0, 0.0, -alpha / a0, -2 * math.cos(w) / a0, (1 - alpha) / a0)
    out = np.zeros_like(x)
    block = 256
    state = [0.0, 0.0, 0.0, 0.0]
    for start in range(0, len(x), block):
        f = float(freq[min(start, len(freq) - 1)])
        w = 2 * math.pi * f / RATE
        alpha = math.sin(w) / (2 * q)
        a0 = 1 + alpha
        b0, b2, a1, a2 = alpha / a0, -alpha / a0, -2 * math.cos(w) / a0, (1 - alpha) / a0
        x1, x2, y1, y2 = state
        for i in range(start, min(start + block, len(x))):
            xi = x[i]
            yi = b0 * xi + b2 * x2 - a1 * y1 - a2 * y2
            x2, x1 = x1, xi
            y2, y1 = y1, yi
            out[i] = yi
        state = [x1, x2, y1, y2]
    return out


def lowpass(x, freq):
    w = 2 * math.pi * freq / RATE
    alpha = math.sin(w) / (2 * 0.7071)
    a0 = 1 + alpha
    c = math.cos(w)
    return _biquad(x, (1 - c) / 2 / a0, (1 - c) / a0, (1 - c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)


def highpass(x, freq):
    w = 2 * math.pi * freq / RATE
    alpha = math.sin(w) / (2 * 0.7071)
    a0 = 1 + alpha
    c = math.cos(w)
    return _biquad(x, (1 + c) / 2 / a0, -(1 + c) / a0, (1 + c) / 2 / a0, -2 * c / a0, (1 - alpha) / a0)


def env(n, attack, decay, sustain=0.0, release=0.0):
    """Attack/decay/release envelope over n samples (seconds)."""
    t = np.arange(n) / RATE
    e = np.ones(n)
    if attack > 0:
        e *= np.clip(t / attack, 0, 1)
    if decay > 0:
        e *= sustain + (1 - sustain) * np.exp(-np.maximum(0, t - attack) / decay)
    if release > 0:
        end = n / RATE
        e *= np.clip((end - t) / release, 0, 1)
    return e


def blip(n, at, freq_from, freq_to, length, amp, rng_phase=0.0):
    """A short gliding sine (a drop, a plop, a squeak) placed at {@code at} seconds."""
    out = np.zeros(n)
    s = int(at * RATE)
    m = min(int(length * RATE), n - s)
    if m <= 0:
        return out
    t = np.arange(m) / RATE
    f = np.linspace(freq_from, freq_to, m)
    phase = 2 * math.pi * np.cumsum(f) / RATE + rng_phase
    out[s:s + m] = amp * np.sin(phase) * np.exp(-t / (length / 4)) * np.clip(t / 0.002, 0, 1)
    return out


def click(n, at, freq, length, amp, rng):
    out = np.zeros(n)
    s = int(at * RATE)
    m = min(int(length * RATE), n - s)
    if m <= 0:
        return out
    burst = rng.standard_normal(m) * np.exp(-np.arange(m) / (length * RATE / 5))
    out[s:s + m] = amp * bandpass(burst, freq, 1.2)
    return out


def loop(x, fade=0.18):
    """Makes a seamless loop: the tail is crossfaded into the head and cut off."""
    f = int(fade * RATE)
    head = x[:f].copy()
    tail = x[-f:]
    ramp = np.linspace(0, 1, f)
    x = x[:-f].copy()
    x[:f] = head * ramp + tail * (1 - ramp)
    return x


def normalize(x, peak=0.8):
    m = np.max(np.abs(x))
    return x if m <= 0 else x * (peak / m)


# ---------------------------------------------------------------------------------------------- the rack

def scrape():
    """The blade drawn through flesh: a bright, gritty rasp in irregular strokes (loop)."""
    rng = _rng(101)
    n = int(1.8 * RATE)
    noise = rng.standard_normal(n)
    rasp = bandpass(noise, 3200, 1.4) + 0.5 * bandpass(noise, 1500, 2.0)
    t = np.arange(n) / RATE
    strokes = 0.6 + 0.4 * np.abs(np.sin(2 * math.pi * 3.1 * t + 0.6 * np.sin(2 * math.pi * 0.7 * t)))
    grit = (rng.random(n) < 0.004) * rng.standard_normal(n) * 4
    rasp = rasp * strokes + 0.4 * bandpass(grit, 2400, 3.0)
    return normalize(loop(rasp), 0.55)


def scrape_rough():
    """The blade on leather already clean: dull, dry and catching."""
    rng = _rng(102)
    n = int(0.45 * RATE)
    noise = rng.standard_normal(n)
    x = bandpass(noise, 1500, 2.5) * env(n, 0.02, 0.18)
    for k in range(6):
        x += click(n, 0.04 + k * 0.055 + rng.random() * 0.02, 900 + rng.random() * 500, 0.012, 0.6, rng)
    return normalize(x, 0.6)


def wet_leather():
    """A wet hide slaps and settles."""
    rng = _rng(103)
    n = int(0.4 * RATE)
    noise = rng.standard_normal(n)
    slap = bandpass(noise, 700, 1.1) * env(n, 0.002, 0.05)
    squelch = bandpass(noise, np.linspace(650, 260, n), 3.0) * env(n, 0.03, 0.12) * 0.7
    return normalize(slap + squelch, 0.7)


def lace():
    """Cord drawn through the frame's holes, then a creak as it takes the load."""
    rng = _rng(104)
    n = int(0.55 * RATE)
    noise = rng.standard_normal(n)
    zip_ = bandpass(noise, np.linspace(1800, 2600, n), 2.5) * np.linspace(0.3, 1.0, n) * env(n, 0.01, 0.0, 1.0, 0.08)
    t = np.arange(n) / RATE
    creak = np.sign(np.sin(2 * math.pi * np.cumsum(np.linspace(140, 210, n)) / RATE)) * (t > 0.38) * np.exp(-np.maximum(0, t - 0.38) / 0.05)
    return normalize(zip_ + 0.25 * lowpass(creak, 1200), 0.6)


def seat():
    """The tension lever drops into its detent: a wooden clunk with a click."""
    rng = _rng(105)
    n = int(0.4 * RATE)
    x = blip(n, 0.0, 170, 150, 0.12, 1.0) + blip(n, 0.0, 330, 320, 0.06, 0.35)
    x += click(n, 0.0, 3000, 0.008, 0.5, rng)
    return normalize(x, 0.75)


def burnish():
    """The block pressed across the hide: a low, firm rub."""
    rng = _rng(106)
    n = int(0.75 * RATE)
    noise = rng.standard_normal(n)
    t = np.arange(n) / RATE
    x = bandpass(noise, 520, 1.4) * (0.55 + 0.45 * np.sin(2 * math.pi * 2.6 * t) ** 2) * env(n, 0.05, 0.0, 1.0, 0.2)
    return normalize(x, 0.6)


def smooth():
    """Soft leather smoothed and flexed: two cloth-like swells."""
    rng = _rng(107)
    n = int(0.65 * RATE)
    noise = rng.standard_normal(n)
    t = np.arange(n) / RATE
    swell = np.exp(-((t - 0.16) / 0.09) ** 2) + 0.8 * np.exp(-((t - 0.44) / 0.1) ** 2)
    x = bandpass(noise, 1500, 0.9) * swell
    return normalize(x, 0.5)


# ---------------------------------------------------------------------------------------------- the vat

def pour():
    """A bucket poured into the vat: a stream, then the surface settling with a few bubbles."""
    rng = _rng(201)
    n = int(1.4 * RATE)
    noise = rng.standard_normal(n)
    t = np.arange(n) / RATE
    stream = highpass(lowpass(noise, 2600), 280) * env(n, 0.08, 0.0, 1.0, 0.0) * np.clip((1.0 - t) / 0.25, 0, 1)
    stream *= 0.8 + 0.2 * np.sin(2 * math.pi * 11 * t + 2 * np.sin(2 * math.pi * 1.3 * t))
    bubbles = np.zeros(n)
    for k in range(34):
        at = rng.random() * 1.25
        f = 450 + rng.random() * 800
        bubbles += blip(n, at, f, f * 1.25, 0.035, 0.18 + rng.random() * 0.12)
    return normalize(stream + bubbles, 0.7)


def bark():
    """Bark scattered into the liquor: small woody clicks, then light plops."""
    rng = _rng(202)
    n = int(0.7 * RATE)
    x = np.zeros(n)
    for k in range(7):
        x += click(n, 0.02 + rng.random() * 0.2, 1800 + rng.random() * 1700, 0.01, 0.5, rng)
    for k in range(6):
        at = 0.18 + rng.random() * 0.4
        x += blip(n, at, 900 + rng.random() * 300, 380, 0.05, 0.35)
    return normalize(x, 0.6)


def sweep():
    """The paddle drawn slowly through the liquor (loop)."""
    rng = _rng(203)
    n = int(1.6 * RATE)
    noise = rng.standard_normal(n)
    t = np.arange(n) / RATE
    swish = bandpass(noise, 480, 1.0) * (0.5 + 0.5 * np.sin(2 * math.pi * 0.9 * t) ** 2)
    bubbles = np.zeros(n)
    for k in range(10):
        f = 350 + rng.random() * 500
        bubbles += blip(n, rng.random() * 1.5, f, f * 1.3, 0.05, 0.12)
    return normalize(loop(swish + bubbles), 0.5)


def lift():
    """Tanned hides lifted out: a heavy splash, then drips."""
    rng = _rng(204)
    n = int(1.0 * RATE)
    noise = rng.standard_normal(n)
    x = bandpass(noise, 900, 0.8) * env(n, 0.01, 0.12)
    for k in range(9):
        at = 0.15 + k * 0.08 + rng.random() * 0.04
        f = 1300 + rng.random() * 800
        x += blip(n, at, f, f * 0.7, 0.03, 0.3)
    return normalize(x, 0.7)


# ---------------------------------------------------------------------------------------------- the bench

def cut():
    """A blade drawn through leather on the mat: a smooth, rising slice."""
    rng = _rng(301)
    n = int(0.55 * RATE)
    noise = rng.standard_normal(n)
    t = np.arange(n) / RATE
    slice_ = bandpass(noise, np.linspace(2400, 3200, n), 2.0) * np.sin(math.pi * np.clip(t / 0.5, 0, 1)) ** 0.8
    drag = bandpass(noise, 420, 1.5) * 0.3 * np.sin(math.pi * np.clip(t / 0.5, 0, 1))
    return normalize(slice_ + drag, 0.6)


def panel():
    """A cut panel set down on the jig."""
    rng = _rng(302)
    n = int(0.3 * RATE)
    noise = rng.standard_normal(n)
    x = blip(n, 0.0, 130, 110, 0.08, 0.8) + bandpass(noise, 650, 1.2) * env(n, 0.002, 0.05) * 0.6
    return normalize(x, 0.6)


def needle():
    """The awl pierces the leather."""
    rng = _rng(303)
    n = int(0.2 * RATE)
    noise = rng.standard_normal(n)
    x = click(n, 0.0, 4200, 0.004, 0.8, rng) + bandpass(noise, 1500, 2.0) * env(n, 0.004, 0.035) * 0.6
    return normalize(x, 0.6)


def tighten():
    """Thread drawn through and pulled snug, with a slight squeak at the end."""
    rng = _rng(304)
    n = int(0.45 * RATE)
    noise = rng.standard_normal(n)
    zip_ = bandpass(noise, np.linspace(1200, 3000, n), 3.0) * env(n, 0.02, 0.0, 1.0, 0.06) * np.linspace(0.4, 1.0, n)
    squeak = blip(n, 0.3, 900, 1300, 0.12, 0.15)
    return normalize(zip_ + squeak, 0.6)


def tie():
    """The last knot: two tugs and a soft tap."""
    rng = _rng(305)
    n = int(0.42 * RATE)
    noise = rng.standard_normal(n)
    x = np.zeros(n)
    for at in (0.0, 0.13):
        s = int(at * RATE)
        m = int(0.06 * RATE)
        x[s:s + m] += bandpass(noise[s:s + m], 1500, 2.0) * env(m, 0.004, 0.02)
    x += blip(n, 0.27, 260, 230, 0.08, 0.5)
    return normalize(x, 0.6)


def page():
    """A page of the pattern book turned."""
    rng = _rng(306)
    n = int(0.4 * RATE)
    noise = rng.standard_normal(n)
    t = np.arange(n) / RATE
    x = highpass(noise, 2200) * env(n, 0.03, 0.12) * (0.6 + 0.4 * np.abs(np.sin(2 * math.pi * 28 * t)))
    return normalize(x, 0.45)


def loosen():
    """A stitch let out again: the thread slides back."""
    rng = _rng(307)
    n = int(0.35 * RATE)
    noise = rng.standard_normal(n)
    x = bandpass(noise, np.linspace(2600, 1000, n), 2.5) * env(n, 0.01, 0.0, 1.0, 0.08)
    return normalize(x, 0.5)


def sounds():
    """Every sound by its path under assets/immersive_tanning/sounds/ (without .ogg)."""
    return {
        'rack/scrape': scrape(),
        'rack/scrape_rough': scrape_rough(),
        'rack/wet_leather': wet_leather(),
        'rack/lace': lace(),
        'rack/seat': seat(),
        'rack/burnish': burnish(),
        'rack/smooth': smooth(),
        'vat/pour': pour(),
        'vat/bark': bark(),
        'vat/sweep': sweep(),
        'vat/lift': lift(),
        'bench/cut': cut(),
        'bench/panel': panel(),
        'bench/needle': needle(),
        'bench/tighten': tighten(),
        'bench/tie': tie(),
        'bench/page': page(),
        'bench/loosen': loosen(),
    }
