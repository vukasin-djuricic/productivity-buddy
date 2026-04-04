package org.productivity_buddy;

/**
 * Informacija o jednom browser tabu — naslov, URL, kategorija.
 */
public class TabInfo {

    private final String title;
    private final String url;
    private volatile ProcessCategory category;

    public TabInfo(String title, String url) {
        this.title = title;
        this.url = url;
        this.category = ProcessCategory.UNCATEGORIZED;
    }

    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getCategory() { return category.getDisplayName(); }
    public ProcessCategory getCategoryEnum() { return category; }
    public void setCategory(ProcessCategory cat) { this.category = cat; }

    // izvuci domen iz URL-a za prikaz
    public String getDomain() {
        if (url == null || url.isEmpty()) return "";
        try {
            String stripped = url;
            if (stripped.startsWith("https://")) stripped = stripped.substring(8);
            else if (stripped.startsWith("http://")) stripped = stripped.substring(7);
            int slash = stripped.indexOf('/');
            if (slash > 0) stripped = stripped.substring(0, slash);
            // ukloni www.
            if (stripped.startsWith("www.")) stripped = stripped.substring(4);
            return stripped;
        } catch (Exception e) {
            return url;
        }
    }
}
