package chordfinder;

import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.Sequencer;
import java.io.File;

public class MidiPlayer implements Player {
    private final Sequencer sequencer;

    public MidiPlayer(File file) throws Exception {
        Sequence seq = MidiSystem.getSequence(file);
        sequencer = MidiSystem.getSequencer(); // уже подключён к синтезатору по умолчанию
        sequencer.open();
        sequencer.setSequence(seq);
    }

    @Override public void play() { sequencer.start(); }
    @Override public void pause() { sequencer.stop(); }
    @Override public void stop() { sequencer.stop(); sequencer.setMicrosecondPosition(0); }
    @Override public double getPositionSeconds() { return sequencer.getMicrosecondPosition() / 1_000_000.0; }
    @Override public double getDurationSeconds() { return sequencer.getMicrosecondLength() / 1_000_000.0; }
    @Override public boolean isPlaying() { return sequencer.isRunning(); }
}
