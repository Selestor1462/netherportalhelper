package de.alek.netherportalhelper.hud;

public final class CompassSmoother {
    private boolean initialized;
    private float value;

    public void reset() {
        initialized = false;
        value = 0.0f;
    }

    public float update(float target, float smoothing, double elapsedSeconds) {
        if (!initialized) {
            initialized = true;
            return value = wrap(target);
        }
        float strength = Math.max(0.0f, Math.min(1.0f, smoothing));
        if (strength == 0.0f) return value = wrap(target);
        // 0 is direct; 1 is the softest response. The default keeps its previous
        // approximate response time, independently of the rendering frame rate.
        float alpha = (float) -Math.expm1(-Math.max(0.0, elapsedSeconds) / (0.18 * strength));
        value = wrap(value + wrap(target - value) * alpha);
        return value;
    }

    private static float wrap(float degrees) {
        float wrapped = degrees % 360.0f;
        if (wrapped >= 180.0f) wrapped -= 360.0f;
        if (wrapped < -180.0f) wrapped += 360.0f;
        return wrapped;
    }
}
