package io.github.humbleui.skija.paragraph;

import java.util.*;
import io.github.humbleui.types.Rect;

public class TextBox {
    public final Rect _rect;
    public final Direction _direction;

    public TextBox(Rect _rect, Direction _direction) {
        this._rect = _rect;
        this._direction = _direction;
    }

    public TextBox(float l, float t, float r, float b, int direction) {
        this(Rect.makeLTRB(l, t, r, b), Direction._values[direction]);
    }

    public Rect getRect() {
        return this._rect;
    }

    public Direction getDirection() {
        return this._direction;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextBox textBox = (TextBox) o;
        return Objects.equals(_rect, textBox._rect) && _direction == textBox._direction;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_rect, _direction);
    }

    @Override
    public String toString() {
        return "TextBox{" +
                "_rect=" + _rect +
                ", _direction=" + _direction +
                '}';
    }
}