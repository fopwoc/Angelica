package com.gtnewhorizons.angelica.sdlgpu.compat;

/** State upstream applies but never reports back; Julia mirrors it only so queries return what the caller set. */
public final class ReportedState {
    public float polygonOffsetFactor, polygonOffsetUnits;
    public int renderbuffer;
}
