package chordfinder;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import java.io.File;

public class WavPlayer implements Player {
    private final Clip clip;

    public WavPlayer(File file) throws Exception {
        AudioInputStream ais = AudioSystem.getAudioInputStream(file);
        clip = AudioSystem.getClip();
        clip.open(ais);
    }

    @Override public void play() { clip.start(); }
    @Override public void pause() { clip.stop(); }
    @Override public void stop() { clip.stop(); clip.setFramePosition(0); }
    @Override public double getPositionSeconds() { return clip.getMicrosecondPosition() / 1_000_000.0; }
    @Override public double getDurationSeconds() { return clip.getMicrosecondLength() / 1_000_000.0; }
    @Override public boolean isPlaying() { return clip.isRunning(); }
}
