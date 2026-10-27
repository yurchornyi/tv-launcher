package com.gathin.tvhome;

import android.graphics.drawable.Drawable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * What Search can find (MainActivity builds the entries: the installed apps, the live channels the launcher ships
 * lists for, the films and videos on the shelves) and how the typed words are matched against them.
 */
final class SearchIndex {

    static final class Result {
        final String title;
        final String subtitle;
        /** An icon to draw, or null to load `image`. */
        final Drawable icon;
        final String image;
        /** The picture fills the card (a banner, a backdrop); otherwise it sits inside it with room around (an icon, a logo). */
        final boolean fill;
        final Runnable open;
        /** Apps first, then channels, then films: ties in closeness break by this. */
        final int rank;

        Result(String title, String subtitle, Drawable icon, String image, boolean fill, int rank, Runnable open) {
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.image = image;
            this.fill = fill;
            this.rank = rank;
            this.open = open;
        }
    }

    private SearchIndex() { }

    /** Matches: the title starts with the words typed, else has a word that does, else contains them; up to 30. */
    static List<Result> filter(List<Result> all, String raw) {
        String q = fold(raw);
        List<Result> out = new ArrayList<>();
        if (q.isEmpty()) return out;
        List<Result> starts = new ArrayList<>(), words = new ArrayList<>(), inside = new ArrayList<>();
        for (Result r : all) {
            String t = fold(r.title);
            if (t.startsWith(q)) starts.add(r);
            else if (t.contains(" " + q)) words.add(r);
            else if (t.contains(q)) inside.add(r);
        }
        java.util.Comparator<Result> byRank = (a, b) -> Integer.compare(a.rank, b.rank);
        starts.sort(byRank);
        words.sort(byRank);
        inside.sort(byRank);
        out.addAll(starts);
        out.addAll(words);
        out.addAll(inside);
        if (out.size() > 30) out.subList(30, out.size()).clear();
        return out;
    }

    /**
     * The one app or channel named exactly by what was said ("YouTube", "1+1 Україна"), so the launcher opens it
     * without asking the AI; null when nothing is named so, or more than one thing is.
     */
    static Result exact(List<Result> all, String raw) {
        String q = bare(raw);
        if (q.isEmpty()) return null;
        Result found = null;
        for (Result r : all) {
            if (r.rank > 1 || !bare(r.title).equals(q)) continue;
            if (found != null) return null;
            found = r;
        }
        return found;
    }

    /** Folded, without the full stop or comma a recogniser may put at either end. */
    private static String bare(String s) {
        return fold(s).replaceAll("^[\\p{Punct}\\s]+|[\\p{Punct}\\s]+$", "");
    }

    /** Lower case, no apostrophes or extra spaces: "Кінопоказ" and "кінопоказ" are the same word. */
    static String fold(String s) {
        if (s == null) return "";
        return s.toLowerCase(Locale.ROOT).replace("'", "").replace("’", "").replace("ʼ", "").trim()
                .replaceAll("\\s+", " ");
    }
}
