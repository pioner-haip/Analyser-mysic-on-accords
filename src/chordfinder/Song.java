package chordfinder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Всё, что интерфейсу нужно знать о проанализированной песне. */
public class Song {
    public final String name;
    public final File file;          // файл, который показываем пользователю
    public final Player player;      // может быть null, если звук воспроизвести не удалось
    public final double duration;    // секунды
    public final double bpm;
    public final KeyEstimator.KeyResult key;
    public final List<ChordEvent> simple;    // только мажор/минор
    public final List<ChordEvent> extended;  // допускает септаккорды (для MIDI совпадает с simple)
    public final boolean fromAudio;

    public Song(File file, Player player, double duration, double bpm, KeyEstimator.KeyResult key,
                List<ChordEvent> simple, List<ChordEvent> extended, boolean fromAudio) {
        this.name = file.getName();
        this.file = file;
        this.player = player;
        this.duration = duration;
        this.bpm = bpm;
        this.key = key;
        this.simple = simple;
        this.extended = extended;
        this.fromAudio = fromAudio;
    }

    public List<ChordEvent> events(boolean simplified) {
        return simplified ? simple : extended;
    }

    /** Анализ звукового файла (долгий — вызывать вне потока интерфейса). */
    public static Song fromAudio(File shown, File playable, java.util.function.DoubleConsumer progress) throws Exception {
        AudioLoader.Samples samples = AudioLoader.load(playable);
        AudioChordDetector.Result r = AudioChordDetector.analyzeDetailed(samples, progress);
        if (r.simple.isEmpty()) {
            throw new IllegalStateException("Не удалось найти в записи аккорды: она слишком короткая или тихая.");
        }
        double bpm = TempoEstimator.fromAudio(samples);
        Player player = null;
        try {
            player = new WavPlayer(playable);
        } catch (Exception e) {
            // анализ всё равно полезен, даже если звуковая карта недоступна
        }
        return new Song(shown, player, r.durationSec, bpm, r.key, r.simple, r.extended, true);
    }

    /** Анализ MIDI-файла: аккорд на такт (размер 4/4). */
    public static Song fromMidi(File file) throws Exception {
        List<List<Note>> measures = MidiImporter.importMeasures(file);
        if (measures.isEmpty()) throw new IllegalStateException("В MIDI-файле нет нот.");
        List<Note> all = new ArrayList<>();
        for (List<Note> m : measures) all.addAll(m);
        KeyEstimator.KeyResult key = KeyEstimator.estimate(all);
        List<Chord> chords = ChordSuggester.suggestProgression(measures, key.tonic, key.scale);
        double bpm = TempoEstimator.fromMidi(file);
        double perMeasure = 4 * 60.0 / bpm;

        List<ChordEvent> events = new ArrayList<>();
        for (int i = 0; i < chords.size(); i++) {
            events.add(new ChordEvent(i * perMeasure, (i + 1) * perMeasure, chords.get(i)));
        }
        Player player = null;
        try {
            player = new MidiPlayer(file);
        } catch (Exception e) {
            // без звука тоже можно смотреть аккорды
        }
        double duration = player != null ? player.getDurationSeconds() : chords.size() * perMeasure;
        return new Song(file, player, duration, bpm, key, events, events, false);
    }
}
