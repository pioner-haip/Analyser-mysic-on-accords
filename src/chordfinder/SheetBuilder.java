package chordfinder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Строит «лист» для среднего столбца: строки текста с аккордами над нужными словами.
 * Не знает про окна и рисование — поэтому его можно проверять тестом без экрана.
 *
 * Три режима:
 *  1. Без текста («лента»): аккорды по 4 в строке, под каждым — время.
 *  2. Текст без разметки: найденные аккорды раскладываются по строкам пропорционально их длине.
 *     Здесь каждый аккорд листа — настоящий найденный аккорд со своим временем.
 *  3. Текст с аккордами в [скобках] (например, с сайта): аккорды берутся из текста, а время каждого
 *     оценивается РАВНОМЕРНО по длительности записи (точной привязки к слогам нет).
 */
public class SheetBuilder {
    /** Один аккорд на листе: имя для показа и интервал времени, когда он звучит. */
    public static class Slot {
        public final String name;
        public final double start, end;
        public int line, mark;
        Slot(String name, double start, double end) { this.name = name; this.start = start; this.end = end; }
    }

    /** Метка аккорда над строкой: позиция в тексте (номер символа) и подпись под аккордом (время или null). */
    public static class Mark {
        public final int pos;
        public final int slot;
        public final String sub;
        Mark(int pos, int slot, String sub) { this.pos = pos; this.slot = slot; this.sub = sub; }
    }

    public static class Line {
        public final String lyric;
        public final List<Mark> marks = new ArrayList<>();
        Line(String lyric) { this.lyric = lyric; }
    }

    public static class Sheet {
        public final List<Slot> slots = new ArrayList<>();
        public final List<Line> lines = new ArrayList<>();
        public boolean timeline;

        /** Индекс аккорда, звучащего в момент t (до начала — первый, после конца — последний). */
        public int slotAt(double t) {
            if (slots.isEmpty()) return -1;
            int lo = 0, hi = slots.size() - 1, ans = 0;
            while (lo <= hi) {
                int mid = (lo + hi) >>> 1;
                if (slots.get(mid).start <= t) { ans = mid; lo = mid + 1; } else hi = mid - 1;
            }
            return ans;
        }

        /** Все разные аккорды в порядке первого появления (для кнопок «Все аккорды песни»). */
        public List<String> distinctChords() {
            Set<String> set = new LinkedHashSet<>();
            for (Slot s : slots) set.add(s.name);
            return new ArrayList<>(set);
        }
    }

    private static final int PER_LINE = 4;

    // ---------------------------------------------------------------------------------------

    public static Sheet timeline(List<ChordEvent> events, int shift, boolean simplified) {
        Sheet sheet = new Sheet();
        sheet.timeline = true;
        List<Slot> slots = slotsFromEvents(events, shift, simplified);
        sheet.slots.addAll(slots);
        Line line = null;
        for (int i = 0; i < slots.size(); i++) {
            if (i % PER_LINE == 0) { line = new Line(""); sheet.lines.add(line); }
            Slot s = slots.get(i);
            s.line = sheet.lines.size() - 1;
            s.mark = line.marks.size();
            line.marks.add(new Mark(line.marks.size(), i, ChordEvent.formatTime(s.start)));
        }
        return sheet;
    }

    public static Sheet withLyrics(List<ChordEvent> events, List<String> rawLines, boolean annotated,
                                   int shift, boolean simplified, double fallbackDuration) {
        return annotated ? annotatedSheet(events, rawLines, shift, simplified, fallbackDuration)
                         : overlaySheet(events, rawLines, shift, simplified);
    }

    // ---- режим 2: текст без разметки ----------------------------------------------------------

    private static Sheet overlaySheet(List<ChordEvent> events, List<String> rawLines, int shift, boolean simplified) {
        Sheet sheet = new Sheet();
        List<Slot> slots = slotsFromEvents(events, shift, simplified);
        sheet.slots.addAll(slots);
        int n = rawLines.size();
        double[] cum = new double[n + 1];
        for (int i = 0; i < n; i++) {
            String l = rawLines.get(i);
            sheet.lines.add(new Line(l));
            cum[i + 1] = cum[i] + (l.trim().isEmpty() ? 0 : Math.max(l.length(), 8));
        }
        double total = cum[n];
        int E = slots.size();
        if (E == 0 || total == 0) return sheet;
        int li = 0;
        for (int k = 0; k < E; k++) {
            double m = (k + 0.5) / E * total;
            while (li < n - 1 && cum[li + 1] <= m) li++;
            Line line = sheet.lines.get(li);
            double w = cum[li + 1] - cum[li];
            int pos = w <= 0 ? 0 : (int) ((m - cum[li]) / w * line.lyric.length());
            pos = Math.max(0, Math.min(pos, Math.max(0, line.lyric.length() - 1)));
            pos = line.lyric.lastIndexOf(' ', pos) + 1;      // ставим над началом слова
            Slot s = slots.get(k);
            s.line = li;
            s.mark = line.marks.size();
            line.marks.add(new Mark(pos, k, null));
        }
        return sheet;
    }

    // ---- режим 3: текст с аккордами в скобках ---------------------------------------------------

    private static final Pattern BRACKET = Pattern.compile("\\[([^\\]]*)]");

    private static Sheet annotatedSheet(List<ChordEvent> events, List<String> rawLines, int shift,
                                        boolean simplified, double fallbackDuration) {
        Sheet sheet = new Sheet();
        List<String> tokens = new ArrayList<>();
        List<int[]> where = new ArrayList<>(); // {строка, номер метки в строке}
        for (int li = 0; li < rawLines.size(); li++) {
            String raw = rawLines.get(li);
            StringBuilder lyric = new StringBuilder();
            List<int[]> pos = new ArrayList<>();
            List<String> toks = new ArrayList<>();
            Matcher m = BRACKET.matcher(raw);
            int last = 0;
            while (m.find()) {
                lyric.append(raw, last, m.start());
                pos.add(new int[]{lyric.length()});
                toks.add(m.group(1));
                last = m.end();
            }
            lyric.append(raw.substring(last));
            Line line = new Line(lyric.toString());
            sheet.lines.add(line);
            for (int j = 0; j < toks.size(); j++) {
                tokens.add(toks.get(j));
                where.add(new int[]{li, line.marks.size()});
                line.marks.add(new Mark(pos.get(j)[0], tokens.size() - 1, null));
            }
        }
        double t0 = 0, t1 = fallbackDuration;
        if (!events.isEmpty() && !Double.isNaN(events.get(0).start)) {
            t0 = events.get(0).start;
            t1 = events.get(events.size() - 1).end;
        }
        int M = tokens.size();
        for (int j = 0; j < M; j++) {
            double a = t0 + (t1 - t0) * j / M, b = t0 + (t1 - t0) * (j + 1) / M;
            Slot s = new Slot(ChordNames.display(tokens.get(j), shift, simplified), a, b);
            s.line = where.get(j)[0];
            s.mark = where.get(j)[1];
            sheet.slots.add(s);
        }
        return sheet;
    }

    // ---- общее -------------------------------------------------------------------------------------

    /** События -> аккорды для показа: транспонирование/упрощение, одинаковые соседние склеиваются. */
    private static List<Slot> slotsFromEvents(List<ChordEvent> events, int shift, boolean simplified) {
        List<Slot> out = new ArrayList<>();
        for (ChordEvent e : events) {
            if (e.chord == null) continue;
            String name = ChordNames.display(e.chord.name(), shift, simplified);
            double start = Double.isNaN(e.start) ? out.size() : e.start;
            double end = Double.isNaN(e.end) ? out.size() + 1 : e.end;
            if (!out.isEmpty() && out.get(out.size() - 1).name.equals(name)) {
                Slot prev = out.remove(out.size() - 1);
                out.add(new Slot(name, prev.start, end));
            } else {
                out.add(new Slot(name, start, end));
            }
        }
        return out;
    }

    /** Текстовый вид листа: аккорды строкой над текстом (для экспорта и копирования). */
    public static String toText(Sheet sheet) {
        StringBuilder sb = new StringBuilder();
        for (Line line : sheet.lines) {
            StringBuilder chords = new StringBuilder();
            if (sheet.timeline) {
                for (Mark m : line.marks) chords.append(sheet.slots.get(m.slot).name).append("  ");
                sb.append(chords.toString().stripTrailing());
                if (!line.marks.isEmpty()) sb.append("    (").append(line.marks.get(0).sub).append(")");
                sb.append('\n');
                continue;
            }
            for (Mark m : line.marks) {
                while (chords.length() < m.pos) chords.append(' ');
                if (chords.length() > 0 && chords.charAt(chords.length() - 1) != ' ') chords.append(' ');
                chords.append(sheet.slots.get(m.slot).name);
            }
            if (chords.length() > 0) sb.append(chords).append('\n');
            sb.append(line.lyric).append('\n');
        }
        return sb.toString();
    }
}
