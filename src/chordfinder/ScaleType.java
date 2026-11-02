package chordfinder;

/**
 * Гамма: смещения ступеней от тоники (в полутонах) и тип трезвучия на каждой ступени.
 */
public enum ScaleType {
    MAJOR(
        new int[]{0, 2, 4, 5, 7, 9, 11},
        new ChordType[]{
            ChordType.MAJOR, ChordType.MINOR, ChordType.MINOR, ChordType.MAJOR,
            ChordType.MAJOR, ChordType.MINOR, ChordType.DIMINISHED
        }
    ),
    NATURAL_MINOR(
        new int[]{0, 2, 3, 5, 7, 8, 10},
        new ChordType[]{
            ChordType.MINOR, ChordType.DIMINISHED, ChordType.MAJOR, ChordType.MINOR,
            ChordType.MINOR, ChordType.MAJOR, ChordType.MAJOR
        }
    );

    public final int[] degrees;
    public final ChordType[] qualities;

    ScaleType(int[] degrees, ChordType[] qualities) {
        this.degrees = degrees;
        this.qualities = qualities;
    }
}
