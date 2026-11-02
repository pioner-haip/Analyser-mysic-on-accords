package chordfinder;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;

/**
 * Загружает WAV-файл и превращает его в моно-массив сэмплов [-1..1].
 * Вынесено отдельно, чтобы не читать файл дважды (для аккордов и для темпа).
 */
public class AudioLoader {
    public static class Samples {
        public final double[] data;
        public final float sampleRate;
        public Samples(double[] data, float sampleRate) {
            this.data = data;
            this.sampleRate = sampleRate;
        }
    }

    public static Samples load(File wavFile) throws Exception {
        AudioInputStream original = AudioSystem.getAudioInputStream(wavFile);
        AudioFormat baseFormat = original.getFormat();
        int channels = baseFormat.getChannels();
        float sampleRate = baseFormat.getSampleRate();

        AudioFormat pcm16 = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED, sampleRate, 16, channels, channels * 2, sampleRate, false);
        AudioInputStream pcmStream = baseFormat.matches(pcm16)
                ? original : AudioSystem.getAudioInputStream(pcm16, original);

        byte[] bytes = pcmStream.readAllBytes();
        pcmStream.close();

        int bytesPerFrame = 2 * channels;
        int totalFrames = bytes.length / bytesPerFrame;
        double[] samples = new double[totalFrames];
        for (int f = 0; f < totalFrames; f++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                int idx = f * bytesPerFrame + c * 2;
                int lo = bytes[idx] & 0xFF;
                int hi = bytes[idx + 1];
                short s = (short) ((hi << 8) | lo);
                sum += s;
            }
            samples[f] = (sum / (double) channels) / 32768.0;
        }
        return new Samples(samples, sampleRate);
    }
}
