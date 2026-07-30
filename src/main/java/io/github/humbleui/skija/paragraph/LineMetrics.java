package io.github.humbleui.skija.paragraph;

import java.util.*;

public class LineMetrics {
    /**
     * The index in the text buffer the line begins.
     */
    public final long    _startIndex;

    /**
     * The index in the text buffer the line ends.
     */
    public final long    _endIndex;
    public final long    _endExcludingWhitespaces;
    public final long    _endIncludingNewline;
    public final boolean _hardBreak;

    /**
     * The final computed ascent for the line. This can be impacted by the strut, height,
     * scaling, as well as outlying runs that are very tall. The top edge is
     * {@code getBaseline() - getAscent()} and the bottom edge is {@code getBaseline() + getDescent()}.
     * Ascent and descent are provided as positive numbers. These values are the cumulative metrics for the entire line.
     */
    public final double  _ascent;

    /**
     * The final computed descent for the line. This can be impacted by the strut, height,
     * scaling, as well as outlying runs that are very tall. The top edge is
     * {@code getBaseline() - getAscent()} and the bottom edge is {@code getBaseline() + getDescent()}.
     * Ascent and descent are provided as positive numbers. These values are the cumulative metrics for the entire line.
     */
    public final double  _descent;
    public final double  _unscaledAscent;

    /**
     * Total height of the paragraph including the current line.
     */
    public final double  _height;

    /**
     * Width of the line.
     */
    public final double  _width;

    /**
     * The left edge of the line.
     */
    public final double  _left;

    /**
     * The y position of the baseline for this line from the top of the paragraph.
     */
    public final double  _baseline;

    /**
     * Zero indexed line number
     */
    public final long    _lineNumber;

    public LineMetrics(long _startIndex, long _endIndex, long _endExcludingWhitespaces, long _endIncludingNewline,
                       boolean _hardBreak, double _ascent, double _descent, double _unscaledAscent, double _height,
                       double _width, double _left, double _baseline, long _lineNumber) {
        this._startIndex = _startIndex;
        this._endIndex = _endIndex;
        this._endExcludingWhitespaces = _endExcludingWhitespaces;
        this._endIncludingNewline = _endIncludingNewline;
        this._hardBreak = _hardBreak;
        this._ascent = _ascent;
        this._descent = _descent;
        this._unscaledAscent = _unscaledAscent;
        this._height = _height;
        this._width = _width;
        this._left = _left;
        this._baseline = _baseline;
        this._lineNumber = _lineNumber;
    }

    public long getStartIndex() {
        return this._startIndex;
    }

    public long getEndIndex() {
        return this._endIndex;
    }

    public long getEndExcludingWhitespaces() {
        return this._endExcludingWhitespaces;
    }

    public long getEndIncludingNewline() {
        return this._endIncludingNewline;
    }

    public boolean isHardBreak() {
        return this._hardBreak;
    }

    public double getAscent() {
        return this._ascent;
    }

    public double getDescent() {
        return this._descent;
    }

    public double getUnscaledAscent() {
        return this._unscaledAscent;
    }

    public double getHeight() {
        return this._height;
    }

    public double getWidth() {
        return this._width;
    }

    public double getLeft() {
        return this._left;
    }

    public double getBaseline() {
        return this._baseline;
    }

    public long getLineNumber() {
        return this._lineNumber;
    }

    /**
     * The height of the current line, equals to {@code Math.round(getAscent() + getDescent())}.
     */
    public double getLineHeight() {
        return _ascent + _descent;
    }

    /**
     * The right edge of the line, equals to {@code getLeft() + getWidth()}
     */
    public double getRight() {
        return _left + _width;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LineMetrics that = (LineMetrics) o;
        return _startIndex == that._startIndex &&
                _endIndex == that._endIndex &&
                _endExcludingWhitespaces == that._endExcludingWhitespaces &&
                _endIncludingNewline == that._endIncludingNewline &&
                _hardBreak == that._hardBreak &&
                Double.compare(that._ascent, _ascent) == 0 &&
                Double.compare(that._descent, _descent) == 0 &&
                Double.compare(that._unscaledAscent, _unscaledAscent) == 0 &&
                Double.compare(that._height, _height) == 0 &&
                Double.compare(that._width, _width) == 0 &&
                Double.compare(that._left, _left) == 0 &&
                Double.compare(that._baseline, _baseline) == 0 &&
                _lineNumber == that._lineNumber;
    }

    @Override
    public int hashCode() {
        return Objects.hash(_startIndex, _endIndex, _endExcludingWhitespaces, _endIncludingNewline, _hardBreak,
                _ascent, _descent, _unscaledAscent, _height, _width, _left, _baseline, _lineNumber);
    }

    @Override
    public String toString() {
        return "LineMetrics{" +
                "_startIndex=" + _startIndex +
                ", _endIndex=" + _endIndex +
                ", _endExcludingWhitespaces=" + _endExcludingWhitespaces +
                ", _endIncludingNewline=" + _endIncludingNewline +
                ", _hardBreak=" + _hardBreak +
                ", _ascent=" + _ascent +
                ", _descent=" + _descent +
                ", _unscaledAscent=" + _unscaledAscent +
                ", _height=" + _height +
                ", _width=" + _width +
                ", _left=" + _left +
                ", _baseline=" + _baseline +
                ", _lineNumber=" + _lineNumber +
                '}';
    }
}