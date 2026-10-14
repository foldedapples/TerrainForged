// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// TerrainForged changes: the Whittaker lookup image (biomes.png) is precomputed into an RLE table with RTF's own
// nearest-colour rule (BiomeTypeLoader), so no java.awt/ImageIO is needed at runtime; display colours removed.
package net.freak4u.terrainforged.engine.rtf.cell.biome.type;

import java.util.ArrayList;
import java.util.List;

import net.freak4u.terrainforged.engine.rtf.biome.Humidity;
import net.freak4u.terrainforged.engine.rtf.biome.Temperature;
import net.freak4u.terrainforged.engine.rtf.noise.NoiseUtil;

public enum BiomeType {
    TROPICAL_RAINFOREST(range(Temperature.LEVEL_3, Temperature.LEVEL_3, Humidity.LEVEL_3, Humidity.LEVEL_4)),
    SAVANNA(range(Temperature.LEVEL_3, Temperature.LEVEL_3, Humidity.LEVEL_0, Humidity.LEVEL_1)),
    DESERT(range(Temperature.LEVEL_4, Temperature.LEVEL_4, Humidity.LEVEL_0, Humidity.LEVEL_4)),
    TEMPERATE_RAINFOREST(addAdditional(5, Temperature.LEVEL_2, Humidity.LEVEL_1, List.of(pair(Temperature.LEVEL_2, Humidity.LEVEL_4)))),
    TEMPERATE_FOREST(addAdditional(5, Temperature.LEVEL_2, Humidity.LEVEL_1, range(Temperature.LEVEL_2, Temperature.LEVEL_2, Humidity.LEVEL_2, Humidity.LEVEL_3))),
    GRASSLAND(range(Temperature.LEVEL_1, Temperature.LEVEL_2, Humidity.LEVEL_0, Humidity.LEVEL_1)),
    COLD_STEPPE(List.of(pair(Temperature.LEVEL_0, Humidity.LEVEL_4))),
    STEPPE(range(Temperature.LEVEL_1, Temperature.LEVEL_2, Humidity.LEVEL_1, Humidity.LEVEL_2)),
    TAIGA(range(Temperature.LEVEL_1, Temperature.LEVEL_1, Humidity.LEVEL_3, Humidity.LEVEL_4)),
    TUNDRA(range(Temperature.LEVEL_0, Temperature.LEVEL_0, Humidity.LEVEL_0, Humidity.LEVEL_3)),
    ALPINE(List.of(pair(Temperature.LEVEL_1, Humidity.LEVEL_4)));

    public static final int RESOLUTION = 256;
    public static final int MAX = 255;
    private static final BiomeType[] BIOMES = values();
    /** [moisture][temperature], generated from RTF's biomes.png */
    private static final String RLE = "j10,g1r,hs,c3l,k1,j10,g1s,hx,c3e,k2,j11,g1r,h11,c39,k3,j12,g1s,h14,c33,k4,j12,g1u,h14,c30,k5,j12,g1w,h13,c2y,k6,j13,g1w,h14,c2v,k7,j13,g1x,h14,c2t,k8,j13,g1y,h14,c2r,k9,j12,g20,h15,c2o,ka,j12,g20,h16,c2m,kb,j12,i2,g1z,h15,c2l,kc,j11,i9,g1u,h14,c2k,kd,j10,id,fe,g1e,h13,c2j,ke,j10,ig,ff,g1b,h12,c2i,kf,jz,ij,fg,g19,h11,c2h,kg,jy,il,fh,g17,h11,c2g,kh,jx,im,fl,g13,h11,c2f,ki,jx,in,fn,g10,h10,c2f,kj,jw,ip,fs,gu,h10,c2e,kk,jv,ir,fy,gn,h10,c2d,kl,ju,is,f13,gi,h11,c2b,km,jt,iu,f17,gd,h11,c2a,kn,jt,iu,f1c,g8,h12,c28,ko,js,iw,f1h,g3,h11,c27,kp,jr,iy,f1l,hz,c26,kq,jq,iz,f1o,hx,c24,kr,jq,i10,f1r,ht,c23,ks,jp,i11,f1y,hm,c22,kt,jo,i12,f24,hg,c21,ku,jn,i13,f28,hd,c1z,kv,jm,i14,f2b,hb,c1x,kw,jm,i15,f2c,h9,c1w,kx,jl,i16,f2f,h7,c1u,ky,jk,i17,f2k,h3,c1s,kz,jj,i18,f2n,c1r,k10,ji,i19,f2o,c1p,k11,ji,i19,f2p,c1n,k12,jh,i1a,f2r,c1k,k13,jg,i1b,f2t,c1h,k14,jf,i1d,f2t,b2,c1d,k15,je,i1e,f2t,b4,c1a,k16,jd,i1f,f2s,b8,c16,k17,jc,i1f,e2,f2r,b9,c14,k18,jb,i1g,e2,g1,f2p,bd,c10,k19,ja,i1g,e4,f2p,bf,cx,k1a,j9,i1g,e6,f2n,bi,cu,k1b,j8,i1g,e7,g1,f2l,bk,cs,k1c,j7,i1h,e8,f2k,bl,cr,k1d,j6,i1h,ea,f2j,bo,cn,k1e,j5,i1h,eb,b1,f2h,br,ck,k1f,j4,i1h,ed,f2g,bt,ci,k1g,j2,i1i,ee,g1,f2e,bv,cg,k1h,j1,i1i,eg,f2e,bw,ce,k1i,i1i,eh,g1,f2c,bx,cd,k1j,i1h,ej,f2c,bx,cc,k1k,i1h,ej,f2b,by,cb,k1l,i1g,ek,i1,f29,b10,c9,k1m,i1f,em,f28,b11,c8,k1n,i1e,en,f28,b11,c7,k1o,i1d,eo,g1,f26,b12,c6,k1p,i1d,eo,i1,f25,b13,c5,k1q,i1c,eq,f24,b14,c4,k1r,i1b,er,f24,b14,c3,k1s,i1a,es,f23,b14,c3,k1t,i19,eu,f21,b15,c2,k1u,i18,ev,f20,b15,c2,k1v,i17,ew,f1z,b16,c1,k1w,i17,ew,f1y,b17,k1x,i16,ex,f1y,b16,k1y,i15,ey,f1x,b16,k1z,i14,ez,f1w,b16,k20,i13,e10,f1v,b16,k21,i12,e11,f1u,b16,k22,i11,e12,f1t,b16,k23,i10,e13,g1,f1r,b16,k24,iz,e15,f1q,b16,k25,iy,e17,f1o,b16,k26,ix,e18,f1n,b16,k27,iw,e1a,f1l,b16,k28,iv,e1c,f1j,b16,k29,iu,e1d,i1,f1h,b16,k2a,it,e1h,f1e,b16,k2b,is,e1j,f1c,b16,k2c,ir,e1l,f1a,b16,k2d,iq,e1n,f19,b15,k2e,ip,e1p,f17,b15,k2f,io,e1s,f14,b15,k2g,in,e1u,f12,b15,k2h,im,e1x,fz,b15,k2i,il,e1z,fx,b15,k2j,ik,e21,fv,b15,k2k,ij,e25,fr,b15,k2l,ii,e28,fo,b15,k2m,ih,e2a,fm,b15,k2n,ig,e2d,fj,b15,k2o,if,e2g,fg,b15,k2p,ie,e2i,fd,b16,k2q,id,e2l,fa,b16,k2r,ic,e2q,f5,b16,k2s,ib,e2u,f1,b16,k2t,ia,e2v,b16,k2u,i9,e2v,b16,k2v,i8,e2v,b16,k2w,i8,e2u,b16,k2x,i7,e2u,b16,k2y,i6,e2u,b16,k2z,i5,e2t,b17,k30,i4,e2t,b17,k31,i3,e2t,b17,k32,i2,e2t,b17,k33,i1,e2s,b18,k34,e2s,b18,k35,e2q,b19,k36,e2p,b19,k37,e2n,b1a,k38,e2m,b1a,k39,e2l,b1a,k3a,e2k,b1a,k3b,e2j,b1a,k3c,e2i,b1a,k3d,e2g,b1b,k3e,e2f,b1b,k3f,e2e,b1b,k3g,e2d,b1b,k3h,e2b,b1c,k3i,e2a,b1c,k3j,e28,b1d,k3k,e27,b1d,k3l,e26,b1d,k3m,e25,b1d,k3n,e23,b1e,k3o,e22,b1e,k3p,e21,b1e,k3q,e20,b1e,k3r,e1z,b1e,k3s,e1y,b1e,k3t,e1w,b1f,k3u,e1v,b1f,k3v,e1u,b1f,k3w,d1,e1s,b1f,k3x,d2,e1q,b1f,k3y,d3,e1o,b1f,k3z,d4,e1l,b1g,k40,d5,e1j,b1g,k41,d7,e1g,b1g,k42,d9,e1d,b1g,k43,db,e1a,b1g,k44,dd,e17,b1g,k45,de,e15,b1g,k46,dh,e10,b1h,k47,dj,ex,b1h,k48,dn,es,b1h,k49,dr,en,b1h,k4a,du,ej,b1h,k4b,d11,ea,b1i,k4c,d17,e3,b1i,k4d,d19,ac,b16,k4e,d18,ai,b10,k4f,d17,aq,bs,k4g,d16,au,bo,k4h,d15,aw,bm,k4i,d14,a12,bg,k4j,d13,a15,bd,k4k,d12,a17,bb,k4l,d11,a19,b9,k4m,d10,a1c,b6,k4n,dz,a1e,b4,k4o,dz,a1e,b3,k4p,dy,a1g,b1,k4q,dx,a1h,k4r,dw,a1h,k4s,dv,a1h,k4t,du,a1h,k4u,dt,a1h,k4v,ds,a1h,k4w,dr,a1h,k4x,dr,a1g,k4y,dq,a1g,k4z,dp,a1g,k50,do,a1g,k51,dn,a1g,k52,dm,a1g,k53,dm,a1f,k54,dl,a1f,k55,dk,a1f,k56,dj,a1f,k57,di,a1f,k58,dh,a1f,k59,dh,a1e,k5a,dg,a1e,k5b,df,a1e,k5c,de,a1e,k5d,dd,a1e,k5e,dd,a1d,k5f,dc,a1d,k5g,db,a1d,k5h,da,a1d,k5i,da,a1c,k5j,d9,a1c,k5k,d8,a1c,k5l,d7,a1c,k5m,d7,a1b,k5n,d6,a1b,k5o,d5,a1b,k5p,d4,a1b,k5q,d4,a1a,k5r,d3,a1a,k5s,d2,a1a,k5t,d1,a1a,k5u,d1,a19,k5v,a19,k5w,a18,k5x,a17,k5y,a16,k5z,a15,k60,a14,k61,a13,k62,a12,k63,a11,k64,a10,k65,az,k66,ay,k67,ax,k68,aw,k69,av,k6a,au,k6b,at,k6c,as,k6d,ar,k6e,aq,k6f,ap,k6g,ao,k6h,an,k6i,am,k6j,al,k6k,ak,k6l,aj,k6m,ai,k6n,ah,k6o,ag,k6p,af,k6q,ae,k6r,ad,k6s,ac,k6t,ab,k6u,aa,k6v,a9,k6w,a8,k6x,a7,k6y,a6,k6z,a5,k70,a4,k71,a3,k72,a2,k73,a1";
    private static final BiomeType[][] MAP = decode();

    private record Pair(Temperature temperature, Humidity humidity) {}

    private final List<Pair> noisePairs;
    private float minTemp, maxTemp, minMoist, maxMoist;

    BiomeType(List<Pair> noisePairs) {
        this.noisePairs = List.copyOf(noisePairs);
    }

    public float getTemperature(float identity) {
        return this.getRandomPair(identity).temperature().mid();
    }

    public float getMoisture(float identity) {
        return this.getRandomPair(identity).humidity().mid();
    }

    private Pair getRandomPair(float identity) {
        int maxIndex = this.noisePairs.size() - 1;
        return this.noisePairs.get(NoiseUtil.round(identity * maxIndex));
    }

    public float mapTemperature(float value) {
        return (value - this.minTemp) / (this.maxTemp - this.minTemp);
    }

    public float mapMoisture(float value) {
        return (value - this.minMoist) / (this.maxMoist - this.minMoist);
    }

    public static BiomeType get(int id) {
        return BIOMES[id];
    }

    public static BiomeType get(float temperature, float moisture) {
        return getCurve(temperature, moisture);
    }

    public static BiomeType getLinear(float temperature, float moisture) {
        int x = NoiseUtil.round(255.0F * temperature);
        int y = moisture > temperature ? x : NoiseUtil.round(255.0F * moisture);
        return MAP[y][x];
    }

    public static BiomeType getCurve(float temperature, float moisture) {
        int x = NoiseUtil.round(255.0F * temperature);
        int max = x + (255 - x) / 2;
        int y = Math.min(x, NoiseUtil.round(max * moisture));
        return MAP[y][x];
    }

    private static List<Pair> range(Temperature t0, Temperature t1, Humidity h0, Humidity h1) {
        List<Pair> set = new ArrayList<>();
        Temperature[] temperatures = Temperature.values();
        Humidity[] humidities = Humidity.values();
        for (int i = t0.ordinal(); i <= t1.ordinal(); i++) {
            for (int j = h0.ordinal(); j <= h1.ordinal(); j++) {
                set.add(new Pair(temperatures[i], humidities[j]));
            }
        }
        return set;
    }

    private static Pair pair(Temperature t, Humidity h) {
        return new Pair(t, h);
    }

    private static List<Pair> addAdditional(int count, Temperature t, Humidity h, List<Pair> noisePairs) {
        List<Pair> out = new ArrayList<>(noisePairs);
        for (int i = 0; i < count; i++) out.add(new Pair(t, h));
        return out;
    }

    private static BiomeType[][] decode() {
        BiomeType[] values = values();
        BiomeType[][] map = new BiomeType[256][256];
        int i = 0;
        for (String run : RLE.split(",")) {
            BiomeType t = values[run.charAt(0) - 'a'];
            int n = Integer.parseInt(run.substring(1), 36);
            for (int k = 0; k < n; k++, i++) map[i >> 8][i & 255] = t;
        }
        if (i != 65536) throw new IllegalStateException("biome type table has " + i + " entries");
        for (BiomeType type : values) {
            float minT = 1, maxT = 0, minM = 1, maxM = 0;
            for (int m = 0; m < 256; m++) {
                for (int t = 0; t < 256; t++) {
                    if (map[m][t] != type) continue;
                    float temperature = t / 255.0F, moisture = m / 255.0F;
                    minT = Math.min(minT, temperature);
                    maxT = Math.max(maxT, temperature);
                    minM = Math.min(minM, moisture);
                    maxM = Math.max(maxM, moisture);
                }
            }
            type.minTemp = minT;
            type.maxTemp = maxT;
            type.minMoist = minM;
            type.maxMoist = maxM;
        }
        return map;
    }
}
