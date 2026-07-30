package io.github.humbleui.skija.impl;

import java.io.*;
import java.net.*;

public final class ResourceFinder implements Closeable {
    private final String _moduleName;

    public ResourceFinder(String moduleName) {
        this._moduleName = moduleName;
    }

    public URL findResource(String path) throws IOException {
        return ResourceFinder.class.getClassLoader().getResource(path);
    }

    @Override
    public void close() throws IOException {
    }
}
