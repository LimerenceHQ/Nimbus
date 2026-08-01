package cc.nimbus.features.command.commands;

import cc.nimbus.core.Core;
import cc.nimbus.features.command.api.BasicCommand;
import cc.nimbus.features.music.Quality;
import cc.nimbus.features.music.MusicConfig;
import cc.nimbus.features.music.lyrics.Lyrics;
import cc.nimbus.features.music.model.Music;
import cc.nimbus.features.music.model.Playlist;
import cc.nimbus.features.music.playback.PlayMode;
import cc.nimbus.features.music.playback.PlaybackState;
import cc.nimbus.features.music.provider.NeteaseApi;
import cc.nimbus.managers.MusicManager;
import cc.nimbus.utils.client.MessageUtils;
import cc.nimbus.utils.client.input.Keyboard;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public final class MusicCommand extends BasicCommand {
    private static final List<String> ACTIONS = List.of("status", "search", "searchpl", "play", "playlist",
            "pause", "resume", "toggle", "stop", "next", "prev", "seek", "volume", "rate", "mode",
            "quality", "queue", "lyrics", "account", "refresh", "logout", "login", "liked", "like",
            "add", "remove", "daily", "recommend", "intelligence", "prefetch", "cache", "bind");

    public MusicCommand() { super("Music", "控制网易云音乐播放器。", "m", "ncm"); }

    @Override
    public void execute(String[] args) {
        if (args.length == 0) { status(); return; }
        String action = args[0].toLowerCase(Locale.ROOT);
        try {
            switch (action) {
                case "status", "info" -> status();
                case "search" -> search(args, false);
                case "searchpl", "searchplaylist" -> search(args, true);
                case "play" -> play(args);
                case "playlist", "pl" -> playlist(args);
                case "pause" -> { music().pause(); message("已暂停。"); }
                case "resume" -> { music().resume(); message("已继续。"); }
                case "toggle" -> { music().togglePause(); status(); }
                case "stop" -> { music().stop(); message("已停止播放。"); }
                case "next" -> selected("下一首", music().next());
                case "prev", "previous" -> selected("上一首", music().previous());
                case "seek" -> { require(args, 2); music().seek(decimal(args[1], "秒数")); status(); }
                case "volume", "vol" -> volume(args);
                case "rate", "speed" -> rate(args);
                case "mode" -> mode(args);
                case "quality" -> quality(args);
                case "queue" -> queue();
                case "lyrics", "lyric" -> lyrics(args);
                case "account" -> account();
                case "refresh" -> complete(music().refreshAccount(), user -> account());
                case "logout" -> { music().logout(); message("网易云账号已退出。"); }
                case "login" -> login(args);
                case "liked" -> liked(args);
                case "like" -> like(args);
                case "add" -> editPlaylist(args, true);
                case "remove", "del" -> editPlaylist(args, false);
                case "daily", "recommend", "rec" -> daily(args);
                case "intelligence", "intelligent", "heartbeat" -> intelligence(args);
                case "prefetch" -> prefetch(args);
                case "cache" -> cache(args);
                case "bind" -> bind(args);
                default -> usage();
            }
        } catch (IllegalArgumentException error) {
            message(error.getMessage());
        }
    }

    private void search(String[] args, boolean playlists) {
        require(args, 2);
        String keyword = join(args, 1);
        message("正在搜索 " + keyword + "...");
        if (playlists) complete(music().searchPlaylists(keyword, 10), MusicCommand::printPlaylists);
        else complete(music().searchSongs(keyword, 10), MusicCommand::printSongs);
    }
    private void play(String[] args) {
        require(args, 2);
        long id = id(args[1]);
        message("正在准备歌曲 " + id + "...");
        complete(music().play(id), song -> selected("开始播放", song));
    }
    private void playlist(String[] args) {
        require(args, 2);
        long id = id(args[1]);
        int index = args.length > 2 ? Math.max(0, integer(args[2], "索引") - 1) : 0;
        message("正在加载歌单 " + id + "...");
        complete(music().playPlaylist(id, index), songs -> selected("开始播放", songs.get(Math.min(index, songs.size() - 1))));
    }
    private void volume(String[] args) {
        if (args.length == 1) { message("音量: " + percent(music().config().volume())); return; }
        float value = (float) decimal(args[1], "音量");
        if (value > 1f) value /= 100f;
        music().volume(value);
        message("音量: " + percent(music().config().volume()));
    }
    private void rate(String[] args) {
        if (args.length == 1) { message("倍速: " + format(music().config().rate()) + "x"); return; }
        music().rate((float) decimal(args[1], "倍速"));
        message("倍速: " + format(music().config().rate()) + "x");
    }
    private void mode(String[] args) {
        if (args.length == 1) { message("播放模式: " + music().config().playMode()); return; }
        PlayMode mode = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "single", "one" -> PlayMode.SINGLE;
            case "loop", "list" -> PlayMode.LOOP_LIST;
            case "shuffle", "random" -> PlayMode.SHUFFLE;
            default -> throw new IllegalArgumentException("模式应为 single、loop 或 shuffle。");
        };
        music().mode(mode);
        message("播放模式: " + mode);
    }
    private void quality(String[] args) {
        if (args.length == 1) { message("音质: " + music().config().quality().apiValue()); return; }
        Quality quality = Quality.parse(args[1]);
        if (quality == null) throw new IllegalArgumentException("未知音质，可用值: " + String.join(", ", qualityNames()));
        music().quality(quality);
        message("音质: " + quality.apiValue());
    }
    private void queue() {
        List<Music> songs = music().queue();
        if (songs.isEmpty()) { message("播放队列为空。"); return; }
        int current = music().state().queueIndex();
        for (int index = 0; index < songs.size(); index++) {
            plain((index == current ? "* " : "- ") + (index + 1) + ". " + songs.get(index));
        }
    }
    private void lyrics(String[] args) {
        long id = args.length > 1 ? id(args[1]) : currentId();
        complete(music().lyrics(id), lyrics -> {
            String value = !lyrics.words().isBlank() ? lyrics.words() : lyrics.line();
            if (value.isBlank()) { message("没有找到歌词。"); return; }
            value.lines().limit(20).forEach(MusicCommand::plain);
        });
    }
    private void account() {
        var profile = music().profile();
        if (profile == null) { message("网易云账号未登录。"); return; }
        message("账号: " + profile.name() + " (" + profile.id() + ")，歌单 "
                + music().accountPlaylists().size() + "，喜欢 " + music().likedIds().size());
    }
    private void login(String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "qr";
        if (action.equals("qr") || action.equals("new")) {
            complete(music().requestQrLogin(), login -> {
                if (login == null) { message("二维码登录请求失败。"); return; }
                message("请使用网易云扫描: " + login.url());
                plain("二维码文件: " + music().qrImage().toAbsolutePath());
            });
            return;
        }
        if (action.equals("check")) {
            complete(music().checkQrLogin(), MusicCommand::printQrStatus);
            return;
        }
        if (action.equals("sms")) {
            require(args, 3);
            switch (args[2].toLowerCase(Locale.ROOT)) {
                case "send" -> {
                    require(args, 4);
                    String countryCode = args.length > 4 ? args[4] : "86";
                    complete(music().requestSmsLogin(args[3], countryCode), status -> {
                        if (status.successful()) message("短信验证码已发送。");
                        else message(apiFailure("验证码发送失败", status.code(), status.message()));
                    });
                }
                case "verify", "login" -> {
                    require(args, 4);
                    complete(music().completeSmsLogin(args[3]), status -> {
                        if (status.successful()) message("手机号登录成功。");
                        else message(apiFailure("手机号登录失败", status.code(), status.message()));
                    });
                }
                default -> throw new IllegalArgumentException("用法: .music login sms send <手机号> [国家码] | verify <验证码>");
            }
            return;
        }
        throw new IllegalArgumentException("用法: .music login qr|check|sms");
    }
    private void liked(String[] args) {
        int limit = args.length > 1 ? Math.max(1, Math.min(100, integer(args[1], "数量"))) : 30;
        complete(music().likedSongs(limit), MusicCommand::printSongs);
    }
    private void like(String[] args) {
        long musicId = args.length > 2 ? id(args[2]) : currentId();
        boolean liked = args.length <= 1 || switch (args[1].toLowerCase(Locale.ROOT)) {
            case "on", "yes", "true", "add" -> true;
            case "off", "no", "false", "remove" -> false;
            case "toggle" -> !music().likedIds().contains(musicId);
            default -> throw new IllegalArgumentException("用法: .music like on|off|toggle [歌曲ID]");
        };
        complete(music().like(musicId, liked), changed -> message(changed ? (liked ? "已喜欢。" : "已取消喜欢。") : "操作失败。"));
    }
    private void editPlaylist(String[] args, boolean add) {
        require(args, 2);
        long playlist = id(args[1]);
        long song = args.length > 2 ? id(args[2]) : currentId();
        complete(music().editPlaylist(playlist, song, add), changed -> message(changed ? "歌单已更新。" : "歌单更新失败。"));
    }
    private void daily(String[] args) {
        String type = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "songs";
        if (type.startsWith("p")) complete(music().dailyRecommendedPlaylists(), MusicCommand::printPlaylists);
        else complete(music().dailyRecommendedSongs(), MusicCommand::printSongs);
    }
    private void intelligence(String[] args) {
        require(args, 2);
        long playlistId = id(args[1]);
        long songId = args.length > 2 ? id(args[2]) : currentId();
        int count = args.length > 3 ? Math.max(1, Math.min(100, integer(args[3], "数量"))) : 30;
        message("正在生成心动模式队列...");
        complete(music().playIntelligent(songId, playlistId, count), songs -> {
            if (songs.isEmpty()) message("没有获得智能推荐歌曲。");
            else message("心动模式已开始，共 " + songs.size() + " 首。");
        });
    }
    private void prefetch(String[] args) {
        if (args.length == 1) { message("预下载: " + (music().config().prefetch() ? "开启" : "关闭")); return; }
        boolean enabled = booleanValue(args[1]);
        music().prefetch(enabled);
        message("预下载: " + (enabled ? "开启" : "关闭"));
    }
    private void cache(String[] args) {
        String action = args.length > 1 ? args[1].toLowerCase(Locale.ROOT) : "status";
        switch (action) {
            case "status", "size" -> message("音乐缓存: " + bytes(music().cacheSizeBytes()) + " / " + bytes(music().config().cacheLimitBytes()));
            case "clear" -> complete(music().clearCache(), freed -> message("已释放 " + bytes(freed) + "。"));
            case "trim" -> complete(music().trimCache(), freed -> message("已整理缓存，释放 " + bytes(freed) + "。"));
            case "limit" -> {
                require(args, 3);
                long bytes = (long) (decimal(args[2], "GB") * 1024 * 1024 * 1024L);
                music().cacheLimitBytes(bytes);
                message("缓存上限: " + bytes(music().config().cacheLimitBytes()));
            }
            default -> throw new IllegalArgumentException("用法: .music cache status|clear|trim|limit <GB>");
        }
    }
    private void bind(String[] args) {
        require(args, 2);
        MusicConfig.Hotkey action = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "prev", "previous" -> MusicConfig.Hotkey.PREVIOUS;
            case "next" -> MusicConfig.Hotkey.NEXT;
            case "pause", "toggle" -> MusicConfig.Hotkey.PAUSE;
            default -> throw new IllegalArgumentException("操作应为 prev、next 或 pause。");
        };
        if (args.length == 2) {
            message(action + " 组合键: " + hotkeyText(music().config().hotkey(action)));
            return;
        }
        if (args[2].equalsIgnoreCase("none") || args[2].equalsIgnoreCase("clear")) {
            music().hotkey(action);
            message(action + " 组合键已清除。");
            return;
        }
        String[] names = args[2].split("\\+");
        if (names.length > 3) throw new IllegalArgumentException("组合键最多包含三个按键。");
        int[] keys = new int[names.length];
        for (int index = 0; index < names.length; index++) {
            var key = Keyboard.findKeyIndex(names[index]);
            if (key.isEmpty() || key.getAsInt() == 0) throw new IllegalArgumentException("未知按键: " + names[index]);
            keys[index] = key.getAsInt();
        }
        music().hotkey(action, keys);
        message(action + " 组合键: " + hotkeyText(keys));
    }
    private void status() {
        PlaybackState state = music().state();
        if (state.music() == null) {
            message("播放器空闲，音质 " + music().config().quality().apiValue() + "，缓存 " + bytes(music().cacheSizeBytes()) + "。");
            return;
        }
        message(state.phase() + ": " + state.music() + " [" + duration(state.progressSeconds()) + "/"
                + duration(state.durationSeconds()) + "] 音量 " + percent(state.volume()) + " 倍速 " + format(state.rate()) + "x");
        if (!state.error().isBlank()) plain("错误: " + state.error());
        if (state.transfer().stage() != cc.nimbus.features.music.resource.TransferStage.IDLE) {
            plain("资源: " + state.transfer().stage() + (state.transfer().error().isBlank() ? "" : " - " + state.transfer().error()));
        }
    }

    private static void printSongs(List<Music> songs) {
        if (songs.isEmpty()) { message("没有结果。"); return; }
        for (int index = 0; index < songs.size(); index++) plain((index + 1) + ". " + songs.get(index));
    }
    private static void printPlaylists(List<Playlist> playlists) {
        if (playlists.isEmpty()) { message("没有结果。"); return; }
        for (int index = 0; index < playlists.size(); index++) plain((index + 1) + ". " + playlists.get(index));
    }
    private static void printQrStatus(NeteaseApi.QrStatus status) {
        String text = switch (status.code()) {
            case 800 -> "二维码已过期，请重新生成。";
            case 801 -> "等待扫码。";
            case 802 -> "已扫码，等待手机确认。";
            case 803 -> "登录成功。";
            default -> "未知登录状态: " + status.code();
        };
        message(text);
    }
    private static <T> void complete(CompletableFuture<T> future, Consumer<T> success) {
        future.whenComplete((value, error) -> {
            if (error != null) message("操作失败: " + rootMessage(error));
            else success.accept(value);
        });
    }
    private long currentId() {
        Music current = music().state().music();
        if (current == null) throw new IllegalArgumentException("当前没有播放歌曲，请提供歌曲 ID。");
        return current.id();
    }
    private static void selected(String action, Music song) { message(song == null ? "播放队列为空。" : action + ": " + song); }
    private static MusicManager music() { return Core.INSTANCE.getMusicManager(); }
    private static void require(String[] args, int count) { if (args.length < count) throw new IllegalArgumentException("参数不足，请使用 .help music 查看用法。"); }
    private static long id(String value) { try { long id = Long.parseLong(value); if (id > 0) return id; } catch (NumberFormatException ignored) {} throw new IllegalArgumentException("无效 ID: " + value); }
    private static int integer(String value, String name) { try { return Integer.parseInt(value); } catch (NumberFormatException ignored) { throw new IllegalArgumentException("无效" + name + ": " + value); } }
    private static double decimal(String value, String name) { try { return Double.parseDouble(value); } catch (NumberFormatException ignored) { throw new IllegalArgumentException("无效" + name + ": " + value); } }
    private static boolean booleanValue(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1" -> true;
            case "off", "false", "no", "0" -> false;
            default -> throw new IllegalArgumentException("值应为 on 或 off。");
        };
    }
    private static String join(String[] values, int start) { return String.join(" ", java.util.Arrays.copyOfRange(values, start, values.length)); }
    private static String percent(float value) { return Math.round(value * 100f) + "%"; }
    private static String format(float value) { return String.format(Locale.ROOT, "%.2f", value); }
    private static String duration(float seconds) { int value = Math.max(0, Math.round(seconds)); return value / 60 + ":" + String.format("%02d", value % 60); }
    private static String bytes(long value) {
        double amount = Math.max(0L, value);
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        int unit = 0;
        while (amount >= 1024d && unit < units.length - 1) { amount /= 1024d; unit++; }
        return String.format(Locale.ROOT, amount >= 100 || unit == 0 ? "%.0f %s" : "%.1f %s", amount, units[unit]);
    }
    private static String hotkeyText(int[] keys) {
        if (keys.length == 0) return "NONE";
        return java.util.Arrays.stream(keys).mapToObj(Keyboard::getKeyName).collect(java.util.stream.Collectors.joining("+"));
    }
    private static String rootMessage(Throwable error) {
        Throwable value = error;
        while (value.getCause() != null) value = value.getCause();
        return value.getMessage() == null ? value.getClass().getSimpleName() : value.getMessage();
    }
    private static String apiFailure(String action, int code, String detail) {
        return action + "（" + code + "）" + (detail == null || detail.isBlank() ? "。" : ": " + detail);
    }
    private static List<String> qualityNames() { return java.util.Arrays.stream(Quality.values()).map(Quality::apiValue).toList(); }
    private static void message(String value) { MessageUtils.addMessage(value); }
    private static void plain(String value) { MessageUtils.addMessageWithoutPrefix(value); }
    private static void usage() { message("用法: .music status|search|play|playlist|daily|intelligence|login|lyrics|queue|cache ..."); }

    @Override public String getUsage() { return ".music <status|search|play|playlist|controls|login|account|lyrics|cache|settings>"; }
    @Override public List<String> suggest(String[] args) {
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        if (args.length <= 1) return ACTIONS.stream().filter(value -> value.startsWith(prefix)).toList();
        return switch (args[0].toLowerCase(Locale.ROOT)) {
            case "mode" -> List.of("single", "loop", "shuffle").stream().filter(value -> value.startsWith(prefix)).toList();
            case "quality" -> qualityNames().stream().filter(value -> value.startsWith(prefix)).toList();
            case "login" -> loginSuggestions(args, prefix);
            case "prefetch" -> List.of("on", "off").stream().filter(value -> value.startsWith(prefix)).toList();
            case "cache" -> List.of("status", "clear", "trim", "limit").stream().filter(value -> value.startsWith(prefix)).toList();
            case "daily", "recommend" -> List.of("songs", "playlists").stream().filter(value -> value.startsWith(prefix)).toList();
            case "like" -> List.of("on", "off", "toggle").stream().filter(value -> value.startsWith(prefix)).toList();
            case "bind" -> args.length <= 2
                    ? List.of("prev", "next", "pause").stream().filter(value -> value.startsWith(prefix)).toList()
                    : List.of("none").stream().filter(value -> value.startsWith(prefix)).toList();
            default -> List.of();
        };
    }
    private static List<String> loginSuggestions(String[] args, String prefix) {
        List<String> values = args.length <= 2
                ? List.of("qr", "check", "sms")
                : args[1].equalsIgnoreCase("sms") && args.length <= 3 ? List.of("send", "verify") : List.of();
        return values.stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
