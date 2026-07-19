package net.kdt.pojavlaunch.modloaders.modpacks.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.downloader.Downloader;
import net.kdt.pojavlaunch.downloader.TaskMetadata;
import net.kdt.pojavlaunch.mirrors.DownloadMirror;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModrinthIndex;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipFile;

public class ModrinthApi implements ModpackApi{
    private final ApiHandler mApiHandler;
    public ModrinthApi(){
        mApiHandler = new ApiHandler("https://api.modrinth.com/v2");
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        ModrinthSearchResult modrinthSearchResult = (ModrinthSearchResult) previousPageResult;

        // Fixes an issue where the offset being equal or greater than total_hits is ignored
        if (modrinthSearchResult != null && modrinthSearchResult.previousOffset >= modrinthSearchResult.totalResultCount) {
            ModrinthSearchResult emptyResult = new ModrinthSearchResult();
            emptyResult.results = new ModItem[0];
            emptyResult.totalResultCount = modrinthSearchResult.totalResultCount;
            emptyResult.previousOffset = modrinthSearchResult.previousOffset;
            return emptyResult;
        }


        // Build the facets filters
        HashMap<String, Object> params = new HashMap<>();
        StringBuilder facetString = new StringBuilder();
        facetString.append("[");

        String projType = "modpack";
        if (searchFilters.isMod) {
            projType = "mod";
        } else if (searchFilters.isResourcePack) {
            projType = "resourcepack";
        } else if (searchFilters.isShaderPack) {
            projType = "shader";
        } else if (searchFilters.isModpack) {
            projType = "modpack";
        } else {
            projType = "mod"; // Fallback default
        }

        facetString.append(String.format("[\"project_type:%s\"]", projType));
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            facetString.append(String.format(",[\"versions:%s\"]", searchFilters.mcVersion));
        facetString.append("]");
        params.put("facets", facetString.toString());
        params.put("query", searchFilters.name);
        params.put("limit", 50);
        params.put("index", "relevance");
        if(modrinthSearchResult != null)
            params.put("offset", modrinthSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray responseHits = response.getAsJsonArray("hits");
        if(responseHits == null) return null;

        ModItem[] items = new ModItem[responseHits.size()];
        for(int i=0; i<responseHits.size(); ++i){
            JsonObject hit = responseHits.get(i).getAsJsonObject();
            String pType = hit.has("project_type") && !hit.get("project_type").isJsonNull() ? hit.get("project_type").getAsString() : "mod";
            items[i] = new ModItem(
                    Constants.SOURCE_MODRINTH,
                    pType.equals("modpack"),
                    hit.get("project_id").getAsString(),
                    hit.get("title").getAsString(),
                    hit.get("description").getAsString(),
                    hit.has("icon_url") && !hit.get("icon_url").isJsonNull() ? hit.get("icon_url").getAsString() : ""
            );
            items[i].itemType = pType;
        }
        if(modrinthSearchResult == null) modrinthSearchResult = new ModrinthSearchResult();
        modrinthSearchResult.previousOffset += responseHits.size();
        modrinthSearchResult.results = items;
        modrinthSearchResult.totalResultCount = response.get("total_hits").getAsInt();
        return modrinthSearchResult;
    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        String fullDesc = item.description;
        String previewUrl = "";

        try {
            JsonObject projectDetails = mApiHandler.get(String.format("project/%s", item.id), JsonObject.class);
            if (projectDetails != null) {
                if (projectDetails.has("body") && !projectDetails.get("body").isJsonNull()) {
                    fullDesc = projectDetails.get("body").getAsString();
                }
                if (projectDetails.has("gallery") && !projectDetails.get("gallery").isJsonNull()) {
                    JsonArray gallery = projectDetails.getAsJsonArray("gallery");
                    if (gallery != null && gallery.size() > 0) {
                        JsonObject firstImg = gallery.get(0).getAsJsonObject();
                        if (firstImg.has("url") && !firstImg.get("url").isJsonNull()) {
                            previewUrl = firstImg.get("url").getAsString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        JsonArray response = mApiHandler.get(String.format("project/%s/version", item.id), JsonArray.class);
        if(response == null) return null;
        System.out.println(response);
        String[] names = new String[response.size()];
        String[] mcNames = new String[response.size()];
        String[] urls = new String[response.size()];
        String[] hashes = new String[response.size()];
        String[] types = new String[response.size()];

        for (int i=0; i<response.size(); ++i) {
            JsonObject version = response.get(i).getAsJsonObject();
            names[i] = version.get("name").getAsString();

            String mcNameStr = "any";
            if (version.has("game_versions") && !version.get("game_versions").isJsonNull()) {
                JsonArray gv = version.getAsJsonArray("game_versions");
                StringBuilder mcVersionBuilder = new StringBuilder();
                for (int g = 0; g < gv.size(); g++) {
                    if (mcVersionBuilder.length() > 0) {
                        mcVersionBuilder.append(", ");
                    }
                    mcVersionBuilder.append(gv.get(g).getAsString());
                }
                if (mcVersionBuilder.length() > 0) {
                    mcNameStr = mcVersionBuilder.toString();
                }
            }
            mcNames[i] = mcNameStr;

            urls[i] = version.get("files").getAsJsonArray().get(0).getAsJsonObject().get("url").getAsString();

            String vType = "release";
            if (version.has("version_type") && !version.get("version_type").isJsonNull()) {
                vType = version.get("version_type").getAsString();
            }
            types[i] = vType;

            // Assume there may not be hashes, in case the API changes
            JsonObject hashesMap = version.getAsJsonArray("files").get(0).getAsJsonObject()
                    .get("hashes").getAsJsonObject();
            if(hashesMap == null || hashesMap.get("sha1") == null){
                hashes[i] = null;
                continue;
            }

            hashes[i] = hashesMap.get("sha1").getAsString();
        }

        return new ModDetail(item, names, mcNames, urls, hashes, fullDesc, previewUrl, types);
    }

    @Override
    public ModLoader installModpack(ModDetail modDetail, int selectedVersion) throws IOException{
        // Check if the item being installed is a standard Mod, Resource Pack, or Shader Pack
        if (modDetail != null && !modDetail.isModpack) {
            String projType = "mods";
            if ("shader".equals(modDetail.itemType)) {
                projType = "shaderpacks";
            } else if ("resourcepack".equals(modDetail.itemType)) {
                projType = "resourcepacks";
            } else if ("mod".equals(modDetail.itemType)) {
                projType = "mods";
            } else {
                if (modDetail.imageUrl != null && modDetail.imageUrl.contains("shader")) {
                    projType = "shaderpacks";
                } else if (modDetail.imageUrl != null && modDetail.imageUrl.contains("resourcepack")) {
                    projType = "resourcepacks";
                } else {
                    String title = modDetail.title.toLowerCase();
                    if (title.contains("shader") || title.contains("complementary") || title.contains("solas")) {
                        projType = "shaderpacks";
                    } else if (title.contains("resource") || title.contains("pack") || title.contains("textures")) {
                        projType = "resourcepacks";
                    }
                }
            }

            net.kdt.pojavlaunch.instances.Instance currentInstance = net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
            if (currentInstance == null) {
                throw new IOException("No Minecraft Instance currently selected to download items into!");
            }

            File destFolder = new File(currentInstance.getGameDirectory(), projType);
            if (!destFolder.exists()) {
                destFolder.mkdirs();
            }

            String versionUrl = modDetail.versionUrls[selectedVersion];
            String file_name = versionUrl.substring(versionUrl.lastIndexOf('/') + 1);
            if (file_name.contains("?")) {
                file_name = file_name.substring(0, file_name.indexOf('?'));
            }
            File destFile = new File(destFolder, file_name);

            byte[] buffer = new byte[8192];
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, "DOWNLOADING TO " + projType.toUpperCase() + "...");
            net.kdt.pojavlaunch.utils.DownloadUtils.downloadFileMonitored(versionUrl, destFile, buffer,
                    new net.kdt.pojavlaunch.progresskeeper.DownloaderProgressWrapper(R.string.modpack_download_downloading_metadata, ProgressLayout.INSTALL_MODPACK)
            );

            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 100, "INSTALLATION COMPLETE!");
            try { Thread.sleep(800); } catch (Exception e) {}
            ProgressLayout.clearProgress(ProgressLayout.INSTALL_MODPACK);
            return new ModLoader(ModLoader.MOD_LOADER_FABRIC, "0.15.11", "1.21.1") {
                @Override
                public boolean requiresGuiInstallation() { return false; }
                @Override
                public String installHeadlessly() { return "1.21.1-fabric-0.15.11"; }
            };
        }

        // Default: download and install full modpack
        return ModpackInstaller.downloadModpack(modDetail, selectedVersion, this::installMrpack);
    }

    public ModLoader installLocalModpack(String modpackName, File modpackFile, String icon) throws IOException {
        return ModpackInstaller.installModpack(modpackName, modpackName, modpackFile, icon, this::installMrpack);
    }

    private static ModLoader createInfo(ModrinthIndex modrinthIndex) {
        if(modrinthIndex == null) return null;
        Map<String, String> dependencies = modrinthIndex.dependencies;
        String mcVersion = dependencies.get("minecraft");
        if(mcVersion == null) return null;
        String modLoaderVersion;
        if((modLoaderVersion = dependencies.get("forge")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FORGE, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("fabric-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_FABRIC, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("quilt-loader")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_QUILT, modLoaderVersion, mcVersion);
        }
        if((modLoaderVersion = dependencies.get("neoforge")) != null) {
            return new ModLoader(ModLoader.MOD_LOADER_NEOFORGE, modLoaderVersion, mcVersion);
        }

        return null;
    }

    private ModLoader installMrpack(File mrpackFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(mrpackFile)){
            ModrinthIndex modrinthIndex = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "modrinth.index.json")),
                    ModrinthIndex.class);
            try {
                new ModrinthDownloader().startDownloads(modrinthIndex.files, instanceDestination);
            }catch (InterruptedException e) {
                throw new IOException("NIY: InterruptedException", e);
            }
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 0, R.string.modpack_download_applying_overrides, 1, 2);
            ZipUtils.zipExtract(modpackZipFile, "overrides/", instanceDestination);
            ProgressLayout.setProgress(ProgressLayout.INSTALL_MODPACK, 50, R.string.modpack_download_applying_overrides, 2, 2);
            ZipUtils.zipExtract(modpackZipFile, "client-overrides/", instanceDestination);
            return createInfo(modrinthIndex);
        }
    }

    class ModrinthSearchResult extends SearchResult {
        int previousOffset;
    }

    static class ModrinthDownloader extends Downloader {
        public ModrinthDownloader() {
            super(ProgressLayout.INSTALL_MODPACK);
        }

        protected void startDownloads(ModrinthIndex.ModrinthIndexFile[] indexFiles, File instanceDestination) throws IOException, InterruptedException {
            String absoluteInstancePath = instanceDestination.getAbsolutePath();
            ArrayList<TaskMetadata> taskMetadatas = new ArrayList<>(indexFiles.length);
            for(ModrinthIndex.ModrinthIndexFile file : indexFiles) {
                File targetPath = new File(instanceDestination, file.path);
                if(!targetPath.getAbsolutePath().startsWith(absoluteInstancePath)) throw new IOException("Bad path!");
                FileUtils.ensureParentDirectory(targetPath);
                taskMetadatas.add(new TaskMetadata(
                        targetPath, new URL(file.downloads[0]), // TODO source selection
                        file.fileSize, file.hashes.sha1,
                        DownloadMirror.DOWNLOAD_CLASS_NONE
                ));
            }
            runDownloads(taskMetadatas);
        }
    }
}
