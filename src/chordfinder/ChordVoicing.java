package chordfinder;

/** Одна вариация аппликатуры: -1 = струна заглушена, 0 = открытая, иначе — номер лада. */
public class ChordVoicing {
    public final String label;
    public final int[] frets;
    public ChordVoicing(String label, int[] frets) {
        this.label = label;
        this.frets = frets;
    }
}
