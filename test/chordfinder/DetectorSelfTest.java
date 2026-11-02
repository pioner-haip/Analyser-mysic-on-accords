package chordfinder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Самопроверка анализатора на синтетическом аудио с известным ответом (без JUnit — просто main).
 * Запуск: см. README. Код возврата 0 — все сценарии пройдены.
 */
public class DetectorSelfTest {
    static int failures = 0;

    interface Setup { void apply(SynthAudio.Options o); }

    public static void main(String[] args) {
        String[] amFCG = {"Am", "F", "C", "G"};
        run("Am F C G, чистый звук", amFCG, o -> {}, true);
        run("Am F C G, шум + мелодия поверх", amFCG, o -> { o.noise = 0.08; o.melody = true; }, true);
        run("Am F C G, строй +35 цент + мелодия", amFCG, o -> { o.tuningCents = 35; o.melody = true; }, true);
        run("Am F C G, без баса, строй -30", amFCG, o -> { o.bass = false; o.tuningCents = -30; }, true);
        run("D A Bm G, 140 уд/мин, по 2 доли (баррэ)", new String[]{"D", "A", "Bm", "G"},
                o -> { o.bpm = 140; o.beatsPerChord = 2; o.loops = 8; }, true);
        run("C G Am Em F C F G, 8 аккордов в круге", new String[]{"C", "G", "Am", "Em", "F", "C", "F", "G"},
                o -> { o.loops = 3; o.melody = true; }, true);
        run("Em C G D, 80 уд/мин, шум", new String[]{"Em", "C", "G", "D"},
                o -> { o.bpm = 80; o.noise = 0.1; o.loops = 4; }, true);
        run("Dm Bb F C (бемоли, баррэ)", new String[]{"Dm", "A#", "F", "C"}, o -> { o.melody = true; }, true);
        run("СЕПТАККОРДЫ Am7 D7 G7 C, чистый звук", new String[]{"Am7", "D7", "G7", "C"}, o -> {}, true);
        run("СЕПТАККОРДЫ Em7 A7 Dm7 G7, шум, строй +20", new String[]{"Em7", "A7", "Dm7", "G7"},
                o -> { o.noise = 0.08; o.tuningCents = 20; }, true);

        System.out.println();
        System.out.println(failures == 0 ? "ВСЕ СЦЕНАРИИ ПРОЙДЕНЫ" : "ПРОВАЛЕНО СЦЕНАРИЕВ: " + failures);
        System.exit(failures == 0 ? 0 : 1);
    }

    static void run(String title, String[] prog, Setup setup, boolean checkTempo) {
        SynthAudio.Options o = new SynthAudio.Options();
        setup.apply(o);
        SynthAudio s = SynthAudio.progression(prog, o);
        long t0 = System.nanoTime();
        AudioChordDetector.Result r = AudioChordDetector.analyzeDetailed(s.samples, null);
        double sec = (System.nanoTime() - t0) / 1e9;

        Set<String> trueSet = new LinkedHashSet<>();          // правильные ТРЕЗВУЧИЯ
        Set<String> trueExt = new LinkedHashSet<>();          // правильные аккорды с септимами
        for (SynthAudio.Truth t : s.truth) { trueSet.add(triad(t.name)); trueExt.add(norm(t.name)); }
        boolean hasSevenths = !trueSet.equals(trueExt);
        Set<String> gotSet = new LinkedHashSet<>();
        StringBuilder seq = new StringBuilder();
        for (ChordEvent e : r.simple) { gotSet.add(e.chord.name()); seq.append(e.chord.name()).append(' '); }

        double acc = accuracy(s, r.simple, true);
        double accExt = accuracy(s, r.extended, false);
        double bpm = TempoEstimator.fromAudio(s.samples);
        double bpmErr = tempoError(bpm, o.bpm);

        boolean ok = gotSet.equals(trueSet)
                && r.simple.size() <= s.truth.size() + 2
                && acc >= 0.90
                && (!checkTempo || bpmErr < 0.06);

        // Расширенный режим (септаккорды) — экспериментальный: на синтетике бывают единичные ложные септимы
        // и пропуски настоящих. Критерием он не является, только выводится справкой.
        int sevenths = 0;
        for (ChordEvent e : r.extended) if (e.chord.type.isSeventh()) sevenths++;

        System.out.printf("%s %s%n", ok ? "[ OK ]" : "[FAIL]", title);
        System.out.printf("       истина: %d смен, %d разных %s%n", s.truth.size(), trueSet.size(), trueSet);
        System.out.printf("       получено: %d событий, %d разных %s, точность по времени %.0f%%, тональность %s, темп %.0f (истина %.0f), %.1f с%n",
                r.simple.size(), gotSet.size(), gotSet, acc * 100, r.key.name(), bpm, o.bpm, sec);
        System.out.printf("       [справка] расширенный режим: септаккордов найдено %d, точность с учётом септим %.0f%%%n",
                sevenths, accExt * 100);
        if (!ok) {
            StringBuilder ext = new StringBuilder();
            for (ChordEvent e : r.extended) ext.append(e.chord.name()).append(' ');
            System.out.println("       расширенная последовательность: " + ext);
            System.out.println("       последовательность: " + seq);
            failures++;
        }
    }

    static String norm(String name) { return Chord.parse(name).name(); }
    static String triad(String name) { return Chord.parse(name).simplified().name(); }

    /** Доля времени, в которое аккорд угадан; ±0.4 с вокруг смен аккордов не считаем (там граница «плавает»). */
    static double accuracy(SynthAudio s, List<ChordEvent> ev, boolean triadsOnly) {
        int ok = 0, total = 0;
        for (SynthAudio.Truth t : s.truth) {
            for (double x = t.start + 0.4; x < t.end - 0.4; x += 0.05) {
                total++;
                ChordEvent at = null;
                for (ChordEvent e : ev) if (x >= e.start && x < e.end) { at = e; break; }
                String want = triadsOnly ? triad(t.name) : norm(t.name);
                if (at != null && at.chord.name().equals(want)) ok++;
            }
        }
        return total == 0 ? 0 : ok / (double) total;
    }

    static double tempoError(double got, double truth) {
        double best = Double.MAX_VALUE;
        for (double m : new double[]{0.5, 1, 2}) best = Math.min(best, Math.abs(got - truth * m) / (truth * m));
        return best;
    }
}
