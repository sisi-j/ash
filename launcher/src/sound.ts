/**
 * LAUNCH GAME's sounds, synthesised rather than shipped as files: nothing to
 * licence, and exactly the sounds approved in the mockup, whose recipe this
 * is (`docs/research/0008`).
 *
 * "click" is a short noise sweep under two rising tones, played on the click;
 * "start" is the same two tones higher and quieter, when the game is up.
 */
export type LaunchSound = "click" | "start";

let context: AudioContext | null = null;

export function playLaunchSound(kind: LaunchSound) {
  try {
    context ??= new AudioContext();
    const now = context.currentTime;
    const out = context.createGain();
    out.gain.value = kind === "start" ? 0.12 : 0.2;
    out.connect(context.destination);

    if (kind === "click") {
      const length = 0.35;
      const buffer = context.createBuffer(1, Math.floor(context.sampleRate * length), context.sampleRate);
      const samples = buffer.getChannelData(0);
      for (let i = 0; i < samples.length; i++) {
        samples[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / samples.length, 2);
      }
      const noise = context.createBufferSource();
      const sweep = context.createBiquadFilter();
      const level = context.createGain();
      noise.buffer = buffer;
      sweep.type = "bandpass";
      sweep.Q.value = 0.9;
      sweep.frequency.setValueAtTime(500, now);
      sweep.frequency.exponentialRampToValueAtTime(3200, now + length);
      level.gain.value = 0.7;
      noise.connect(sweep).connect(level).connect(out);
      noise.start(now);
    }

    for (const [hz, at] of [
      [660, 0.05],
      [990, 0.13],
    ] as const) {
      const tone = context.createOscillator();
      const envelope = context.createGain();
      tone.type = "sine";
      tone.frequency.value = kind === "start" ? hz * 1.5 : hz;
      envelope.gain.setValueAtTime(0, now + at);
      envelope.gain.linearRampToValueAtTime(0.5, now + at + 0.015);
      envelope.gain.exponentialRampToValueAtTime(0.001, now + at + 0.6);
      tone.connect(envelope).connect(out);
      tone.start(now + at);
      tone.stop(now + at + 0.65);
    }
  } catch {
    // No audio device: the button's own feedback still says it was clicked.
  }
}
