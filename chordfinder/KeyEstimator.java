package chordfinder;

import java.util.List;

/**
 * Оценка тональности произведения по распределению звуковысотных классов,
 * без ручного ввода. Алгоритм Крамхансла-Кесслера: сравнение гистограммы нот
 * с эмпирическими профилями устойчивости ступеней мажора/минора.
 */
public class KeyEstimator {
    private static final double[] MAJOR_PROFILE =
            {6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88};
    private static final double[] MINOR_PROFILE =
            {6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17};

    public static class KeyResult {
        public final Note tonic;
        public final ScaleType scale;
        public KeyResult(Note tonic, ScaleType scale) {
            this.tonic = tonic;
            this.scale = scale;
        }
    }

    /** Оценивает тональность по списку нот (весом служит частота встречаемости). */
    public static KeyResult estimate(List<Note> notes) {
        double[] histogram = new double[12];
        for (Note n : notes) histogram[n.pitchClass] += 1;

        double bestScore = Double.NEGATIVE_INFINITY;
        Note bestTonic = Note.C;
        ScaleType bestScale = ScaleType.MAJOR;

        for (int tonic = 0; tonic < 12; tonic++) {
            double majorScore = correlate(histogram, rotate(MAJOR_PROFILE, tonic));
            if (majorScore > bestScore) {
                bestScore = majorScore;
                bestTonic = Note.values()[tonic];
                bestScale = ScaleType.MAJOR;
            }
            double minorScore = correlate(histogram, rotate(MINOR_PROFILE, tonic));
            if (minorScore > bestScore) {
                bestScore = minorScore;
                bestTonic = Note.values()[tonic];
                bestScale = ScaleType.NATURAL_MINOR;
            }
        }
        return new KeyResult(bestTonic, bestScale);
    }

    private static double[] rotate(double[] profile, int shift) {
        double[] result = new double[12];
        for (int i = 0; i < 12; i++) {
            result[(i + shift) % 12] = profile[i];
        }
        return result;
    }

    private static double correlate(double[] a, double[] b) {
        double meanA = mean(a), meanB = mean(b);
        double num = 0, denomA = 0, denomB = 0;
        for (int i = 0; i < a.length; i++) {
            double da = a[i] - meanA, db = b[i] - meanB;
            num += da * db;
            denomA += da * da;
            denomB += db * db;
        }
        if (denomA == 0 || denomB == 0) return 0;
        return num / Math.sqrt(denomA * denomB);
    }

    private static double mean(double[] a) {
        double s = 0;
        for (double v : a) s += v;
        return s / a.length;
    }
}
