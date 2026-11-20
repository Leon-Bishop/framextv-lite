package io.framextv.lite;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Read-only catalogue adapter. The .tech endpoints are community-observed, NOT
 * part of the public embed API contract. Fail clearly if they change or disappear.
 * Playback itself uses the publicly documented FrameXTV embed URL instead.
 */
final class FrameApi {
    private static final String BASE = "https://api.framextv.tech";
    private static final int MAX_RESPONSE_BYTES = 2_000_000;

    private FrameApi() { }

    static List<Entry> home() throws Exception {
        JSONObject data = get("/api/home");
        JSONObject rails = data.optJSONObject("rails");
        if (rails == null) throw new IOException("Catalogue data unavailable");

        ArrayList<Entry> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        String[] preferred = { "trending_movies", "popular_movies", "popular_tv", "new_releases" };
        for (String key : preferred) {
            readRail(rails, key, result, ids);
        }
        // Handle changed section names without requiring an app update.
        if (result.isEmpty()) {
            Iterator<String> keys = rails.keys();
            while (keys.hasNext() && result.size() < 80) {
                readRail(rails, keys.next(), result, ids);
            }
        }
        if (result.isEmpty()) throw new IOException("No movies or shows found");
        return result;
    }

    static List<Entry> search(String text) throws Exception {
        JSONObject data = get("/api/search?q=" + URLEncoder.encode(text, "UTF-8"));
        JSONArray items = data.optJSONArray("results");
        return fromArray(items, "", 80);
    }

    static List<Entry> seasons(Entry show) throws Exception {
        JSONObject data = get("/api/details?id=" + checkedId(show.id) + "&type=tv");
        JSONArray seasons = data.optJSONArray("seasons");
        ArrayList<Entry> result = new ArrayList<>();
        if (seasons != null) {
            for (int i = 0; i < seasons.length() && i < 40; i++) {
                JSONObject s = seasons.optJSONObject(i);
                if (s == null) continue;
                int number = s.optInt("number", -1);
                if (number < 0) continue;
                String name = s.optString("name", "Season " + number);
                String extra = s.optInt("episodeCount", 0) > 0
                        ? s.optInt("episodeCount") + " episodes" : "Select to view episodes";
                result.add(new Entry(show.id, name, "season", extra, number, 0));
            }
        }
        // Some series omit season summaries. Allow opening the first season.
        if (result.isEmpty()) {
            result.add(new Entry(show.id, "Season 1", "season", "Browse episodes", 1, 0));
        }
        return result;
    }

    static List<Entry> episodes(Entry show, int season) throws Exception {
        JSONObject data = get("/api/season?id=" + checkedId(show.id) + "&season=" + season);
        JSONArray episodes = data.optJSONArray("episodes");
        ArrayList<Entry> result = new ArrayList<>();
        if (episodes != null) {
            for (int i = 0; i < episodes.length() && i < 150; i++) {
                JSONObject e = episodes.optJSONObject(i);
                if (e == null) continue;
                int number = e.optInt("number", -1);
                if (number < 1) continue;
                String title = e.optString("name", "Episode " + number);
                if (title.trim().isEmpty()) title = "Episode " + number;
                result.add(new Entry(show.id, "E" + number + "  " + title,
                        "episode", "Season " + season + " · Episode " + number,
                        season, number));
            }
        }
        if (result.isEmpty()) throw new IOException("No episode list was returned");
        return result;
    }

    private static void readRail(JSONObject rails, String key, List<Entry> rows, Set<String> ids) {
        JSONObject rail = rails.optJSONObject(key);
        if (rail == null || rows.size() >= 80) return;
        String label = rail.optString("title", key.replace('_', ' '));
        String defaultType = key.contains("tv") ? "tv" : key.contains("anime") ? "anime" : "movie";
        List<Entry> entries = fromArray(rail.optJSONArray("results"), defaultType, 24);
        for (Entry entry : entries) {
            if (rows.size() >= 80) return;
            if (ids.add(entry.type + ":" + entry.id)) {
                rows.add(new Entry(entry.id, entry.name, entry.type,
                        label + " · " + entry.description, 0, 0));
            }
        }
    }

    private static List<Entry> fromArray(JSONArray input, String fallbackType, int max) {
        ArrayList<Entry> result = new ArrayList<>();
        if (input == null) return result;
        for (int i = 0; i < input.length() && result.size() < max; i++) {
            JSONObject value = input.optJSONObject(i);
            if (value == null) continue;
            String id = value.optString("id", "");
            if (!id.matches("[0-9]{1,12}")) continue; // Valid TMDB ID only
            String type = value.optString("type", fallbackType).toLowerCase(java.util.Locale.US);
            if (type.equals("series") || type.equals("tvseries") || type.equals("show")) type = "tv";
            if (type.equals("film")) type = "movie";
            // Anime may use non-TMDB IDs and has a different player URL contract.
            if (!type.equals("movie") && !type.equals("tv")) continue;
            String title = value.optString("title", value.optString("name", ""));
            if (title.trim().isEmpty()) continue;
            String year = value.optString("year", "");
            String sub = (type.equals("tv") ? "Series" : "Movie") +
                    (year.matches("[0-9]{4}") ? " · " + year : "");
            result.add(new Entry(id, title, type, sub, 0, 0));
        }
        return result;
    }

    private static String checkedId(String id) {
        if (id == null || !id.matches("[0-9]{1,12}"))
            throw new IllegalArgumentException("Invalid TMDB ID");
        return id;
    }

    private static JSONObject get(String path) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(BASE + path).openConnection();
        c.setConnectTimeout(7000);
        c.setReadTimeout(10000);
        c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 9; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        c.setRequestProperty("Origin", "https://framextv.xyz");
        c.setRequestProperty("Referer", "https://framextv.xyz/");
        try {
            int code = c.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("Catalogue HTTP " + code);
            ByteArrayOutputStream memory = new ByteArrayOutputStream(32768);
            try (InputStream in = c.getInputStream()) {
                byte[] buffer = new byte[8192];
                int n;
                while ((n = in.read(buffer)) != -1) {
                    if (memory.size() + n > MAX_RESPONSE_BYTES)
                        throw new IOException("Catalogue response too large");
                    memory.write(buffer, 0, n);
                }
            }
            JSONObject json = new JSONObject(new String(memory.toByteArray(), StandardCharsets.UTF_8));
            if (json.has("success") && !json.optBoolean("success", true))
                throw new JSONException("Catalogue reported an error");
            return json;
        } finally {
            c.disconnect();
        }
    }
}
