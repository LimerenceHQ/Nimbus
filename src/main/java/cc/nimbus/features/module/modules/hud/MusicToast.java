package cc.nimbus.features.module.modules.hud;

import cc.nimbus.core.Core;
import cc.nimbus.event.component.EventTarget;
import cc.nimbus.event.impl.game.EventTick;
import cc.nimbus.features.module.api.BasicModule;
import cc.nimbus.features.module.api.ModuleCategory;
import cc.nimbus.features.module.api.ModuleInfo;
import cc.nimbus.features.music.integration.MusicToastState;
import cc.nimbus.features.music.model.Music;
import cc.nimbus.features.music.playback.PlaybackPhase;
import cc.nimbus.managers.MusicManager;
import net.minecraft.client.Minecraft;

@ModuleInfo(
        name = "MusicToast",
        displayName = "Music Toast",
        description = "Shows the native Minecraft toast when the Nimbus Music track changes.",
        category = ModuleCategory.HUD
)
public final class MusicToast extends BasicModule {
    private static final long UNOBSERVED_SONG = Long.MIN_VALUE;

    private long observedSongId = UNOBSERVED_SONG;

    @Override
    protected void onEnable() {
        observedSongId = UNOBSERVED_SONG;
        MusicToastState.clear();
    }

    @Override
    protected void onDisable() {
        observedSongId = -1L;
        MusicToastState.clear();
        Minecraft minecraft = Minecraft.getInstance();
        minecraft.getToastManager().hideNowPlayingToast();
    }

    @EventTarget
    private void onTick(EventTick event) {
        if (event.getPhase() != EventTick.Phase.POST) return;
        MusicManager music = Core.INSTANCE.getMusicManager();
        var state = music.state();
        Music current = state.music();
        long currentId = current == null ? -1L : current.id();
        if (observedSongId == UNOBSERVED_SONG) {
            observedSongId = currentId;
            return;
        }
        if (currentId == observedSongId) return;
        observedSongId = currentId;
        if (current == null || state.phase() == PlaybackPhase.IDLE) {
            MusicToastState.clear();
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.getToastManager().hideNowPlayingToast();
            return;
        }
        MusicToastState.set(current.name() + " · " + current.artistsText());
        Minecraft.getInstance().getToastManager().showNowPlayingToast();
    }
}
