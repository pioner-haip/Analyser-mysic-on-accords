package chordfinder;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Импорт текста песни с аккордами с сайтов, где аккорды даны обычным текстом прямо
 * в строке (например akkords.pro). НЕ подходит для сайтов, где аппликатуры и текст
 * показаны картинкой (например pereborom.ru) — оттуда программно текст не вытащить.
 */
public class SongImporter {
    private static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125 Safari/537.36";

    private static final String[] START_MARKERS = {"текст песни", "текст, аккорды", "аккорды, бой, текст"};
    private static final String[] END_MARKERS = {"Автор текста:", "Источник:", "Метки записи:", "Комментари"};

    private static final Pattern SECTION = Pattern.compile(
            "(Куплет\\s*\\d*|Припев\\s*\\d*|Проигрыш|Вступление|Бридж|Кода|Интро)\\s*:", Pattern.CASE_INSENSITIVE);
    private static final Pattern LATIN_RUN = Pattern.compile("[A-Za-z][A-Za-z0-9#/]*");
    private static final Pattern BR_TAG = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern BLOCK_CLOSE = Pattern.compile("(?i)</(p|div|li|h[1-6])>");
    private static final Pattern SCRIPT_STYLE = Pattern.compile("(?is)<(script|style)[^>]*>.*?</\\1>");
    private static final Pattern ANY_TAG = Pattern.compile("<[^>]+>");

    public static String fetchHtml(String url) throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .timeout(Duration.ofSeconds(15))
                .GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            throw new RuntimeException("Сайт ответил кодом " + resp.statusCode());
        }
        return resp.body();
    }

    /** Best-effort поиск страницы песни через REST API WordPress на akkords.pro. */
    public static String searchSongUrl(String query) {
        try {
            String q = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String json = fetchHtml("https://akkords.pro/wp-json/wp/v2/search?search=" + q + "&per_page=5");
            String normalized = json.replace("\\/", "/");
            Matcher m = Pattern.compile("\"url\":\"(https://akkords\\.pro/songs/[^\"]+)\"").matcher(normalized);
            if (m.find()) return m.group(1);
        } catch (Exception ignored) {
            // сайт может не отдавать REST API — вызывающий код покажет пользователю понятное сообщение
        }
        return null;
    }

    /** Достаёт текст с аккордами со страницы и приводит его к формату "[Аккорд]слово". */
    public static String extractAnnotatedLyrics(String html) {
        String text = SCRIPT_STYLE.matcher(html).replaceAll("");
        text = BR_TAG.matcher(text).replaceAll("\n");
        text = BLOCK_CLOSE.matcher(text).replaceAll("\n");
        text = ANY_TAG.matcher(text).replaceAll("");
        text = decodeEntities(text);

        int start = -1;
        String lower = text.toLowerCase();
        for (String marker : START_MARKERS) {
            int idx = lower.indexOf(marker);
            if (idx >= 0 && (start == -1 || idx < start)) start = idx;
        }
        if (start == -1) start = 0;

        int end = text.length();
        for (String marker : END_MARKERS) {
            int idx = text.indexOf(marker, start);
            if (idx >= 0 && idx < end) end = idx;
        }

        String chunk = text.substring(start, end);
        List<String> resultLines = new ArrayList<>();

        List<String[]> sections = new ArrayList<>();
        Matcher sm = SECTION.matcher(chunk);
        int last = -1;
        String lastLabel = null;
        while (sm.find()) {
            if (lastLabel != null) sections.add(new String[]{lastLabel, chunk.substring(last, sm.start())});
            lastLabel = sm.group().trim();
            last = sm.end();
        }
        if (lastLabel != null) {
            sections.add(new String[]{lastLabel, chunk.substring(last)});
        } else {
            sections.add(new String[]{null, chunk});
        }

        for (String[] section : sections) {
            String label = section[0];
            String body = section[1].replaceAll("\\s+", " ").trim();
            if (body.isEmpty() && label == null) continue;
            if (label != null) resultLines.add(label);
            for (String line : splitByChords(body)) {
                if (!line.isBlank()) resultLines.add(line);
            }
        }

        return String.join("\n", resultLines);
    }

    /** "Am Белый снег C На земле" -> ["[Am] Белый снег", "[C] На земле"] - по строке на аккорд. */
    private static List<String> splitByChords(String body) {
        String bracketed = bracketChords(body);
        List<String> lines = new ArrayList<>();
        Matcher m = Pattern.compile("\\[[^\\]]+\\]").matcher(bracketed);
        int prevChordStart = -1;
        int firstChordStart = -1;
        while (m.find()) {
            if (firstChordStart == -1) firstChordStart = m.start();
            if (prevChordStart != -1) {
                lines.add(bracketed.substring(prevChordStart, m.start()).trim());
            }
            prevChordStart = m.start();
        }
        if (firstChordStart > 0) {
            String lead = bracketed.substring(0, firstChordStart).trim();
            if (!lead.isEmpty()) lines.add(lead);
        }
        if (prevChordStart != -1) {
            lines.add(bracketed.substring(prevChordStart).trim());
        } else if (firstChordStart == -1 && !bracketed.isBlank()) {
            lines.add(bracketed.trim());
        }
        return lines;
    }

    /** Латинские "оторванные" от кириллицы куски текста считаем аккордами и берём в скобки. */
    private static String bracketChords(String body) {
        Matcher m = LATIN_RUN.matcher(body);
        StringBuilder out = new StringBuilder();
        int last = 0;
        while (m.find()) {
            out.append(body, last, m.start());
            out.append('[').append(m.group()).append(']');
            last = m.end();
        }
        out.append(body.substring(last));
        return out.toString();
    }

    private static String decodeEntities(String s) {
        return s.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&laquo;", "«")
                .replace("&raquo;", "»")
                .replace("&mdash;", "—")
                .replace("&ndash;", "–")
                .replace("&#171;", "«")
                .replace("&#187;", "»")
                .replace("&#8212;", "—")
                .replace("&#8211;", "–")
                .replace("&#8217;", "\u2019");
    }
}
