package net.kdt.pojavlaunch.modloaders.modpacks.api;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.kdt.mcgui.ProgressLayout;

import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.downloader.AcquireableTaskMetadata;
import net.kdt.pojavlaunch.downloader.Downloader;
import net.kdt.pojavlaunch.mirrors.DownloadMirror;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.CurseManifest;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.utils.FileUtils;
import net.kdt.pojavlaunch.utils.GsonJsonUtils;
import net.kdt.pojavlaunch.utils.ZipUtils;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

public class CurseforgeApi implements ModpackApi{
    private static final Pattern sMcVersionPattern = Pattern.compile("([0-9]+)\\.([0-9]+)\\.?([0-9]+)?");
    private static final int ALGO_SHA_1 = 1;
    // Stolen from
    // https://github.com/AnzhiZhang/CurseForgeModpackDownloader/blob/6cb3f428459f0cc8f444d16e54aea4cd1186fd7b/utils/requester.py#L93
    private static final int CURSEFORGE_MINECRAFT_GAME_ID = 432;
    private static final int CURSEFORGE_MODPACK_CLASS_ID = 4471;
    // https://api.curseforge.com/v1/categories?gameId=432 and search for "Mods" (case-sensitive)
    private static final int CURSEFORGE_MOD_CLASS_ID = 6;
    private static final int CURSEFORGE_SORT_RELEVANCY = 1;
    private static final int CURSEFORGE_PAGINATION_SIZE = 50;
    private static final int CURSEFORGE_PAGINATION_END_REACHED = -1;
    private static final int CURSEFORGE_PAGINATION_ERROR = -2;

    private final ApiHandler mApiHandler;
    public CurseforgeApi(String apiKey) {
        mApiHandler = new ApiHandler("https://api.curseforge.com/v1", apiKey);
    }

    @Override
    public SearchResult searchMod(SearchFilters searchFilters, SearchResult previousPageResult) {
        CurseforgeSearchResult curseforgeSearchResult = (CurseforgeSearchResult) previousPageResult;

        HashMap<String, Object> params = new HashMap<>();
        params.put("gameId", CURSEFORGE_MINECRAFT_GAME_ID);
        int classId = CURSEFORGE_MOD_CLASS_ID;
        if (searchFilters.isModpack) {
            classId = CURSEFORGE_MODPACK_CLASS_ID;
        } else if (searchFilters.isResourcePack) {
            classId = 12;
        } else if (searchFilters.isShaderPack) {
            classId = 6552;
        }
        params.put("classId", classId);
        params.put("searchFilter", searchFilters.name);
        params.put("sortField", CURSEFORGE_SORT_RELEVANCY);
        params.put("sortOrder", "desc");
        if(searchFilters.mcVersion != null && !searchFilters.mcVersion.isEmpty())
            params.put("gameVersion", searchFilters.mcVersion);
        if(previousPageResult != null)
            params.put("index", curseforgeSearchResult.previousOffset);

        JsonObject response = mApiHandler.get("mods/search", params, JsonObject.class);
        if(response == null) return null;
        JsonArray dataArray = response.getAsJsonArray("data");
        if(dataArray == null) return null;
        JsonObject paginationInfo = response.getAsJsonObject("pagination");
        ArrayList<ModItem> modItemList = new ArrayList<>(dataArray.size());
        for(int i = 0; i < dataArray.size(); i++) {
            JsonObject dataElement = dataArray.get(i).getAsJsonObject();
            JsonElement allowModDistribution = dataElement.get("allowModDistribution");
            // Gson automatically casts null to false, which leans to issues
            // So, only check the distribution flag if it is non-null
            if(!allowModDistribution.isJsonNull() && !allowModDistribution.getAsBoolean()) {
                Log.i("CurseforgeApi", "Skipping modpack "+dataElement.get("name").getAsString() + " because curseforge sucks");
                continue;
            }
            String itemType = "mod";
            if (searchFilters.isModpack) itemType = "modpack";
            else if (searchFilters.isResourcePack) itemType = "resourcepack";
            else if (searchFilters.isShaderPack) itemType = "shader";

            String thumbUrl = "";
            if (dataElement.has("logo") && !dataElement.get("logo").isJsonNull()) {
                JsonObject logoObj = dataElement.getAsJsonObject("logo");
                if (logoObj.has("thumbnailUrl") && !logoObj.get("thumbnailUrl").isJsonNull()) {
                    thumbUrl = logoObj.get("thumbnailUrl").getAsString();
                }
            }

            ModItem modItem = new ModItem(Constants.SOURCE_CURSEFORGE,
                    searchFilters.isModpack,
                    dataElement.get("id").getAsString(),
                    dataElement.get("name").getAsString(),
                    dataElement.get("summary").getAsString(),
                    thumbUrl);
            modItem.itemType = itemType;
            modItemList.add(modItem);
        }
        if(curseforgeSearchResult == null) curseforgeSearchResult = new CurseforgeSearchResult();
        curseforgeSearchResult.results = modItemList.toArray(new ModItem[0]);
        curseforgeSearchResult.totalResultCount = paginationInfo.get("totalCount").getAsInt();
        curseforgeSearchResult.previousOffset += dataArray.size();
        return curseforgeSearchResult;

    }

    @Override
    public ModDetail getModDetails(ModItem item) {
        String fullDesc = item.description;
        String previewUrl = "";

        try {
            JsonObject descObj = mApiHandler.get(String.format("mods/%s/description", item.id), JsonObject.class);
            if (descObj != null && descObj.has("data") && !descObj.get("data").isJsonNull()) {
                fullDesc = descObj.get("data").getAsString();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        try {
            JsonObject modObj = mApiHandler.get(String.format("mods/%s", item.id), JsonObject.class);
            if (modObj != null && modObj.has("data") && !modObj.get("data").isJsonNull()) {
                JsonObject data = modObj.getAsJsonObject("data");
                if (data.has("screenshots") && !data.get("screenshots").isJsonNull()) {
                    JsonArray screenshots = data.getAsJsonArray("screenshots");
                    if (screenshots != null && screenshots.size() > 0) {
                        JsonObject firstSc = screenshots.get(0).getAsJsonObject();
                        if (firstSc.has("url") && !firstSc.get("url").isJsonNull()) {
                            previewUrl = firstSc.get("url").getAsString();
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        ArrayList<JsonObject> allModDetails = new ArrayList<>();
        int index = 0;
        while(index != CURSEFORGE_PAGINATION_END_REACHED &&
                index != CURSEFORGE_PAGINATION_ERROR) {
            index = getPaginatedDetails(allModDetails, index, item.id);
        }
        if(index == CURSEFORGE_PAGINATION_ERROR) return null;
        int length = allModDetails.size();
        String[] versionNames = new String[length];
        String[] mcVersionNames = new String[length];
        String[] versionUrls = new String[length];
        String[] hashes = new String[length];
        String[] types = new String[length];

        for(int i = 0; i < allModDetails.size(); i++) {
            JsonObject modDetail = allModDetails.get(i);
            versionNames[i] = modDetail.get("displayName").getAsString();

            JsonElement downloadUrl = modDetail.get("downloadUrl");
            versionUrls[i] = downloadUrl.isJsonNull() ? "" : downloadUrl.getAsString();

            JsonArray gameVersions = modDetail.getAsJsonArray("gameVersions");
            StringBuilder mcVersionBuilder = new StringBuilder();
            for(JsonElement jsonElement : gameVersions) {
                String gameVersion = jsonElement.getAsString();
                if(!sMcVersionPattern.matcher(gameVersion).matches()) {
                    continue;
                }
                if (mcVersionBuilder.length() > 0) {
                    mcVersionBuilder.append(", ");
                }
                mcVersionBuilder.append(gameVersion);
            }
            mcVersionNames[i] = mcVersionBuilder.length() > 0 ? mcVersionBuilder.toString() : "any";

            String vType = "release";
            if (modDetail.has("releaseType") && !modDetail.get("releaseType").isJsonNull()) {
                int rType = modDetail.get("releaseType").getAsInt();
                if (rType == 2) {
                    vType = "beta";
                } else if (rType == 3) {
                    vType = "alpha";
                }
            }
            types[i] = vType;

            hashes[i] = getSha1FromModData(modDetail);
        }
        return new ModDetail(item, versionNames, mcVersionNames, versionUrls, hashes, fullDesc, previewUrl, types);
    }

    @Override
    public ModLoader installModpack(ModDetail modDetail, int selectedVersion) throws IOException{
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
        return ModpackInstaller.downloadModpack(modDetail, selectedVersion, this::installCurseforgeZip);
    }

    public ModLoader installLocalModpack(String modpackName, File modpackFile, String icon) throws IOException {
        return ModpackInstaller.installModpack(modpackName, modpackName, modpackFile, icon, this::installCurseforgeZip);
    }

    private int getPaginatedDetails(ArrayList<JsonObject> objectList, int index, String modId) {
        HashMap<String, Object> params = new HashMap<>();
        params.put("index", index);
        params.put("pageSize", CURSEFORGE_PAGINATION_SIZE);

        JsonObject response = mApiHandler.get("mods/"+modId+"/files", params, JsonObject.class);
        JsonArray data = GsonJsonUtils.getJsonArraySafe(response, "data");
        Log.i("CurseforgeApi", "data...");
        if(data == null) return CURSEFORGE_PAGINATION_ERROR;
        Log.i("CurseforgeApi", "filtering...");
        for(int i = 0; i < data.size(); i++) {
            JsonObject fileInfo = data.get(i).getAsJsonObject();
            if(fileInfo.get("isServerPack").getAsBoolean()) continue;
            objectList.add(fileInfo);
        }
        Log.i("CurseforgeApi", "pag_end");
        if(data.size() < CURSEFORGE_PAGINATION_SIZE) {
            return CURSEFORGE_PAGINATION_END_REACHED; // we read the remainder! yay!
        }
        return index + data.size();
    }

    private ModLoader installCurseforgeZip(File zipFile, File instanceDestination) throws IOException {
        try (ZipFile modpackZipFile = new ZipFile(zipFile)){
            CurseManifest curseManifest = Tools.GLOBAL_GSON.fromJson(
                    Tools.read(ZipUtils.getEntryStream(modpackZipFile, "manifest.json")),
                    CurseManifest.class);
            if(!verifyManifest(curseManifest)) {
                Log.i("CurseforgeApi","manifest verification failed");
                return null;
            }
            try {
                new CurseDownloader().start(curseManifest, instanceDestination);
            }catch (InterruptedException e) {
                throw new IOException("NIY: InterruptedException", e);
            }
            String overridesDir = "overrides";
            if(curseManifest.overrides != null) overridesDir = curseManifest.overrides;
            ZipUtils.zipExtract(modpackZipFile, overridesDir, instanceDestination);
            return createInfo(curseManifest.minecraft);
        }
    }

    private ModLoader createInfo(CurseManifest.CurseMinecraft minecraft) {
        CurseManifest.CurseModLoader primaryModLoader = null;
        for(CurseManifest.CurseModLoader modLoader : minecraft.modLoaders) {
            if(modLoader.primary) {
                primaryModLoader = modLoader;
                break;
            }
        }
        if(primaryModLoader == null) primaryModLoader = minecraft.modLoaders[0];
        String modLoaderId = primaryModLoader.id;
        int dashIndex = modLoaderId.indexOf('-');
        String modLoaderName = modLoaderId.substring(0, dashIndex);
        String modLoaderVersion = modLoaderId.substring(dashIndex+1);
        Log.i("CurseforgeApi", modLoaderId + " " + modLoaderName + " "+modLoaderVersion);
        int modLoaderTypeInt;
        switch (modLoaderName) {
            case "forge":
                modLoaderTypeInt = ModLoader.MOD_LOADER_FORGE;
                break;
            case "fabric":
                modLoaderTypeInt = ModLoader.MOD_LOADER_FABRIC;
                break;
            case "neoforge":
                modLoaderTypeInt = ModLoader.MOD_LOADER_NEOFORGE;
                break;
            default:
                return null;
            //TODO: Quilt is also Forge? How does that work?
        }
        return new ModLoader(modLoaderTypeInt, modLoaderVersion, minecraft.version);
    }

    private String getDownloadUrl(JsonObject fileMetadata) throws IOException {
        if(fileMetadata.get("modId").isJsonNull() || fileMetadata.get("id").isJsonNull()) throw new IOException("Bad metadata schema!");
        long projectID = fileMetadata.get("modId").getAsLong();
        long fileID = fileMetadata.get("id").getAsLong();

        // First try the official api endpoint
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID+"/download-url", JsonObject.class);
        if (response != null && !response.get("data").isJsonNull())
            return response.get("data").getAsString();

        // Otherwise, fallback to building an edge link
        return String.format("https://edge.forgecdn.net/files/%s/%s/%s", fileID/1000, fileID % 1000, fileMetadata.get("fileName").getAsString());
    }

    private void checkRequiredFileFields(JsonObject fileMetadata) throws IOException {
        if(fileMetadata == null || fileMetadata.isJsonNull()) throw new IOException("File metadata is null!");
        boolean hasProjectId = fileMetadata.has("modId");
        boolean hasFileId = fileMetadata.has("id");
        boolean hasLength = fileMetadata.has("fileLength");
        if(!hasProjectId || !hasFileId || !hasLength) {
            StringBuilder builder = new StringBuilder().append("File metadata is mising the following fields:");
            if(!hasProjectId) builder.append(" modId");
            if(!hasFileId) builder.append(" id");
            if(!hasLength) builder.append(" fileLength");
            throw new IOException(builder.toString());
        }
    }

    private @Nullable JsonObject getFile(long projectID, long fileID) {
        JsonObject response = mApiHandler.get("mods/"+projectID+"/files/"+fileID, JsonObject.class);
        return GsonJsonUtils.getJsonObjectSafe(response, "data");
    }

    private String getSha1FromModData(@NonNull JsonObject object) {
        JsonArray hashes = GsonJsonUtils.getJsonArraySafe(object, "hashes");
        if(hashes == null) return null;
        for (JsonElement jsonElement : hashes) {
            // The sha1 = 1; md5 = 2;
            JsonObject jsonObject = GsonJsonUtils.getJsonObjectSafe(jsonElement);
            if(GsonJsonUtils.getIntSafe(
                    jsonObject,
                    "algo",
                    -1) == ALGO_SHA_1) {
                return GsonJsonUtils.getStringSafe(jsonObject, "value");
            }
        }
        return null;
    }

    private boolean verifyManifest(CurseManifest manifest) {
        if(!"minecraftModpack".equals(manifest.manifestType)) return false;
        if(manifest.manifestVersion != 1) return false;
        if(manifest.minecraft == null) return false;
        if(manifest.minecraft.version == null) return false;
        if(manifest.minecraft.modLoaders == null) return false;
        return manifest.minecraft.modLoaders.length >= 1;
    }

    static class CurseforgeSearchResult extends SearchResult {
        int previousOffset;
    }

    class CurseDownloader extends Downloader {

        public CurseDownloader() {
            super(ProgressLayout.INSTALL_MODPACK);
        }

        public void start(CurseManifest curseManifest, File instanceDestination) throws IOException, InterruptedException {
            ArrayList<AcquireableTaskMetadata> taskMetadatas = new ArrayList<>(curseManifest.files.length);
            for(final CurseManifest.CurseFile file : curseManifest.files) {
                taskMetadatas.add(new CurseTaskMetadata(file, instanceDestination));
            }
            runDownloads(taskMetadatas);
        }
    }

    class CurseTaskMetadata extends AcquireableTaskMetadata {
        private final CurseManifest.CurseFile mFile;
        private final File mInstanceDestination;

        public CurseTaskMetadata(CurseManifest.CurseFile mFile, File mInstanceDestination) {
            super(DownloadMirror.DOWNLOAD_CLASS_METADATA);
            this.mFile = mFile;
            this.mInstanceDestination = mInstanceDestination;
        }

        @Override
        public void acquireMetadata() throws IOException {
            JsonObject fileMetadata = getFile(mFile.projectID, mFile.fileID);
            checkRequiredFileFields(fileMetadata);
            String url = getDownloadUrl(fileMetadata);
            this.url = new URL(url);
            this.path = new File(mInstanceDestination, "mods/"+ URLDecoder.decode(FileUtils.getFileName(url),"UTF-8"));
            FileUtils.ensureParentDirectorySilently(this.path);
            this.sha1Hash = getSha1FromModData(fileMetadata);
            this.size = fileMetadata.get("fileLength").getAsLong();
        }
    }
}
