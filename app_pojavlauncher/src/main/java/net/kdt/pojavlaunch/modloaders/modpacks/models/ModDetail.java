package net.kdt.pojavlaunch.modloaders.modpacks.models;


import androidx.annotation.NonNull;

import java.util.Arrays;

public class ModDetail extends ModItem {
    /* A cheap way to map from the front facing name to the underlying id */
    public String[] versionNames;
    public String [] mcVersionNames;
    public String[] versionUrls;
    /* SHA 1 hashes, null if a hash is unavailable */
    public String[] versionHashes;

    public String fullDescription;
    public String previewImageUrl;
    public String[] versionTypes; // "release", "beta", "alpha"

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionUrls, String[] hashes) {
        super(item.apiSource, item.isModpack, item.id, item.title, item.description, item.imageUrl);
        this.itemType = item.itemType;
        this.versionNames = versionNames;
        this.mcVersionNames = mcVersionNames;
        this.versionUrls = versionUrls;
        this.versionHashes = hashes;
        this.fullDescription = item.description;
        this.previewImageUrl = "";
        this.versionTypes = new String[versionNames.length];
        java.util.Arrays.fill(this.versionTypes, "release");

        // Add the mc version to the version model
        for (int i=0; i<versionNames.length; i++){
            if (!versionNames[i].contains(mcVersionNames[i]))
                versionNames[i] += " - " + mcVersionNames[i];
        }
    }

    public ModDetail(ModItem item, String[] versionNames, String[] mcVersionNames, String[] versionUrls, String[] hashes, String fullDescription, String previewImageUrl, String[] versionTypes) {
        super(item.apiSource, item.isModpack, item.id, item.title, item.description, item.imageUrl);
        this.itemType = item.itemType;
        this.versionNames = versionNames;
        this.mcVersionNames = mcVersionNames;
        this.versionUrls = versionUrls;
        this.versionHashes = hashes;
        this.fullDescription = fullDescription != null ? fullDescription : item.description;
        this.previewImageUrl = previewImageUrl != null ? previewImageUrl : "";
        this.versionTypes = versionTypes != null ? versionTypes : new String[versionNames.length];
        if (versionTypes == null) {
            java.util.Arrays.fill(this.versionTypes, "release");
        }

        // Add the mc version to the version model
        for (int i=0; i<versionNames.length; i++){
            if (versionNames[i] != null && mcVersionNames[i] != null && !versionNames[i].contains(mcVersionNames[i]))
                versionNames[i] += " - " + mcVersionNames[i];
        }
    }

    @NonNull
    @Override
    public String toString() {
        return "ModDetail{" +
                "versionNames=" + Arrays.toString(versionNames) +
                ", mcVersionNames=" + Arrays.toString(mcVersionNames) +
                ", versionIds=" + Arrays.toString(versionUrls) +
                ", id='" + id + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", apiSource=" + apiSource +
                ", isModpack=" + isModpack +
                '}';
    }
}
