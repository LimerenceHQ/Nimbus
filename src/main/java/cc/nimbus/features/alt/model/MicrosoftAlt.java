package cc.nimbus.features.alt.model;

public final class MicrosoftAlt extends Alt {
    private final String refreshToken;

    public MicrosoftAlt(String userName,String refreshToken) {
        super(userName, AccountEnum.MICROSOFT);
        this.refreshToken = refreshToken;
    }

    public String getRefreshToken() { return refreshToken; }

}
