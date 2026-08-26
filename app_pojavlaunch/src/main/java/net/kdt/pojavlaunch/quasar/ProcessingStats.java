package net.kdt.pojavlaunch.quasar;

import java.util.HashMap;
import java.util.Map;

public class ProcessingStats {
    private Map<String, StrategyStats> strategyStats = new HashMap<>();
    private CacheStats cacheStats = new CacheStats();
    private int totalShadersProcessed = 0;
    
    public void addStrategyStats(String strategyName, StrategyStats stats) {
        strategyStats.put(strategyName, stats);
        totalShadersProcessed += stats.getTotalAttempts();
    }
    public void setCacheStats(CacheStats stats) { this.cacheStats = stats; }
    public StrategyStats getStrategyStats(String strategyName) { return strategyStats.get(strategyName); }
    public Map<String, StrategyStats> getAllStrategyStats() { return new HashMap<>(strategyStats); }
    public CacheStats getCacheStats() { return cacheStats; }
    public int getTotalShadersProcessed() { return totalShadersProcessed; }
    public double getCacheHitRate() { return cacheStats.getHitRate(); }
    public String getMostSuccessfulStrategy() {
        String best = null; double bestRate = 0.0;
        for (Map.Entry<String, StrategyStats> e : strategyStats.entrySet()) {
            double rate = e.getValue().getSuccessRate();
            if (rate > bestRate) { bestRate = rate; best = e.getKey(); }
        }
        return best;
    }
    public void reset() { strategyStats.clear(); cacheStats = new CacheStats(); totalShadersProcessed = 0; }
    
    public static class CacheStats {
        private int cacheHits = 0; private int cacheMisses = 0; private int cacheSize = 0;
        public void incrementHits() { cacheHits++; }
        public void incrementMisses() { cacheMisses++; }
        public void setCacheSize(int size) { cacheSize = size; }
        public int getCacheHits() { return cacheHits; }
        public int getCacheMisses() { return cacheMisses; }
        public int getCacheSize() { return cacheSize; }
        public double getHitRate() { int total = cacheHits + cacheMisses; return total == 0 ? 0.0 : (double) cacheHits / total * 100.0; }
        public void reset() { cacheHits = 0; cacheMisses = 0; cacheSize = 0; }
    }
}