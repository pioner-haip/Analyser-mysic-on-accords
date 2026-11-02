package chordfinder;

/**
 * Тип аккорда: набор интервалов (в полутонах от корня) и суффикс для имени аккорда.
 * Детектор распознаёт MAJOR, MINOR и (в «расширенном» режиме) DOM7 и MIN7;
 * остальные типы нужны, чтобы разбирать и рисовать аккорды из текстов песен.
 */
public enum ChordType {
    MAJOR(new int[]{0, 4, 7}, ""),
    MINOR(new int[]{0, 3, 7}, "m"),
    DIMINISHED(new int[]{0, 3, 6}, "dim"),
    DOM7(new int[]{0, 4, 7, 10}, "7"),
    MIN7(new int[]{0, 3, 7, 10}, "m7"),
    MAJ7(new int[]{0, 4, 7, 11}, "maj7");

    public final int[] intervals;
    public final String suffix;

    ChordType(int[] intervals, String suffix) {
        this.intervals = intervals;
        this.suffix = suffix;
    }

    public boolean isSeventh() {
        return this == DOM7 || this == MIN7 || this == MAJ7;
    }

    /** Упрощение до трезвучия: G7 -> G, Am7 -> Am, Cmaj7 -> C, Bdim -> Bm. */
    public ChordType triad() {
        switch (this) {
            case DOM7: case MAJ7: return MAJOR;
            case MIN7: case DIMINISHED: return MINOR;
            default: return this;
        }
    }
}
