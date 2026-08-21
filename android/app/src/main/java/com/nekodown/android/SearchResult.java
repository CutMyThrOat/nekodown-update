package com.nekodown.android;

public class SearchResult {
    public static final String YOUTUBE = "youtube";
    public static final String ARTIST = "artist";
    public static final String ALBUM = "album";
    public static final String TRACK = "track";

    public final String id;
    public final String title;
    public final String artist;
    public final String thumbnail;
    public final String kind;
    public final String previewUrl;
    public final String album;
    public final String year;
    public final String genre;
    public final int trackNumber;
    public final String duration;

    public SearchResult(String id, String title, String artist, String thumbnail, String duration) {
        this(id, title, artist, thumbnail, duration, YOUTUBE, "", "", "", "", 0);
    }

    public SearchResult(String id, String title, String artist, String thumbnail, String duration,
                        String kind, String previewUrl, String album,
                        String year, String genre, int trackNumber) {
        this.id = id;
        this.title = title;
        this.artist = artist;
        this.thumbnail = thumbnail;
        this.duration = duration;
        this.kind = kind;
        this.previewUrl = previewUrl;
        this.album = album;
        this.year = year;
        this.genre = genre;
        this.trackNumber = trackNumber;
    }
}
