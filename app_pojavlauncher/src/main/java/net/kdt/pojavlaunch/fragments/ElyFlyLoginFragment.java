package net.kdt.pojavlaunch.fragments;

import net.kdt.pojavlaunch.extra.ExtraConstants;

public class ElyFlyLoginFragment extends OAuthFragment {
    public static final String TAG = "ELYFLY_LOGIN_FRAGMENT";
    public ElyFlyLoginFragment() {
        super("internalredirect",
                "https://account.ely.by/oauth2/v1" +
                        "?client_id=fearlauncher2" +
                        "&redirect_uri=internalredirect%3A%2F%2Fcomplete" +
                        "&response_type=code" +
                        "&scope=account_info%20offline_access%20minecraft_server_session",
                ExtraConstants.ELYFLY_LOGIN_TODO);
    }
}
