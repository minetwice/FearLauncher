package net.kdt.pojavlaunch.authenticator.model;

/** Matches the JSON shape returned by FearNet's /authserver/authenticate and /authserver/refresh. */
public class FearNetAuthResponse {
    public String accessToken;
    public String clientToken;
    public SelectedProfile selectedProfile;

    public static class SelectedProfile {
        public String id;   // UUID without dashes
        public String name; // username
    }
}
