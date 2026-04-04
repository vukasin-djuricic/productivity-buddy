package org.productivity_buddy;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Informacija o jednom browser tabu — naslov, URL, kategorija, vreme.
 * Domen se kesira pri konstrukciji i sluzi kao stabilan kljuc za mapu.
 */
public class TabInfo {

    private final String title;
    private final String url;
    private final String domain;
    private volatile ProcessCategory category;

    // pracenje vremena po tabu (isti model kao ProcessInfo)
    private final AtomicLong sessionTime;
    private volatile long totalTime;
    private volatile boolean active;

    public TabInfo(String title, String url) {
        this.title = title;
        this.url = url;
        this.domain = extractDomain(url);
        this.category = ProcessCategory.UNCATEGORIZED;
        this.sessionTime = new AtomicLong(0);
        this.totalTime = 0;
        this.active = false;
    }

    // --- GETTERI ---
    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getDomain() { return domain; }
    public String getCategory() { return category.getDisplayName(); }
    public ProcessCategory getCategoryEnum() { return category; }
    public long getSessionTime() { return sessionTime.get(); }
    public long getTotalTime() { return totalTime; }
    public boolean isActive() { return active; }

    public long getEffectiveTotalTime() {
        return totalTime + sessionTime.get();
    }

    // --- SETTERI ---
    public void setCategory(ProcessCategory cat) { this.category = cat; }
    public void setTotalTime(long t) { this.totalTime = t; }
    public void setActive(boolean a) { this.active = a; }

    public void addSessionTime(long delta) {
        sessionTime.addAndGet(delta);
    }

    public void resetSessionTime() {
        sessionTime.set(0);
    }

    // izvuci domen iz URL-a
    private static String extractDomain(String url) {
        if (url == null || url.isEmpty()) return "";
        try {
            String stripped = url;
            if (stripped.startsWith("https://")) stripped = stripped.substring(8);
            else if (stripped.startsWith("http://")) stripped = stripped.substring(7);
            int slash = stripped.indexOf('/');
            if (slash > 0) stripped = stripped.substring(0, slash);
            if (stripped.startsWith("www.")) stripped = stripped.substring(4);
            return stripped;
        } catch (Exception e) {
            return url;
        }
    }
}
