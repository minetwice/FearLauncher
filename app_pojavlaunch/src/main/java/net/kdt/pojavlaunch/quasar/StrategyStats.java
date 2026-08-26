package net.kdt.pojavlaunch.quasar;

public class StrategyStats {
    private int totalAttempts = 0;
    private int totalSuccesses = 0;
    private int totalFailures = 0;
    private long totalProcessingTimeNanos = 0;
    private long minProcessingTimeNanos = Long.MAX_VALUE;
    private long maxProcessingTimeNanos = 0;
    
    public void incrementAttempts() { totalAttempts++; }
    public void incrementSuccesses() { totalSuccesses++; }
    public void incrementFailures() { totalFailures++; }
    public void addProcessingTime(long nanos) {
        totalProcessingTimeNanos += nanos;
        if (nanos < minProcessingTimeNanos) minProcessingTimeNanos = nanos;
        if (nanos > maxProcessingTimeNanos) maxProcessingTimeNanos = nanos;
    }
    public int getTotalAttempts() { return totalAttempts; }
    public int getTotalSuccesses() { return totalSuccesses; }
    public int getTotalFailures() { return totalFailures; }
    public double getSuccessRate() { return totalAttempts == 0 ? 0.0 : (double) totalSuccesses / totalAttempts * 100.0; }
    public long getTotalProcessingTimeNanos() { return totalProcessingTimeNanos; }
    public double getAverageProcessingTimeNanos() { return totalAttempts == 0 ? 0.0 : (double) totalProcessingTimeNanos / totalAttempts; }
    public long getMinProcessingTimeNanos() { return minProcessingTimeNanos == Long.MAX_VALUE ? 0 : minProcessingTimeNanos; }
    public long getMaxProcessingTimeNanos() { return maxProcessingTimeNanos; }
    public void reset() { totalAttempts = 0; totalSuccesses = 0; totalFailures = 0; totalProcessingTimeNanos = 0; minProcessingTimeNanos = Long.MAX_VALUE; maxProcessingTimeNanos = 0; }
}