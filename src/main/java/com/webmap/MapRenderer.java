package com.webmap;

import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.lang.reflect.Field;
import java.util.*;

public class MapRenderer {
    
    private final WebMapPlugin plugin;
    
    // Pixels per block (higher = better quality)
    private static final int PIXELS_PER_BLOCK = 12;
    
    public MapRenderer(WebMapPlugin plugin) {
        this.plugin = plugin;
    }
    
    public BufferedImage renderLevel(Level level) {
        return renderLevelWithInfo(level).image;
    }
    
    public static class RenderResult {
        public BufferedImage image;
        public int minBlockX, maxBlockX, minBlockZ, maxBlockZ;
        
        public RenderResult(BufferedImage img, int minX, int maxX, int minZ, int maxZ) {
            this.image = img;
            this.minBlockX = minX;
            this.maxBlockX = maxX;
            this.minBlockZ = minZ;
            this.maxBlockZ = maxZ;
        }
    }
    
    public RenderResult renderRegion(Level level, int centerX, int centerZ, int radius) {
        plugin.getLogger().info("=== Rendering region: center(" + centerX + "," + centerZ + ") radius=" + radius + " ===");
        
        int minBlockX = centerX - radius;
        int maxBlockX = centerX + radius;
        int minBlockZ = centerZ - radius;
        int maxBlockZ = centerZ + radius;
        
        int minCX = minBlockX >> 4;
        int maxCX = maxBlockX >> 4;
        int minCZ = minBlockZ >> 4;
        int maxCZ = maxBlockZ >> 4;
        
        int chunksX = maxCX - minCX + 1;
        int chunksZ = maxCZ - minCZ + 1;
        
        plugin.getLogger().info("Region chunks: " + chunksX + " x " + chunksZ);
        
        int imageWidth = (radius * 2) * PIXELS_PER_BLOCK;
        int imageHeight = (radius * 2) * PIXELS_PER_BLOCK;
        
        int maxSize = 16384;
        double scale = 1.0;
        if (imageWidth > maxSize || imageHeight > maxSize) {
            scale = (double) maxSize / Math.max(imageWidth, imageHeight);
            imageWidth = (int)(imageWidth * scale);
            imageHeight = (int)(imageHeight * scale);
        }
        
        plugin.getLogger().info("Image size: " + imageWidth + "x" + imageHeight);
        
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(25, 50, 100));
        g.fillRect(0, 0, imageWidth, imageHeight);
        g.dispose();
        
        int rendered = 0;
        int total = chunksX * chunksZ;
        final double pixelsPerBlock = PIXELS_PER_BLOCK * scale;
        
        for (int cx = minCX; cx <= maxCX; cx++) {
            for (int cz = minCZ; cz <= maxCZ; cz++) {
                try {
                    level.loadChunk(cx, cz, false);
                    FullChunk chunk = level.getChunk(cx, cz);
                    
                    if (chunk != null) {
                        int chunkBlockX = cx * 16;
                        int chunkBlockZ = cz * 16;
                        
                        for (int bx = 0; bx < 16; bx++) {
                            for (int bz = 0; bz < 16; bz++) {
                                int worldX = chunkBlockX + bx;
                                int worldZ = chunkBlockZ + bz;
                                
                                if (worldX < minBlockX || worldX >= maxBlockX ||
                                    worldZ < minBlockZ || worldZ >= maxBlockZ) continue;
                                
                                int imgX = (int)((worldX - minBlockX) * pixelsPerBlock);
                                int imgZ = (int)((worldZ - minBlockZ) * pixelsPerBlock);
                                
                                if (imgX < 0 || imgX >= imageWidth || imgZ < 0 || imgZ >= imageHeight) continue;
                                
                                int height = chunk.getHighestBlockAt(bx, bz);
                                int blockId = 0, blockData = 0;
                                
                                for (int y = height; y >= 0; y--) {
                                    blockId = chunk.getBlockId(bx, y, bz);
                                    if (blockId != 0 && !isTransparent(blockId)) {
                                        blockData = chunk.getBlockData(bx, y, bz);
                                        height = y;
                                        break;
                                    }
                                }
                                
                                Color c = getBlockColor(blockId, blockData);
                                int rgb = applyShading(c, height, bx, bz, chunk).getRGB();
                                
                                int pixelSize = Math.max(1, (int)Math.ceil(pixelsPerBlock));
                                for (int px = 0; px < pixelSize && imgX + px < imageWidth; px++) {
                                    for (int pz = 0; pz < pixelSize && imgZ + pz < imageHeight; pz++) {
                                        image.setRGB(imgX + px, imgZ + pz, rgb);
                                    }
                                }
                            }
                        }
                        rendered++;
                    }
                } catch (Exception e) {}
                
                if ((rendered % 500) == 0 && rendered > 0) {
                    plugin.getLogger().info("Progress: " + rendered + "/" + total);
                }
            }
        }
        
        plugin.getLogger().info("=== Region render complete: " + rendered + " chunks ===");
        return new RenderResult(image, minBlockX, maxBlockX, minBlockZ, maxBlockZ);
    }
    
    public RenderResult renderLevelWithInfo(Level level) {
        plugin.getLogger().info("=== Starting world scan: " + level.getName() + " ===");
        
        Set<Long> allChunkKeys = new HashSet<>();
        
        try {
            Map<Long, ? extends FullChunk> memChunks = level.getChunks();
            for (Long key : memChunks.keySet()) {
                allChunkKeys.add(key);
            }
            plugin.getLogger().info("Memory chunks: " + memChunks.size());
        } catch (Exception e) {
            plugin.getLogger().warning("Memory scan error: " + e.getMessage());
        }
        
        try {
            LevelProvider provider = level.getProvider();
            if (provider != null) {
                scanProviderChunks(provider, allChunkKeys);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Provider scan error: " + e.getMessage());
        }
        
        exhaustiveScan(level, allChunkKeys);
        
        plugin.getLogger().info("Total chunks found: " + allChunkKeys.size());
        
        if (allChunkKeys.isEmpty()) {
            int cx = level.getSpawnLocation().getFloorX() >> 4;
            int cz = level.getSpawnLocation().getFloorZ() >> 4;
            for (int dx = -4; dx <= 4; dx++) {
                for (int dz = -4; dz <= 4; dz++) {
                    allChunkKeys.add(chunkKey(cx + dx, cz + dz));
                }
            }
        }
        
        int minCX = Integer.MAX_VALUE, maxCX = Integer.MIN_VALUE;
        int minCZ = Integer.MAX_VALUE, maxCZ = Integer.MIN_VALUE;
        
        for (Long key : allChunkKeys) {
            int cx = (int)(key >> 32);
            int cz = key.intValue();
            minCX = Math.min(minCX, cx);
            maxCX = Math.max(maxCX, cx);
            minCZ = Math.min(minCZ, cz);
            maxCZ = Math.max(maxCZ, cz);
        }
        
        int blockMinX = minCX * 16;
        int blockMaxX = (maxCX + 1) * 16;
        int blockMinZ = minCZ * 16;
        int blockMaxZ = (maxCZ + 1) * 16;
        
        plugin.getLogger().info("Bounds: X[" + blockMinX + " to " + blockMaxX + "] Z[" + blockMinZ + " to " + blockMaxZ + "]");
        
        int chunksX = maxCX - minCX + 1;
        int chunksZ = maxCZ - minCZ + 1;
        int imageWidth = chunksX * 16 * PIXELS_PER_BLOCK;
        int imageHeight = chunksZ * 16 * PIXELS_PER_BLOCK;
        
        double scale = 1.0;
        int maxSize = 16384;
        if (imageWidth > maxSize || imageHeight > maxSize) {
            scale = Math.min((double)maxSize / imageWidth, (double)maxSize / imageHeight);
            imageWidth = (int)(imageWidth * scale);
            imageHeight = (int)(imageHeight * scale);
            plugin.getLogger().info("Scaled to: " + imageWidth + "x" + imageHeight);
        }
        
        plugin.getLogger().info("Image: " + imageWidth + "x" + imageHeight + " pixels");
        
        BufferedImage image = new BufferedImage(imageWidth, imageHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(25, 50, 100));
        g.fillRect(0, 0, imageWidth, imageHeight);
        g.dispose();
        
        int rendered = 0;
        int total = allChunkKeys.size();
        final double pixelsPerBlock = PIXELS_PER_BLOCK * scale;
        final int fMinCX = minCX, fMinCZ = minCZ;
        
        for (Long key : allChunkKeys) {
            int cx = (int)(key >> 32);
            int cz = key.intValue();
            
            try {
                level.loadChunk(cx, cz, false);
                FullChunk chunk = level.getChunk(cx, cz);
                
                if (chunk != null) {
                    int baseImgX = (int)((cx - fMinCX) * 16 * pixelsPerBlock);
                    int baseImgZ = (int)((cz - fMinCZ) * 16 * pixelsPerBlock);
                    renderChunkHQ(image, chunk, baseImgX, baseImgZ, pixelsPerBlock);
                    rendered++;
                }
                
                if (rendered % 1000 == 0) {
                    plugin.getLogger().info("Progress: " + rendered + "/" + total);
                }
            } catch (Exception e) {}
        }
        
        plugin.getLogger().info("=== Done: " + rendered + " chunks rendered ===");
        return new RenderResult(image, blockMinX, blockMaxX, blockMinZ, blockMaxZ);
    }
    
    private void renderChunkHQ(BufferedImage img, FullChunk chunk, int baseX, int baseZ, double pixelsPerBlock) {
        int w = img.getWidth(), h = img.getHeight();
        int pixelSize = Math.max(1, (int)Math.ceil(pixelsPerBlock));
        
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int imgX = baseX + (int)(x * pixelsPerBlock);
                int imgZ = baseZ + (int)(z * pixelsPerBlock);
                if (imgX < 0 || imgX >= w || imgZ < 0 || imgZ >= h) continue;
                
                int height = chunk.getHighestBlockAt(x, z);
                int blockId = 0, blockData = 0;
                
                for (int y = height; y >= 0; y--) {
                    blockId = chunk.getBlockId(x, y, z);
                    if (blockId != 0 && !isTransparent(blockId)) {
                        blockData = chunk.getBlockData(x, y, z);
                        height = y;
                        break;
                    }
                }
                
                Color c = getBlockColor(blockId, blockData);
                int rgb = applyShading(c, height, x, z, chunk).getRGB();
                
                for (int dx = 0; dx < pixelSize && imgX + dx < w; dx++) {
                    for (int dz = 0; dz < pixelSize && imgZ + dz < h; dz++) {
                        img.setRGB(imgX + dx, imgZ + dz, rgb);
                    }
                }
            }
        }
    }
    
    /**
     * Apply height shading + neighbor shading for better 3D effect
     * Enhanced saturation for more vivid colors
     */
    private Color applyShading(Color c, int height, int x, int z, FullChunk chunk) {
        // Base brightness from height (0.5 to 1.4)
        float bright = 0.5f + (height / 180f) * 0.9f;
        
        // Add neighbor-based shading for slope effect
        int northHeight = z > 0 ? chunk.getHighestBlockAt(x, z - 1) : height;
        int westHeight = x > 0 ? chunk.getHighestBlockAt(x - 1, z) : height;
        
        // If north or west is higher, darken (shadow)
        // If north or west is lower, brighten (sunlight)
        float slopeFactor = 0;
        slopeFactor += (height - northHeight) * 0.025f;
        slopeFactor += (height - westHeight) * 0.025f;
        slopeFactor = Math.max(-0.25f, Math.min(0.25f, slopeFactor));
        
        bright += slopeFactor;
        bright = Math.max(0.35f, Math.min(1.5f, bright));
        
        // Apply brightness
        int r = Math.min(255, Math.max(0, (int)(c.getRed() * bright)));
        int g = Math.min(255, Math.max(0, (int)(c.getGreen() * bright)));
        int b = Math.min(255, Math.max(0, (int)(c.getBlue() * bright)));
        
        // Boost saturation significantly for more vivid colors
        float[] hsb = Color.RGBtoHSB(r, g, b, null);
        float newSat = Math.min(1.0f, hsb[1] * 1.4f);
        
        return Color.getHSBColor(hsb[0], newSat, hsb[2]);
    }
    
    private void scanProviderChunks(LevelProvider provider, Set<Long> chunkKeys) {
        try {
            String[] fieldNames = {"chunks", "chunkCache", "loadedChunks", "db", "database"};
            for (String fieldName : fieldNames) {
                try {
                    Field field = findField(provider.getClass(), fieldName);
                    if (field != null) {
                        field.setAccessible(true);
                        Object value = field.get(provider);
                        if (value instanceof Map) {
                            Map<?, ?> map = (Map<?, ?>) value;
                            for (Object key : map.keySet()) {
                                if (key instanceof Long) {
                                    chunkKeys.add((Long) key);
                                }
                            }
                        }
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Reflection scan failed: " + e.getMessage());
        }
    }
    
    private Field findField(Class<?> clazz, String name) {
        Class<?> current = clazz;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }
    
    private void exhaustiveScan(Level level, Set<Long> chunkKeys) {
        int spawnCX = level.getSpawnLocation().getFloorX() >> 4;
        int spawnCZ = level.getSpawnLocation().getFloorZ() >> 4;
        
        Set<int[]> scanCenters = new LinkedHashSet<>();
        scanCenters.add(new int[]{spawnCX, spawnCZ});
        scanCenters.add(new int[]{0, 0});
        
        for (cn.nukkit.Player p : level.getPlayers().values()) {
            scanCenters.add(new int[]{p.getFloorX() >> 4, p.getFloorZ() >> 4});
        }
        
        for (Long key : new HashSet<>(chunkKeys)) {
            int cx = (int)(key >> 32);
            int cz = key.intValue();
            scanCenters.add(new int[]{cx, cz});
        }
        
        plugin.getLogger().info("Scanning from " + scanCenters.size() + " points");
        
        int maxRadius = 150;
        int totalFound = 0;
        
        for (int[] center : scanCenters) {
            int centerX = center[0];
            int centerZ = center[1];
            int emptyRings = 0;
            
            for (int r = 0; r <= maxRadius && emptyRings < 10; r++) {
                int found = 0;
                
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (r > 0 && Math.abs(dx) != r && Math.abs(dz) != r) continue;
                        
                        int cx = centerX + dx;
                        int cz = centerZ + dz;
                        long key = chunkKey(cx, cz);
                        
                        if (chunkKeys.contains(key)) continue;
                        
                        try {
                            if (level.loadChunk(cx, cz, false)) {
                                FullChunk chunk = level.getChunk(cx, cz);
                                if (chunk != null) {
                                    chunkKeys.add(key);
                                    found++;
                                    totalFound++;
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
                
                emptyRings = (found == 0) ? emptyRings + 1 : 0;
            }
        }
        
        plugin.getLogger().info("Found " + totalFound + " chunks via scan");
    }
    
    private long chunkKey(int cx, int cz) {
        return (((long)cx) << 32) | (cz & 0xFFFFFFFFL);
    }
    
    private boolean isTransparent(int id) {
        return id == 0 || id == 20 || id == 95 || id == 102 || id == 160 ||
               id == 65 || id == 66 || id == 27 || id == 28 || id == 50 || id == 76 ||
               id == 63 || id == 68 || id == 323;
    }
    
    // ============ ULTRA HIGH CONTRAST COLOR MAPPING ============
    // Maximum color differentiation between similar blocks
    
    private Color getBlockColor(int id, int data) {
        switch (id) {
            case 0: return new Color(20, 40, 90);            // Air - dark ocean blue
            
            // ===== STONE FAMILY - distinct undertones =====
            case 1:
                switch (data) {
                    case 1: return new Color(190, 100, 90);   // Granite - SALMON RED
                    case 2: return new Color(210, 130, 120);  // Polished Granite - lighter salmon
                    case 3: return new Color(230, 230, 240);  // Diorite - NEAR WHITE
                    case 4: return new Color(245, 245, 250);  // Polished Diorite - bright white
                    case 5: return new Color(70, 75, 80);     // Andesite - DARK GRAY
                    case 6: return new Color(90, 95, 100);    // Polished Andesite
                    default: return new Color(128, 128, 128); // Stone - neutral gray
                }
            case 4: return new Color(95, 95, 95);            // Cobblestone - darker gray
            case 48: return new Color(75, 110, 80);          // Mossy Cobblestone - GREEN tint
            case 98: // Stone Bricks
                switch (data) {
                    case 1: return new Color(80, 120, 85);    // Mossy - obvious GREEN
                    case 2: return new Color(90, 90, 90);     // Cracked - darker
                    case 3: return new Color(150, 150, 155);  // Chiseled - lighter
                    default: return new Color(120, 120, 125);
                }
            
            // ===== NATURAL TERRAIN - very distinct =====
            case 2: return new Color(70, 200, 40);           // Grass - VIVID GREEN
            case 3:
                switch (data) {
                    case 1: return new Color(110, 70, 40);    // Coarse Dirt - DARK brown
                    case 2: return new Color(75, 55, 30);     // Podzol - very dark
                    default: return new Color(170, 120, 75);  // Dirt - WARM brown
                }
            case 12: return data == 1 ? new Color(210, 100, 35) : new Color(245, 230, 170); // Red/Yellow sand
            case 13: return new Color(160, 155, 150);        // Gravel - light gray brown
            case 82: return new Color(175, 175, 195);        // Clay - BLUE-gray
            
            // ===== WOOD - maximum color variation =====
            case 5: // Planks
                switch (data) {
                    case 0: return new Color(195, 160, 100);  // Oak - GOLDEN
                    case 1: return new Color(70, 50, 30);     // Spruce - VERY DARK
                    case 2: return new Color(245, 235, 190);  // Birch - CREAM/WHITE
                    case 3: return new Color(190, 130, 80);   // Jungle - TAN
                    case 4: return new Color(215, 100, 35);   // Acacia - ORANGE
                    case 5: return new Color(45, 30, 15);     // Dark Oak - NEAR BLACK
                    default: return new Color(195, 160, 100);
                }
            case 17: // Log
                switch (data & 3) {
                    case 0: return new Color(125, 95, 55);    // Oak - brown
                    case 1: return new Color(40, 30, 20);     // Spruce - VERY DARK
                    case 2: return new Color(250, 245, 230);  // Birch - WHITE bark
                    case 3: return new Color(90, 70, 35);     // Jungle - dark tan
                    default: return new Color(125, 95, 55);
                }
            case 162: // Log 2
                return data == 0 ? new Color(175, 115, 70) : new Color(35, 25, 15); // Acacia/Dark Oak
            
            // ===== LEAVES - distinct greens =====
            case 18:
                switch (data & 3) {
                    case 0: return new Color(55, 180, 45);    // Oak - BRIGHT green
                    case 1: return new Color(35, 80, 50);     // Spruce - DARK blue-green
                    case 2: return new Color(110, 160, 60);   // Birch - YELLOW-green
                    case 3: return new Color(30, 170, 25);    // Jungle - LIME green
                    default: return new Color(55, 180, 45);
                }
            case 161:
                return data == 0 ? new Color(50, 145, 35) : new Color(45, 110, 40); // Acacia/Dark Oak
            
            // ===== WATER & LAVA - very vivid =====
            case 8: case 9: return new Color(25, 85, 255);   // Water - BRIGHT BLUE
            case 10: case 11: return new Color(255, 90, 0);  // Lava - BRIGHT ORANGE
            case 79: return new Color(140, 200, 255);        // Ice - LIGHT BLUE
            case 174: return new Color(120, 180, 250);       // Packed Ice
            
            // ===== ORES - unique colors =====
            case 14: return new Color(180, 165, 50);         // Gold Ore - YELLOW tint
            case 15: return new Color(170, 140, 120);        // Iron Ore - RUST tint
            case 16: return new Color(55, 55, 55);           // Coal Ore - VERY DARK
            case 21: return new Color(50, 75, 180);          // Lapis Ore - BLUE tint
            case 56: return new Color(80, 230, 240);         // Diamond Ore - CYAN
            case 73: case 74: return new Color(175, 70, 70); // Redstone Ore - RED tint
            case 129: return new Color(70, 215, 130);        // Emerald Ore - GREEN
            case 153: return new Color(165, 135, 125);       // Nether Quartz Ore
            
            // ===== MINERAL BLOCKS - pure colors =====
            case 22: return new Color(30, 50, 210);          // Lapis Block - DEEP BLUE
            case 41: return new Color(255, 235, 55);         // Gold Block - BRIGHT GOLD
            case 42: return new Color(235, 235, 240);        // Iron Block - SILVER
            case 57: return new Color(85, 250, 255);         // Diamond Block - BRIGHT CYAN
            case 133: return new Color(55, 250, 115);        // Emerald Block - BRIGHT GREEN
            case 152: return new Color(240, 35, 25);         // Redstone Block - BRIGHT RED
            case 173: return new Color(15, 15, 20);          // Coal Block - NEAR BLACK
            
            // ===== NETHER BLOCKS - reds and darks =====
            case 87: return new Color(150, 55, 55);          // Netherrack - DARK RED
            case 88: return new Color(75, 55, 45);           // Soul Sand - BROWN
            case 89: return new Color(255, 245, 140);        // Glowstone - BRIGHT YELLOW
            case 112: return new Color(45, 25, 35);          // Nether Brick - DARK PURPLE
            case 213: return new Color(240, 110, 30);        // Magma Block - ORANGE
            case 214: return new Color(140, 55, 55);         // Nether Wart Block - RED
            case 215: return new Color(110, 35, 40);         // Red Nether Brick - DARK RED
            
            // ===== END BLOCKS =====
            case 121: return new Color(250, 255, 185);       // End Stone - PALE YELLOW
            case 206: return new Color(240, 245, 195);       // End Stone Bricks
            case 201: case 202: case 203: return new Color(190, 145, 200); // Purpur - PURPLE
            
            // ===== BUILDING BLOCKS =====
            case 7: return new Color(50, 50, 55);            // Bedrock - VERY DARK
            case 24: // Sandstone
                switch (data) {
                    case 1: return new Color(235, 220, 165);
                    case 2: return new Color(245, 230, 180);
                    default: return new Color(225, 210, 155);
                }
            case 45: return new Color(185, 95, 65);          // Bricks - RED-BROWN
            case 49: return new Color(15, 10, 25);           // Obsidian - NEAR BLACK purple
            case 155: return data == 1 ? new Color(250, 245, 240) : new Color(255, 250, 245); // Quartz - WHITE
            case 179: return new Color(210, 115, 55);        // Red Sandstone - ORANGE
            
            // ===== DECORATIVE =====
            case 19: return data == 1 ? new Color(195, 180, 85) : new Color(230, 230, 100); // Sponge - YELLOW
            case 20: return new Color(210, 245, 255);        // Glass - LIGHT BLUE
            case 35: return getWoolColor(data);              // Wool
            case 47: return new Color(175, 140, 90);         // Bookshelf - TAN
            case 86: return new Color(240, 150, 20);         // Pumpkin - ORANGE
            case 91: return new Color(250, 165, 30);         // Jack o'Lantern
            case 103: return new Color(150, 225, 55);        // Melon - YELLOW-GREEN
            case 170: return new Color(215, 195, 60);        // Hay Bale - GOLDEN
            case 216: return new Color(250, 250, 230);       // Bone Block - OFF-WHITE
            
            // ===== PLANTS =====
            case 31: return data == 1 ? new Color(70, 195, 50) : new Color(180, 165, 110); // Grass/Fern
            case 32: return new Color(165, 135, 80);         // Dead Bush - TAN
            case 37: return new Color(255, 250, 55);         // Dandelion - BRIGHT YELLOW
            case 38: return getFlowerColor(data);
            case 39: return new Color(185, 150, 115);        // Brown Mushroom
            case 40: return new Color(245, 45, 40);          // Red Mushroom - BRIGHT RED
            case 81: return new Color(15, 165, 40);          // Cactus - GREEN
            case 83: return new Color(120, 210, 95);         // Sugar Cane - LIGHT GREEN
            case 106: return new Color(45, 160, 35);         // Vines
            case 111: return new Color(30, 150, 35);         // Lily Pad
            case 175: return getTallFlowerColor(data);
            
            // ===== REDSTONE =====
            case 55: return new Color(200, 0, 0);            // Redstone Wire - PURE RED
            case 50: return new Color(255, 240, 110);        // Torch - BRIGHT YELLOW
            case 51: return new Color(255, 140, 25);         // Fire - ORANGE
            case 123: case 124: return new Color(150, 90, 50); // Redstone Lamp
            
            // ===== FUNCTIONAL BLOCKS =====
            case 23: return new Color(100, 100, 105);        // Dispenser
            case 25: return new Color(130, 90, 60);          // Note Block
            case 46: return new Color(220, 60, 50);          // TNT - RED
            case 52: return new Color(25, 45, 75);           // Spawner - DARK BLUE
            case 54: case 146: return new Color(175, 135, 65); // Chest - GOLDEN BROWN
            case 58: return new Color(160, 120, 65);         // Crafting Table
            case 61: case 62: return new Color(115, 115, 120); // Furnace
            case 84: return new Color(135, 95, 60);          // Jukebox
            case 116: return new Color(65, 50, 90);          // Enchanting Table - DARK PURPLE
            case 130: return new Color(35, 55, 60);          // Ender Chest - DARK CYAN
            case 138: return new Color(90, 250, 240);        // Beacon - BRIGHT CYAN
            case 145: return new Color(60, 60, 65);          // Anvil
            case 154: return new Color(105, 105, 110);       // Hopper
            
            // ===== PRISMARINE =====
            case 168:
                switch (data) {
                    case 0: return new Color(95, 200, 190);   // Prismarine - CYAN
                    case 1: return new Color(70, 145, 130);   // Bricks - darker
                    case 2: return new Color(50, 90, 85);     // Dark - very dark
                    default: return new Color(95, 200, 190);
                }
            case 169: return new Color(200, 240, 230);       // Sea Lantern - BRIGHT CYAN
            
            // ===== GLAZED TERRACOTTA & CONCRETE =====
            case 159: return getTerracottaColor(data);
            case 235: return new Color(245, 220, 190);       // Glazed Terracotta
            case 236: return getConcreteColor(data);
            case 237: return getConcretePowderColor(data);
            
            // ===== MISC =====
            case 78: return new Color(255, 255, 255);        // Snow - PURE WHITE
            case 80: return new Color(250, 255, 255);        // Snow Block
            case 90: return new Color(120, 50, 200);         // Nether Portal - PURPLE
            case 95: return getStainedGlassColor(data);
            case 110: return new Color(160, 125, 145);       // Mycelium - PURPLE tint
            case 165: return new Color(80, 235, 60);         // Slime Block - BRIGHT GREEN
            case 171: return getWoolColor(data);             // Carpet
            case 172: return new Color(185, 110, 75);        // Hardened Clay - TERRACOTTA
            case 208: return new Color(190, 175, 140);       // Grass Path
            
            // ===== STAIRS/SLABS - match their base =====
            case 43: case 44: return getSlabColor(data);
            case 53: return new Color(195, 160, 100);        // Oak Stairs
            case 67: return new Color(95, 95, 95);           // Cobble Stairs
            case 108: return new Color(185, 95, 65);         // Brick Stairs
            case 109: return new Color(120, 120, 125);       // Stone Brick Stairs
            case 114: return new Color(45, 25, 35);          // Nether Brick Stairs
            case 128: return new Color(225, 210, 155);       // Sandstone Stairs
            case 134: return new Color(70, 50, 30);          // Spruce Stairs
            case 135: return new Color(245, 235, 190);       // Birch Stairs
            case 136: return new Color(190, 130, 80);        // Jungle Stairs
            case 156: return new Color(255, 250, 245);       // Quartz Stairs
            case 163: return new Color(215, 100, 35);        // Acacia Stairs
            case 164: return new Color(45, 30, 15);          // Dark Oak Stairs
            case 180: return new Color(210, 115, 55);        // Red Sandstone Stairs
            
            // ===== FENCES/DOORS =====
            case 85: return new Color(195, 160, 100);        // Oak Fence
            case 113: return new Color(45, 25, 35);          // Nether Brick Fence
            case 188: return new Color(70, 50, 30);          // Spruce Fence
            case 189: return new Color(245, 235, 190);       // Birch Fence
            case 190: return new Color(190, 130, 80);        // Jungle Fence
            case 191: return new Color(45, 30, 15);          // Dark Oak Fence
            case 192: return new Color(215, 100, 35);        // Acacia Fence
            
            case 64: return new Color(195, 160, 100);        // Oak Door
            case 71: return new Color(200, 200, 205);        // Iron Door
            case 193: return new Color(70, 50, 30);          // Spruce Door
            case 194: return new Color(245, 235, 190);       // Birch Door
            case 195: return new Color(190, 130, 80);        // Jungle Door
            case 196: return new Color(215, 100, 35);        // Acacia Door
            case 197: return new Color(45, 30, 15);          // Dark Oak Door
            
            // ===== SHULKER BOXES =====
            case 218: return new Color(165, 210, 180);       // Shulker Box
            case 219: case 220: case 221: case 222: case 223: case 224: case 225: case 226:
            case 227: case 228: case 229: case 230: case 231: case 232: case 233: case 234:
                return getShulkerColor(id - 219);
            
            // ===== CROPS =====
            case 59: return new Color(180, 210, 65);         // Wheat - YELLOW-GREEN
            case 60: return new Color(115, 80, 50);          // Farmland - DARK BROWN
            case 115: return new Color(190, 20, 20);         // Nether Wart - DARK RED
            case 127: return new Color(185, 135, 75);        // Cocoa
            case 141: return new Color(50, 185, 40);         // Carrots - GREEN
            case 142: return new Color(90, 195, 60);         // Potatoes
            case 207: return new Color(155, 125, 65);        // Beetroots
            
            default: return new Color(220, 150, 220);        // Unknown - PINK
        }
    }
    
    private Color getFlowerColor(int data) {
        switch (data) {
            case 0: return new Color(245, 35, 30);           // Poppy - BRIGHT RED
            case 1: return new Color(45, 185, 250);          // Blue Orchid - BRIGHT BLUE
            case 2: return new Color(245, 110, 245);         // Allium - BRIGHT MAGENTA
            case 3: return new Color(255, 255, 255);         // Azure Bluet - WHITE
            case 4: return new Color(245, 45, 35);           // Red Tulip
            case 5: return new Color(255, 165, 40);          // Orange Tulip - BRIGHT ORANGE
            case 6: return new Color(255, 255, 255);         // White Tulip
            case 7: return new Color(255, 150, 210);         // Pink Tulip - BRIGHT PINK
            case 8: return new Color(255, 255, 230);         // Oxeye Daisy
            default: return new Color(225, 70, 70);
        }
    }
    
    private Color getTallFlowerColor(int data) {
        switch (data & 7) {
            case 0: return new Color(255, 250, 65);          // Sunflower - BRIGHT YELLOW
            case 1: return new Color(230, 130, 245);         // Lilac - BRIGHT PURPLE
            case 2: return new Color(85, 190, 50);           // Tall Grass - GREEN
            case 3: return new Color(60, 140, 40);           // Large Fern - DARK GREEN
            case 4: return new Color(245, 45, 85);           // Rose Bush - BRIGHT RED
            case 5: return new Color(255, 140, 210);         // Peony - BRIGHT PINK
            default: return new Color(90, 180, 45);
        }
    }
    
    private Color getSlabColor(int data) {
        switch (data & 7) {
            case 0: return new Color(180, 180, 185);         // Stone - GRAY
            case 1: return new Color(230, 215, 165);         // Sandstone - YELLOW
            case 3: return new Color(100, 100, 100);         // Cobblestone - DARK GRAY
            case 4: return new Color(190, 100, 70);          // Brick - RED-BROWN
            case 5: return new Color(125, 125, 130);         // Stone Brick
            case 6: return new Color(45, 25, 35);            // Nether Brick - DARK PURPLE
            case 7: return new Color(250, 245, 240);         // Quartz - WHITE
            default: return new Color(180, 180, 185);
        }
    }
    
    private Color getStainedGlassColor(int data) {
        return getWoolColor(data); // Same base colors
    }
    
    private Color getShulkerColor(int data) {
        return getWoolColor(data);
    }
    
    private Color getWoolColor(int d) {
        Color[] c = {
            new Color(255, 255, 255), // White - PURE WHITE
            new Color(255, 140, 40),  // Orange - BRIGHT ORANGE
            new Color(230, 70, 230),  // Magenta - VIVID MAGENTA
            new Color(100, 170, 255), // Light Blue - SKY BLUE
            new Color(255, 230, 35),  // Yellow - BRIGHT YELLOW
            new Color(100, 230, 50),  // Lime - BRIGHT LIME
            new Color(255, 160, 190), // Pink - BRIGHT PINK
            new Color(60, 60, 65),    // Gray - DARK GRAY
            new Color(165, 165, 165), // Light Gray
            new Color(50, 160, 175),  // Cyan - TEAL
            new Color(140, 50, 220),  // Purple - BRIGHT PURPLE
            new Color(50, 60, 200),   // Blue - BRIGHT BLUE
            new Color(130, 85, 50),   // Brown
            new Color(75, 125, 40),   // Green - FOREST GREEN
            new Color(200, 55, 55),   // Red - BRIGHT RED
            new Color(20, 20, 25)     // Black - NEAR BLACK
        };
        return d >= 0 && d < 16 ? c[d] : c[0];
    }
    
    private Color getTerracottaColor(int d) {
        Color[] c = {
            new Color(235, 210, 195), // White - CREAM
            new Color(195, 105, 40),  // Orange - BURNT ORANGE
            new Color(180, 85, 140),  // Magenta - PLUM
            new Color(125, 120, 170), // Light Blue - LAVENDER
            new Color(220, 175, 50),  // Yellow - GOLD
            new Color(110, 145, 50),  // Lime - OLIVE
            new Color(185, 95, 95),   // Pink - DUSTY ROSE
            new Color(60, 50, 45),    // Gray - CHARCOAL
            new Color(165, 135, 125), // Light Gray - TAUPE
            new Color(95, 115, 115),  // Cyan - SLATE
            new Color(135, 75, 100),  // Purple - WINE
            new Color(85, 70, 105),   // Blue - INDIGO
            new Color(95, 65, 40),    // Brown - CHOCOLATE
            new Color(85, 100, 50),   // Green - MOSS
            new Color(175, 70, 60),   // Red - RUST
            new Color(45, 40, 40)     // Black - DARK BROWN
        };
        return d >= 0 && d < 16 ? c[d] : new Color(185, 115, 85);
    }
    
    private Color getConcreteColor(int d) {
        Color[] c = {
            new Color(240, 245, 245), // White
            new Color(255, 110, 5),   // Orange - VIVID ORANGE
            new Color(205, 45, 195),  // Magenta - VIVID MAGENTA
            new Color(35, 155, 245),  // Light Blue - SKY BLUE
            new Color(255, 210, 20),  // Yellow - BRIGHT YELLOW
            new Color(95, 205, 25),   // Lime - BRIGHT LIME
            new Color(250, 105, 165), // Pink - HOT PINK
            new Color(50, 50, 55),    // Gray
            new Color(130, 130, 125), // Light Gray
            new Color(20, 145, 155),  // Cyan - TEAL
            new Color(110, 25, 195),  // Purple - VIVID PURPLE
            new Color(40, 45, 175),   // Blue - ROYAL BLUE
            new Color(110, 70, 35),   // Brown
            new Color(75, 110, 35),   // Green
            new Color(180, 35, 35),   // Red - BRIGHT RED
            new Color(10, 10, 15)     // Black
        };
        return d >= 0 && d < 16 ? c[d] : c[0];
    }
    
    private Color getConcretePowderColor(int d) {
        Color[] c = {
            new Color(248, 248, 248), // White
            new Color(255, 145, 40),  // Orange
            new Color(215, 95, 205),  // Magenta
            new Color(80, 190, 250),  // Light Blue
            new Color(255, 225, 50),  // Yellow
            new Color(130, 220, 50),  // Lime
            new Color(255, 155, 195), // Pink
            new Color(80, 80, 85),    // Gray
            new Color(180, 180, 175), // Light Gray
            new Color(50, 175, 180),  // Cyan
            new Color(150, 70, 215),  // Purple
            new Color(75, 80, 200),   // Blue
            new Color(150, 105, 65),  // Brown
            new Color(115, 140, 55),  // Green
            new Color(200, 70, 70),   // Red
            new Color(40, 40, 45)     // Black
        };
        return d >= 0 && d < 16 ? c[d] : c[0];
    }
}
