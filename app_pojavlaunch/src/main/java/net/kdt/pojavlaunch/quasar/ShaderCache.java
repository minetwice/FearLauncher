package net.kdt.pojavlaunch.quasar;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ShaderCache {
    private static final int DEFAULT_MAX_SIZE = 1000;
    private final Map<String, CacheEntry> cache;
    private final int maxSize;
    private ProcessingStats.CacheStats stats = new ProcessingStats.CacheStats();
    
    public ShaderCache() { this(DEFAULT_MAX_SIZE); }
    public ShaderCache(int maxSize) {
        this.cache = new ConcurrentHashMap<>();
        this.maxSize = maxSize;
    }
    
    public String get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null) {
            entry.markAccessed();
            stats.incrementHits();
            return entry.getShaderSource();
        }
        stats.incrementMisses();
        return null;
    }
    
    public void put(String key, String shaderSource) {
        if (shaderSource != null && shaderSource.length() > 100 * 1024) return;
        if (cache.size() >= maxSize) evictOldest();
        cache.put(key, new CacheEntry(shaderSource));
        stats.setCacheSize(cache.size());
    }
    
    private void evictOldest() {
        String oldestKey = null;
        long oldestTime = Long.MAX_VALUE;
        for (Map.Entry<String, CacheEntry> entry : cache.entrySet()) {
            if (entry.getValue().getLastAccessedTime() < oldestTime) {
                oldestTime = entry.getValue().getLastAccessedTime();
                oldestKey = entry.getKey();
            }
        }
        if (oldestKey != null) cache.remove(oldestKey);
    }
    
    public void clear() { cache.clear(); stats.setCacheSize(0); }
    public boolean containsKey(String key) { return cache.containsKey(key); }
    public int size() { return cache.size(); }
    public ProcessingStats.CacheStats getStats() { return stats; }
    public void resetStats() { stats.reset(); }
    
    private static class CacheEntry {
        private final String shaderSource;
        private long lastAccessedTime;
        CacheEntry(String shaderSource) {
            this.shaderSource = shaderSource;
            this.lastAccessedTime = System.nanoTime();
        }
        String getShaderSource() { return shaderSource; }
        long getLastAccessedTime() { return lastAccessedTime; }
        void markAccessed() { this.lastAccessedTime = System.nanoTime(); }
    }
}