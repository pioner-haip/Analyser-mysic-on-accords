package chordfinder;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Оценка «насколько вектор хромы похож на аккорд».
 *
 * Хрома — 12 чисел: сколько энергии пришлось на каждую из 12 нот (C, C#, ... B) в кадре.
 * Для аккорда строим шаблон из 0 и 1 (1 на нотах аккорда) и считаем косинус угла между векторами:
 * 1.0 — идеально, 0 — ничего общего. К косинусу добавляем три небольших поправки:
 *   + бонус, если самая сильная нота баса совпадает с корнем аккорда (бас почти всегда играет корень);
 *   + бонус аккордам, которые «свои» для тональности песни (в песне чаще встречаются аккорды лада);
 *   - штраф септаккордам: трезвучие — выбор по умолчанию, 7-я нота должна быть реально слышна.
 */
public class ChordTemplateMatcher {
    public static final double BASS_WEIGHT = 0.20;
    public static final double KEY_BONUS = 0.03;
    public static final double SEVENTH_PENALTY = 0.06;

    /** Все 24 трезвучия (12 мажорных + 12 минорных). */
    public static final List<Chord> TRIADS = new ArrayList<>();
    static {
        for (Note n : Note.values()) TRIADS.add(new Chord(n, ChordType.MAJOR));
        for (Note n : Note.values()) TRIADS.add(new Chord(n, ChordType.MINOR));
    }

    /** Заранее подготовленный набор состояний (аккордов) для быстрой оценки кадров. */
    public static final class Scorer {
        public final List<Chord> states;
        private final double[][] template;
        private final double[] templateNorm;
        private final int[] root;
        private final double[] prior;

        /** @param diatonic аккорды тональности песни (может быть null) */
        public Scorer(List<Chord> states, Set<Chord> diatonic) {
            this.states = states;
            int s = states.size();
            template = new double[s][12];
            templateNorm = new double[s];
            root = new int[s];
            prior = new double[s];
            for (int i = 0; i < s; i++) {
                Chord c = states.get(i);
                for (int interval : c.type.intervals) template[i][(c.root.pitchClass + interval) % 12] = 1.0;
                templateNorm[i] = Math.sqrt(c.type.intervals.length);
                root[i] = c.root.pitchClass;
                if (c.type.isSeventh()) prior[i] -= SEVENTH_PENALTY;
                if (diatonic != null && diatonic.contains(c.simplified())) prior[i] += KEY_BONUS;
            }
        }

        public int size() { return states.size(); }

        /**
         * @param chroma хрома кадра, нормированная по длине (L2 = 1)
         * @param bass   доли нот в басовом диапазоне (сумма = 1) или нули, если баса не слышно
         * @param out    сюда пишется оценка для каждого состояния
         */
        public void score(double[] chroma, double[] bass, double[] out) {
            for (int i = 0; i < template.length; i++) {
                double dot = 0;
                for (int k = 0; k < 12; k++) dot += chroma[k] * template[i][k];
                out[i] = dot / templateNorm[i] + BASS_WEIGHT * bass[root[i]] + prior[i];
            }
        }
    }

    /** Результат разового сопоставления (для простых случаев и отладки). */
    public static class Match {
        public final Chord chord; // null = тишина/шум
        public final double confidence;
        public Match(Chord chord, double confidence) {
            this.chord = chord;
            this.confidence = confidence;
        }
    }

    public static Match match(double[] chroma) {
        return match(chroma, null);
    }

    public static Match match(double[] chroma, Integer bassRootHint) {
        double norm = 0;
        for (double v : chroma) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm < 1e-9) return new Match(null, 0);
        double[] unit = new double[12];
        for (int i = 0; i < 12; i++) unit[i] = chroma[i] / norm;
        double[] bass = new double[12];
        if (bassRootHint != null) bass[bassRootHint] = 1.0;
        Scorer scorer = new Scorer(TRIADS, null);
        double[] out = new double[scorer.size()];
        scorer.score(unit, bass, out);
        int best = 0;
        for (int i = 1; i < out.length; i++) if (out[i] > out[best]) best = i;
        return new Match(TRIADS.get(best), out[best]);
    }
}
