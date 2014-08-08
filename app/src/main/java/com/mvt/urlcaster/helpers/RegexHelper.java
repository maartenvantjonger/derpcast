package com.mvt.urlcaster.helpers;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RegexHelper {

    public static List<String> getMatches(String pattern, String text) {
        List<String> matches = new ArrayList<String>();
        Matcher matcher = Pattern
                .compile(pattern, Pattern.CASE_INSENSITIVE)
                .matcher(text);

        while (matcher.find()) {
            matches.add(matcher.group(1));
        }

        return matches;
    }

    public static String getFirstMatch(String pattern, String text) {
        String match = null;

        List<String> matches = getMatches(pattern, text);
        if (matches.size() > 0) {
            match = matches.get(0);
        }

        return match;
    }
}
