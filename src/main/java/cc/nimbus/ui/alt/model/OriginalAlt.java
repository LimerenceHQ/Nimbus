package cc.nimbus.ui.alt.model;

public final class OriginalAlt extends Alt {
    private final String accessToken;
    private final String uuid;
    private final String type;

    public OriginalAlt(String userName, String accessToken, String uuid) {
        super(userName, AccountEnum.ORIGINAL);
        this.accessToken = accessToken;
        this.uuid = uuid;
        this.type = "msa";
    }

    public String getUUID() {
        return uuid;
    }

    public String getAccessToken() { return accessToken; }
    public String getType() { return type; }
}
