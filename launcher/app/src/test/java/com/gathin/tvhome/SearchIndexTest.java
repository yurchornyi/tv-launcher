package com.gathin.tvhome;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** What a spoken name opens without the AI (SearchIndex.exact). */
public class SearchIndexTest {

    private static SearchIndex.Result result(String title, int rank) {
        return new SearchIndex.Result(title, "", null, null, false, rank, () -> { });
    }

    private final SearchIndex.Result youtube = result("YouTube", 0);
    private final SearchIndex.Result oneplusone = result("1+1 Україна", 1);
    private final SearchIndex.Result film = result("Дюна", 2);
    private final List<SearchIndex.Result> all = Arrays.asList(youtube, oneplusone, film, result("ICTV", 1),
            result("ICTV", 1));

    @Test
    public void anAppOrChannelSaidByItsNameIsFound() {
        assertSame(youtube, SearchIndex.exact(all, "youtube"));
        assertSame(youtube, SearchIndex.exact(all, " YouTube. "));
        assertSame(oneplusone, SearchIndex.exact(all, "1+1 україна"));
    }

    @Test
    public void anythingElseIsLeftToTheAi() {
        assertNull(SearchIndex.exact(all, "увімкни youtube"));
        assertNull(SearchIndex.exact(all, "you"));
        assertNull(SearchIndex.exact(all, "дюна"));   // a film: the AI picks how to open it
        assertNull(SearchIndex.exact(all, "ictv"));   // two of that name
        assertNull(SearchIndex.exact(all, " . "));
    }
}
