package de.alek.netherportalhelper.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

final class LocalizationRegressionCheck {
    private static final Pattern ARGUMENT = Pattern.compile("%(?:[1-9][0-9]*\\$)?s");

    static void run() throws Exception {
        JsonObject base = load("en_us");
        assert arguments(base.get("hud.netherportalhelper.coordinates").getAsString()) == 4
                : "Coordinate renderer supplies dimension, X, Y and Z";
        for (String language : new String[]{"de_de", "es_es", "fr_fr", "pl_pl", "ru_ru", "zh_cn"}) {
            JsonObject localized = load(language);
            assert localized.keySet().equals(base.keySet()) : "Missing translation in " + language;
            for (String key : base.keySet()) {
                String text = localized.get(key).getAsString();
                assert !text.isBlank() : language + ": " + key;
                assert arguments(text) == arguments(base.get(key).getAsString())
                        : "Translation format mismatch: " + language + ": " + key;
            }
        }
    }

    private static long arguments(String value) { return ARGUMENT.matcher(value).results().count(); }

    private static JsonObject load(String language) throws Exception {
        String path = "assets/netherportalhelper/lang/" + language + ".json";
        try (var stream = LocalizationRegressionCheck.class.getClassLoader().getResourceAsStream(path)) {
            assert stream != null : path;
            return JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
        }
    }
}
