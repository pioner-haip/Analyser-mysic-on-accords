package chordfinder;

import java.util.HashMap;
import java.util.Map;

/**
 * Аппликатуры для гитары. Сначала пробуем красивую открытую позицию из словаря,
 * если её нет — считаем баррэ (по E- или A-форме), это покрывает вообще любой
 * мажорный/минорный аккорд. Для уменьшённых трезвучий простой формы нет.
 */
public class GuitarChordDiagrams {
    private static final Map<String, String> SHAPES = new HashMap<>();
    static {
        SHAPES.put("C", "x32010");
        SHAPES.put("D", "xx0232");
        SHAPES.put("E", "022100");
        SHAPES.put("F", "133211");
        SHAPES.put("G", "320003");
        SHAPES.put("A", "x02220");
        SHAPES.put("B", "x24442");
        SHAPES.put("Am", "x02210");
        SHAPES.put("Bm", "x24432");
        SHAPES.put("Cm", "x35543");
        SHAPES.put("Dm", "xx0231");
        SHAPES.put("Em", "022000");
        SHAPES.put("Fm", "133111");
        SHAPES.put("Gm", "355333");
    }

    public static String diagram(Chord chord) {
        String name = chord.name();
        String open = SHAPES.get(name);
        if (open != null) {
            return renderOpen(name, open);
        }
        if (chord.type == ChordType.DIMINISHED) {
            return "  [" + name + ": простой аппликатуры нет, потребуется отдельный разбор]";
        }
        return renderBarre(chord);
    }

    private static String renderOpen(String name, String frets) {
        String[] strings = {"E", "A", "D", "G", "B", "e"};
        StringBuilder sb = new StringBuilder();
        sb.append("  ").append(name).append(" (открытая позиция):\n");
        for (int i = 0; i < 6; i++) {
            sb.append("  ").append(strings[i]).append(" |").append(frets.charAt(i)).append("\n");
        }
        return sb.toString();
    }

    /** Движимая баррэ-форма от E (root на 6-й струне) или от A (root на 5-й струне). */
    private static String renderBarre(Chord chord) {
        int[] eShape = chord.type == ChordType.MAJOR
                ? new int[]{0, 2, 2, 1, 0, 0} : new int[]{0, 2, 2, 0, 0, 0};
        int[] aShape = chord.type == ChordType.MAJOR
                ? new int[]{-1, 0, 2, 2, 2, 0} : new int[]{-1, 0, 2, 2, 1, 0};

        int nE = ((chord.root.ordinal() - Note.E.ordinal()) % 12 + 12) % 12;
        int nA = ((chord.root.ordinal() - Note.A.ordinal()) % 12 + 12) % 12;

        int[] shape;
        int n;
        String form;
        if (nE <= nA) {
            shape = eShape; n = nE; form = "E";
        } else {
            shape = aShape; n = nA; form = "A";
        }

        String[] strings = {"E", "A", "D", "G", "B", "e"};
        StringBuilder sb = new StringBuilder();
        sb.append("  ").append(chord.name())
          .append(" (баррэ по форме ").append(form).append(", ").append(n).append(" лад):\n");
        for (int i = 0; i < 6; i++) {
            int fret = shape[i];
            String cell = fret < 0 ? "x" : String.valueOf(fret + n);
            sb.append("  ").append(strings[i]).append(" |").append(cell).append("\n");
        }
        return sb.toString();
    }
}
