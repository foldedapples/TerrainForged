// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.cell.rivermap.gen;

import net.freak4u.terrainforged.engine.rtf.noise.domain.Domain;
import net.freak4u.terrainforged.engine.rtf.noise.domain.Domains;

public record GenWarp(Domain lake, Domain river) {
    public static final GenWarp EMPTY = new GenWarp(Domains.direct(), Domains.direct());
    
    public static GenWarp make(int seed, int continentScale) {
        Domain lake = Domains.domainPerlin(++seed, 200, 1, 300.0F);
        lake = Domains.add(lake, Domains.domainPerlin(++seed, 50, 2, 50.0F));

        Domain river = Domains.domainPerlin(++seed, 95, 1, 25.0F);
        river = Domains.add(river, Domains.domainPerlin(++seed, 16, 1, 5.0F));
        return new GenWarp(lake, river);
    }
}
