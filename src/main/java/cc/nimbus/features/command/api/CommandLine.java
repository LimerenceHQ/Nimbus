package cc.nimbus.features.command.api;

import java.util.ArrayList;
import java.util.List;

public final class CommandLine {
    private CommandLine() {}

    public static List<String> parse(String input) {
        if (input == null || input.isBlank()) return List.of();
        List<String> tokens = new ArrayList<>();
        StringBuilder token = new StringBuilder();
        char quote = 0;
        boolean escaped = false;
        boolean started = false;
        for (int index = 0; index < input.length(); index++) {
            char current = input.charAt(index);
            if (escaped) {
                token.append(current);
                escaped = false;
                started = true;
                continue;
            }
            if (current == '\\') {
                escaped = true;
                started = true;
                continue;
            }
            if (quote != 0) {
                if (current == quote) quote = 0;
                else token.append(current);
                started = true;
                continue;
            }
            if (current == '"' || current == '\'') {
                quote = current;
                started = true;
            } else if (Character.isWhitespace(current)) {
                if (started) {
                    tokens.add(token.toString());
                    token.setLength(0);
                    started = false;
                }
            } else {
                token.append(current);
                started = true;
            }
        }
        if (escaped) token.append('\\');
        if (quote != 0) throw new IllegalArgumentException("Unclosed quote");
        if (started) tokens.add(token.toString());
        return List.copyOf(tokens);
    }

    public static int distance(String left, String right) {
        String a = left == null ? "" : left.toLowerCase();
        String b = right == null ? "" : right.toLowerCase();
        int[] previous = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) previous[j] = j;
        int[] previousPrevious = null;
        for (int i = 1; i <= a.length(); i++) {
            int[] current = new int[b.length() + 1];
            current[0] = i;
            for (int j = 1; j <= b.length(); j++) {
                int substitution = previous[j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1);
                current[j] = Math.min(Math.min(previous[j] + 1, current[j - 1] + 1), substitution);
                if (previousPrevious != null && j > 1
                        && a.charAt(i - 1) == b.charAt(j - 2)
                        && a.charAt(i - 2) == b.charAt(j - 1)) {
                    current[j] = Math.min(current[j], previousPrevious[j - 2] + 1);
                }
            }
            previousPrevious = previous;
            previous = current;
        }
        return previous[b.length()];
    }
}
