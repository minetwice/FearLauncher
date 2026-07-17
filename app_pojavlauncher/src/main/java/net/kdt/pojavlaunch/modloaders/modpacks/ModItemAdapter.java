package net.kdt.pojavlaunch.modloaders.modpacks;

import android.annotation.SuppressLint;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewStub;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.recyclerview.widget.RecyclerView;

import com.kdt.SimpleArrayAdapter;

import net.kdt.pojavlaunch.PojavApplication;
import git.artdeell.mojo.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.modloaders.modpacks.api.ModpackApi;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ImageReceiver;
import net.kdt.pojavlaunch.modloaders.modpacks.imagecache.ModIconCache;
import net.kdt.pojavlaunch.modloaders.modpacks.models.Constants;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModDetail;
import net.kdt.pojavlaunch.modloaders.modpacks.models.ModItem;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchFilters;
import net.kdt.pojavlaunch.modloaders.modpacks.models.SearchResult;
import net.kdt.pojavlaunch.progresskeeper.TaskCountListener;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.Future;

public class ModItemAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> implements TaskCountListener {
    private static final ModItem[] MOD_ITEMS_EMPTY = new ModItem[0];
    private static final int VIEW_TYPE_MOD_ITEM = 0;
    private static final int VIEW_TYPE_LOADING = 1;

    /* Used when versions haven't loaded yet, default text to reduce layout shifting */
    private final SimpleArrayAdapter<String> mLoadingAdapter = new SimpleArrayAdapter<>(Collections.singletonList("Loading"));
    /* This my seem horribly inefficient but it is in fact the most efficient way without effectively writing a weak collection from scratch */
    private final Set<ViewHolder> mViewHolderSet = Collections.newSetFromMap(new WeakHashMap<>());
    private final ModIconCache mIconCache = new ModIconCache();
    private final SearchResultCallback mSearchResultCallback;
    private ModItem[] mModItems;
    private final ModpackApi mModpackApi;

    /* Cache for ever so slightly rounding the image for the corner not to stick out of the layout */
    private final float mCornerDimensionCache;

    private Future<?> mTaskInProgress;
    private SearchFilters mSearchFilters;
    private SearchResult mCurrentResult;
    private boolean mLastPage;
    private boolean mTasksRunning;


    public ModItemAdapter(Resources resources, ModpackApi api, SearchResultCallback callback) {
        mCornerDimensionCache = resources.getDimension(R.dimen._1sdp) / 250;
        mModpackApi = api;
        mModItems = new ModItem[]{};
        mSearchResultCallback = callback;
    }

    public void performSearchQuery(SearchFilters searchFilters) {
        if(mTaskInProgress != null) {
            mTaskInProgress.cancel(true);
            mTaskInProgress = null;
        }
        this.mSearchFilters = searchFilters;
        this.mLastPage = false;
        mTaskInProgress = new SelfReferencingFuture(new SearchApiTask(mSearchFilters, null))
                .startOnExecutor(PojavApplication.sExecutorService);
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(viewGroup.getContext());
        View view;
        switch (viewType) {
            case VIEW_TYPE_MOD_ITEM:
                // Create a new view, which defines the UI of the list item
                view = layoutInflater.inflate(R.layout.view_mod, viewGroup, false);
                return new ViewHolder(view);
            case VIEW_TYPE_LOADING:
                // Create a new view, which is actually just the progress bar
                view = layoutInflater.inflate(R.layout.view_loading, viewGroup, false);
                return new LoadingViewHolder(view);
            default:
                throw new RuntimeException("Unimplemented view type!");
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        switch (getItemViewType(position)) {
            case VIEW_TYPE_MOD_ITEM:
                ((ModItemAdapter.ViewHolder)holder).setStateLimited(mModItems[position]);
                break;
            case VIEW_TYPE_LOADING:
                loadMoreResults();
                break;
            default:
                throw new RuntimeException("Unimplemented view type!");
        }
    }

    @Override
    public int getItemCount() {
        if(mLastPage || mModItems.length == 0) return mModItems.length;
        return mModItems.length+1;
    }

    private void loadMoreResults() {
        if(mTaskInProgress != null) return;
        mTaskInProgress = new SelfReferencingFuture(new SearchApiTask(mSearchFilters, mCurrentResult))
                .startOnExecutor(PojavApplication.sExecutorService);
    }

    @Override
    public int getItemViewType(int position) {
        if(position < mModItems.length) return VIEW_TYPE_MOD_ITEM;
        return VIEW_TYPE_LOADING;
    }

    @Override
    public boolean onUpdateTaskCount(int taskCount) {
        Tools.runOnUiThread(()->{
            mTasksRunning = taskCount != 0;
            for(ViewHolder viewHolder : mViewHolderSet) {
                viewHolder.updateInstallButtonState();
            }
        });
        return false;
    }


    /**
     * Basic viewholder with expension capabilities
     */
    public class ViewHolder extends RecyclerView.ViewHolder {

        private ModDetail mModDetail = null;
        private ModItem mModItem = null;
        private final TextView mTitle, mDescription;
        private final ImageView mIconView, mSourceView;
        private View mExtendedLayout;
        private Spinner mExtendedSpinner;
        private Button mExtendedButton;
        private TextView mExtendedErrorTextView;
        private Future<?> mExtensionFuture;
        private Bitmap mThumbnailBitmap;
        private ImageReceiver mImageReceiver;
        private boolean mInstallEnabled;

        /* Used to display available versions of the mod(pack) */
        private final SimpleArrayAdapter<String> mVersionAdapter = new SimpleArrayAdapter<>(null);

        public ViewHolder(View view) {
            super(view);
            mViewHolderSet.add(this);
            view.setOnClickListener(v -> {
                android.content.Context context = v.getContext();
                android.app.Dialog dialog = new android.app.Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen);
                dialog.setContentView(R.layout.dialog_mod_detail_fullscreen);

                View closeBtn = dialog.findViewById(R.id.detail_close_btn);
                closeBtn.setOnClickListener(v2 -> dialog.dismiss());

                ImageView detailIcon = dialog.findViewById(R.id.detail_icon);
                if (mThumbnailBitmap != null) {
                    detailIcon.setImageBitmap(mThumbnailBitmap);
                }

                TextView detailTitle = dialog.findViewById(R.id.detail_title);
                detailTitle.setText(mModItem.title);

                ImageView sourceIcon = dialog.findViewById(R.id.detail_source_icon);
                TextView sourceText = dialog.findViewById(R.id.detail_source_text);
                sourceIcon.setImageResource(getSourceDrawable(mModItem.apiSource));
                sourceText.setText(mModItem.apiSource == Constants.SOURCE_MODRINTH ? "MODRINTH ENGINE" : "CURSEFORGE DATA STREAM");

                TextView detailDesc = dialog.findViewById(R.id.detail_desc);
                detailDesc.setText(mModItem.description);

                androidx.appcompat.widget.AppCompatSpinner detailSpinner = dialog.findViewById(R.id.detail_version_spinner);
                androidx.appcompat.widget.AppCompatSpinner loaderSpinner = dialog.findViewById(R.id.detail_modloader_spinner);
                TextView errorText = dialog.findViewById(R.id.detail_error_text);
                Button installBtn = dialog.findViewById(R.id.detail_install_btn);

                // Version type filters buttons (ALL, RELEASE, BETA, SNAPSHOT)
                Button btnAll = dialog.findViewById(R.id.filter_type_all);
                Button btnRelease = dialog.findViewById(R.id.filter_type_release);
                Button btnBeta = dialog.findViewById(R.id.filter_type_beta);
                Button btnAlpha = dialog.findViewById(R.id.filter_type_alpha);

                boolean isShader = false;
                if (mModItem.itemType != null && mModItem.itemType.equals("shader")) {
                    isShader = true;
                } else if (mModItem.title != null && (mModItem.title.toLowerCase().contains("shader") || mModItem.title.toLowerCase().contains("complementary") || mModItem.title.toLowerCase().contains("solas"))) {
                    isShader = true;
                }
                final boolean finalIsShader = isShader;

                installBtn.setEnabled(false);
                detailSpinner.setAdapter(mLoadingAdapter);

                dialog.show();

                PojavApplication.sExecutorService.execute(() -> {
                    try {
                        mModDetail = mModpackApi.getModDetails(mModItem);
                        if (mModDetail != null) {
                            // Fetch full preview image if available
                            if (mModDetail.previewImageUrl != null && !mModDetail.previewImageUrl.isEmpty()) {
                                PojavApplication.sExecutorService.execute(() -> {
                                    try {
                                        java.io.InputStream in = new java.net.URL(mModDetail.previewImageUrl).openStream();
                                        final android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(in);
                                        if (bmp != null) {
                                            Tools.runOnUiThread(() -> {
                                                androidx.cardview.widget.CardView previewCard = dialog.findViewById(R.id.detail_preview_card);
                                                ImageView previewImage = dialog.findViewById(R.id.detail_preview_image);
                                                if (previewCard != null && previewImage != null) {
                                                    previewCard.setVisibility(View.VISIBLE);
                                                    previewImage.setImageBitmap(bmp);
                                                }
                                            });
                                        }
                                    } catch (Exception imgEx) {
                                        imgEx.printStackTrace();
                                    }
                                });
                            }

                            // Dynamic supported loaders list builder scan
                            final java.util.ArrayList<String> supportedLoaders = new java.util.ArrayList<>();
                            supportedLoaders.add("All");
                            for (String vName : mModDetail.versionNames) {
                                if (vName == null) continue;
                                String nameL = vName.toLowerCase();
                                if (nameL.contains("fabric") && !supportedLoaders.contains("Fabric")) supportedLoaders.add("Fabric");
                                if (nameL.contains("forge") && !nameL.contains("neoforge") && !supportedLoaders.contains("Forge")) supportedLoaders.add("Forge");
                                if ((nameL.contains("neoforge") || nameL.contains("neo")) && !supportedLoaders.contains("NeoForge")) supportedLoaders.add("NeoForge");
                                if (nameL.contains("quilt") && !supportedLoaders.contains("Quilt")) supportedLoaders.add("Quilt");
                                if (nameL.contains("optifine") && !supportedLoaders.contains("OptiFine")) supportedLoaders.add("OptiFine");
                                if (nameL.contains("iris") && !supportedLoaders.contains("Iris")) supportedLoaders.add("Iris");
                            }

                            // Fallback loaders list if none scanned
                            if (supportedLoaders.size() == 1) {
                                if (finalIsShader) {
                                    supportedLoaders.add("OptiFine");
                                    supportedLoaders.add("Iris");
                                } else {
                                    supportedLoaders.add("Fabric");
                                    supportedLoaders.add("Forge");
                                    supportedLoaders.add("NeoForge");
                                    supportedLoaders.add("Quilt");
                                }
                            }

                            final String[] finalLoadersArray = supportedLoaders.toArray(new String[0]);
                            Tools.runOnUiThread(() -> {
                                com.kdt.SimpleArrayAdapter<String> loaderAdapter = new com.kdt.SimpleArrayAdapter<>(java.util.Arrays.asList(finalLoadersArray));
                                loaderSpinner.setAdapter(loaderAdapter);
                            });

                            // Clean description string to ensure proper line format and spacing
                            String richText = mModDetail.fullDescription;
                            if (richText != null) {
                                richText = richText.replaceAll("(?m)^###\\s+(.*)$", "<b>$1</b>");
                                richText = richText.replaceAll("(?m)^##\\s+(.*)$", "<b>$1</b>");
                                richText = richText.replaceAll("(?m)^#\\s+(.*)$", "<b><font size=\"5\">$1</font></b>");
                                richText = richText.replaceAll("(?m)^\\s*-\\s+(.*)$", "• $1<br/>");
                                richText = richText.replaceAll("(?m)^\\s*\\*\\s+(.*)$", "• $1<br/>");
                                richText = richText.replaceAll("\r\n", "\n");
                                richText = richText.replaceAll("\n\n", "<p></p>");
                                richText = richText.replaceAll("\n", "<br/>");
                            }
                            final String finalRichText = richText;

                            // Dynamic combined filtering, pinning and recommendation helper
                            final class VersionFilterHelper {
                                private String chosenLoader = "All";
                                private String currentTypeFilter = "all";

                                void applyFilterAndPopulate() {
                                    String instanceMcVersion = "";
                                    net.kdt.pojavlaunch.instances.Instance currentInstance = net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
                                    if (currentInstance != null && currentInstance.versionId != null) {
                                        instanceMcVersion = currentInstance.versionId;
                                        int dashIdx = instanceMcVersion.indexOf('-');
                                        if (dashIdx > 0) {
                                            instanceMcVersion = instanceMcVersion.substring(0, dashIdx);
                                        }
                                    }

                                    // Classify into recommended indices and other indices
                                    java.util.ArrayList<Integer> recommendedIndices = new java.util.ArrayList<>();
                                    java.util.ArrayList<Integer> otherIndices = new java.util.ArrayList<>();

                                    for (int i = 0; i < mModDetail.versionNames.length; i++) {
                                        String vName = mModDetail.versionNames[i] != null ? mModDetail.versionNames[i].toLowerCase() : "";
                                        String vUrl = mModDetail.versionUrls[i] != null ? mModDetail.versionUrls[i].toLowerCase() : "";

                                        // 1. Apply release type filter
                                        String vType = mModDetail.versionTypes[i];
                                        if (vType == null) vType = "release";
                                        if (!currentTypeFilter.equals("all") && !vType.equalsIgnoreCase(currentTypeFilter)) {
                                            continue;
                                        }

                                        // 2. Apply loader filter matching
                                        if (!chosenLoader.equals("All")) {
                                            String target = chosenLoader.toLowerCase();
                                            // Handle special case: do not mix up forge and neoforge
                                            if (target.equals("forge") && vName.contains("neoforge")) {
                                                continue;
                                            }
                                            if (!vName.contains(target) && !vUrl.contains(target)) {
                                                continue;
                                            }
                                        }

                                        String mcVer = mModDetail.mcVersionNames[i];
                                        if (mcVer != null && !instanceMcVersion.isEmpty() && (mcVer.equals(instanceMcVersion) || mcVer.contains(instanceMcVersion) || instanceMcVersion.contains(mcVer))) {
                                            recommendedIndices.add(i);
                                        } else {
                                            otherIndices.add(i);
                                        }
                                    }

                                    // Combine: recommendedIndices go absolute top (Pinned!)
                                    final java.util.ArrayList<Integer> combinedIndices = new java.util.ArrayList<>();
                                    combinedIndices.addAll(recommendedIndices);
                                    combinedIndices.addAll(otherIndices);

                                    final String[] dispNames = new String[combinedIndices.size()];
                                    for (int j = 0; j < combinedIndices.size(); j++) {
                                        int originalIdx = combinedIndices.get(j);
                                        if (j < recommendedIndices.size()) {
                                            dispNames[j] = "⭐ [RECOMMENDED] " + mModDetail.versionNames[originalIdx];
                                        } else {
                                            dispNames[j] = "  " + mModDetail.versionNames[originalIdx];
                                        }
                                    }

                                    Tools.runOnUiThread(() -> {
                                        if (dispNames.length == 0) {
                                            com.kdt.SimpleArrayAdapter<String> emptyAdapter = new com.kdt.SimpleArrayAdapter<>(java.util.Collections.singletonList("No versions match filter criteria"));
                                            detailSpinner.setAdapter(emptyAdapter);
                                            installBtn.setEnabled(false);
                                        } else {
                                            com.kdt.SimpleArrayAdapter<String> adapter = new com.kdt.SimpleArrayAdapter<>(java.util.Arrays.asList(dispNames));
                                            detailSpinner.setAdapter(adapter);
                                            detailSpinner.setSelection(0); // auto pre-select matching recommended
                                            installBtn.setEnabled(true);
                                            installBtn.setOnClickListener(v3 -> {
                                                int spinnerPos = detailSpinner.getSelectedItemPosition();
                                                if (spinnerPos >= 0 && spinnerPos < combinedIndices.size()) {
                                                    int finalOrigIdx = combinedIndices.get(spinnerPos);
                                                    dialog.dismiss();
                                                    mModpackApi.handleModpackInstallation(
                                                        context.getApplicationContext(),
                                                        mModDetail,
                                                        finalOrigIdx
                                                    );
                                                }
                                            });
                                        }
                                    });
                                }

                                void updateTabStyles(String activeFilter) {
                                    currentTypeFilter = activeFilter;
                                    Tools.runOnUiThread(() -> {
                                        btnAll.setBackgroundResource(activeFilter.equals("all") ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
                                        btnAll.setTextColor(activeFilter.equals("all") ? 0xFF000000 : 0xFFFFFFFF);

                                        btnRelease.setBackgroundResource(activeFilter.equals("release") ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
                                        btnRelease.setTextColor(activeFilter.equals("release") ? 0xFF000000 : 0xFFFFFFFF);

                                        btnBeta.setBackgroundResource(activeFilter.equals("beta") ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
                                        btnBeta.setTextColor(activeFilter.equals("beta") ? 0xFF000000 : 0xFFFFFFFF);

                                        btnAlpha.setBackgroundResource(activeFilter.equals("alpha") ? R.drawable.premium_button_bg : R.drawable.premium_glass_black_bg);
                                        btnAlpha.setTextColor(activeFilter.equals("alpha") ? 0xFF000000 : 0xFFFFFFFF);
                                    });
                                    applyFilterAndPopulate();
                                }
                            }

                            final VersionFilterHelper filterHelper = new VersionFilterHelper();

                            Tools.runOnUiThread(() -> {
                                // Set clean rich HTML text to the description with custom UrlImageGetter inline images rendering!
                                if (finalRichText != null && !finalRichText.isEmpty()) {
                                    UrlImageGetter imgGetter = new UrlImageGetter(detailDesc);
                                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                                        detailDesc.setText(android.text.Html.fromHtml(finalRichText, android.text.Html.FROM_HTML_MODE_COMPACT, imgGetter, null));
                                    } else {
                                        detailDesc.setText(android.text.Html.fromHtml(finalRichText, imgGetter, null));
                                    }
                                }

                                // Auto select default matching loader based on current selected instance
                                int preSelectLoaderIndex = 0;
                                String currentInstanceLoader = "";
                                net.kdt.pojavlaunch.instances.Instance currentInstance = net.kdt.pojavlaunch.instances.Instances.loadSelectedInstance();
                                if (currentInstance != null && currentInstance.versionId != null) {
                                    String idL = currentInstance.versionId.toLowerCase();
                                    if (idL.contains("fabric")) currentInstanceLoader = "Fabric";
                                    else if (idL.contains("neoforge")) currentInstanceLoader = "NeoForge";
                                    else if (idL.contains("forge")) currentInstanceLoader = "Forge";
                                    else if (idL.contains("quilt")) currentInstanceLoader = "Quilt";
                                }
                                for (int k = 0; k < finalLoadersArray.length; k++) {
                                    if (!currentInstanceLoader.isEmpty() && finalLoadersArray[k].equalsIgnoreCase(currentInstanceLoader)) {
                                        preSelectLoaderIndex = k;
                                        break;
                                    }
                                }

                                // Wire up loader spinner selection listener
                                loaderSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                                    @Override
                                    public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                                        String sel = finalLoadersArray[position];
                                        if (sel.startsWith("All")) {
                                            filterHelper.chosenLoader = "All";
                                        } else {
                                            filterHelper.chosenLoader = sel;
                                        }
                                        filterHelper.applyFilterAndPopulate();
                                    }

                                    @Override
                                    public void onNothingSelected(android.widget.AdapterView<?> parent) {}
                                });

                                loaderSpinner.setSelection(preSelectLoaderIndex);

                                // Wire up release type filter buttons
                                btnAll.setOnClickListener(vTab -> filterHelper.updateTabStyles("all"));
                                btnRelease.setOnClickListener(vTab -> filterHelper.updateTabStyles("release"));
                                btnBeta.setOnClickListener(vTab -> filterHelper.updateTabStyles("beta"));
                                btnAlpha.setOnClickListener(vTab -> filterHelper.updateTabStyles("alpha"));
                            });

                            // Default populate
                            filterHelper.updateTabStyles("all");

                        } else {
                            Tools.runOnUiThread(() -> {
                                errorText.setVisibility(View.VISIBLE);
                                errorText.setText("FAILED TO RESOLVE METADATA CHANNELS.");
                            });
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                        Tools.runOnUiThread(() -> {
                            errorText.setVisibility(View.VISIBLE);
                            errorText.setText("METADATA NETWORK ERROR: " + e.getMessage());
                        });
                    }
                });
            });

            // Define click listener for the ViewHolder's View
            mTitle = view.findViewById(R.id.mod_title_textview);
            mDescription = view.findViewById(R.id.mod_body_textview);
            mIconView = view.findViewById(R.id.mod_thumbnail_imageview);
            mSourceView = view.findViewById(R.id.mod_source_imageview);
        }

        /** Display basic info about the moditem */
        public void setStateLimited(ModItem item) {
            mModDetail = null;
            if(mThumbnailBitmap != null) {
                mIconView.setImageBitmap(null);
                mThumbnailBitmap.recycle();
            }
            if(mImageReceiver != null) {
                mIconCache.cancelImage(mImageReceiver);
            }
            if(mExtensionFuture != null) {
                /*
                 * Since this method reinitializes the ViewHolder for a new mod, this Future stops being ours, so we cancel it
                 * and null it. The rest is handled above
                 */
                mExtensionFuture.cancel(true);
                mExtensionFuture = null;
            }

            mModItem = item;
            // here the previous reference to the image receiver will disappear
            mImageReceiver = bm->{
                mImageReceiver = null;
                mThumbnailBitmap = bm;
                RoundedBitmapDrawable drawable = RoundedBitmapDrawableFactory.create(mIconView.getResources(), bm);
                drawable.setCornerRadius(mCornerDimensionCache * bm.getHeight());
                mIconView.setImageDrawable(drawable);
            };
            mIconCache.getImage(mImageReceiver, mModItem.getIconCacheTag(), mModItem.imageUrl);
            mSourceView.setImageResource(getSourceDrawable(item.apiSource));
            mTitle.setText(item.title);
            mDescription.setText(item.description);

            if(hasExtended()){
                closeDetailedView();
            }
        }

        /** Display extended info/interaction about a modpack */
        private void setStateDetailed(ModDetail detailedItem) {
            if(detailedItem != null) {
                setInstallEnabled(true);
                mExtendedErrorTextView.setVisibility(View.GONE);
                mVersionAdapter.setObjects(Arrays.asList(detailedItem.versionNames));
                mExtendedSpinner.setAdapter(mVersionAdapter);
            } else {
                closeDetailedView();
                setInstallEnabled(false);
                mExtendedErrorTextView.setVisibility(View.VISIBLE);
                mExtendedSpinner.setAdapter(null);
                mVersionAdapter.setObjects(null);
            }
        }

        private void openDetailedView() {
            mExtendedLayout.setVisibility(View.VISIBLE);
            mDescription.setMaxLines(99);

            // We need to align to the longer section
            int futureBottom = mDescription.getBottom() + Tools.mesureTextviewHeight(mDescription) - mDescription.getHeight();
            ConstraintLayout.LayoutParams params = (ConstraintLayout.LayoutParams) mExtendedLayout.getLayoutParams();
            params.topToBottom = futureBottom > mIconView.getBottom() ? R.id.mod_body_textview : R.id.mod_thumbnail_imageview;
            mExtendedLayout.setLayoutParams(params);
        }

        private void closeDetailedView(){
            mExtendedLayout.setVisibility(View.GONE);
            mDescription.setMaxLines(3);
        }

        private void setDetailedStateDefault() {
            setInstallEnabled(false);
            mExtendedSpinner.setAdapter(mLoadingAdapter);
            mExtendedErrorTextView.setVisibility(View.GONE);
            openDetailedView();
        }

        private boolean hasExtended(){
            return mExtendedLayout != null;
        }

        private boolean isExtended(){
            return hasExtended() && mExtendedLayout.getVisibility() == View.VISIBLE;
        }

        private int getSourceDrawable(int apiSource) {
            switch (apiSource) {
                case Constants.SOURCE_CURSEFORGE:
                    return R.drawable.ic_curseforge;
                case Constants.SOURCE_MODRINTH:
                    return R.drawable.ic_modrinth;
                default:
                    throw new RuntimeException("Unknown API source");
            }
        }

        private void setInstallEnabled(boolean enabled) {
            mInstallEnabled = enabled;
            updateInstallButtonState();
        }

        private void updateInstallButtonState() {
            if(mExtendedButton != null)
                mExtendedButton.setEnabled(mInstallEnabled && !mTasksRunning);
        }
    }

    /**
     * The view holder used to hold the progress bar at the end of the list
     */
    private static class LoadingViewHolder extends RecyclerView.ViewHolder {
        public LoadingViewHolder(View view) {
            super(view);
        }
    }

    private class SearchApiTask implements SelfReferencingFuture.FutureInterface {
        private final SearchFilters mSearchFilters;
        private final SearchResult mPreviousResult;

        private SearchApiTask(SearchFilters searchFilters, SearchResult previousResult) {
            this.mSearchFilters = searchFilters;
            this.mPreviousResult = previousResult;
        }

        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void run(Future<?> myFuture) {
            SearchResult result = mModpackApi.searchMod(mSearchFilters, mPreviousResult);
            ModItem[] resultModItems = result != null ? result.results : null;
            if(resultModItems != null && resultModItems.length != 0 && mPreviousResult != null) {
                ModItem[] newModItems = new ModItem[resultModItems.length + mModItems.length];
                System.arraycopy(mModItems, 0, newModItems, 0, mModItems.length);
                System.arraycopy(resultModItems, 0, newModItems, mModItems.length, resultModItems.length);
                resultModItems = newModItems;
            }
            ModItem[] finalModItems = resultModItems;
            Tools.runOnUiThread(() -> {
                if(myFuture.isCancelled()) return;
                mTaskInProgress = null;
                if(finalModItems == null) {
                    mSearchResultCallback.onSearchError(SearchResultCallback.ERROR_INTERNAL);
                }else if(finalModItems.length == 0) {
                    if(mPreviousResult != null) {
                        mLastPage = true;
                        notifyItemChanged(mModItems.length);
                        mSearchResultCallback.onSearchFinished();
                        return;
                    }
                    mSearchResultCallback.onSearchError(SearchResultCallback.ERROR_NO_RESULTS);
                }else{
                    mSearchResultCallback.onSearchFinished();
                }
                mCurrentResult = result;
                if(finalModItems == null) {
                    mModItems = MOD_ITEMS_EMPTY;
                    notifyDataSetChanged();
                    return;
                }
                if(mPreviousResult != null) {
                    int prevLength = mModItems.length;
                    mModItems = finalModItems;
                    notifyItemChanged(prevLength);
                    notifyItemRangeInserted(prevLength+1, mModItems.length);
                }else {
                    mModItems = finalModItems;
                    notifyDataSetChanged();
                }
            });
        }
    }

    public interface SearchResultCallback {
        int ERROR_INTERNAL = 0;
        int ERROR_NO_RESULTS = 1;
        void onSearchFinished();
        void onSearchError(int error);
    }

    static class UrlImageGetter implements android.text.Html.ImageGetter {
        private final TextView mTextView;

        public UrlImageGetter(TextView textView) {
            mTextView = textView;
        }

        @Override
        public android.graphics.drawable.Drawable getDrawable(String source) {
            final android.graphics.drawable.LevelListDrawable drawable = new android.graphics.drawable.LevelListDrawable();
            drawable.setBounds(0, 0, 100, 100);

            PojavApplication.sExecutorService.execute(() -> {
                try {
                    java.io.InputStream is = new java.net.URL(source).openStream();
                    android.graphics.Bitmap bmp = android.graphics.BitmapFactory.decodeStream(is);
                    if (bmp != null) {
                        final android.graphics.drawable.BitmapDrawable bitmapDrawable = new android.graphics.drawable.BitmapDrawable(mTextView.getResources(), bmp);
                        Tools.runOnUiThread(() -> {
                            int maxWidth = mTextView.getWidth() - mTextView.getPaddingLeft() - mTextView.getPaddingRight();
                            if (maxWidth <= 0) maxWidth = 600;
                            int width = bmp.getWidth();
                            int height = bmp.getHeight();
                            if (width > maxWidth) {
                                float ratio = (float) maxWidth / (float) width;
                                width = maxWidth;
                                height = (int) (height * ratio);
                            }
                            bitmapDrawable.setBounds(0, 0, width, height);
                            drawable.addLevel(1, 1, bitmapDrawable);
                            drawable.setBounds(0, 0, width, height);
                            drawable.setLevel(1);
                            mTextView.setText(mTextView.getText());
                        });
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            return drawable;
        }
    }
}
