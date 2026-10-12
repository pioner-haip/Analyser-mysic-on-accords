package chordfinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Распознавание аккордов прямо из звука. Улучшения точности по сравнению с первой версией:
 * - окна анализа перекрываются (более плавный, "не дёрганый" результат);
 * - сглаживание голосованием большинства по соседним окнам (убирает единичные ошибки);
 * - учёт басовой ноты как подсказки корня аккорда.
 */
public class AudioChordDetector {
    private static final int WINDOW_SIZE = 8192;
    private static final int HOP_SIZE = WINDOW_SIZE / 2; // 50% перекрытие
    private static final double MIN_FREQ = 80;
    private static final double MAX_FREQ = 4000;
    private static final double BASS_MIN_FREQ = 60;
    private static final double BASS_MAX_FREQ = 300;
    private static final int SMOOTH_RADIUS = 2;

    public static List<ChordEvent> analyze(AudioLoader.Samples audio) {
        double[] samples = audio.data;
        float sampleRate = audio.sampleRate;

        int numWindows = (samples.length - WINDOW_SIZE) / HOP_SIZE + 1;
        if (numWindows < 1) return new ArrayList<>();

        Chord[] raw = new Chord[numWindows];
        for (int w = 0; w < numWindows; w++) {
            int offset = w * HOP_SIZE;
            double[] real = new double[WINDOW_SIZE];
            double[] imag = new double[WINDOW_SIZE];
            for (int i = 0; i < WINDOW_SIZE; i++) {
                double hann = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (WINDOW_SIZE - 1));
                real[i] = samples[offset + i] * hann;
            }
            SimpleFFT.fft(real, imag);

            double[] chroma = new double[12];
            double[] bassChroma = new double[12];
            for (int k = 1; k < WINDOW_SIZE / 2; k++) {
                double freq = k * sampleRate / WINDOW_SIZE;
                if (freq < MIN_FREQ || freq > MAX_FREQ) continue;
                double magnitude = Math.hypot(real[k], imag[k]);
                double midi = 69 + 12 * (Math.log(freq / 440.0) / Math.log(2));
                int pitchClass = (((int) Math.round(midi)) % 12 + 12) % 12;
                chroma[pitchClass] += magnitude;
                if (freq >= BASS_MIN_FREQ && freq <= BASS_MAX_FREQ) {
                    bassChroma[pitchClass] += magnitude;
                }
            }

            Integer bassHint = null;
            double bassMax = 0, bassTotal = 0;
            int bassArg = -1;
            for (double v : bassChroma) bassTotal += v;
            for (int pc = 0; pc < 12; pc++) {
                if (bassChroma[pc] > bassMax) { bassMax = bassChroma[pc]; bassArg = pc; }
            }
            if (bassArg >= 0 && bassTotal > 0 && bassMax / bassTotal > 0.3) {
                bassHint = bassArg;
            }

            raw[w] = ChordTemplateMatcher.match(chroma, bassHint).chord;
        }

        Chord[] smoothed = smooth(raw);

        List<ChordEvent> rawEvents = new ArrayList<>();
        for (int w = 0; w < numWindows; w++) {
            double startSec = (w * HOP_SIZE) / sampleRate;
            double endSec = ((w * HOP_SIZE) + HOP_SIZE) / sampleRate;
            rawEvents.add(new ChordEvent(formatTime(startSec) + "\u2013" + formatTime(endSec), smoothed[w]));
        }

        return mergeConsecutive(rawEvents);
    }

    /** Голосование большинства по соседним окнам — убирает единичные "дребезги" распознавания. */
    private static Chord[] smooth(Chord[] raw) {
        Chord[] result = new Chord[raw.length];
        for (int i = 0; i < raw.length; i++) {
            Map<String, Integer> counts = new HashMap<>();
            Map<String, Chord> byKey = new HashMap<>();
            for (int j = Math.max(0, i - SMOOTH_RADIUS); j <= Math.min(raw.length - 1, i + SMOOTH_RADIUS); j++) {
                Chord c = raw[j];
                String key = c == null ? "-" : c.name();
                counts.merge(key, 1, Integer::sum);
                byKey.put(key, c);
            }
            String bestKey = null;
            int bestCount = -1;
            for (Map.Entry<String, Integer> e : counts.entrySet()) {
                if (e.getValue() > bestCount) {
                    bestCount = e.getValue();
                    bestKey = e.getKey();
                }
            }
            result[i] = byKey.get(bestKey);
        }
        return result;
    }

    private static List<ChordEvent> mergeConsecutive(List<ChordEvent> events) {
        List<ChordEvent> merged = new ArrayList<>();
        int i = 0;
        while (i < events.size()) {
            Chord current = events.get(i).chord;
            String[] firstParts = events.get(i).label.split("\u2013");
            String startLabel = firstParts[0];
            String endLabel = firstParts[1];
            int j = i;
            while (j + 1 < events.size() && sameChord(events.get(j + 1).chord, current)) {
                j++;
                endLabel = events.get(j).label.split("\u2013")[1];
            }
            if (current != null) {
                merged.add(new ChordEvent(startLabel + "\u2013" + endLabel, current));
            }
            i = j + 1;
        }
        return merged;
    }

    private static boolean sameChord(Chord a, Chord b) {
        if (a == null || b == null) return a == b;
        return a.root == b.root && a.type == b.type;
    }

    private static String formatTime(double seconds) {
        int m = (int) (seconds / 60);
        int s = (int) (seconds % 60);
        return String.format("%d:%02d", m, s);
    }
}
