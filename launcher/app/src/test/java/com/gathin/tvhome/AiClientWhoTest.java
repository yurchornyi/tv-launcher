package com.gathin.tvhome;

import org.junit.Test;
import java.util.Map;
import static org.junit.Assert.*;

/** Who asks the AI (AiClient.Who): the account only as this TV's sign-in, in its header; profile and TV in the body. */
public class AiClientWhoTest {
    private static final String TOKEN = "0123456789abcdef0123456789abcdef0123456789abcdef";
    private static final String TV = "3f2c9d1e-0a1b-4c2d-8e3f-0123456789ab";

    @SuppressWarnings("unchecked")
    private static Map<String, String> part(AiClient.Who who, String name) {
        return (Map<String, String>) who.fields().get(name);
    }

    @Test public void signedInTheTokenGoesOnlyInTheHeader() {
        AiClient.Who who = new AiClient.Who(TOKEN, "u0", AiClient.Who.MAIN_PROFILE, TV, "Xiaomi Mi TV 4A");
        assertEquals("Bearer " + TOKEN, who.authorization());
        assertEquals("u0", part(who, "profile").get("id"));
        assertEquals("основний", part(who, "profile").get("name"));
        assertEquals(TV, part(who, "device").get("id"));
        assertEquals("Xiaomi Mi TV 4A", part(who, "device").get("name"));
        assertFalse(who.fields().toString().contains(TOKEN));
        assertEquals(2, who.fields().size());
    }

    @Test public void signedOutNothingNamesAnAccount() {
        AiClient.Who who = new AiClient.Who("", "u10", "10", TV, "TCL 43P615");
        assertNull(who.authorization());
        assertEquals("u10", part(who, "profile").get("id"));
        assertEquals(TV, part(who, "device").get("id"));
        assertNull(new AiClient.Who(null, "u0", "основний", TV, "TV").authorization());
    }

    @Test public void missingPartsAreEmptyAndLongOnesShort() {
        String longName = new String(new char[100]).replace('\0', 'Ж');
        AiClient.Who who = new AiClient.Who(" " + TOKEN + "\n", null, null, null, "  " + longName + " ");
        assertEquals("Bearer " + TOKEN, who.authorization());
        assertEquals("", part(who, "profile").get("id"));
        assertEquals("", part(who, "profile").get("name"));
        assertEquals("", part(who, "device").get("id"));
        assertEquals(64, part(who, "device").get("name").length());
    }
}
