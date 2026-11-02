package chordfinder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/** Список недавно открытых файлов; хранится в настройках пользователя (реестр/plist/~/.java), отдельных файлов не создаёт. */
public class RecentFiles {
    public static class Entry {
        public final File file;
        public final long openedAt;
        Entry(File file, long openedAt) { this.file = file; this.openedAt = openedAt; }
    }

    private static final int MAX = 8;
    private static final Preferences PREFS = Preferences.userNodeForPackage(RecentFiles.class);

    private static List<Entry> readAll() {
        List<Entry> list = new ArrayList<>();
        for (int i = 0; i < MAX; i++) {
            String v = PREFS.get("recent" + i, null);
            if (v == null) continue;
            int tab = v.indexOf('\t');
            if (tab < 0) continue;
            try {
                list.add(new Entry(new File(v.substring(tab + 1)), Long.parseLong(v.substring(0, tab))));
            } catch (NumberFormatException ignored) {
                // повреждённая запись — пропускаем
            }
        }
        return list;
    }

    /** Недавние файлы (только те, что ещё существуют), самые свежие первыми. */
    public static List<Entry> list(int max) {
        List<Entry> result = new ArrayList<>();
        for (Entry e : readAll()) {
            if (e.file.isFile() && result.size() < max) result.add(e);
        }
        return result;
    }

    public static void add(File file) {
        List<Entry> all = readAll();
        all.removeIf(e -> e.file.equals(file));
        all.add(0, new Entry(file, System.currentTimeMillis()));
        for (int i = 0; i < MAX; i++) {
            if (i < all.size()) PREFS.put("recent" + i, all.get(i).openedAt + "\t" + all.get(i).file.getAbsolutePath());
            else PREFS.remove("recent" + i);
        }
    }
}
