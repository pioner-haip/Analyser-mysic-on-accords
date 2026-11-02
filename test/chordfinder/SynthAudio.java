package chordfinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Генератор тестового аудио: «гитарный» бой (алгоритм Карплюса–Стронга), бас, шум и
 * необязательная «вокальная» мелодия. Нужен, чтобы проверять распознавание аккордов
 * без реальных записей и заранее знать правильный ответ.
 */
public class SynthAudio {
    public static final float SR = 44100f;

    public static class Truth {
        public final double start, end;
        public final String name;
        public Truth(double start, double end, String name) { this.start = start; this.end = end; this.name = name; }
    }

    public static class Options {
        public double bpm = 100;
        public int beatsPerChord = 4;
        public int loops = 4;
        public double tuningCents = 0;
        public double noise = 0.02;
        public boolean bass = true;
        public boolean melody = false;
        public double melodyLevel = 0.25;
        public long seed = 1;
    }

    public final AudioLoader.Samples samples;
    public final List<Truth> truth;

    private SynthAudio(AudioLoader.Samples s, List<Truth> t) { samples = s; truth = t; }

    public static SynthAudio progression(String[] chords, Options o) {
        Random rnd = new Random(o.seed);
        double beat = 60.0 / o.bpm;
        double chordSec = beat * o.beatsPerChord;
        int total = chords.length * o.loops;
        double lead = 0.4, tail = 1.0;
        int n = (int) ((lead + total * chordSec + tail) * SR);
        double[] out = new double[n];
        List<Truth> truth = new ArrayList<>();
        double tune = Math.pow(2, o.tuningCents / 1200.0);

        for (int c = 0; c < total; c++) {
            String name = chords[c % chords.length];
            double t0 = lead + c * chordSec;
            truth.add(new Truth(t0, t0 + chordSec, name));
            Chord chord = Chord.parse(name);
            int[] frets = ChordLibrary.variations(chord).get(0).frets;
            double[] open = {82.41, 110.0, 146.83, 196.0, 246.94, 329.63};
            for (int b = 0; b < o.beatsPerChord; b++) {
                double strumT = t0 + b * beat;
                boolean down = (b % 2 == 0);
                int k = 0;
                for (int s = 0; s < 6; s++) {
                    int si = down ? s : 5 - s;
                    if (frets[si] < 0) continue;
                    double f = open[si] * Math.pow(2, frets[si] / 12.0) * tune;
                    pluck(out, strumT + k * 0.011, f, 0.35 * (0.8 + 0.4 * rnd.nextDouble()), beat * 1.6, rnd);
                    k++;
                }
            }
            if (o.bass) {
                double rootF = 41.2 * Math.pow(2, ((chord.root.pitchClass - 4 + 12) % 12) / 12.0) * tune;
                if (rootF < 55) rootF *= 2;
                for (int b = 0; b < o.beatsPerChord; b += 2) {
                    addTone(out, t0 + b * beat, beat * 1.8, rootF, 0.30, 3);
                }
            }
        }
        if (o.melody) {
            int[] scale = {9, 11, 0, 2, 4, 5, 7}; // A natural minor
            double t = lead;
            double end = lead + total * chordSec;
            while (t < end) {
                int pc = scale[rnd.nextInt(scale.length)];
                double f = 440.0 * Math.pow(2, (pc - 9 + 12) / 12.0 - (rnd.nextBoolean() ? 0 : 1)) * tune;
                double len = beat * (0.5 + 0.5 * rnd.nextInt(3));
                addTone(out, t, len * 0.95, f, o.melodyLevel, 4);
                t += len;
            }
        }
        for (int i = 0; i < n; i++) out[i] += o.noise * (rnd.nextDouble() * 2 - 1);
        double peak = 0;
        for (double v : out) peak = Math.max(peak, Math.abs(v));
        if (peak > 0) for (int i = 0; i < n; i++) out[i] = out[i] / peak * 0.8;
        return new SynthAudio(new AudioLoader.Samples(out, SR), truth);
    }

    private static void pluck(double[] out, double startSec, double freq, double amp, double dur, Random rnd) {
        int start = (int) (startSec * SR);
        int len = (int) Math.max(2, Math.round(SR / freq));
        double[] buf = new double[len];
        for (int i = 0; i < len; i++) buf[i] = rnd.nextDouble() * 2 - 1;
        int total = (int) (dur * SR);
        int idx = 0;
        double decay = 0.9965;
        for (int i = 0; i < total && start + i < out.length; i++) {
            double cur = buf[idx];
            double next = buf[(idx + 1) % len];
            buf[idx] = decay * 0.5 * (cur + next);
            if (start + i >= 0) out[start + i] += amp * cur;
            idx = (idx + 1) % len;
        }
    }

    private static void addTone(double[] out, double startSec, double dur, double f, double amp, int harmonics) {
        int start = (int) (startSec * SR);
        int len = (int) (dur * SR);
        for (int i = 0; i < len && start + i < out.length; i++) {
            double t = i / (double) SR;
            double env = Math.min(1, t / 0.01) * Math.exp(-t * 1.5);
            double v = 0;
            for (int h = 1; h <= harmonics; h++) v += Math.sin(2 * Math.PI * f * h * t) / h;
            out[start + i] += amp * env * v;
        }
    }
}
