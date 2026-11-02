package chordfinder;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.File;
import java.util.LinkedHashSet;
import java.util.Set;

/** Проверка реальных файлов: WAV 44.1k моно, WAV 48k стерео, MP3 (через ffmpeg), MIDI; затем экспорт. */
public class FileE2E {
    static int failures = 0;

    public static void main(String[] a) throws Exception {
        File dir = new File(a[0]);
        for (String name : new String[]{"demo song.wav", "demo48_stereo.wav", "demo.mp3"}) {
            File f = new File(dir, name);
            if (!f.exists()) { System.out.println("[SKIP] " + name); continue; }
            long t0 = System.currentTimeMillis();
            File playable = AudioConverter.toWav(f);
            double[] last = {0};
            Song s = Song.fromAudio(f, playable, p -> last[0] = p);
            report(name, s, System.currentTimeMillis() - t0, last[0]);
        }

        // MIDI: Am F C G, по такту на аккорд, по 4 ноты в такте
        File mid = new File(dir, "demo.mid");
        Sequence seq = new Sequence(Sequence.PPQ, 480);
        Track tr = seq.createTrack();
        int[][] chords = {{57, 60, 64, 60}, {53, 57, 60, 57}, {60, 64, 67, 64}, {55, 59, 62, 59}};
        long tick = 0;
        for (int bar = 0; bar < 8; bar++) {
            for (int n : chords[bar % 4]) {
                tr.add(new javax.sound.midi.MidiEvent(new ShortMessage(ShortMessage.NOTE_ON, 0, n, 90), tick));
                tr.add(new javax.sound.midi.MidiEvent(new ShortMessage(ShortMessage.NOTE_OFF, 0, n, 0), tick + 400));
                tick += 480;
            }
        }
        MidiSystem.write(seq, 1, mid);
        Song m = Song.fromMidi(mid);
        report("demo.mid", m, 0, 1);

        // экспорт
        SheetBuilder.Sheet sh = SheetBuilder.timeline(m.simple, 0, true);
        String txt = Exporter.toText(m, sh);
        String csv = Exporter.toCsv(sh);
        check("экспорт .txt содержит тональность и аккорды", txt.contains("Тональность:") && txt.contains("Am"));
        check("экспорт .csv: заголовок + строки с точкой в числах", csv.startsWith("start_sec,end_sec,chord\n") && csv.contains("0.00,"));
        System.out.println(csv.lines().limit(4).reduce("", (x, y) -> x + "   " + y + "\n"));
        System.exit(failures == 0 ? 0 : 1);
    }

    static void report(String name, Song s, long ms, double progress) {
        Set<String> uniq = new LinkedHashSet<>();
        StringBuilder seq = new StringBuilder();
        for (ChordEvent e : s.simple) { uniq.add(e.chord.name()); if (seq.length() < 70) seq.append(e.chord.name()).append(' '); }
        boolean ok = uniq.equals(new LinkedHashSet<>(java.util.Arrays.asList("Am", "F", "C", "G")));
        check(name + ": ровно аккорды Am F C G", ok);
        System.out.printf("       событий %d, тональность %s, темп %.0f, длит. %.1f с, анализ %d мс, прогресс %.2f%n       %s...%n",
                s.simple.size(), s.key.name(), s.bpm, s.duration, ms, progress, seq);
    }

    static void check(String what, boolean cond) {
        System.out.println((cond ? "[ OK ] " : "[FAIL] ") + what);
        if (!cond) failures++;
    }
}
