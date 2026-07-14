/**
 * sounds.ts — Sound effects using Howler.js
 *
 * Howler is used for audio playback.
 * If actual WAV/MP3 files are not present in /public/sounds/,
 * the Web Audio API fallback generates tones programmatically.
 *
 * To add custom sounds: place success.mp3 / review.mp3 in
 * the us-conversations/public/sounds/ directory.
 */
import { Howl } from 'howler';

// ---- Howl instances (lazy-loaded on first use) ----
let _successHowl: Howl | null = null;
let _reviewHowl: Howl | null = null;
let _soundsEnabled = true;

function getSuccessHowl(): Howl {
  if (!_successHowl) {
    _successHowl = new Howl({
      src: ['/sounds/success.mp3', '/sounds/success.wav'],
      volume: 0.55,
      preload: true,
      onloaderror: (_id, err) => {
        console.info('[sounds] success file not found, will use Web Audio fallback.', err);
        _successHowl = null;
      },
    });
  }
  return _successHowl;
}

function getReviewHowl(): Howl {
  if (!_reviewHowl) {
    _reviewHowl = new Howl({
      src: ['/sounds/review.mp3', '/sounds/review.wav'],
      volume: 0.45,
      preload: true,
      onloaderror: (_id, err) => {
        console.info('[sounds] review file not found, will use Web Audio fallback.', err);
        _reviewHowl = null;
      },
    });
  }
  return _reviewHowl;
}

// ---- Web Audio API fallback ----
function playTone(frequency: number, durationSec: number, volume = 0.25, startDelaySec = 0) {
  try {
    const AudioCtx = window.AudioContext ?? (window as any).webkitAudioContext;
    if (!AudioCtx) return;
    const ctx = new AudioCtx() as AudioContext;
    const osc = ctx.createOscillator();
    const gain = ctx.createGain();

    osc.connect(gain);
    gain.connect(ctx.destination);

    osc.type = 'sine';
    osc.frequency.setValueAtTime(frequency, ctx.currentTime + startDelaySec);

    gain.gain.setValueAtTime(0, ctx.currentTime + startDelaySec);
    gain.gain.linearRampToValueAtTime(volume, ctx.currentTime + startDelaySec + 0.01);
    gain.gain.exponentialRampToValueAtTime(
      0.001,
      ctx.currentTime + startDelaySec + durationSec
    );

    osc.start(ctx.currentTime + startDelaySec);
    osc.stop(ctx.currentTime + startDelaySec + durationSec);

    // Close context after tone finishes
    osc.onended = () => {
      ctx.close().catch(() => {});
    };
  } catch (e) {
    console.warn('[sounds] Web Audio API unavailable:', e);
  }
}

// ---- Public API ----

/** Enable or disable all sound effects globally */
export function setSoundsEnabled(enabled: boolean) {
  _soundsEnabled = enabled;
}

/**
 * Play a success / "mastered" chime.
 * Howl file: /public/sounds/success.mp3
 * Fallback: C5 → E5 → G5 ascending arpeggio
 */
export function playSuccess() {
  if (!_soundsEnabled) return;
  const howl = getSuccessHowl();
  if (howl && howl.state() === 'loaded') {
    howl.play();
    return;
  }
  // Fallback: ascending arpeggio (C5, E5, G5)
  playTone(523.25, 0.13, 0.28, 0.00);
  playTone(659.25, 0.13, 0.28, 0.13);
  playTone(783.99, 0.22, 0.28, 0.26);
}

/**
 * Play a "needs review" notification tone.
 * Howl file: /public/sounds/review.mp3
 * Fallback: descending two-note figure (A4 → F4)
 */
export function playReview() {
  if (!_soundsEnabled) return;
  const howl = getReviewHowl();
  if (howl && howl.state() === 'loaded') {
    howl.play();
    return;
  }
  // Fallback: gentle descending duo
  playTone(440.0, 0.10, 0.22, 0.00);
  playTone(349.23, 0.18, 0.22, 0.10);
}
