package chordfinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Проверка построителя листа и преобразования имён аккордов (без экрана и звука). */
public class SheetBuilderSelfTest {
    static int failures = 0;

    static void check(String what, boolean cond) {
        System.out.println((cond ? "[ OK ] " : "[FAIL] ") + what);
        if (!cond) failures++;
    }

    public static void main(String[] a) {
        // имена: транспонирование и упрощение
        check("Am +2 -> Bm", ChordNames.display("Am", 2, false).equals("Bm"));
        check("G7 упрощённо -> G", ChordNames.display("G7", 0, true).equals("G"));
        check("Am7 +3 упрощённо -> Cm", ChordNames.display("Am7", 3, true).equals("Cm"));
        check("C/G -2 -> A#/F", ChordNames.display("C/G", -2, false).equals("A#/F"));
        check("Bb разбирается как A#", ChordNames.display("Bb", 0, false).equals("A#"));
        check("Asus4 при сдвиге сохраняет суффикс", ChordNames.display("Asus4", 2, false).equals("Bsus4"));
        check("Chord.parse(Cmaj7)=MAJ7", Chord.parse("Cmaj7").type == ChordType.MAJ7);
        check("Chord.parse(F#m7)=MIN7", Chord.parse("F#m7").type == ChordType.MIN7);

        List<ChordEvent> ev = new ArrayList<>();
        String[] names = {"Am", "F", "C", "G", "Am", "F", "C", "G", "Am7", "F"};
        for (int i = 0; i < names.length; i++) ev.add(new ChordEvent(i * 2.0, i * 2.0 + 2, Chord.parse(names[i])));

        SheetBuilder.Sheet t = SheetBuilder.timeline(ev, 0, false);
        check("лента: 10 аккордов, 3 строки (4+4+2)", t.slots.size() == 10 && t.lines.size() == 3 && t.lines.get(2).marks.size() == 2);
        SheetBuilder.Sheet ts = SheetBuilder.timeline(ev, 0, true);
        check("упрощённо: Am и Am7 склеиваются? (Am7 -> Am, но между ними F) -> 10 аккордов", ts.slots.size() == 10);
        check("slotAt(5.0) = третий аккорд (C)", t.slotAt(5.0) == 2 && t.slots.get(2).name.equals("C"));
        check("slotAt после конца = последний", t.slotAt(999) == 9);
        check("distinct: Am F C G Am7", t.distinctChords().equals(Arrays.asList("Am", "F", "C", "G", "Am7")));

        List<String> lyrics = Arrays.asList("Снег идёт, а мы с тобой гуляем", "", "По аллее старого парка", "Тихо падает листва осенняя");
        SheetBuilder.Sheet o = SheetBuilder.withLyrics(ev, lyrics, false, 0, true, 20);
        int marks = 0;
        for (SheetBuilder.Line l : o.lines) marks += l.marks.size();
        check("текст без разметки: все 10 аккордов разложены по строкам", marks == 10);
        check("пустая строка без аккордов", o.lines.get(1).marks.isEmpty());
        boolean wordStarts = true;
        for (SheetBuilder.Line l : o.lines) for (SheetBuilder.Mark m : l.marks)
            if (m.pos > 0 && l.lyric.charAt(m.pos - 1) != ' ') wordStarts = false;
        check("аккорды стоят над началом слов", wordStarts);

        List<String> ann = Arrays.asList("[Am]Снег идёт, а мы с [F]тобой", "[C]гуляем по [G]парку", "и [Am7]всё");
        SheetBuilder.Sheet an = SheetBuilder.withLyrics(ev, ann, true, 2, true, 20);
        check("разметка: 5 аккордов из текста, транспонированных на +2 и упрощённых (Bm D... )",
                an.slots.size() == 5 && an.slots.get(0).name.equals("Bm") && an.slots.get(4).name.equals("Bm"));
        check("время равномерно: последний аккорд заканчивается в конце записи",
                Math.abs(an.slots.get(4).end - 20.0) < 1e-6);
        check("текст строки очищен от скобок", an.lines.get(0).lyric.equals("Снег идёт, а мы с тобой"));
        System.out.println();
        System.out.println(SheetBuilder.toText(an));
        System.out.println(SheetBuilder.toText(o));
        System.out.println(SheetBuilder.toText(t));
        System.exit(failures == 0 ? 0 : 1);
    }
}
