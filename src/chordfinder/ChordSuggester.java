package chordfinder;

import java.util.ArrayList;
import java.util.List;

public class ChordSuggester {

    /**
     * Находит аккорд из кандидатов, который содержит больше всего нот такта.
     * Первая нота такта (сильная доля) учитывается с двойным весом.
     */
    public static Chord bestChord(List<Note> measureNotes, List<Chord> candidates) {
        Chord best = null;
        int bestScore = -1;
        for (Chord c : candidates) {
            int score = 0;
            for (int i = 0; i < measureNotes.size(); i++) {
                if (c.contains(measureNotes.get(i))) {
                    score += (i == 0) ? 2 : 1; // сильная доля весит больше
                }
            }
            if (score > bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /** Подбирает последовательность аккордов для списка тактов мелодии. */
    public static List<Chord> suggestProgression(List<List<Note>> measures, Note tonic, ScaleType scale) {
        List<Chord> candidates = KeyChords.diatonicChords(tonic, scale);
        List<Chord> result = new ArrayList<>();
        for (List<Note> measure : measures) {
            result.add(bestChord(measure, candidates));
        }
        return result;
    }
}
