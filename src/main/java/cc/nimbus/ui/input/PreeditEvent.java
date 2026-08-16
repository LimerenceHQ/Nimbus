package cc.nimbus.ui.input;

public record PreeditEvent(String fullText) {
    public PreeditEvent {
        fullText = fullText == null ? "" : fullText;
    }
}
