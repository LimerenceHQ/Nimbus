package cc.nimbus.features.module.modules.combat;

import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.module.api.setting.impl.BooleanSetting;
import cc.nimbus.features.module.api.setting.impl.ColorSetting;
import cc.nimbus.features.module.api.setting.impl.FoldSetting;
import cc.nimbus.features.module.api.setting.impl.ModeSetting;
import cc.nimbus.features.module.api.setting.impl.MultiSelectSetting;
import cc.nimbus.features.module.api.setting.impl.NumberSetting;
import cc.nimbus.features.module.api.setting.impl.TextSetting;

import java.util.Set;

@ModuleInfo(
        name = "SettingPreview",
        displayName = "Setting Preview",
        description = "Visual preview for every supported setting control.",
        category = ModuleCategory.Combat
)
public final class SettingPreview extends BasicModule {
    public final BooleanSetting toggle = new BooleanSetting("Toggle", true);
    public final NumberSetting amount = new NumberSetting("Amount", 0.65, 0, 1, 0.05);
    public final ModeSetting mode = new ModeSetting("Mode", new String[]{"Default", "Compact", "Detailed"}, "Default");
    public final MultiSelectSetting elements = new MultiSelectSetting("Elements", new String[]{"Outline", "Fill", "Labels"}, Set.of("Outline", "Labels"));
    public final ColorSetting color = new ColorSetting("Color", 0xCCFFFFFF);
    public final TextSetting label = new TextSetting("Label", "Nimbus", "Display text", false, false, null, 32);
    public final TextSetting secret = new TextSetting("Secret", "preview", "Hidden value", true, false, null, 32);
    public final TextSetting notes = new TextSetting("Notes", "A multiline setting used to verify wrapping, clipping and input behavior.", "Write notes", false, true, null, 256);
    public final FoldSetting advanced = new FoldSetting("Advanced", false);
    public final BooleanSetting advancedBoolean = new BooleanSetting("Advanced Boolean",true, advanced::getValue);
    public final BooleanSetting advancedBoolean1 = new BooleanSetting("Advanced Boolean 1",true, advanced::getValue);
    public final BooleanSetting advancedBoolean2 = new BooleanSetting("Advanced Boolean 2",true, advanced::getValue);
    public final BooleanSetting advancedBoolean3 = new BooleanSetting("Advanced Boolean 3",true, advanced::getValue);
    public final BooleanSetting advancedBoolean4 = new BooleanSetting("Advanced Boolean 4",true, advanced::getValue);
    public final BooleanSetting advancedBoolean5 = new BooleanSetting("Advanced Boolean 5",true, advanced::getValue);
    public final BooleanSetting advancedBoolean6 = new BooleanSetting("Advanced Boolean 6",true, advanced::getValue);

    public final NumberSetting conditionalAmount = new NumberSetting("Conditional amount", 50, 0, 100, 1, advanced::getValue);
    public final TextSetting conditionalText = new TextSetting("Conditional text", "Visible while Advanced is open", advanced::getValue);
}
