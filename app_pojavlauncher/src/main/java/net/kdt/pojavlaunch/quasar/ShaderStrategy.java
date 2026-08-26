package net.kdt.pojavlaunch.quasar;

public interface ShaderStrategy {
    String getName();
    String getDescription();
    boolean canProcess(ShaderInfo shaderInfo);
    String process(String shaderSource, ShaderInfo shaderInfo);
    boolean isSuccessful();
    void setEnabled(boolean enabled);
    boolean isEnabled();
    StrategyStats getStats();
    void resetStats();
    default int getPriority() { return 0; }
}