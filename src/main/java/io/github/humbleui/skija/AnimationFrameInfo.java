package io.github.humbleui.skija;

import java.util.*;
import org.jetbrains.annotations.*;
import io.github.humbleui.types.*;

/**
 * Information about individual frames in a multi-framed image.
 */
public class AnimationFrameInfo {
    /**
     * <p>The frame that this frame needs to be blended with, or
     * -1 if this frame is independent (so it can be
     * drawn over an uninitialized buffer).</p>
     *
     * <p>Note that this is the *earliest* frame that can be used
     * for blending. Any frame from [_requiredFrame, i) can be
     * used, unless its getDisposalMethod() is {@link AnimationDisposalMode#RESTORE_PREVIOUS}.</p>
     */
    @ApiStatus.Internal
    public final int _requiredFrame;

    /**
     * Number of milliseconds to show this frame.
     */
    @ApiStatus.Internal
    public final int _duration;

    /**
     * <p>Whether the end marker for this frame is contained in the stream.</p>
     *
     * <p>Note: this does not guarantee that an attempt to decode will be complete.
     * There could be an error in the stream.</p>
     */
    @ApiStatus.Internal
    public final boolean _fullyReceived;

    /**
     * <p>This is conservative; it will still return non-opaque if e.g. a
     * color index-based frame has a color with alpha but does not use it.</p>
     */
    @ApiStatus.Internal
    public final ColorAlphaType _alphaType;

    /**
     * <p>Whether the updated rectangle contains alpha.</p>
     *
     * <p>This is conservative; it will still be set to true if e.g. a color
     * index-based frame has a color with alpha but does not use it. In
     * addition, it may be set to true, even if the final frame, after
     * blending, is opaque.</p>
     */
    @ApiStatus.Internal
    public final boolean _hasAlphaWithinBounds;

    /**
     * <p>How this frame should be modified before decoding the next one.</p>
     */
    @ApiStatus.Internal
    public final AnimationDisposalMode _disposalMethod;

    /**
     * <p>How this frame should blend with the prior frame.</p>
     */
    @ApiStatus.Internal
    public final BlendMode _blendMode;

    /**
     * <p>The rectangle updated by this frame.</p>
     *
     * <p>It may be empty, if the frame does not change the image. It will
     * always be contained by {@link Codec#getSize()}.
     */
    @ApiStatus.Internal
    public final IRect _frameRect;

    public AnimationFrameInfo(int requiredFrame, int duration, boolean fullyReceived, ColorAlphaType alphaType, boolean hasAlphaWithinBounds, AnimationDisposalMode disposalMethod, BlendMode blendMode, IRect frameRect) {
        this._requiredFrame = requiredFrame;
        this._duration = duration;
        this._fullyReceived = fullyReceived;
        this._alphaType = alphaType;
        this._hasAlphaWithinBounds = hasAlphaWithinBounds;
        this._disposalMethod = disposalMethod;
        this._blendMode = blendMode;
        this._frameRect = frameRect;
    }

    @ApiStatus.Internal
    public AnimationFrameInfo(int requiredFrame, int duration, boolean fullyReceived, int alphaTypeOrdinal, boolean hasAlphaWithinBounds, int disposalMethodOrdinal, int blendModeOrdinal, IRect frameRect) {
        this(requiredFrame, duration, fullyReceived, ColorAlphaType._values[alphaTypeOrdinal], hasAlphaWithinBounds, AnimationDisposalMode._values[disposalMethodOrdinal], BlendMode._values[blendModeOrdinal], frameRect);
    }

    public int getRequiredFrame() {
        return this._requiredFrame;
    }

    public int getDuration() {
        return this._duration;
    }

    public boolean isFullyReceived() {
        return this._fullyReceived;
    }

    public ColorAlphaType getAlphaType() {
        return this._alphaType;
    }

    public boolean isHasAlphaWithinBounds() {
        return this._hasAlphaWithinBounds;
    }

    public AnimationDisposalMode getDisposalMethod() {
        return this._disposalMethod;
    }

    public BlendMode getBlendMode() {
        return this._blendMode;
    }

    public IRect getFrameRect() {
        return this._frameRect;
    }

    public AnimationFrameInfo withRequiredFrame(int _requiredFrame) {
        return this._requiredFrame == _requiredFrame ? this : new AnimationFrameInfo(_requiredFrame, this._duration, this._fullyReceived, this._alphaType, this._hasAlphaWithinBounds, this._disposalMethod, this._blendMode, this._frameRect);
    }

    public AnimationFrameInfo withDuration(int _duration) {
        return this._duration == _duration ? this : new AnimationFrameInfo(this._requiredFrame, _duration, this._fullyReceived, this._alphaType, this._hasAlphaWithinBounds, this._disposalMethod, this._blendMode, this._frameRect);
    }

    public AnimationFrameInfo withFullyReceived(boolean _fullyReceived) {
        return this._fullyReceived == _fullyReceived ? this : new AnimationFrameInfo(this._requiredFrame, this._duration, _fullyReceived, this._alphaType, this._hasAlphaWithinBounds, this._disposalMethod, this._blendMode, this._frameRect);
    }

    public AnimationFrameInfo withAlphaType(ColorAlphaType _alphaType) {
        return this._alphaType == _alphaType ? this : new AnimationFrameInfo(this._requiredFrame, this._duration, this._fullyReceived, _alphaType, this._hasAlphaWithinBounds, this._disposalMethod, this._blendMode, this._frameRect);
    }

    public AnimationFrameInfo withHasAlphaWithinBounds(boolean _hasAlphaWithinBounds) {
        return this._hasAlphaWithinBounds == _hasAlphaWithinBounds ? this : new AnimationFrameInfo(this._requiredFrame, this._duration, this._fullyReceived, this._alphaType, _hasAlphaWithinBounds, this._disposalMethod, this._blendMode, this._frameRect);
    }

    public AnimationFrameInfo withDisposalMethod(AnimationDisposalMode _disposalMethod) {
        return this._disposalMethod == _disposalMethod ? this : new AnimationFrameInfo(this._requiredFrame, this._duration, this._fullyReceived, this._alphaType, this._hasAlphaWithinBounds, _disposalMethod, this._blendMode, this._frameRect);
    }

    public AnimationFrameInfo withBlendMode(BlendMode _blendMode) {
        return this._blendMode == _blendMode ? this : new AnimationFrameInfo(this._requiredFrame, this._duration, this._fullyReceived, this._alphaType, this._hasAlphaWithinBounds, this._disposalMethod, _blendMode, this._frameRect);
    }

    public AnimationFrameInfo withFrameRect(IRect _frameRect) {
        return this._frameRect == _frameRect ? this : new AnimationFrameInfo(this._requiredFrame, this._duration, this._fullyReceived, this._alphaType, this._hasAlphaWithinBounds, this._disposalMethod, this._blendMode, _frameRect);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AnimationFrameInfo that = (AnimationFrameInfo) o;
        return _requiredFrame == that._requiredFrame &&
                _duration == that._duration &&
                _fullyReceived == that._fullyReceived &&
                _hasAlphaWithinBounds == that._hasAlphaWithinBounds &&
                Objects.equals(_alphaType, that._alphaType) &&
                Objects.equals(_disposalMethod, that._disposalMethod) &&
                Objects.equals(_blendMode, that._blendMode) &&
                Objects.equals(_frameRect, that._frameRect);
    }

    @Override
    public int hashCode() {
        return Objects.hash(_requiredFrame, _duration, _fullyReceived, _alphaType, _hasAlphaWithinBounds, _disposalMethod, _blendMode, _frameRect);
    }

    @Override
    public String toString() {
        return "AnimationFrameInfo{" +
                "_requiredFrame=" + _requiredFrame +
                ", _duration=" + _duration +
                ", _fullyReceived=" + _fullyReceived +
                ", _alphaType=" + _alphaType +
                ", _hasAlphaWithinBounds=" + _hasAlphaWithinBounds +
                ", _disposalMethod=" + _disposalMethod +
                ", _blendMode=" + _blendMode +
                ", _frameRect=" + _frameRect +
                '}';
    }
}