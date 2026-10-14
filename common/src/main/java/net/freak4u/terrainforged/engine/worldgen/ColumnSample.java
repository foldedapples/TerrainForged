package net.freak4u.terrainforged.engine.worldgen;

/**
 * Everything the Minecraft side needs about one (x, z) column. Values are final after
 * {@link TerrainEngine#sample}; instances are cached and must be treated as read-only by callers.
 */
public final class ColumnSample {
    public int x, z;
    /** terrain surface height in blocks (solid at and below); rivers, lakes and oceans are carved below sea level */
    public double height;
    /** RTF height in [0, 1] of the terrain scale */
    public float heightRaw;
    /** RTF slope measure (0 flat .. ~1 cliff) and sediment deposited by droplet erosion */
    public float gradient, sediment;
    /** RTF river proximity: 1 far from rivers, 0 in the channel */
    public float riverMask;
    /** {@code RtfTerrain.FLAG_*} bits (river, lake, wetland, volcano, beach, ocean) */
    public byte flags;
    // vanilla multi-noise climate coordinates (vanilla parameter space), from RTF's cell fields
    public double vContinentalness, vErosion, vWeirdness, vTemperature, vHumidity;

    public boolean is(byte flag) {
        return (flags & flag) != 0;
    }
}
