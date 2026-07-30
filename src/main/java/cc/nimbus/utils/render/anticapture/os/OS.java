package cc.nimbus.utils.render.anticapture.os;

public enum OS {
    Windows("Windows"),
    MacOS("MacOS"),
    IDK("Null");

    private final String name;

    OS(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return name;
    }
}
