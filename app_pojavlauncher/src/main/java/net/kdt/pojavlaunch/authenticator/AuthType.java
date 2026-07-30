package net.kdt.pojavlaunch.authenticator;

import com.google.gson.annotations.SerializedName;

import net.kdt.pojavlaunch.authenticator.impl.MicrosoftBackgroundLogin;

import git.artdeell.mojo.R;

public enum AuthType {
    @SerializedName("microsoft")
    MICROSOFT(
            MicrosoftBackgroundLogin.CREATOR,
            R.drawable.ic_auth_ms,
            null,
            "https://mineskin.eu/skin/%s" // Switched from mc-heads.net cause blocked in Russia
    ),
    // ---- CraftynMC (your own FearNet server) ----
    // injectorUrl: bare domain only, no "https://", no trailing slash - this is
    // passed straight into the authlib-injector javaagent argument at launch time.
    // THIS WAS null BEFORE, WHICH MEANT SKINS NEVER SHOWED IN-GAME - now fixed.
    // skinUrl: full URL template (%s = username), used only for the small face
    // icon shown in the account list. Must use the /skins/name/ route (keyed by
    // username), not /skins/ (which is keyed by UUID on the server).
    @SerializedName("craftynmc")
    CRAFTYN_MC(
            net.kdt.pojavlaunch.authenticator.impl.CraftynBackgroundLogin.CREATOR,
            R.drawable.ic_auth_craftynmc,
            "farmer-my1t.onrender.com",
            "https://farmer-my1t.onrender.com/skins/name/%s.png"
    ),
    @SerializedName("local")
    LOCAL(null, 0, null, null);

    private final BackgroundLogin.Creator mCreator;
    public final int iconResource;
    public final String injectorUrl;
    public final String skinUrl;

    AuthType(BackgroundLogin.Creator creator, int iconResource, String injectorUrl, String skinUrl) {
        this.mCreator = creator;
        this.iconResource = iconResource;
        this.injectorUrl = injectorUrl;
        this.skinUrl = skinUrl;
    }

    public boolean requiresLogin() {
        return mCreator != null;
    }

    public BackgroundLogin createAuth() {
        if(mCreator == null) throw new RuntimeException("This account does not support login");
        return mCreator.create();
    }
}
