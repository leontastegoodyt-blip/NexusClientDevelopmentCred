package com.nexusclient.backend.moderation;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ChatFilter – many Minecraft players are kids, so bad words in DMs and party chat are replaced with
 * ****. The word list is chat-filter.txt (one word per line; the owner can extend it). Common tricks
 * are caught too: capitals, repeated letters ("fuuuck") and number swaps ("sh1t").
 */
@Component
public class ChatFilter {

    private final List<Pattern> patterns = new ArrayList<>();

    public ChatFilter() throws IOException {
        try (InputStream in = new ClassPathResource("chat-filter.txt").getInputStream()) {
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String word = line.trim().toLowerCase(Locale.ROOT);
                if (!word.isEmpty() && !word.startsWith("#")) {
                    patterns.add(Pattern.compile(toPattern(word), Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE));
                }
            }
        }
    }

    /** The message with bad words replaced by stars. */
    public String clean(String text) {
        String result = text;
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(result);
            StringBuilder out = new StringBuilder();
            while (matcher.find()) {
                matcher.appendReplacement(out, "*".repeat(matcher.group().length()));
            }
            matcher.appendTail(out);
            result = out.toString();
        }
        return result;
    }

    /** "shit" → a pattern also matching "SHIT", "shiiit", "sh1t", "5hit" (as a whole word). */
    private static String toPattern(String word) {
        StringBuilder regex = new StringBuilder("(?<![\\p{L}\\p{N}])");
        for (char c : word.toCharArray()) {
            String options = switch (c) {
                case 'a' -> "a4@";
                case 'e' -> "e3";
                case 'i' -> "i1!l";
                case 'o' -> "o0";
                case 's' -> "s5$";
                case 't' -> "t7";
                default -> Pattern.quote(String.valueOf(c)).replace("\\Q", "").replace("\\E", "");
            };
            regex.append('[').append(options.replace("]", "\\]").replace("-", "\\-")).append("]+");
        }
        return regex.append("(?![\\p{L}\\p{N}])").toString();
    }
}
