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
    // ---- CraftynMC (your own FearNet/CraftynMC server) ----
    // injectorUrl now includes "/yggdrasil" because the server's homepage and
    // its Yggdrasil meta endpoint both used to fight over the bare "/" path -
    // the website's homepage always won, so authlib-injector was getting HTML
    // back instead of JSON ("Unable to parse metadata: Invalid JSON"). The
    // Yggdrasil API now lives at its own path so both work at once.
    @SerializedName("craftynmc")
    CRAFTYN_MC(
            net.kdt.pojavlaunch.authenticator.impl.CraftynBackgroundLogin.CREATOR,
            R.drawable.ic_auth_craftynmc,
            "farmer-my1t.onrender.com/yggdrasil",
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
