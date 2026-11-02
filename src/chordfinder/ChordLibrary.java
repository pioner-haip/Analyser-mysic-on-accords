package chordfinder;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Справочник аппликатур. Для каждого аккорда: открытая позиция (если она есть в словаре)
 * и баррэ-формы от E (корень на 6-й струне) и от A (корень на 5-й струне).
 * Строка вида "x32010": по символу на струну от 6-й (низкая E) к 1-й; x = заглушена.
 */
public class ChordLibrary {
    private static final Map<String, String> OPEN_SHAPES = new HashMap<>();
    static {
        // трезвучия
        OPEN_SHAPES.put("C", "x32010");   OPEN_SHAPES.put("D", "xx0232");   OPEN_SHAPES.put("E", "022100");
        OPEN_SHAPES.put("F", "133211");   OPEN_SHAPES.put("G", "320003");   OPEN_SHAPES.put("A", "x02220");
        OPEN_SHAPES.put("B", "x24442");
        OPEN_SHAPES.put("Am", "x02210");  OPEN_SHAPES.put("Bm", "x24432");  OPEN_SHAPES.put("Cm", "x35543");
        OPEN_SHAPES.put("Dm", "xx0231");  OPEN_SHAPES.put("Em", "022000");  OPEN_SHAPES.put("Fm", "133111");
        OPEN_SHAPES.put("Gm", "355333");
        // доминантсептаккорды
        OPEN_SHAPES.put("C7", "x32310");  OPEN_SHAPES.put("D7", "xx0212");  OPEN_SHAPES.put("E7", "020100");
        OPEN_SHAPES.put("G7", "320001");  OPEN_SHAPES.put("A7", "x02020");  OPEN_SHAPES.put("B7", "x21202");
        // минорные септаккорды
        OPEN_SHAPES.put("Am7", "x02010"); OPEN_SHAPES.put("Dm7", "xx0211"); OPEN_SHAPES.put("Em7", "020000");
        OPEN_SHAPES.put("Bm7", "x20202");
        // большие септаккорды
        OPEN_SHAPES.put("Cmaj7", "x32000"); OPEN_SHAPES.put("Dmaj7", "xx0222"); OPEN_SHAPES.put("Emaj7", "021100");
        OPEN_SHAPES.put("Fmaj7", "xx3210"); OPEN_SHAPES.put("Gmaj7", "320002"); OPEN_SHAPES.put("Amaj7", "x02120");
    }

    /** Подвижные баррэ-формы: -1 = не играется, остальное — смещение от барре. */
    private static final Map<ChordType, int[]> E_FORM = new EnumMap<>(ChordType.class);
    private static final Map<ChordType, int[]> A_FORM = new EnumMap<>(ChordType.class);
    static {
        E_FORM.put(ChordType.MAJOR, new int[]{0, 2, 2, 1, 0, 0});
        E_FORM.put(ChordType.MINOR, new int[]{0, 2, 2, 0, 0, 0});
        E_FORM.put(ChordType.DOM7,  new int[]{0, 2, 0, 1, 0, 0});
        E_FORM.put(ChordType.MIN7,  new int[]{0, 2, 0, 0, 0, 0});
        E_FORM.put(ChordType.MAJ7,  new int[]{0, 2, 1, 1, 0, 0});
        A_FORM.put(ChordType.MAJOR, new int[]{-1, 0, 2, 2, 2, 0});
        A_FORM.put(ChordType.MINOR, new int[]{-1, 0, 2, 2, 1, 0});
        A_FORM.put(ChordType.DOM7,  new int[]{-1, 0, 2, 0, 2, 0});
        A_FORM.put(ChordType.MIN7,  new int[]{-1, 0, 2, 0, 1, 0});
        A_FORM.put(ChordType.MAJ7,  new int[]{-1, 0, 2, 1, 2, 0});
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

        int nE = fretOffset(chord, Note.E);
        int nA = fretOffset(chord, Note.A);
        if (nE > 0 && E_FORM.containsKey(chord.type)) {
            result.add(new ChordVoicing("Баррэ, форма E, " + nE + " лад", barre(E_FORM.get(chord.type), nE)));
        }
        if (nA > 0 && A_FORM.containsKey(chord.type)) {
            result.add(new ChordVoicing("Баррэ, форма A, " + nA + " лад", barre(A_FORM.get(chord.type), nA)));
        }

        if (result.isEmpty()) {
            result.add(none());
        }
        return result;
    }

    /** Самая простая для исполнения аппликатура: открытая, а если её нет — баррэ на самом низком ладу. */
    public static ChordVoicing easiest(Chord chord) {
        List<ChordVoicing> all = variations(chord);
        String open = OPEN_SHAPES.get(chord.name());
        if (open != null) return all.get(0);
        ChordVoicing best = null;
        int bestFret = Integer.MAX_VALUE;
        for (ChordVoicing v : all) {
            int min = lowestFret(v.frets);
            if (min < bestFret) { bestFret = min; best = v; }
        }
        return best != null ? best : none();
    }

    public static boolean hasShape(ChordVoicing v) {
        for (int f : v.frets) if (f >= 0) return true;
        return false;
    }

    private static ChordVoicing none() {
        return new ChordVoicing("Простой формы нет — нужен индивидуальный разбор", new int[]{-1, -1, -1, -1, -1, -1});
    }

    private static int lowestFret(int[] frets) {
        int min = Integer.MAX_VALUE;
        for (int f : frets) if (f > 0) min = Math.min(min, f);
        return min == Integer.MAX_VALUE ? 0 : min;
    }

    private static int fretOffset(Chord chord, Note openRoot) {
        return ((chord.root.ordinal() - openRoot.ordinal()) % 12 + 12) % 12;
    }

    private static int[] barre(int[] base, int n) {
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
