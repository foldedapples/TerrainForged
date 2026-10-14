// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.cell.heightmap;

import net.freak4u.terrainforged.engine.rtf.settings.Presets;
import net.freak4u.terrainforged.engine.rtf.settings.WorldSettings;

@Deprecated
public record ControlPoints(float mushroomFieldsInland, float mushroomFieldsCoast, float deepOcean, float shallowOcean, float beach, float coast, float coastMarker, float inland) {
    
    public static ControlPoints make(WorldSettings.ControlPoints points) {
        if (!validate(points)) {
            points = Presets.makeLegacyDefault().world().controlPoints;
        }
        return new ControlPoints(points.mushroomFieldsInland, points.mushroomFieldsCoast, points.deepOcean, points.shallowOcean, points.beach, points.coast, points.coast + (points.inland - points.coast) / 2.0F, points.inland);
   }
    
    public static boolean validate(WorldSettings.ControlPoints points) {
        return points.inland <= 1.0F && points.inland > points.coast && points.coast > points.beach && points.beach > points.shallowOcean && points.shallowOcean > points.deepOcean && points.mushroomFieldsCoast >= points.mushroomFieldsInland && points.mushroomFieldsInland >= 0.0F;
    }
}
