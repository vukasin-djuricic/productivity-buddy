package org.productivity_buddy;

public enum ProcessCategory {

    WORK("Work", "#8b5cf6"),
    FUN("Fun", "#06b6d4"),
    OTHER("Other", "#f59e0b"),
    UNCATEGORIZED("Uncategorized", "#9ca3af");

    private final String displayName;
    private final String color;

    ProcessCategory(String displayName, String color) {
        this.displayName = displayName;
        this.color = color;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getColor() {
        return color;
    }

    // po display imenu vrati enum (npr. "Work" -> WORK)
    public static ProcessCategory fromDisplayName(String name) {
        for (ProcessCategory cat : values()) {
            if (cat.displayName.equals(name)) {
                return cat;
            }
        }
        return UNCATEGORIZED;
    }

    // vrati niz display imena za dijaloge
    public static String[] allDisplayNames() {
        ProcessCategory[] all = values();
        String[] names = new String[all.length];
        for (int i = 0; i < all.length; i++) {
            names[i] = all[i].displayName;
        }
        return names;
    }
}
