package chordfinder;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Импорт мелодии/партии из MIDI-файла (.mid). Работает для любой MIDI-музыки —
 * от электронных аранжировок до транскрипций акустических произведений.
 */
public class MidiImporter {
    private static final int BEATS_PER_MEASURE = 4; // предполагаем размер 4/4

    /** Импортирует MIDI-файл и группирует ноты по тактам. */
    public static List<List<Note>> importMeasures(File file) throws Exception {
        Sequence sequence = MidiSystem.getSequence(file);
        int resolution = sequence.getResolution(); // тиков на четверть
        long ticksPerMeasure = (long) resolution * BEATS_PER_MEASURE;
        if (ticksPerMeasure <= 0) ticksPerMeasure = 1;

        TreeMap<Long, List<Note>> byMeasure = new TreeMap<>();
        for (Track track : sequence.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                if (event.getMessage() instanceof ShortMessage) {
                    ShortMessage sm = (ShortMessage) event.getMessage();
                    if (sm.getCommand() == ShortMessage.NOTE_ON && sm.getData2() > 0) {
                        int noteNumber = sm.getData1();
                        int pitchClass = noteNumber % 12;
                        Note note = Note.values()[pitchClass];
                        long measureIndex = event.getTick() / ticksPerMeasure;
                        byMeasure.computeIfAbsent(measureIndex, k -> new ArrayList<>()).add(note);
                    }
                }
            }
        }

        List<List<Note>> measures = new ArrayList<>();
        for (List<Note> notes : byMeasure.values()) {
            measures.add(notes);
        }
        return measures;
    }
}
