package com.gathin.tvhome;

import android.content.Context;

/**
 * Which scene falls across an idle Home (SeasonView): the weather outside when it rains or snows (Weather's reading),
 * else the season, as the northern hemisphere has them: snow in winter, blossom in spring, fireflies in summer,
 * leaves in autumn. On by default with motion on; a row in the launcher's settings switches it off.
 */
final class Season {

    /** NONE: nothing falls (a holiday's own day, HolidayScene). */
    enum Scene { SNOW, RAIN, STORM, LEAVES, BLOSSOM, FIREFLIES, NONE }

    /** The settings key (Config.SETTINGS_NAME). */
    static final String KEY = "season_scene";

    private Season() { }

    /** The scene for this month (1-12) and WMO weather code (-1: none known). */
    static Scene pick(int month, int code) {
        if (snow(code)) return Scene.SNOW;
        if (code >= 95 && code <= 99) return Scene.STORM;
        if (rain(code)) return Scene.RAIN;
        if (month == 12 || month <= 2) return Scene.SNOW;
        if (month <= 5) return Scene.BLOSSOM;
        if (month <= 8) return Scene.FIREFLIES;
        return Scene.LEAVES;
    }

    /** Falling outside right now, so the scene comes thicker than a season's gentle one. */
    static boolean heavy(int code) {
        return snow(code) || rain(code) || (code >= 95 && code <= 99);
    }

    /** How many pieces fall at once. */
    static int count(Scene scene, boolean heavy) {
        switch (scene) {
            case SNOW: return heavy ? 90 : 45;
            case RAIN: return heavy ? 110 : 70;
            case STORM: return 130;
            case LEAVES: return 20;
            case BLOSSOM: return 32;
            case NONE: return 0;
            default: return 26;   // fireflies
        }
    }

    /** The weather code from a Weather reading ("code|temperature"), or -1. */
    static int code(String reading) {
        int bar = reading == null ? -1 : reading.indexOf('|');
        if (bar <= 0) return -1;
        try {
            return Integer.parseInt(reading.substring(0, bar));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    static boolean enabled(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE).getBoolean(KEY, true);
    }

    /** It snows outside. */
    static boolean snow(int code) {
        return (code >= 71 && code <= 77) || code == 85 || code == 86;
    }

    private static boolean rain(int code) {
        return (code >= 51 && code <= 67) || (code >= 80 && code <= 82);
    }
}
