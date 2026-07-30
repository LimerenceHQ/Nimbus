package io.github.humbleui.skija;

import java.util.*;
import org.jetbrains.annotations.*;

public class FontFamilyName {
    public final String _name;
    public final String _language;

    public FontFamilyName(String _name, String _language) {
        this._name = _name;
        this._language = _language;
    }

    public String getName() {
        return this._name;
    }

    public String getLanguage() {
        return this._language;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FontFamilyName that = (FontFamilyName) o;
        return Objects.equals(_name, that._name) &&
                Objects.equals(_language, that._language);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_name, _language);
    }

    @Override
    public String toString() {
        return "FontFamilyName{" +
                "_name='" + _name + '\'' +
                ", _language='" + _language + '\'' +
                '}';
    }
}