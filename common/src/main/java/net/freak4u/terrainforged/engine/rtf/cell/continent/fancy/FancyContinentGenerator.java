// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// Minecraft/DataFixerUpper dependencies (codecs, registries) removed for TerrainForged's loader-free engine.
package net.freak4u.terrainforged.engine.rtf.cell.continent.fancy;

import net.freak4u.terrainforged.engine.rtf.settings.WorldSettings;
import net.freak4u.terrainforged.engine.rtf.GeneratorContext;
import net.freak4u.terrainforged.engine.rtf.cell.Cell;
import net.freak4u.terrainforged.engine.rtf.cell.continent.Continent;
import net.freak4u.terrainforged.engine.rtf.cell.rivermap.RiverCache;
import net.freak4u.terrainforged.engine.rtf.cell.rivermap.Rivermap;
import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;
import net.freak4u.terrainforged.engine.rtf.noise.domain.Domain;
import net.freak4u.terrainforged.engine.rtf.noise.domain.Domains;
import net.freak4u.terrainforged.engine.rtf.util.PosUtil;
import net.freak4u.terrainforged.engine.rtf.util.Seed;

public class FancyContinentGenerator implements Continent {
    private float frequency;
    private Domain warp;
    private FancyContinent source;
    private RiverCache riverCache;
    
    public FancyContinentGenerator(Seed seed, GeneratorContext context) {
        WorldSettings settings = context.preset.world();
        int warpScale = settings.continent.continentScale / 2;
        float warpStrength = warpScale * 0.4F;
        this.source = new FancyContinent(seed.next(), 4, 0.2F, context, this);
        this.frequency = 1.0F / settings.continent.continentScale;
        this.riverCache = new RiverCache(this.source);
        
        Domain warp = Domains.domainSimplex(seed.next(), warpScale, 2, warpStrength);
        warp = Domains.add(warp, Domains.domainPerlin(seed.next(), 80, 2, 40.0F)); 
        warp = Domains.add(warp, Domains.domainPerlin(seed.next(), 20, 1, 15.0F));
        this.warp = warp;
    }
    
    public FancyContinent getSource() {
        return this.source;
    }
    
    @Override
    public Rivermap getRivermap(int x, int y) {
        return this.riverCache.getRivers(x, y);
    }
    
    @Override
    public float getEdgeValue(float x, float y) {
        float px = this.warp.getX(x, y, 0);
        float py = this.warp.getZ(x, y, 0);
        px *= this.frequency;
        py *= this.frequency;
        return this.source.getEdgeValue(px, py, 0);
    }
    
    @Override
    public float getLandValue(float x, float y) {
        float px = this.warp.getX(x, y, 0);
        float py = this.warp.getZ(x, y, 0);
        px *= this.frequency;
        py *= this.frequency;
        float value = this.source.getLandValue(px, py);
        return NoiseUtil.map(value, 0.2F, 0.4F, 0.2F);
    }
    
    @Override
    public long getNearestCenter(float x, float y) {
        long min = this.source.getMin();
        long max = this.source.getMax();
        float width = PosUtil.unpackLeftf(max) - PosUtil.unpackLeftf(min);
        float height = PosUtil.unpackRightf(max) - PosUtil.unpackRightf(min);
        float cx = width * 0.5F;
        float cz = height * 0.5F;
        int centerX = (int)(cx / this.frequency);
        int centerZ = (int)(cz / this.frequency);
        return PosUtil.pack(centerX, centerZ);
    }
    
    @Override
    public void apply(Cell cell, float x, float y) {
        cell.continentX = 0;
        cell.continentZ = 0;
        cell.continentId = 0.0F;
        cell.continentEdge = this.getEdgeValue(x, y);
    }
}
