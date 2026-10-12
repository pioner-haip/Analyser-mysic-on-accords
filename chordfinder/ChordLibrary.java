package chordfinder;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Справочник: для каждого аккорда даёт открытую позицию (если есть) и 1-2 баррэ-формы. */
public class ChordLibrary {
    private static final Map<String, String> OPEN_SHAPES = new HashMap<>();
    static {
        OPEN_SHAPES.put("C", "x32010");
        OPEN_SHAPES.put("D", "xx0232");
        OPEN_SHAPES.put("E", "022100");
        OPEN_SHAPES.put("F", "133211");
        OPEN_SHAPES.put("G", "320003");
        OPEN_SHAPES.put("A", "x02220");
        OPEN_SHAPES.put("B", "x24442");
        OPEN_SHAPES.put("Am", "x02210");
        OPEN_SHAPES.put("Bm", "x24432");
        OPEN_SHAPES.put("Cm", "x35543");
        OPEN_SHAPES.put("Dm", "xx0231");
        OPEN_SHAPES.put("Em", "022000");
        OPEN_SHAPES.put("Fm", "133111");
        OPEN_SHAPES.put("Gm", "355333");
    }

    public static List<Chord> allChords() {
        List<Chord> list = new ArrayList<>();
        for (Note n : Note.values()) list.add(new Chord(n, ChordType.MAJOR));
        for (Note n : Note.values()) list.add(new Chord(n, ChordType.MINOR));
        return list;
    }

    /** Открытая позиция (если есть в словаре) + баррэ-формы от E и от A. */
    public static List<ChordVoicing> variations(Chord chord) {
        List<ChordVoicing> result = new ArrayList<>();

        String open = OPEN_SHAPES.get(chord.name());
        if (open != null) {
            result.add(new ChordVoicing("Открытая позиция", parse(open)));
        }

        if (chord.type != ChordType.DIMINISHED) {
            int nE = ((chord.root.ordinal() - Note.E.ordinal()) % 12 + 12) % 12;
            int nA = ((chord.root.ordinal() - Note.A.ordinal()) % 12 + 12) % 12;
            if (nE > 0) {
                result.add(new ChordVoicing("Баррэ, форма E, " + nE + " лад", barre(chord.type, true, nE)));
            }
            if (nA > 0) {
                result.add(new ChordVoicing("Баррэ, форма A, " + nA + " лад", barre(chord.type, false, nA)));
            }
        }

        if (result.isEmpty()) {
            result.add(new ChordVoicing("Простой формы нет — нужен индивидуальный разбор",
                    new int[]{-1, -1, -1, -1, -1, -1}));
        }
        return result;
    }

    private static int[] barre(ChordType type, boolean eShape, int n) {
        int[] base;
        if (eShape) {
            base = type == ChordType.MAJOR ? new int[]{0, 2, 2, 1, 0, 0} : new int[]{0, 2, 2, 0, 0, 0};
        } else {
            base = type == ChordType.MAJOR ? new int[]{-1, 0, 2, 2, 2, 0} : new int[]{-1, 0, 2, 2, 1, 0};
        }
        int[] result = new int[6];
        for (int i = 0; i < 6; i++) {
            result[i] = base[i] < 0 ? -1 : base[i] + n;
        }
        return result;
    }

    private static int[] parse(String s) {
        int[] r = new int[6];
        for (int i = 0; i < 6; i++) {
            char c = s.charAt(i);
            r[i] = c == 'x' ? -1 : (c - '0');
        }
        return r;
    }
}
