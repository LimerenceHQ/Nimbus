package cc.nimbus.utils.render.font;

public final class NimbusFonts {
    public static final FontSpec GEIST = new FontSpec(FontFace.GEIST);
    public static final FontSpec INTER = new FontSpec(FontFace.INTER);
    public static final FontSpec NOTO_SANS_SC = new FontSpec(FontFace.NOTO_SANS_SC);

    public static final FontSpec DISPLAY = GEIST.weight(600f);
    public static final FontSpec BODY = INTER;
    public static final FontSpec BODY_MEDIUM = BODY.weight(500f);
    public static final FontSpec BODY_STRONG = BODY.weight(600f);
    public static final FontSpec CONTENT = NOTO_SANS_SC;
    public static final FontSpec CONTENT_MEDIUM = CONTENT.weight(500f);

    private NimbusFonts() {
    }
}
