// Ported from ReTerraForged 1.20.1 (https://github.com/racoonman2/reterraforged, branch 1.20.1 @ 29239f1, "RTF 0.0.6"),
// MIT License, Copyright (c) 2023 ReTerraForged, itself derived from TerraForged (MIT, Copyright (c) 2020 TerraForged).
// TerrainForged changes: array pools removed; the per-chunk cell stage runs in parallel on a small shared pool (RTF
// batches it on its world-gen pool the same way), the filters run on the calling thread. The per-cell pipeline is
// unchanged and the result does not depend on the thread count (each cell depends only on its own position).
package net.freak4u.terrainforged.engine.rtf.tile.generation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.atomic.AtomicInteger;

import net.freak4u.terrainforged.engine.rtf.WorldFilters;
import net.freak4u.terrainforged.engine.rtf.cell.Cell;
import net.freak4u.terrainforged.engine.rtf.cell.heightmap.Heightmap;
import net.freak4u.terrainforged.engine.rtf.cell.rivermap.Rivermap;
import net.freak4u.terrainforged.engine.rtf.tile.Size;
import net.freak4u.terrainforged.engine.rtf.tile.Tile;
import net.freak4u.terrainforged.engine.rtf.tile.Tile.Chunk;

public class TileGenerator {
	private static final AtomicInteger THREAD_ID = new AtomicInteger();
	/** Shared by all worlds; at least 2 workers, about half the cores so the game's own threads keep running. */
	public static final ForkJoinPool POOL = new ForkJoinPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), pool -> {
		var t = ForkJoinPool.defaultForkJoinWorkerThreadFactory.newThread(pool);
		t.setName("TerrainForged-Tile-" + THREAD_ID.incrementAndGet());
		t.setDaemon(true);
		return t;
	}, null, false);
	private static volatile boolean parallel = true;

	/** Tests only: compare the parallel and sequential paths. */
	public static void setParallel(boolean value) {
		parallel = value;
	}

	private final Heightmap heightmap;
	private final WorldFilters filters;
	private final int tileChunks;
	private final int tileBorder;
	private final Size tileSizeBlocks;
	private final Size tileSizeChunks;

	public TileGenerator(Heightmap heightmap, WorldFilters filters, int tileChunks, int tileBorder) {
		this.heightmap = heightmap;
		this.filters = filters;
		this.tileChunks = tileChunks;
		this.tileBorder = tileBorder;
		this.tileSizeBlocks = Size.blocks(tileChunks, tileBorder);
		this.tileSizeChunks = Size.chunks(tileChunks, tileBorder);
	}

	public Heightmap getHeightmap() {
		return this.heightmap;
	}

	public Size blockSize() {
		return this.tileSizeBlocks;
	}

	public Tile generate(int tileX, int tileZ) {
		Tile tile = this.makeTile(tileX, tileZ);
		int total = this.tileSizeChunks.total();
		if (parallel) {
			CompletableFuture<?>[] rows = new CompletableFuture<?>[total];
			for (int cZ = 0; cZ < total; cZ++) {
				int row = cZ;
				rows[cZ] = CompletableFuture.runAsync(() -> this.generateRow(tile, row, total), POOL);
			}
			CompletableFuture.allOf(rows).join();
		} else {
			for (int cZ = 0; cZ < total; cZ++) this.generateRow(tile, cZ, total);
		}
		this.filters.apply(tile, true);
		return tile;
	}

	private void generateRow(Tile tile, int cZ, int total) {
		for (int cX = 0; cX < total; cX++) {
			Chunk chunk;
			synchronized (tile) {
				chunk = tile.getChunkWriter(cX, cZ); // lazily creates the chunk view
			}
			Rivermap rivers = null;
			for (int dz = 0; dz < 16; dz++) {
				for (int dx = 0; dx < 16; dx++) {
					int worldX = chunk.getBlockX() + dx;
					int worldZ = chunk.getBlockZ() + dz;
					Cell cell = chunk.getCell(dx, dz);
					this.heightmap.applyTerrain(cell, worldX, worldZ);
					rivers = Rivermap.get(cell, rivers, this.heightmap);
					this.heightmap.applyRivers(cell, worldX, worldZ, rivers);
					this.heightmap.applyClimate(cell, worldX, worldZ, true);
				}
			}
		}
	}

	/** RTF's preview path: one tile whose cells are spread {@code zoom} blocks apart around a centre. */
	public Tile generateZoomed(float centerX, float centerZ, float zoom, boolean applyOptionalFilters) {
		Tile tile = this.makeTile(0, 0);
		float translateX = centerX - this.tileSizeBlocks.size() * zoom / 2.0F;
		float translateZ = centerZ - this.tileSizeBlocks.size() * zoom / 2.0F;
		int total = this.tileSizeChunks.total();
		CompletableFuture<?>[] rows = new CompletableFuture<?>[total];
		for (int cZ = 0; cZ < total; cZ++) {
			int row = cZ;
			Runnable task = () -> {
				for (int cX = 0; cX < total; cX++) {
					Chunk chunk;
					synchronized (tile) {
						chunk = tile.getChunkWriter(cX, row);
					}
					Rivermap rivers = null;
					for (int dz = 0; dz < 16; dz++) {
						for (int dx = 0; dx < 16; dx++) {
							float worldX = (chunk.getBlockX() + dx) * zoom + translateX;
							float worldZ = (chunk.getBlockZ() + dz) * zoom + translateZ;
							Cell cell = chunk.getCell(dx, dz);
							this.heightmap.applyTerrain(cell, worldX, worldZ);
							rivers = Rivermap.get(cell, rivers, this.heightmap);
							this.heightmap.applyRivers(cell, worldX, worldZ, rivers);
							this.heightmap.applyClimate(cell, worldX, worldZ, true);
						}
					}
				}
			};
			rows[cZ] = parallel ? CompletableFuture.runAsync(task, POOL) : CompletableFuture.completedFuture(null).thenRun(task);
		}
		CompletableFuture.allOf(rows).join();
		this.filters.apply(tile, applyOptionalFilters);
		return tile;
	}

	private Tile makeTile(int x, int z) {
		Cell[] cells = new Cell[this.tileSizeBlocks.arraySize()];
		for (int i = 0; i < cells.length; i++) cells[i] = new Cell();
		return new Tile(x, z, this.tileChunks, this.tileBorder, this.tileSizeBlocks, this.tileSizeChunks, cells, new Chunk[this.tileSizeChunks.arraySize()]);
	}
}
