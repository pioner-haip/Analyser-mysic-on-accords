package chordfinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.DoubleConsumer;

/**
 * Распознавание аккордов прямо из звука.
 *
 * Почему первая версия выдавала «лишние» аккорды: каждое окно решалось отдельно, а любая ошибка в
 * одном окне превращалась в отдельный аккорд (из 4 аккордов песни получалось 8-12). Здесь решение
 * принимается для ВСЕЙ песни сразу, в шесть шагов:
 *
 *  1. Сигнал уменьшается до ~11 кГц (нам нужны только частоты до 4 кГц) — быстрее и чище.
 *  2. В каждом кадре (~0,37 с) из спектра берутся только пики (а не все ячейки), строится хрома —
 *     12 чисел «сколько энергии на каждой ноте». Строй записи (сдвиг от 440 Гц) оценивается один раз
 *     на всю песню и компенсируется. Отдельно считается «бас» — какие ноты звучат ниже 250 Гц.
 *  3. По средней хроме оценивается тональность: аккорды лада получают небольшой бонус.
 *  4. Алгоритм Витерби (скрытая марковская модель) выбирает самую правдоподобную ПОСЛЕДОВАТЕЛЬНОСТЬ
 *     аккордов: смена аккорда стоит дорого, поэтому единичные «дребезги» не превращаются в аккорды.
 *  5. Подсчитывается, сколько времени звучал каждый аккорд. Аккорды, занявшие меньше ~6% песни,
 *     выбрасываются, и Витерби запускается ещё раз только по оставшемуся «словарю» песни.
 *  6. Слишком короткие отрезки (< 0,6 с) приклеиваются к более подходящему соседу.
 *
 * На выходе два варианта: «простой» (только мажор/минор) и «расширенный» (ещё G7, Am7 и т.п.).
 */
public class AudioChordDetector {
    // ---- параметры анализа (меняйте и смотрите, как меняется результат) ----
    private static final double TARGET_SR = 11025.0;   // частота после уменьшения
    private static final int FRAME = 4096;             // длина окна, сэмплов (~0,37 с)
    private static final int FFT_SIZE = 8192;          // БПФ с дополнением нулями — точнее положение пиков
    private static final int HOP = 1024;               // шаг между кадрами (~93 мс)
    private static final double MIN_FREQ = 65, MAX_FREQ = 4000;
    private static final double BASS_MIN = 60, BASS_MAX = 250;
    private static final double PEAK_REL_THRESHOLD = 0.04; // пик должен быть > 4% от самого сильного в кадре
    private static final double SILENCE_REL = 0.05;        // тише 5% «типичной громкости» = тишина

    private static final double BETA = 8.0;          // перевод оценки схожести в «логарифм правдоподобия»
    private static final double SWITCH_COST = 4.0;   // штраф за смену аккорда (больше = стабильнее, но медленнее реакция)
    private static final double MIN_SHARE = 0.06;    // аккорд, звучащий меньше 6% песни, в словарь не попадает
    private static final int MAX_VOCAB = 10;         // не больше стольких разных аккордов в песне
    private static final double MIN_SEGMENT_SEC = 0.6;
    private static final boolean DEBUG = Boolean.getBoolean("chordfinder.debug"); // java -Dchordfinder.debug=true ...
    // Септаккорд объявляется только при уверенных признаках: лучше пропустить «7», чем выдумать её
    // (7-я гармоника басовой струны сама звучит как малая септима и даёт ложные срабатывания).
    // Настоящие септаккорды на синтетике: отношение 0.79–1.15, устойчивость 1.0; ложные: отношение до 0.81.
    private static final double SEVENTH_MIN_RATIO = 0.82;    // в среднем 7-я нота не слабее 82% от тонов трезвучия...
    private static final double SEVENTH_MIN_PERSIST = 0.90;  // ...и звучит заметно (>= половины) минимум в 90% кадров отрезка

    /** Итог анализа: два варианта списка аккордов и тональность. */
    public static final class Result {
        public final List<ChordEvent> simple;    // только трезвучия
        public final List<ChordEvent> extended;  // допускаются септаккорды
        public final KeyEstimator.KeyResult key;
        public final double durationSec;

        Result(List<ChordEvent> simple, List<ChordEvent> extended, KeyEstimator.KeyResult key, double durationSec) {
            this.simple = simple;
            this.extended = extended;
            this.key = key;
            this.durationSec = durationSec;
        }
    }

    /** Признаки, посчитанные по звуку: по одному кадру на ~93 мс. */
    private static final class Features {
        int frames;
        double sr;                 // частота дискретизации после уменьшения
        double[][] chroma;         // L2-нормированная хрома кадра
        double[][] bass;           // доли нот в басовом диапазоне (сумма = 1) или нули
        boolean[] silent;
    }

    private static final class Seg {
        int start, end, state; // кадры, end включительно
        Seg(int s, int e, int st) { start = s; end = e; state = st; }
        int len() { return end - start + 1; }
    }

    // ------------------------------------------------------------------------------------------
    // Публичный API
    // ------------------------------------------------------------------------------------------

    /** Совместимость с консольной версией: только простой список аккордов. */
    public static List<ChordEvent> analyze(AudioLoader.Samples audio) {
        return analyzeDetailed(audio, null).simple;
    }

    /**
     * Полный анализ. Работает долго (секунды), поэтому вызывайте не из потока интерфейса.
     * Если поток прервать (interrupt), будет выброшено CancellationException.
     *
     * @param progress получает числа от 0 до 1 (можно null)
     */
    public static Result analyzeDetailed(AudioLoader.Samples audio, DoubleConsumer progress) {
        double duration = audio.data.length / (double) audio.sampleRate;
        Features f = extract(audio, progress);
        KeyEstimator.KeyResult none = new KeyEstimator.KeyResult(Note.C, ScaleType.MAJOR);
        if (f == null) return new Result(new ArrayList<>(), new ArrayList<>(), none, duration);
        return decode(f, duration, progress);
    }

    // ------------------------------------------------------------------------------------------
    // Шаги 1–2: звук -> хрома
    // ------------------------------------------------------------------------------------------

    private static Features extract(AudioLoader.Samples audio, DoubleConsumer progress) {
        int factor = Math.max(1, (int) Math.round(audio.sampleRate / TARGET_SR));
        double sr = audio.sampleRate / (double) factor;
        double[] x = decimate(audio.data, factor);
        int frames = (x.length - FRAME) / HOP + 1;
        if (frames < 4) return null;
        report(progress, 0.03);

        double[] hann = new double[FRAME];
        for (int i = 0; i < FRAME; i++) hann[i] = 0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FRAME - 1));
        double[] re = new double[FFT_SIZE], im = new double[FFT_SIZE], mag = new double[FFT_SIZE / 2];
        int kLo = Math.max(3, (int) Math.floor(MIN_FREQ * FFT_SIZE / sr));
        int kHi = Math.min(FFT_SIZE / 2 - 3, (int) Math.ceil(MAX_FREQ * FFT_SIZE / sr));

        float[][] peaks = new float[frames][];  // на пик: [нота MIDI (дробная), вес, частота]
        double[] rms = new double[frames];

        for (int f = 0; f < frames; f++) {
            if (Thread.currentThread().isInterrupted()) throw new CancellationException();
            int off = f * HOP;
            double sum = 0;
            for (int i = 0; i < FRAME; i++) {
                double v = x[off + i];
                sum += v * v;
                re[i] = v * hann[i];
            }
            Arrays.fill(re, FRAME, FFT_SIZE, 0.0);
            Arrays.fill(im, 0.0);
            rms[f] = Math.sqrt(sum / FRAME);
            SimpleFFT.fft(re, im);

            double maxMag = 0;
            for (int k = kLo - 2; k <= kHi + 2; k++) {
                mag[k] = Math.hypot(re[k], im[k]);
                if (k >= kLo && k <= kHi && mag[k] > maxMag) maxMag = mag[k];
            }
            if (maxMag < 1e-9) { peaks[f] = new float[0]; continue; }
            double thr = PEAK_REL_THRESHOLD * maxMag;

            float[] tmp = new float[3 * 160];
            int n = 0;
            for (int k = kLo; k <= kHi && n < 160; k++) {
                double m = mag[k];
                if (m < thr) continue;
                // настоящий пик: не меньше всех соседей в радиусе 2 ячеек (отсекает боковые лепестки окна)
                if (m < mag[k - 1] || m < mag[k + 1] || m <= mag[k - 2] || m < mag[k + 2]) continue;
                // параболическая интерполяция по логарифму: частота точнее, чем размер ячейки БПФ
                double a = Math.log(mag[k - 1] + 1e-12), b = Math.log(m + 1e-12), c = Math.log(mag[k + 1] + 1e-12);
                double denom = a - 2 * b + c;
                double p = denom == 0 ? 0 : 0.5 * (a - c) / denom;
                double freq = (k + p) * sr / FFT_SIZE;
                double midi = 69 + 12 * Math.log(freq / 440.0) / Math.log(2);
                double rel = m / maxMag;
                // логарифмическое сжатие: сильные пики не заглушают слабые ноты аккорда
                double w = Math.log1p(30 * rel) / Math.log1p(30);
                tmp[3 * n] = (float) midi;
                tmp[3 * n + 1] = (float) w;
                tmp[3 * n + 2] = (float) freq;
                n++;
            }
            peaks[f] = Arrays.copyOf(tmp, 3 * n);
            if ((f & 31) == 0) report(progress, 0.03 + 0.77 * f / frames);
        }

        double tuning = estimateTuning(peaks); // в полутонах, от -0.5 до 0.5

        double[][] chroma = new double[frames][12];
        double[][] bass = new double[frames][12];
        for (int f = 0; f < frames; f++) {
            float[] pk = peaks[f];
            for (int i = 0; i < pk.length; i += 3) {
                double m = pk[i] - tuning;
                long nearest = Math.round(m);
                int pc = (int) (((nearest % 12) + 12) % 12);
                double dev = Math.abs(m - nearest);           // насколько пик «мимо» ноты (0..0.5)
                double g = Math.max(0.4, 1 - 1.2 * dev);      // пики между нотами считаем менее надёжными
                double w = pk[i + 1];
                chroma[f][pc] += w * g * freqWeight(pk[i + 2]);
                if (pk[i + 2] >= BASS_MIN && pk[i + 2] <= BASS_MAX) bass[f][pc] += w;
            }
        }
        peaks = null;

        double[][] chromaS = smooth(chroma);
        double[][] bassS = smooth(bass);

        // порог тишины — доля от «типичной громкости» (90-й процентиль), а не абсолютное число
        double[] sorted = rms.clone();
        Arrays.sort(sorted);
        double ref = sorted[(int) (0.9 * (sorted.length - 1))];

        Features F = new Features();
        F.frames = frames;
        F.sr = sr;
        F.chroma = new double[frames][12];
        F.bass = new double[frames][12];
        F.silent = new boolean[frames];
        for (int f = 0; f < frames; f++) {
            double norm = 0;
            for (double v : chromaS[f]) norm += v * v;
            norm = Math.sqrt(norm);
            if (rms[f] < SILENCE_REL * ref || norm < 1e-6) { F.silent[f] = true; continue; }
            for (int k = 0; k < 12; k++) F.chroma[f][k] = chromaS[f][k] / norm;
            double bs = 0;
            for (double v : bassS[f]) bs += v;
            if (bs > 1e-6) for (int k = 0; k < 12; k++) F.bass[f][k] = bassS[f][k] / bs;
        }
        return F;
    }

    /**
     * Строй записи. Если гитара настроена на 30 центов выше эталона, все ноты «съезжают» на треть полутона
     * и часть энергии попадает не в тот класс. Берём круговое среднее дробных частей нот по самым устойчивым
     * пикам (100–1000 Гц): это и есть общий сдвиг строя.
     */
    private static double estimateTuning(float[][] peaks) {
        double sx = 0, sy = 0;
        for (float[] pk : peaks) {
            for (int i = 0; i < pk.length; i += 3) {
                double w = pk[i + 1], f = pk[i + 2];
                if (w < 0.3 || f < 100 || f > 1000) continue;
                double ang = 2 * Math.PI * pk[i];
                sx += w * Math.cos(ang);
                sy += w * Math.sin(ang);
            }
        }
        if (sx == 0 && sy == 0) return 0;
        return Math.atan2(sy, sx) / (2 * Math.PI);
    }

    /** Вес частоты: самые информативные 130–1800 Гц, у краёв диапазона меньше. */
    private static double freqWeight(double f) {
        if (f < 65) return 0;
        if (f < 130) return 0.5 + 0.5 * (f - 65) / 65.0;
        if (f <= 1800) return 1.0;
        if (f <= 3500) return 1.0 - 0.8 * (f - 1800) / 1700.0;
        return 0.2;
    }

    /** Лёгкое сглаживание по времени [1 2 1]/4: убирает «рваные» кадры в момент удара по струнам. */
    private static double[][] smooth(double[][] a) {
        int n = a.length;
        double[][] r = new double[n][12];
        for (int f = 0; f < n; f++) {
            double[] p = a[Math.max(0, f - 1)], c = a[f], q = a[Math.min(n - 1, f + 1)];
            for (int k = 0; k < 12; k++) r[f][k] = 0.25 * p[k] + 0.5 * c[k] + 0.25 * q[k];
        }
        return r;
    }

    /** Понижение частоты дискретизации с ФНЧ (оконный sinc), чтобы частоты выше новой границы не «заворачивались». */
    private static double[] decimate(double[] x, int factor) {
        if (factor <= 1) return x;
        int taps = 8 * factor + 1, half = taps / 2;
        double fc = 0.45 / factor;
        double[] h = new double[taps];
        double sum = 0;
        for (int k = 0; k < taps; k++) {
            int n = k - half;
            double sinc = n == 0 ? 2 * fc : Math.sin(2 * Math.PI * fc * n) / (Math.PI * n);
            double w = 0.54 - 0.46 * Math.cos(2 * Math.PI * k / (taps - 1));
            h[k] = sinc * w;
            sum += h[k];
        }
        for (int k = 0; k < taps; k++) h[k] /= sum;
        int outLen = x.length / factor;
        double[] y = new double[outLen];
        for (int j = 0; j < outLen; j++) {
            int center = j * factor;
            double acc = 0;
            for (int k = 0; k < taps; k++) {
                int idx = center + k - half;
                if (idx >= 0 && idx < x.length) acc += h[k] * x[idx];
            }
            y[j] = acc;
        }
        return y;
    }

    // ------------------------------------------------------------------------------------------
    // Шаги 3–6: хрома -> последовательность аккордов
    // ------------------------------------------------------------------------------------------

    private static Result decode(Features F, double durationSec, DoubleConsumer progress) {
        int first = -1, last = -1;
        for (int f = 0; f < F.frames; f++) {
            if (!F.silent[f]) { if (first < 0) first = f; last = f; }
        }
        KeyEstimator.KeyResult none = new KeyEstimator.KeyResult(Note.C, ScaleType.MAJOR);
        if (first < 0) return new Result(new ArrayList<>(), new ArrayList<>(), none, durationSec);

        // 3. тональность по средней хроме -> «свои» аккорды лада
        double[] mean = new double[12];
        for (int f = first; f <= last; f++) {
            if (F.silent[f]) continue;
            for (int k = 0; k < 12; k++) mean[k] += F.chroma[f][k];
        }
        KeyEstimator.KeyResult key0 = KeyEstimator.estimate(mean);
        Set<Chord> diatonic = new HashSet<>(KeyChords.diatonicChords(key0.tonic, key0.scale));

        // 4. первый проход: 24 трезвучия + 24 септаккорда. Септаккорды здесь нужны только чтобы ВЕРНО найти
        //    корень: у A7 нет ноты B, зато есть E и G, и без септ-шаблона он легко «превращается» в Em.
        List<Chord> states1 = new ArrayList<>(ChordTemplateMatcher.TRIADS);
        for (Note n : Note.values()) states1.add(new Chord(n, ChordType.DOM7));
        for (Note n : Note.values()) states1.add(new Chord(n, ChordType.MIN7));
        ChordTemplateMatcher.Scorer all = new ChordTemplateMatcher.Scorer(states1, diatonic);
        double[][] emis1 = emissions(F, first, last, all);
        int[] path1 = viterbi(emis1, F, first);
        report(progress, 0.90);

        // 5. словарь песни: считаем время по ТРЕЗВУЧИЯМ (G7 засчитывается как G) и оставляем заметные
        int S = ChordTemplateMatcher.TRIADS.size();
        int[] count = new int[S];
        int voiced = 0;
        for (int t = 0; t < path1.length; t++) {
            if (F.silent[first + t]) continue;
            count[triadIndex(states1.get(path1[t]))]++;
            voiced++;
        }
        List<Integer> order = new ArrayList<>();
        for (int s = 0; s < S; s++) order.add(s);
        order.sort((x, y) -> Integer.compare(count[y], count[x]));
        List<Chord> vocab = new ArrayList<>();
        for (int i = 0; i < order.size() && vocab.size() < MAX_VOCAB; i++) {
            int s = order.get(i);
            if (i == 0 || (voiced > 0 && count[s] / (double) voiced >= MIN_SHARE)) {
                vocab.add(ChordTemplateMatcher.TRIADS.get(s));
            }
        }
        int V = vocab.size();
        int minSeg = (int) Math.ceil(MIN_SEGMENT_SEC / (HOP / F.sr));

        // повторный проход только по словарю: каждый аккорд словаря + его септ-вариант (состояния V..2V-1)
        List<Chord> states2 = new ArrayList<>(vocab);
        for (Chord c : vocab) states2.add(new Chord(c.root, c.type == ChordType.MAJOR ? ChordType.DOM7 : ChordType.MIN7));
        ChordTemplateMatcher.Scorer sc = new ChordTemplateMatcher.Scorer(states2, diatonic);
        double[][] emis = emissions(F, first, last, sc);
        int[] path = viterbi(emis, F, first);

        // дальше работаем с трезвучиями: «Am» и «Am7» — один отрезок, септима решается позже, по всему отрезку
        int T = path.length;
        int[] pathTri = new int[T];
        double[][] emisTri = new double[T][V];
        for (int t = 0; t < T; t++) {
            pathTri[t] = path[t] % V;
            for (int v = 0; v < V; v++) emisTri[t][v] = Math.max(emis[t][v], emis[t][V + v]);
        }
        List<Seg> segs = toSegments(pathTri);
        cleanSegments(segs, emisTri, minSeg);
        List<ChordEvent> simple = toEvents(segs, vocab, F, first, durationSec, false);
        List<ChordEvent> extended = toEvents(segs, vocab, F, first, durationSec, true);
        report(progress, 1.0);

        return new Result(simple, extended, KeyEstimator.fromChords(simple), durationSec);
    }

    /** Номер трезвучия (0..11 мажор, 12..23 минор), к которому сводится аккорд: G7 -> G, Am7 -> Am. */
    private static int triadIndex(Chord c) {
        Chord t = c.simplified();
        return (t.type == ChordType.MINOR ? 12 : 0) + t.root.pitchClass;
    }

    /** Оценка каждого состояния (аккорда) в каждом кадре; в тишине все оценки равны (кадр ничего не сообщает). */
    private static double[][] emissions(Features F, int first, int last, ChordTemplateMatcher.Scorer sc) {
        int T = last - first + 1;
        double[][] e = new double[T][sc.size()];
        double[] tmp = new double[sc.size()];
        for (int t = 0; t < T; t++) {
            int f = first + t;
            if (F.silent[f]) continue;
            sc.score(F.chroma[f], F.bass[f], tmp);
            for (int s = 0; s < tmp.length; s++) e[t][s] = BETA * tmp[s];
        }
        return e;
    }

    /**
     * Алгоритм Витерби. Ищет последовательность аккордов с максимальной суммой оценок, где каждая смена
     * аккорда «стоит» SWITCH_COST. Остаться в том же аккорде бесплатно — отсюда устойчивость.
     */
    private static int[] viterbi(double[][] emis, Features F, int first) {
        int T = emis.length, S = emis[0].length;
        double[] prev = emis[0].clone(), cur = new double[S];
        boolean[][] fromSelf = new boolean[T][S];
        int[] bestPrev = new int[T];
        for (int t = 1; t < T; t++) {
            int arg = 0;
            for (int s = 1; s < S; s++) if (prev[s] > prev[arg]) arg = s;
            bestPrev[t - 1] = arg;
            double viaSwitch = prev[arg] - SWITCH_COST;
            for (int s = 0; s < S; s++) {
                if (prev[s] >= viaSwitch) { cur[s] = prev[s] + emis[t][s]; fromSelf[t][s] = true; }
                else { cur[s] = viaSwitch + emis[t][s]; fromSelf[t][s] = false; }
            }
            double[] swap = prev; prev = cur; cur = swap;
        }
        int[] path = new int[T];
        int state = 0;
        for (int s = 1; s < S; s++) if (prev[s] > prev[state]) state = s;
        path[T - 1] = state;
        for (int t = T - 1; t >= 1; t--) {
            state = fromSelf[t][state] ? state : bestPrev[t - 1];
            path[t - 1] = state;
        }
        return path;
    }

    private static List<Seg> toSegments(int[] path) {
        List<Seg> segs = new ArrayList<>();
        int start = 0;
        for (int t = 1; t <= path.length; t++) {
            if (t == path.length || path[t] != path[start]) {
                segs.add(new Seg(start, t - 1, path[start]));
                start = t;
            }
        }
        return segs;
    }

    /** Приклеивает слишком короткие отрезки к тому соседу, чей аккорд лучше подходит к их кадрам. */
    private static void cleanSegments(List<Seg> segs, double[][] emis, int minSeg) {
        while (segs.size() > 1) {
            int idx = -1;
            for (int i = 0; i < segs.size(); i++) {
                if (segs.get(i).len() < minSeg && (idx < 0 || segs.get(i).len() < segs.get(idx).len())) idx = i;
            }
            if (idx < 0) break;
            Seg s = segs.get(idx);
            Seg left = idx > 0 ? segs.get(idx - 1) : null;
            Seg right = idx < segs.size() - 1 ? segs.get(idx + 1) : null;
            Seg target;
            if (left == null) target = right;
            else if (right == null) target = left;
            else target = fit(s, left.state, emis) >= fit(s, right.state, emis) ? left : right;
            s.state = target.state;
            mergeEqual(segs);
        }
    }

    private static double fit(Seg s, int state, double[][] emis) {
        double sum = 0;
        for (int t = s.start; t <= s.end; t++) sum += emis[t][state];
        return sum;
    }

    private static void mergeEqual(List<Seg> segs) {
        for (int i = segs.size() - 1; i > 0; i--) {
            Seg a = segs.get(i - 1), b = segs.get(i);
            if (a.state == b.state) { a.end = b.end; segs.remove(i); }
        }
    }

    private static List<ChordEvent> toEvents(List<Seg> segs, List<Chord> triads, Features F, int first,
                                             double durationSec, boolean allowSevenths) {
        List<ChordEvent> events = new ArrayList<>();
        for (int i = 0; i < segs.size(); i++) {
            Seg s = segs.get(i);
            int fStart = first + s.start;
            double start = fStart == 0 ? 0 : frameBoundary(fStart, F.sr);
            double end;
            if (i + 1 < segs.size()) end = frameBoundary(first + segs.get(i + 1).start, F.sr);
            else end = (first + s.end >= F.frames - 1) ? durationSec : Math.min(durationSec, frameBoundary(first + s.end + 1, F.sr));
            end = Math.min(end, durationSec);
            if (end <= start) continue;
            Chord chord = triads.get(s.state);
            if (allowSevenths) chord = maybeSeventh(chord, s, F, first);
            events.add(new ChordEvent(start, end, chord));
        }
        return events;
    }

    /**
     * Септаккорд (G7, Am7) определяется по ВСЕМУ отрезку аккорда, а не по отдельным кадрам: по кадрам
     * случайная нота мелодии легко выглядит как «седьмая ступень». Берём среднюю хрому отрезка и
     * считаем аккорд септаккордом, только если малая септима звучит почти так же сильно, как тоны
     * трезвучия, и заметно сильнее «посторонних» нот.
     */
    private static Chord maybeSeventh(Chord chord, Seg s, Features F, int first) {
        if (chord.type != ChordType.MAJOR && chord.type != ChordType.MINOR) return chord;
        int r = chord.root.pitchClass;
        int third = (r + (chord.type == ChordType.MAJOR ? 4 : 3)) % 12, fifth = (r + 7) % 12, seventh = (r + 10) % 12;
        double[] m = new double[12];
        int n = 0, persistent = 0;
        for (int t = s.start; t <= s.end; t++) {
            int f = first + t;
            if (F.silent[f]) continue;
            double[] c = F.chroma[f];
            for (int k = 0; k < 12; k++) m[k] += c[k];
            n++;
            double tonesF = (c[r] + c[third] + c[fifth]) / 3.0;
            if (c[seventh] >= 0.5 * tonesF) persistent++;
        }
        if (n == 0) return chord;
        for (int k = 0; k < 12; k++) m[k] /= n;
        double tones = (m[r] + m[third] + m[fifth]) / 3.0;
        if (tones < 1e-9) return chord;
        double ratio = m[seventh] / tones, persist = persistent / (double) n;
        if (DEBUG) {
            System.out.printf("  [7th?] %s кадры %d-%d: септима/тоны = %.2f, устойчивость %.2f%n",
                    chord.name(), s.start, s.end, ratio, persist);
        }
        if (ratio < SEVENTH_MIN_RATIO || persist < SEVENTH_MIN_PERSIST) return chord;
        double others = 0;
        for (int k = 0; k < 12; k++) if (k != r && k != third && k != fifth && k != seventh) others += m[k];
        others /= 8.0;
        if (m[seventh] < 2.0 * others) return chord;
        return new Chord(chord.root, chord.type == ChordType.MAJOR ? ChordType.DOM7 : ChordType.MIN7);
    }

    /** Момент времени на границе между кадрами f-1 и f. Кадр «относится» к своему центру. */
    private static double frameBoundary(int f, double sr) {
        return Math.max(0, (f * (double) HOP + FRAME / 2.0 - HOP / 2.0) / sr);
    }

    private static void report(DoubleConsumer progress, double v) {
        if (progress != null) progress.accept(Math.max(0, Math.min(1, v)));
    }
}
