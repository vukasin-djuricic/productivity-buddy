package org.productivity_buddy.model;

import org.productivity_buddy.service.CategorizationService;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ProcessRegistry {

    private final ConcurrentHashMap<String, ProcessInfo> processMap;
    private volatile CategorizationService categorizationService;

    public ProcessRegistry() {
        this.processMap = new ConcurrentHashMap<>();
    }

    public void setCategorizationService(CategorizationService service) {
        this.categorizationService = service;
    }

    // computeIfAbsent je ATOMICNA operacija — ako dve niti istovremeno
    // pozovu ovo za isti proces, samo jedna ce kreirati objekat
    // pri prvom kreiranju, auto-kategorizacija se primenjuje ako postoji servis
    public ProcessInfo getOrCreate(String procName) {
        return processMap.computeIfAbsent(procName, new java.util.function.Function<String, ProcessInfo>() {
            @Override
            public ProcessInfo apply(String name) {
                ProcessInfo info = new ProcessInfo(name);
                if (categorizationService != null) {
                    ProcessCategory cat = categorizationService.categorize(name);
                    info.setCategory(cat);
                }
                return info;
            }
        });
    }

    public ProcessInfo get(String procName) {
        return processMap.get(procName);
    }

    public Collection<ProcessInfo> getAll() {
        return processMap.values();
    }

    public Map<String, ProcessInfo> getMap() {
        return processMap;
    }

    // Ukloni procese koji vise nisu aktivni I nemaju sacuvano vreme
    // Procesi sa totalTime > 0 se ne brisu jer imaju podatke iz JSON-a
    public void removeDeadProcesses() {
        processMap.entrySet().removeIf(new java.util.function.Predicate<Map.Entry<String, ProcessInfo>>() {
            @Override
            public boolean test(Map.Entry<String, ProcessInfo> entry) {
                ProcessInfo info = entry.getValue();
                return !info.isAlive() && info.getEffectiveTotalTime() == 0;
            }
        });
    }

    public int size() {
        return processMap.size();
    }
}
