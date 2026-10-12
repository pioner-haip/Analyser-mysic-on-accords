package chordfinder;

import java.util.ArrayList;
import java.util.List;

/**
 * Делит общую длительность трека поровну между строками текста, чтобы знать,
 * какую строку подсвечивать в каждый момент воспроизведения. Это упрощение —
 * без точной посекундной привязки к слогам, но для школьного проекта достаточно.
 */
public class TimedSheetBuilder {
    public static class TimedLine {
        public final double start, end;
        public final String chordLine, lyricLine;
        public TimedLine(double start, double end, String chordLine, String lyricLine) {
            this.start = start;
            this.end = end;
            this.chordLine = chordLine;
            this.lyricLine = lyricLine;
        }
    }

    public static List<TimedLine> build(List<LyricsSheet.Line> sheet, double totalDurationSeconds) {
        List<TimedLine> result = new ArrayList<>();
        int n = sheet.size();
        if (n == 0 || totalDurationSeconds <= 0) return result;
        double per = totalDurationSeconds / n;
        for (int i = 0; i < n; i++) {
            LyricsSheet.Line l = sheet.get(i);
            result.add(new TimedLine(i * per, (i + 1) * per, l.chordLine, l.lyricLine));
        }
        return result;
    }
}
