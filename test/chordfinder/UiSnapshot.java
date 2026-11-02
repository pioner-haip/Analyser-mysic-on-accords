package chordfinder;

import javax.imageio.ImageIO;
import java.awt.Component;
import java.awt.Container;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.Arrays;

/** Рисует экраны приложения в PNG без окна (для проверки вёрстки). Запуск: java -Djava.awt.headless=true ... UiSnapshot <папка> */
public class UiSnapshot {
    static void layout(Component c) {
        if (c instanceof Container) {
            Container k = (Container) c;
            k.doLayout();
            for (Component ch : k.getComponents()) layout(ch);
        }
    }

    static void invalidateAll(Component c) {
        c.invalidate();
        if (c instanceof Container) for (Component ch : ((Container) c).getComponents()) invalidateAll(ch);
    }

    static void shot(Component c, int w, int h, File out) throws Exception {
        invalidateAll(c);                   // вне EDT перерасчёт раскладки откладывается — делаем явно
        c.setSize(w, h);
        c.validate();                       // как делает настоящее окно: помечает компоненты валидными
        for (int i = 0; i < 2; i++) layout(c);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        c.paint(g);
        g.dispose();
        ImageIO.write(img, "png", out);
    }

    public static void main(String[] args) throws Exception {
        File dir = new File(args.length > 0 ? args[0] : "/tmp/shots");
        dir.mkdirs();
        int W = 900, H = 572;

        StartPanel start = new StartPanel(new StartPanel.Actions() {
            public void choose() {} public void record() {} public void open(File f) {} public void cancel() {}
        });
        shot(start, W, H, new File(dir, "1-start.png"));
        start.setBusy("my_song.wav");
        start.setProgress(0.42);
        shot(start, W, H, new File(dir, "1b-busy.png"));

        SynthAudio.Options o = new SynthAudio.Options();
        o.loops = 6; o.melody = true;
        SynthAudio s = SynthAudio.progression(new String[]{"Am", "F", "C", "G"}, o);
        AudioChordDetector.Result r = AudioChordDetector.analyzeDetailed(s.samples, null);
        Song song = new Song(new File("/music/Кино - Звезда по имени Солнце (live).wav"), null,
                r.durationSec, 100, r.key, r.simple, r.extended, true);
        ResultPanel res = new ResultPanel(() -> {}, x -> {});
        res.loadSong(song);
        res.showPosition(4.0);
        shot(res, W, H, new File(dir, "2-result-timeline.png"));

        res.setLyricsForTest(Arrays.asList("Белое покрывало на крыше, вновь я в снегу", "Ты уходишь по тропе, я по ней иду", "",
                "Снег идёт, а мы с тобой гуляем по аллее", "Тихо падает листва осенняя в окно", "", "Припев: Звезда по имени Солнце"), false);
        res.showPosition(9.0);
        shot(res, W, H, new File(dir, "3-result-lyrics.png"));

        res.setShiftForTest(2);
        res.showPosition(14.0);
        shot(res, 760, 520, new File(dir, "4-result-narrow-shift.png"));
        System.exit(0);
    }
}
