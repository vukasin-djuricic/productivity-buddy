package org.productivity_buddy;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;


public class ProcessInfo {

    private final String originalName;
    private volatile long pid;
    private volatile long startTime;

    private final AtomicReference<String> aliasName;
    private final AtomicReference<ProcessCategory> category;

    private final AtomicBoolean frozen;
    private final AtomicLong totalTime;
    private final AtomicLong sessionTime;

    private volatile long lastUpdateTime;
    private volatile double cpuUsage;
    private volatile long ramUsageBytes;

    private volatile boolean active;

    // browser tabovi — volatile lista, zamenjuje se atomicno (copy-on-write pristup)
    private volatile List<TabInfo> tabs = Collections.emptyList();


    public ProcessInfo(String originalName) {
        this.originalName = originalName;
        this.pid = -1;
        this.startTime = -1;
        this.aliasName = new AtomicReference<>(originalName);
        this.category = new AtomicReference<>(ProcessCategory.UNCATEGORIZED);
        this.frozen = new AtomicBoolean(false);
        this.totalTime = new AtomicLong(0);
        this.sessionTime = new AtomicLong(0);
        this.lastUpdateTime = System.currentTimeMillis();
        this.cpuUsage = 0.0;
        this.ramUsageBytes = 0;
        this.active = false;
    }

    // --- GETTERI ---
    public String getOriginalName() { return originalName; }
    public long getPid() { return pid; }
    public long getStartTime() { return startTime; }
    public String getAliasName() { return aliasName.get(); }
    public String getCategory() { return category.get().getDisplayName(); }
    public ProcessCategory getCategoryEnum() { return category.get(); }
    public boolean isFrozen() { return frozen.get(); }
    public long getTotalTime() { return totalTime.get(); }
    public long getSessionTime() { return sessionTime.get(); }
    public long getLastUpdateTime() { return lastUpdateTime; }
    public double getCpuUsage() { return cpuUsage; }
    public long getRamUsageBytes() { return ramUsageBytes; }
    public boolean isAlive() { return active; }
    public List<TabInfo> getTabs() { return tabs; }
    public boolean hasTabs() { return !tabs.isEmpty(); }


    // --- SETTERI ---
    public void setPid(long pid) { this.pid = pid; }
    public void setStartTime(long t) { this.startTime = t; }
    public void setAliasName(String name) { this.aliasName.set(name); }
    public void setCategory(String cat) { this.category.set(ProcessCategory.fromDisplayName(cat)); }
    public void setCategory(ProcessCategory cat) { this.category.set(cat); }
    public void setFrozen(boolean frozen) { this.frozen.set(frozen); }
    public void setTotalTime(long t) { this.totalTime.set(t); }
    public void setCpuUsage(double cpu) { this.cpuUsage = cpu; }
    public void setRamUsageBytes(long ram) { this.ramUsageBytes = ram; }
    public void setActive(boolean alive) { this.active = alive; }
    public void setLastUpdateTime(long t) { this.lastUpdateTime = t; }
    public void setTabs(List<TabInfo> tabs) { this.tabs = Collections.unmodifiableList(tabs); }

    public void addSessionTime(long delta)
    {
        this.sessionTime.addAndGet(delta);
    }

    public void resetSessionTime()
    {
        this.sessionTime.set(0);
    }

    public long getEffectiveTotalTime()
    {
        return this.totalTime.get() + this.sessionTime.get();
    }

}
