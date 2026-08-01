package cc.nimbus.features.music.internal.codec.jflac;

import java.io.IOException;

public class DecodeError extends IOException {

    public DecodeError(String message) {
        super(message);
    }

}
