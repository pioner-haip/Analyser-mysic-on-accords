package chordfinder;

import java.util.ArrayList;
import java.util.List;

public class KeyChords {
    /** Возвращает 7 диатонических аккордов тональности (тоника + гамма). */
    public static List<Chord> diatonicChords(Note tonic, ScaleType scale) {
        List<Chord> chords = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            Note root = tonic.transpose(scale.degrees[i]);
            chords.add(new Chord(root, scale.qualities[i]));
        }
        return chords;
    }
}
