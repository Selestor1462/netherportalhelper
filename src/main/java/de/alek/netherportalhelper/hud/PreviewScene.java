package de.alek.netherportalhelper.hud;

/** Deterministic moving examples, independent of the player's live navigation. */
public final class PreviewScene {
    private PreviewScene() { }

    public enum Scenario {
        TRAVELLING("preview_travelling"), BEHIND("preview_behind"), ARRIVED("preview_arrived");
        private final String key;
        Scenario(String key) { this.key = "config.netherportalhelper." + key; }
        public String translationKey() { return key; }
        public Scenario next() { return values()[(ordinal() + 1) % values().length]; }
    }

    public static Sample sample(Scenario scenario, double seconds) {
        return switch (scenario) {
            case TRAVELLING -> new Sample(124.0, (float) (55 + 45 * Math.sin(seconds * 2)), 8, false);
            case BEHIND -> new Sample(124.0, (float) (172 + 4 * Math.sin(seconds * 2)), 8, false);
            case ARRIVED -> new Sample(0.0, 0, 8, true);
        };
    }

    public record Sample(double distance, float rawDifference, int heightDifference, boolean horizontalArrival) { }
}
