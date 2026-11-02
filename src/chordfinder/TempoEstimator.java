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
     * Упрощённая оценка темпа по аудио: энергия по коротким кадрам -> «новизна» (резкие приросты громкости,
     * то есть удары по струнам) -> автокорреляция: ищем, через какой промежуток удары повторяются чаще всего.
     * Это приближение, а не точный бит-трекинг.
     * Улучшения по сравнению с первой версией:
     *  - автокорреляция нормируется на число слагаемых (раньше короткие задержки выигрывали «числом»);
     *  - громкость берётся в логарифмическом масштабе, чтобы один громкий удар не определял всё;
     *  - результат уточняется параболой между соседними задержками и приводится в диапазон 70–170 уд/мин
     *    (темп в 2 раза быстрее или медленнее звучит «так же», выбираем привычный диапазон).
     */
    public static double fromAudio(AudioLoader.Samples audio) {
        int hop = 512;
        double[] samples = audio.data;
        int numFrames = samples.length / hop;
        if (numFrames < 16) return 120.0;

        double[] energy = new double[numFrames];
        double meanE = 0;
        for (int i = 0; i < numFrames; i++) {
            double sum = 0;
            for (int j = 0; j < hop; j++) {
                double v = samples[i * hop + j];
                sum += v * v;
            }
            energy[i] = sum;
            meanE += sum;
        }
        meanE = meanE / numFrames + 1e-12;

        double[] logE = new double[numFrames];
        for (int i = 0; i < numFrames; i++) logE[i] = Math.log1p(10 * energy[i] / meanE);
        double[] novelty = new double[numFrames];
        double meanN = 0;
        for (int i = 1; i < numFrames; i++) {
            novelty[i] = Math.max(0, logE[i] - logE[i - 1]);
            meanN += novelty[i];
        }
        meanN /= numFrames;
        for (int i = 0; i < numFrames; i++) novelty[i] -= meanN; // центрируем: ищем повторяемость, а не «средний уровень»

        double frameRate = audio.sampleRate / (double) hop;
        int minLag = Math.max(1, (int) Math.floor(frameRate * 60.0 / 200.0));
        int maxLag = Math.min(numFrames / 2, (int) Math.ceil(frameRate * 60.0 / 60.0));
        if (maxLag <= minLag + 2) return 120.0;

        double[] score = new double[maxLag + 2];
        for (int lag = minLag; lag <= maxLag + 1 && lag < numFrames; lag++) {
            double acc = 0;
            for (int i = 0; i + lag < numFrames; i++) acc += novelty[i] * novelty[i + lag];
            score[lag] = acc / (numFrames - lag);
        }
        int bestLag = minLag;
        for (int lag = minLag; lag <= maxLag; lag++) if (score[lag] > score[bestLag]) bestLag = lag;

        double refined = bestLag;
        if (bestLag > minLag && bestLag < maxLag) {
            double a = score[bestLag - 1], b = score[bestLag], c = score[bestLag + 1];
            double denom = a - 2 * b + c;
            if (denom != 0) refined += 0.5 * (a - c) / denom;
        }
        double bpm = 60.0 * frameRate / refined;
        while (bpm > 170) bpm /= 2;
        while (bpm < 70) bpm *= 2;
        return bpm;
    }
}
