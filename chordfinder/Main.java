package chordfinder;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;

public class Main {
    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        System.out.println("=== Подбор аккордов ===");
        System.out.println("Выберите источник:");
        System.out.println("1 - ввести мелодию текстом");
        System.out.println("2 - импортировать MIDI-файл (.mid)");
        System.out.println("3 - импортировать аудио-файл (.wav) - акустика, электроника, что угодно");
        System.out.print("> ");
        String choice = sc.nextLine().trim();

        List<ChordEvent> events;
        List<Chord> chordsOnly = new ArrayList<>();
        Double bpm = null;

        switch (choice) {
            case "2": {
                System.out.print("Путь к MIDI-файлу: ");
                String path = sc.nextLine().trim();
                File file = new File(path);
                List<List<Note>> measures = MidiImporter.importMeasures(file);
                events = symbolicPipeline(measures, sc);
                bpm = TempoEstimator.fromMidi(file);
                break;
            }
            case "3": {
                System.out.print("Путь к WAV-файлу: ");
                String path = sc.nextLine().trim();
                File file = new File(path);
                System.out.println("Анализирую аудио (может занять несколько секунд)...");
                AudioLoader.Samples samples = AudioLoader.load(file);
                events = AudioChordDetector.analyze(samples);
                bpm = TempoEstimator.fromAudio(samples);
                break;
            }
            default: {
                System.out.println("Введите мелодию по тактам (ноты через пробел, такты через |):");
                System.out.println("Пример: E G C | D F G | C E G");
                String line = sc.nextLine();
                List<List<Note>> measures = new ArrayList<>();
                for (String bar : line.split("\\|")) {
                    List<Note> notes = new ArrayList<>();
                    for (String tok : bar.trim().split("\\s+")) {
                        if (!tok.isEmpty()) notes.add(Note.fromString(tok));
                    }
                    if (!notes.isEmpty()) measures.add(notes);
                }
                events = symbolicPipeline(measures, sc);

                System.out.print("Темп в BPM, если знаете (Enter, чтобы пропустить): ");
                String bpmLine = sc.nextLine().trim();
                if (!bpmLine.isEmpty()) {
                    try {
                        bpm = Double.parseDouble(bpmLine);
                    } catch (NumberFormatException ignored) {
                        // оставляем bpm = null
                    }
                }
            }
        }

        for (ChordEvent e : events) {
            if (e.chord != null) chordsOnly.add(e.chord);
        }

        System.out.println("\nПодобранные аккорды:");
        for (ChordEvent e : events) {
            System.out.println(e.label + ": " + (e.chord != null ? e.chord.name() : "-"));
        }

        System.out.println("\nАппликатуры для гитары:");
        Set<String> shown = new LinkedHashSet<>();
        for (Chord c : chordsOnly) {
            if (shown.add(c.name())) {
                System.out.println(GuitarChordDiagrams.diagram(c));
            }
        }

        if (bpm != null) {
            System.out.println("\nТемп: " + Math.round(bpm) + " BPM");
            StrumPatternSuggester.Suggestion s = StrumPatternSuggester.suggest(bpm);
            System.out.println("Рекомендуемый бой (" + s.name + "): " + s.pattern);
            System.out.println(s.note);
            System.out.println("(Это типовая рекомендация по темпу, а не расшифровка ударов из записи.)");
        }

        System.out.print("\nДобавить текст песни и наложить на него аккорды? (да/нет): ");
        String wantLyrics = sc.nextLine().trim().toLowerCase();
        if (wantLyrics.startsWith("д")) {
            System.out.print("В тексте уже проставлены аккорды в квадратных скобках, например [Am]? (да/нет): ");
            boolean annotated = sc.nextLine().trim().toLowerCase().startsWith("д");
            System.out.println("Введите текст песни построчно. Пустая строка — конец ввода.");
            List<String> lines = readMultiline(sc);

            List<LyricsSheet.Line> sheet = annotated
                    ? LyricsSheet.parse(lines)
                    : AutoOverlay.overlay(lines, chordsOnly);

            System.out.println("\n=== Текст с аккордами ===");
            for (LyricsSheet.Line l : sheet) {
                System.out.println(l.chordLine);
                System.out.println(l.lyricLine);
            }
        }
    }

    private static List<String> readMultiline(Scanner sc) {
        List<String> lines = new ArrayList<>();
        while (true) {
            String line = sc.nextLine();
            if (line.isEmpty()) break;
            lines.add(line);
        }
        return lines;
    }

    /** Общий путь для символьных источников (текст, MIDI): тональность + подбор по тактам. */
    private static List<ChordEvent> symbolicPipeline(List<List<Note>> measures, Scanner sc) {
        System.out.print("Тональность (например: C major) или пусто для автоопределения: ");
        String keyLine = sc.nextLine().trim();

        Note tonic;
        ScaleType scale;
        if (keyLine.isEmpty()) {
            List<Note> all = new ArrayList<>();
            for (List<Note> m : measures) all.addAll(m);
            KeyEstimator.KeyResult kr = KeyEstimator.estimate(all);
            tonic = kr.tonic;
            scale = kr.scale;
            System.out.println("Определена тональность: " + tonic.display()
                    + " " + (scale == ScaleType.MAJOR ? "major" : "minor"));
        } else {
            String[] parts = keyLine.split("\\s+");
            tonic = Note.fromString(parts[0]);
            scale = (parts.length > 1 && parts[1].equalsIgnoreCase("minor"))
                    ? ScaleType.NATURAL_MINOR : ScaleType.MAJOR;
        }

        List<Chord> progression = ChordSuggester.suggestProgression(measures, tonic, scale);
        List<ChordEvent> events = new ArrayList<>();
        for (int i = 0; i < progression.size(); i++) {
            events.add(new ChordEvent("Такт " + (i + 1), progression.get(i)));
        }
        return events;
    }
}
