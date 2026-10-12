package chordfinder;

/**
 * Рекомендует типовой рисунок боя (гитарной техники правой руки) по темпу.
 * Это учебная рекомендация "по темпу", а не расшифровка реальных ударов из записи —
 * распознать вниз/вверх удары по спектру надёжно нельзя.
 */
public class StrumPatternSuggester {
    public static class Suggestion {
        public final String name;
        public final String pattern; // Д = вниз, У = вверх, Х = глушение
        public final String note;
        public Suggestion(String name, String pattern, String note) {
            this.name = name;
            this.pattern = pattern;
            this.note = note;
        }
    }

    public static Suggestion suggest(double bpm) {
        if (bpm < 70) {
            return new Suggestion("Баллада (четверти)", "Д - Д - Д - Д",
                    "Медленный темп: удар вниз на каждую четверть, даём аккорду звучать.");
        } else if (bpm < 100) {
            return new Suggestion("Шестёрка", "Д Д У У Д У",
                    "Самый ходовой бой на 6 восьмых долей такта.");
        } else if (bpm < 140) {
            return new Suggestion("Четвёрка с глушением", "Д У Х У",
                    "Х — короткое глушение струн ладонью на слабую долю.");
        } else {
            return new Suggestion("Быстрая восьмёрка", "Д У Д У Д У Д У",
                    "Быстрый темп — ровные восьмые без пауз.");
        }
    }
}
