package io.framextv.lite;

/** A small text-only list row. No bitmaps or heavy view models. */
final class Entry {
    final String id;
    final String name;
    final String type; // movie, tv, season, episode
    final String description;
    final int season;
    final int episode;

    Entry(String id, String name, String type, String description, int season, int episode) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.description = description;
        this.season = season;
        this.episode = episode;
    }
}
