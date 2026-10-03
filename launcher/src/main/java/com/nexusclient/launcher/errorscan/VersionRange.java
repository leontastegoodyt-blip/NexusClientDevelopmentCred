package com.nexusclient.launcher.errorscan;

import java.util.ArrayList;
import java.util.List;

/**
 * VersionRange – checks a version against the version rules mods write in fabric.mod.json, such as
 *   "*"  (any)   ">=0.5.4"   "<1.0"   ">=1.20 <1.21"   "1.20.x"   "~1.20.1"   "^2.3"
 * A list of rules means "any of these".
 *
 * Versions are compared number by number (1.20.10 is newer than 1.20.9); extra labels like
 * "+mc1.20.1" or "-beta" are ignored, which is close enough for spotting clashes.
 */
public final class VersionRange {

    private VersionRange() {}

    /** True if the version matches ANY of the rules (an empty list matches everything). */
    public static boolean matchesAny(String version, List<String> rules) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        for (String rule : rules) {
            if (matches(version, rule)) {
                return true;
            }
        }
        return false;
    }

    /** True if the version matches one rule (all its space-separated parts must match). */
    public static boolean matches(String version, String rule) {
        if (rule == null || rule.isBlank() || rule.trim().equals("*")) {
            return true;
        }
        for (String part : rule.trim().split("\\s+")) {
            if (!matchesPart(version, part)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesPart(String version, String part) {
        String operator = part.replaceAll("^([<>=~^]*).*$", "$1");
        String target = part.substring(operator.length());
        if (target.endsWith(".x") || target.endsWith(".*") || target.endsWith(".X")) {
            // "1.20.x" = anything starting with 1.20
            String prefix = target.substring(0, target.length() - 2);
            return version.equals(prefix) || version.startsWith(prefix + ".");
        }
        int compare = compare(version, target);
        return switch (operator) {
            case ">=" -> compare >= 0;
            case "<=" -> compare <= 0;
            case ">" -> compare > 0;
            case "<" -> compare < 0;
            case "~" -> compare >= 0 && sameStart(version, target, 2); // same major.minor
            case "^" -> compare >= 0 && sameStart(version, target, 1); // same major
            default -> compare == 0;                                    // "=" or no operator
        };
    }

    /** Compares two versions number by number: negative = a is older, 0 = same, positive = a is newer. */
    public static int compare(String a, String b) {
        List<Integer> x = numbers(a);
        List<Integer> y = numbers(b);
        for (int i = 0; i < Math.max(x.size(), y.size()); i++) {
            int left = i < x.size() ? x.get(i) : 0;
            int right = i < y.size() ? y.get(i) : 0;
            if (left != right) {
                return Integer.compare(left, right);
            }
        }
        return 0;
    }

    /** "0.5.13+mc1.20.1" → [0, 5, 13] (stops at the first label). */
    private static List<Integer> numbers(String version) {
        List<Integer> numbers = new ArrayList<>();
        String core = version.split("[+\\-\\s]")[0];
        for (String piece : core.split("\\.")) {
            try {
                numbers.add(Integer.parseInt(piece.replaceAll("\\D.*$", "")));
            } catch (NumberFormatException e) {
                break;
            }
        }
        return numbers;
    }

    private static boolean sameStart(String a, String b, int parts) {
        List<Integer> x = numbers(a);
        List<Integer> y = numbers(b);
        for (int i = 0; i < parts; i++) {
            if ((i < x.size() ? x.get(i) : 0) != (i < y.size() ? y.get(i) : 0)) {
                return false;
            }
        }
        return true;
    }
}
