package net.kdt.pojavlaunch.authenticator.model;

/** Matches the JSON shape returned by your server's /yggdrasil/authserver/authenticate and /refresh. */
public class CraftynAuthResponse {
    public String accessToken;
    public String clientToken;
    public SelectedProfile selectedProfile;

    public static class SelectedProfile {
        public String id;   // UUID without dashes
        public String name; // username
    }
}
