package chordfinder;

import java.util.ArrayList;
import java.util.List;

/**
 * Когда текст песни введён без разметки, равномерно распределяет уже подобранную
 * последовательность аккордов по строкам: сколько аккордов приходится на строку —
 * считается пропорционально, позиции внутри строки — равномерно по длине.
 */
public class AutoOverlay {
    public static List<LyricsSheet.Line> overlay(List<String> lyricLines, List<Chord> chords) {
        List<LyricsSheet.Line> result = new ArrayList<>();
        if (lyricLines.isEmpty() || chords.isEmpty()) {
            for (String line : lyricLines) result.add(new LyricsSheet.Line("", line));
            return result;
        }

        int totalLines = lyricLines.size();
        int totalChords = chords.size();
        int chordIndex = 0;

        for (int lineNo = 0; lineNo < totalLines; lineNo++) {
            String lyric = lyricLines.get(lineNo);
            int remainingLines = totalLines - lineNo;
            int remainingChords = totalChords - chordIndex;
            int countForLine = Math.max(1, (int) Math.round(remainingChords / (double) remainingLines));
            countForLine = Math.min(countForLine, remainingChords);

            StringBuilder chordLine = new StringBuilder();
            int lineLen = Math.max(lyric.length(), 1);
            for (int k = 0; k < countForLine; k++) {
                Chord chord = chords.get(chordIndex++);
                int pos = (int) ((long) k * lineLen / countForLine);
                while (chordLine.length() < pos) chordLine.append(' ');
                chordLine.append(chord.name());
            }
            result.add(new LyricsSheet.Line(chordLine.toString(), lyric));
        }
        return result;
    }
}
