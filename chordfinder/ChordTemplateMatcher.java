package chordfinder;

/**
 * Сравнивает вектор хромы с шаблонами всех 24 трезвучий (12 мажор + 12 минор).
 * Может учитывать басовую ноту как подсказку корня — в реальной музыке бас почти
 * всегда играет корень аккорда, это заметно повышает точность распознавания.
 */
public class ChordTemplateMatcher {
    public static class Match {
        public final Chord chord; // null = тишина/шум
        public final double confidence;
        public Match(Chord chord, double confidence) {
            this.chord = chord;
            this.confidence = confidence;
        }
    }

    private static final double SILENCE_THRESHOLD = 0.15;
    private static final double BASS_BONUS = 0.18;

    public static Match match(double[] chroma) {
        return match(chroma, null);
    }

    public static Match match(double[] chroma, Integer bassRootHint) {
        if (norm(chroma) < SILENCE_THRESHOLD) {
            return new Match(null, 0);
        }
        double best = -1;
        Chord bestChord = null;
        for (int root = 0; root < 12; root++) {
            for (ChordType type : new ChordType[]{ChordType.MAJOR, ChordType.MINOR}) {
                double score = cosine(chroma, template(root, type));
                if (bassRootHint != null && bassRootHint == root) {
                    score += BASS_BONUS;
                }
                if (score > best) {
                    best = score;
                    bestChord = new Chord(Note.values()[root], type);
                }
            }
        }
        return new Match(bestChord, best);
    }

    private static double[] template(int root, ChordType type) {
        double[] t = new double[12];
        for (int interval : type.intervals) {
            t[(root + interval) % 12] = 1.0;
        }
        return t;
    }

    private static double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < 12; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return 0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private static double norm(double[] a) {
        double s = 0;
        for (double v : a) s += v * v;
        return Math.sqrt(s);
    }
}
