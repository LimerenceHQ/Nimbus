package cc.nimbus.utils.render.skija.internal.effect.shader;

import cc.nimbus.utils.render.skija.internal.effect.shader.runtime.RuntimeEffectBuilder;

@SuppressWarnings({"SpellCheckingInspection"})
public final class Shaders {
    public static final RuntimeEffectBuilder KAWASE_DOWNSAMPLE = new RuntimeEffectBuilder(
            """
            uniform shader child;
            uniform float value;
            
            half4 main(float2 fragCoord) {
                float2 offset = float2(value);
            
                half4 sum = child.eval(fragCoord) * 4.0;
                sum += child.eval(fragCoord + float2(-1.0, -1.0) * offset);
                sum += child.eval(fragCoord + float2(1.0, 1.0) * offset);
                sum += child.eval(fragCoord + float2(1.0, -1.0) * offset);
                sum += child.eval(fragCoord + float2(-1.0, 1.0) * offset);
            
                return sum * 0.125;
            }
            
            """
    );

    public static final RuntimeEffectBuilder KAWASE_UPSAMPLE = new RuntimeEffectBuilder(
            """
            uniform shader child;
            uniform float value;
            
            half4 main(float2 fragCoord) {
                float2 offset = float2(value);
            
                half4 sum = child.eval(fragCoord + float2(-2.0, 0.0) * offset);
                sum += child.eval(fragCoord + float2(-1.0, 1.0) * offset) * 2.0;
                sum += child.eval(fragCoord + float2(0.0, 2.0) * offset);
                sum += child.eval(fragCoord + float2(1.0, 1.0) * offset) * 2.0;
                sum += child.eval(fragCoord + float2(2.0, 0.0) * offset);
                sum += child.eval(fragCoord + float2(1.0, -1.0) * offset) * 2.0;
                sum += child.eval(fragCoord + float2(0.0, -2.0) * offset);
                sum += child.eval(fragCoord + float2(-1.0, -1.0) * offset) * 2.0;
            
                return sum / 12.0;
            }
            
            """
    );

    private Shaders() {
    }

    public static void close() {
        KAWASE_DOWNSAMPLE.close();
        KAWASE_UPSAMPLE.close();
    }
}
