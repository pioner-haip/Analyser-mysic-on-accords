package chordfinder;

import java.util.ArrayList;
import java.util.List;

/**
 * Текст песни, размеченный аккордами в квадратных скобках прямо в строке,
 * например: "[Am]Снег идёт, а мы с тобой [F]гуляем". Строит классический вид —
 * строку с аккордами точно над той позицией текста, где стоит смена аккорда.
 */
public class LyricsSheet {
    public static class Line {
        public final String chordLine;
        public final String lyricLine;
        public Line(String chordLine, String lyricLine) {
            this.chordLine = chordLine;
            this.lyricLine = lyricLine;
        }
    }

    public static List<Line> parse(List<String> rawLines) {
        List<Line> result = new ArrayList<>();
        for (String raw : rawLines) {
            result.add(parseLine(raw));
        }
        return result;
    }

    private static Line parseLine(String raw) {
        StringBuilder lyric = new StringBuilder();
        List<Integer> positions = new ArrayList<>();
        List<String> chordNames = new ArrayList<>();

        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (c == '[') {
                int close = raw.indexOf(']', i);
                if (close == -1) {
                    lyric.append(raw.substring(i));
                    break;
                }
                positions.add(lyric.length());
                chordNames.add(raw.substring(i + 1, close));
                i = close + 1;
            } else {
                lyric.append(c);
                i++;
            }
        }

        StringBuilder chordLine = new StringBuilder();
        int cursor = 0;
        for (int idx = 0; idx < positions.size(); idx++) {
            int pos = Math.max(positions.get(idx), cursor);
            String name = chordNames.get(idx);
            while (chordLine.length() < pos) chordLine.append(' ');
            chordLine.append(name);
            cursor = pos + name.length() + 1;
        }

        return new Line(chordLine.toString(), lyric.toString());
    }
}
