package com.nekodown.android;

public class DownloadItem {
    public final String id;
    public final String title;
    public final String subtitle;
    public final int progress;
    public final String state;

    public DownloadItem(String id, String title, String subtitle, int progress, String state) {
        this.id = id;
        this.title = title;
        this.subtitle = subtitle;
        this.progress = progress;
        this.state = state;
    }
}
