package cc.nimbus.features.alt.model;

public abstract class Alt {
    private final String userName;
    private final AccountEnum accountType;

    public Alt(String userName,AccountEnum accountType) {
        this.userName = userName;
        this.accountType = accountType;
    }

    public String getUserName() { return userName; }
    public AccountEnum getAccountType() { return accountType; }
}
