package net.kdt.pojavlaunch.modloaders.modpacks.models;

import org.jetbrains.annotations.Nullable;

/**
 * Search filters, passed to APIs
 */
public class SearchFilters {
    public boolean isModpack;
    public boolean isMod;
    public boolean isResourcePack;
    public boolean isShaderPack;
    public String name;
    @Nullable public String mcVersion;

}
