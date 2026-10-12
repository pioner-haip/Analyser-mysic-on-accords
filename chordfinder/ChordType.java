package chordfinder;

/**
 * Тип трезвучия: набор интервалов (в полутонах от корня) и суффикс для имени аккорда.
 */
public enum ChordType {
    MAJOR(new int[]{0, 4, 7}, ""),
    MINOR(new int[]{0, 3, 7}, "m"),
    DIMINISHED(new int[]{0, 3, 6}, "dim");

    public final int[] intervals;
    public final String suffix;

    ChordType(int[] intervals, String suffix) {
        this.intervals = intervals;
        this.suffix = suffix;
    }
}
