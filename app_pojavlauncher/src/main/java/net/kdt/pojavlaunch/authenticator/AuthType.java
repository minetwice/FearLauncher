package net.kdt.pojavlaunch.authenticator;

import com.google.gson.annotations.SerializedName;

import net.kdt.pojavlaunch.authenticator.impl.ElyByBackgroundLogin;
import net.kdt.pojavlaunch.authenticator.impl.FearNetBackgroundLogin;
import net.kdt.pojavlaunch.authenticator.impl.MicrosoftBackgroundLogin;

import git.artdeell.mojo.R;

public enum AuthType {
    @SerializedName("microsoft")
    MICROSOFT(
            MicrosoftBackgroundLogin.CREATOR,
            R.drawable.ic_auth_ms,
            null,
            "https://mineskin.eu/skin/%s"
    ),
    @SerializedName("elyby")
    ELY_BY(
            ElyByBackgroundLogin.CREATOR,
            R.drawable.ic_auth_elyby,
            "ely.by",
            "http://skinsystem.ely.by/skins/%s.png"
    ),
    // ---- FearNet: your own custom auth/skin server ----
    // IMPORTANT: if your Render service URL ever changes, both values below need
    // to be updated to match (rebuild + redistribute the app after changing them).
    //   injectorUrl: bare domain only, no "https://", no trailing slash - this is
    //   passed straight into the authlib-injector javaagent argument, same pattern
    //   Ely.by uses above with just "ely.by".
    //   skinUrl: full URL template, formatted with %s = username, used only for
    //   the small face icon shown in the account list (actual in-game skin
    //   resolution goes through injectorUrl, not this field).
    @SerializedName("fearnet")
    FEAR_NET(
            FearNetBackgroundLogin.CREATOR,
            R.drawable.ic_auth_fearnet,
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
