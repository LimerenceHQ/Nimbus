package cc.nimbus.mixin;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import cc.nimbus.core.Core;
import cc.nimbus.managers.CommandManager;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow @Final private EditBox input;
    @Shadow @Final private List<FormattedCharSequence> commandUsage;
    @Shadow private CompletableFuture<Suggestions> pendingSuggestions;
    @Shadow private boolean keepSuggestions;

    @Shadow public abstract void hide();
    @Shadow public abstract void showSuggestions(boolean narrateFirstSuggestion);

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void nimbus$updateDotCommandSuggestions(CallbackInfo callback) {
        String value = input.getValue();
        if (!value.startsWith(CommandManager.PREFIX)) return;

        callback.cancel();
        if (keepSuggestions) return;

        hide();
        commandUsage.clear();
        input.setSuggestion(null);

        int cursor = input.getCursorPosition();
        List<String> candidates = Core.INSTANCE.getCommandManager().getSuggestions(value.substring(0, cursor));
        if (candidates.isEmpty()) {
            pendingSuggestions = null;
            return;
        }

        int start = cursor;
        while (start > 0 && !Character.isWhitespace(value.charAt(start - 1))) start--;
        int end = cursor;
        while (end < value.length() && !Character.isWhitespace(value.charAt(end))) end++;

        StringRange range = StringRange.between(start, end);
        List<Suggestion> suggestions = candidates.stream()
                .distinct()
                .map(candidate -> new Suggestion(range, candidate))
                .toList();
        pendingSuggestions = CompletableFuture.completedFuture(new Suggestions(range, suggestions));
        showSuggestions(false);
    }
}
