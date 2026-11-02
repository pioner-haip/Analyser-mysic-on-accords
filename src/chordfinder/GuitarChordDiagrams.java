package chordfinder;

/**
 * Текстовая аппликатура для консольной версии. Данные берём из ChordLibrary,
 * чтобы консоль и графический интерфейс показывали одно и то же.
 */
public class GuitarChordDiagrams {
    private static final String[] STRING_NAMES = {"E", "A", "D", "G", "B", "e"};

    public static String diagram(Chord chord) {
        ChordVoicing v = ChordLibrary.easiest(chord);
        if (!ChordLibrary.hasShape(v)) {
            return "  [" + chord.name() + ": простой аппликатуры нет, потребуется отдельный разбор]";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("  ").append(chord.name()).append(" (").append(v.label.toLowerCase()).append("):\n");
        for (int i = 0; i < 6; i++) {
            int f = v.frets[i];
            sb.append("  ").append(STRING_NAMES[i]).append(" |").append(f < 0 ? "x" : String.valueOf(f)).append("\n");
        }
        return sb.toString();
    }
}
