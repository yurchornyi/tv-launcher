package com.gathin.tvhome;

import android.content.Context;

import java.time.LocalDate;

/**
 * The holidays Home dresses up for (HolidayScene): decorations hang on the app tiles all day, and a character walks
 * along them when Home comes back, at most every half hour. Ukraine's calendar since 2023: Saint Nicholas on 6
 * December, Christmas on the 25th. Easter twice (owner, 13.11): the Orthodox date and the Western one, when they
 * differ. On by default with motion on; Launcher settings, Motion, «Свята» switches it off, and «Показати свята»
 * plays them all in turn.
 */
final class Holiday {

    enum Day { NICHOLAS, CHRISTMAS, NEW_YEAR, VALENTINE, EASTER, INDEPENDENCE, HALLOWEEN }

    /** The settings key (Config.SETTINGS_NAME). */
    static final String KEY = "holiday_scene";

    /** «Показати свята»: each in turn, as the year has them. */
    static final Day[] DEMO = Day.values();

    /** A character walks the tiles at most this often (the decorations stay all day). */
    static final long MOMENT_EVERY_MS = 30 * 60_000L;

    private static long lastMoment = -MOMENT_EVERY_MS;

    private Holiday() { }

    /** The holiday on this date, or null. */
    static Day on(LocalDate date) {
        int m = date.getMonthValue(), d = date.getDayOfMonth();
        if (m == 12 && d == 6) return Day.NICHOLAS;
        if (m == 12 && d >= 24 && d <= 26) return Day.CHRISTMAS;
        if ((m == 12 && d == 31) || (m == 1 && d == 1)) return Day.NEW_YEAR;
        if (m == 2 && d == 14) return Day.VALENTINE;
        if (m == 8 && d == 24) return Day.INDEPENDENCE;
        if (m == 10 && d == 31) return Day.HALLOWEEN;
        if (easter(date, orthodoxEaster(date.getYear())) || easter(date, westernEaster(date.getYear()))) {
            return Day.EASTER;
        }
        return null;
    }

    /** Easter Sunday and the Monday after it. */
    private static boolean easter(LocalDate date, LocalDate sunday) {
        return date.equals(sunday) || date.equals(sunday.plusDays(1));
    }

    /** Orthodox Easter (the Julian computus, Meeus), as a Gregorian date. */
    static LocalDate orthodoxEaster(int year) {
        int a = year % 4, b = year % 7, c = year % 19;
        int d = (19 * c + 15) % 30, e = (2 * a + 4 * b - d + 34) % 7;
        int month = (d + e + 114) / 31, day = (d + e + 114) % 31 + 1;
        int julianLag = year / 100 - year / 400 - 2;   // 13 days from 1900 to 2099
        return LocalDate.of(year, month, day).plusDays(julianLag);
    }

    /** Western Easter (the Gregorian computus, the anonymous algorithm). */
    static LocalDate westernEaster(int year) {
        int a = year % 19, b = year / 100, c = year % 100, d = b / 4, e = b % 4;
        int f = (b + 8) / 25, g = (b - f + 1) / 3, h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4, k = c % 4, l = (32 + 2 * e + 2 * i - h - k) % 7, m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31, day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }

    /**
     * What falls across Home on the day (SeasonView): snow for Nicholas and Christmas, blossom at Easter, leaves at
     * Halloween; on New Year's only if it snows outside (the salute wants the sky), else nothing falls.
     */
    static Season.Scene scene(Day day, int weatherCode) {
        switch (day) {
            case NICHOLAS:
            case CHRISTMAS:
                return Season.Scene.SNOW;
            case EASTER:
                return Season.Scene.BLOSSOM;
            case HALLOWEEN:
                return Season.Scene.LEAVES;
            case NEW_YEAR:
                return Season.snow(weatherCode) ? Season.Scene.SNOW : Season.Scene.NONE;
            default:
                return Season.Scene.NONE;
        }
    }

    /** A character's walk is due now (at most every MOMENT_EVERY_MS): it is counted as played. */
    static boolean takeMoment(long now) {
        if (!momentDue(now, lastMoment)) return false;
        lastMoment = now;
        return true;
    }

    static boolean momentDue(long now, long last) {
        return now - last >= MOMENT_EVERY_MS;
    }

    static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getBoolean(KEY, true);
    }
}
