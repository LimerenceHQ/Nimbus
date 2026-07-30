package io.github.humbleui.skija.paragraph;

import java.util.*;

public class PositionWithAffinity {
    public final int _position;
    public final Affinity _affinity;

    public PositionWithAffinity(int _position, Affinity _affinity) {
        this._position = _position;
        this._affinity = _affinity;
    }

    public int getPosition() {
        return this._position;
    }

    public Affinity getAffinity() {
        return this._affinity;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PositionWithAffinity that = (PositionWithAffinity) o;
        return _position == that._position && _affinity == that._affinity;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_position, _affinity);
    }

    @Override
    public String toString() {
        return "PositionWithAffinity{" +
                "_position=" + _position +
                ", _affinity=" + _affinity +
                '}';
    }
}