package chordfinder;

import javax.sound.midi.MetaMessage;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Track;
import java.io.File;

public class TempoEstimator {
    /** MIDI хранит темп точно (события смены темпа), просто читаем их. */
    public static double fromMidi(File file) throws Exception {
        Sequence seq = MidiSystem.getSequence(file);
        double sum = 0;
        int count = 0;
        for (Track track : seq.getTracks()) {
            for (int i = 0; i < track.size(); i++) {
                MidiEvent event = track.get(i);
                if (event.getMessage() instanceof MetaMessage) {
                    MetaMessage mm = (MetaMessage) event.getMessage();
                    if (mm.getType() == 0x51) { // Set Tempo
                        byte[] data = mm.getData();
                        int mpq = ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
                        if (mpq > 0) {
                            sum += 60_000_000.0 / mpq;
                            count++;
                        }
                    }
                }
            }
        }
        return count > 0 ? sum / count : 120.0; // 120 по умолчанию, если темп не прописан
    }

    /**
     * Упрощённая оценка темпа по аудио: строим огибающую энергии, берём её
     * "новизну" (onset-функцию) и ищем период через автокорреляцию в диапазоне
     * 60-200 уд/мин. Это приближение, а не точный бит-трекинг.
     */
    public static double fromAudio(AudioLoader.Samples audio) {
        int hop = 1024;
        double[] samples = audio.data;
        int numFrames = samples.length / hop;
        if (numFrames < 4) return 120.0;

        double[] energy = new double[numFrames];
        for (int i = 0; i < numFrames; i++) {
            double sum = 0;
            for (int j = 0; j < hop; j++) {
                double v = samples[i * hop + j];
                sum += v * v;
            }
            energy[i] = sum;
        }

        double[] novelty = new double[numFrames];
        for (int i = 1; i < numFrames; i++) {
            novelty[i] = Math.max(0, energy[i] - energy[i - 1]);
        }

        double frameRate = audio.sampleRate / hop;
        int minLag = Math.max(1, (int) Math.round(frameRate * 60.0 / 200.0));
        int maxLag = Math.min(numFrames - 1, (int) Math.round(frameRate * 60.0 / 60.0));
        if (maxLag <= minLag) return 120.0;

        double bestScore = -1;
        int bestLag = minLag;
        for (int lag = minLag; lag <= maxLag; lag++) {
            double score = 0;
            for (int i = 0; i + lag < numFrames; i++) {
                score += novelty[i] * novelty[i + lag];
            }
            if (score > bestScore) {
                bestScore = score;
                bestLag = lag;
            }
        }
        return 60.0 * frameRate / bestLag;
    }
}
