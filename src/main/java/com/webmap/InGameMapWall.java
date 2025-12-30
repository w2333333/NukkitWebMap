package com.webmap;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityItemFrame;
import cn.nukkit.item.ItemMap;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.scheduler.AsyncTask;
import cn.nukkit.scheduler.Task;
import cn.nukkit.utils.Config;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Physical map wall manager with persistence
 * Walls are saved and restored after server restart
 */
public class InGameMapWall {
    
    private final WebMapPlugin plugin;
    private static final long BASE_MAP_ID = 10000000;
    
    // Physical walls
    private Map<String, WallInfo> physicalWalls = new ConcurrentHashMap<>();
    private long nextMapId = BASE_MAP_ID;
    
    // Cached base images
    private Map<String, BufferedImage> cachedBaseImages = new ConcurrentHashMap<>();
    private Map<String, Long> cacheTimestamps = new ConcurrentHashMap<>();
    private static final long CACHE_DURATION = 600000; // 10 minutes
    
    // Async control
    private volatile boolean isUpdating = false;
    
    // Config file for persistence
    private Config wallsConfig;
    
    public InGameMapWall(WebMapPlugin plugin) {
        this.plugin = plugin;
        this.wallsConfig = new Config(new File(plugin.getDataFolder(), "walls.yml"), Config.YAML);
        loadWalls();
    }
    
    /**
     * Load saved walls from walls.yml
     */
    private void loadWalls() {
        if (!wallsConfig.exists("walls")) {
            plugin.getLogger().info("[InGameMap] No saved walls found");
            return;
        }
        
        // Load nextMapId to continue from where we left off
        nextMapId = wallsConfig.getLong("nextMapId", BASE_MAP_ID);
        
        Map<String, Object> wallsSection = (Map<String, Object>) wallsConfig.get("walls");
        if (wallsSection == null) return;
        
        for (String wallKey : wallsSection.keySet()) {
            try {
                String prefix = "walls." + wallKey + ".";
                
                WallInfo wall = new WallInfo();
                wall.worldName = wallsConfig.getString(prefix + "world");
                wall.size = wallsConfig.getInt(prefix + "size");
                wall.baseX = wallsConfig.getInt(prefix + "baseX");
                wall.baseY = wallsConfig.getInt(prefix + "baseY");
                wall.baseZ = wallsConfig.getInt(prefix + "baseZ");
                wall.imagePath = wallsConfig.getString(prefix + "imagePath");
                wall.facing = BlockFace.valueOf(wallsConfig.getString(prefix + "facing", "SOUTH"));
                
                // Load mapIds
                wall.mapIds = new long[wall.size][wall.size];
                List<String> mapIdList = wallsConfig.getStringList(prefix + "mapIds");
                int index = 0;
                for (int gx = 0; gx < wall.size && index < mapIdList.size(); gx++) {
                    for (int gy = 0; gy < wall.size && index < mapIdList.size(); gy++) {
                        wall.mapIds[gx][gy] = Long.parseLong(mapIdList.get(index++));
                    }
                }
                
                physicalWalls.put(wallKey, wall);
                plugin.getLogger().info("[InGameMap] Loaded wall: " + wallKey + " (" + wall.size + "x" + wall.size + ")");
                
            } catch (Exception e) {
                plugin.getLogger().warning("[InGameMap] Failed to load wall: " + wallKey);
            }
        }
        
        plugin.getLogger().info("[InGameMap] Loaded " + physicalWalls.size() + " wall(s)");
    }
    
    /**
     * Save all walls to walls.yml
     */
    private void saveWalls() {
        // Clear old data
        wallsConfig.set("walls", null);
        
        // Save nextMapId
        wallsConfig.set("nextMapId", nextMapId);
        
        // Save each wall
        for (Map.Entry<String, WallInfo> entry : physicalWalls.entrySet()) {
            String wallKey = entry.getKey();
            WallInfo wall = entry.getValue();
            String prefix = "walls." + wallKey + ".";
            
            wallsConfig.set(prefix + "world", wall.worldName);
            wallsConfig.set(prefix + "size", wall.size);
            wallsConfig.set(prefix + "baseX", wall.baseX);
            wallsConfig.set(prefix + "baseY", wall.baseY);
            wallsConfig.set(prefix + "baseZ", wall.baseZ);
            wallsConfig.set(prefix + "imagePath", wall.imagePath);
            wallsConfig.set(prefix + "facing", wall.facing.name());
            
            // Save mapIds as list
            List<String> mapIdList = new ArrayList<>();
            for (int gx = 0; gx < wall.size; gx++) {
                for (int gy = 0; gy < wall.size; gy++) {
                    mapIdList.add(String.valueOf(wall.mapIds[gx][gy]));
                }
            }
            wallsConfig.set(prefix + "mapIds", mapIdList);
        }
        
        wallsConfig.save();
    }
    
    /**
     * Refresh wall images after loading (call after map render)
     */
    public void refreshLoadedWalls() {
        if (physicalWalls.isEmpty()) return;
        
        plugin.getServer().getScheduler().scheduleDelayedTask(plugin, new Task() {
            @Override
            public void onRun(int tick) {
                for (Map.Entry<String, WallInfo> entry : physicalWalls.entrySet()) {
                    WallInfo wall = entry.getValue();
                    
                    File mapFile = new File(wall.imagePath);
                    if (!mapFile.exists()) {
                        plugin.getLogger().warning("[InGameMap] Map file not found: " + wall.imagePath);
                        continue;
                    }
                    
                    // Load and split image async
                    final String wallKey = entry.getKey();
                    plugin.getServer().getScheduler().scheduleAsyncTask(plugin, new AsyncTask() {
                        BufferedImage scaled = null;
                        boolean success = false;
                        
                        @Override
                        public void onRun() {
                            try {
                                BufferedImage source = ImageIO.read(mapFile);
                                int targetSize = wall.size * 128;
                                
                                scaled = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
                                Graphics2D g = scaled.createGraphics();
                                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                                g.drawImage(source, 0, 0, targetSize, targetSize, null);
                                g.dispose();
                                success = true;
                            } catch (Exception e) {
                                e.printStackTrace();
                            }
                        }
                        
                        @Override
                        public void onCompletion(Server server) {
                            if (!success || scaled == null) return;
                            
                            // Store image pieces
                            for (int gx = 0; gx < wall.size; gx++) {
                                for (int gy = 0; gy < wall.size; gy++) {
                                    int imgX = gx * 128;
                                    int imgY = (wall.size - 1 - gy) * 128;
                                    BufferedImage piece = scaled.getSubimage(imgX, imgY, 128, 128);
                                    
                                    long mapId = wall.mapIds[gx][gy];
                                    plugin.getMapImages().put(mapId, piece);
                                }
                            }
                            
                            plugin.getLogger().info("[InGameMap] Refreshed wall: " + wallKey);
                            
                            // Send to online players in that world
                            Level level = server.getLevelByName(wall.worldName);
                            if (level != null) {
                                for (Player p : level.getPlayers().values()) {
                                    sendWallToPlayer(p, wall);
                                }
                            }
                        }
                    });
                }
            }
        }, 40); // 2 second delay for world to load
    }
    
    /**
     * Send wall images to a player
     */
    private void sendWallToPlayer(Player player, WallInfo wall) {
        for (int gx = 0; gx < wall.size; gx++) {
            for (int gy = 0; gy < wall.size; gy++) {
                long mapId = wall.mapIds[gx][gy];
                BufferedImage img = plugin.getMapImages().get(mapId);
                if (img != null) {
                    plugin.sendMapImage(player, mapId, img);
                }
            }
        }
    }
    
    public void startScheduler() {
        int updateTicks = plugin.getMarkerUpdateSeconds() * 20;
        
        // Player marker update - fully async
        plugin.getServer().getScheduler().scheduleDelayedRepeatingTask(plugin, new Task() {
            @Override
            public void onRun(int tick) {
                if (physicalWalls.isEmpty()) return;
                if (plugin.getServer().getOnlinePlayers().isEmpty()) return;
                if (isUpdating) return;
                
                updateWallsAsync();
            }
        }, updateTicks, updateTicks);
        
        plugin.getLogger().info("[InGameMap] Started (update interval: " + plugin.getMarkerUpdateSeconds() + "s)");
    }
    
    /**
     * Fully async wall update with batched packet sending
     */
    private void updateWallsAsync() {
        // Collect data on main thread (instant)
        Map<String, List<PlayerPos>> worldPlayers = new HashMap<>();
        Map<String, List<Player>> worldPlayerObjects = new HashMap<>();
        
        for (WallInfo wall : physicalWalls.values()) {
            Level level = plugin.getServer().getLevelByName(wall.worldName);
            if (level == null || level.getPlayers().isEmpty()) continue;
            
            List<PlayerPos> positions = new ArrayList<>();
            List<Player> players = new ArrayList<>(level.getPlayers().values());
            for (Player p : players) {
                positions.add(new PlayerPos(p.getName(), p.getFloorX(), p.getFloorZ()));
            }
            worldPlayers.put(wall.worldName, positions);
            worldPlayerObjects.put(wall.worldName, players);
        }
        
        if (worldPlayers.isEmpty()) return;
        
        isUpdating = true;
        
        // Heavy work in async thread
        plugin.getServer().getScheduler().scheduleAsyncTask(plugin, new AsyncTask() {
            private Map<String, Map<Long, BufferedImage>> results = new HashMap<>();
            
            @Override
            public void onRun() {
                for (Map.Entry<String, WallInfo> entry : physicalWalls.entrySet()) {
                    WallInfo wall = entry.getValue();
                    List<PlayerPos> players = worldPlayers.get(wall.worldName);
                    if (players == null || players.isEmpty()) continue;
                    
                    WebMapPlugin.MapInfo mapInfo = plugin.getRenderedMaps().get(wall.worldName);
                    if (mapInfo == null) continue;
                    
                    int rangeX = mapInfo.blockMaxX - mapInfo.blockMinX;
                    int rangeZ = mapInfo.blockMaxZ - mapInfo.blockMinZ;
                    if (rangeX <= 0 || rangeZ <= 0) continue;
                    
                    try {
                        BufferedImage baseImage = getCachedBaseImage(wall);
                        if (baseImage == null) continue;
                        
                        int targetSize = wall.size * 128;
                        
                        // Draw player markers
                        BufferedImage withMarkers = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
                        Graphics2D g = withMarkers.createGraphics();
                        g.drawImage(baseImage, 0, 0, null);
                        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                        
                        // Calculate sizes based on web proportions
                        int dotSize = Math.max(8, (int)(targetSize * 0.025));
                        int fontSize = Math.max(12, (int)(targetSize * 0.022));
                        int strokeWidth = Math.max(2, (int)(targetSize * 0.004));
                        int nameGap = Math.max(4, (int)(targetSize * 0.008));
                        int namePadX = Math.max(4, (int)(targetSize * 0.006));
                        int namePadY = Math.max(2, (int)(targetSize * 0.003));
                        
                        g.setFont(new Font("SansSerif", Font.BOLD, fontSize));
                        
                        for (PlayerPos p : players) {
                            double relX = (double)(p.x - mapInfo.blockMinX) / rangeX;
                            double relZ = (double)(p.z - mapInfo.blockMinZ) / rangeZ;
                            
                            if (relX >= 0 && relX <= 1 && relZ >= 0 && relZ <= 1) {
                                int imgX = (int)(relX * targetSize);
                                int imgZ = (int)(relZ * targetSize);
                                
                                // Draw red dot with white border
                                g.setColor(new Color(255, 51, 51));
                                g.fillOval(imgX - dotSize/2, imgZ - dotSize/2, dotSize, dotSize);
                                g.setColor(Color.WHITE);
                                g.setStroke(new BasicStroke(strokeWidth));
                                g.drawOval(imgX - dotSize/2, imgZ - dotSize/2, dotSize, dotSize);
                                
                                // Draw name with background box
                                String name = p.name;
                                FontMetrics fm = g.getFontMetrics();
                                int textWidth = fm.stringWidth(name);
                                int textHeight = fm.getAscent();
                                
                                int boxWidth = textWidth + namePadX * 2;
                                int boxHeight = textHeight + namePadY * 2;
                                int boxX = imgX - boxWidth / 2;
                                int boxY = imgZ - dotSize/2 - nameGap - boxHeight;
                                
                                boxX = Math.max(2, Math.min(targetSize - boxWidth - 2, boxX));
                                boxY = Math.max(2, boxY);
                                
                                g.setColor(new Color(0, 0, 0, 230));
                                g.fillRoundRect(boxX, boxY, boxWidth, boxHeight, 
                                    Math.max(2, strokeWidth), Math.max(2, strokeWidth));
                                
                                int textX = boxX + namePadX;
                                int textY = boxY + namePadY + textHeight - fm.getDescent();
                                g.setColor(Color.WHITE);
                                g.drawString(name, textX, textY);
                            }
                        }
                        g.dispose();
                        
                        // Split into pieces
                        Map<Long, BufferedImage> pieces = new HashMap<>();
                        for (int gx = 0; gx < wall.size; gx++) {
                            for (int gy = 0; gy < wall.size; gy++) {
                                int imgX = gx * 128;
                                int imgY = (wall.size - 1 - gy) * 128;
                                
                                BufferedImage piece = new BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB);
                                Graphics2D pg = piece.createGraphics();
                                pg.drawImage(withMarkers, 0, 0, 128, 128, imgX, imgY, imgX + 128, imgY + 128, null);
                                pg.dispose();
                                
                                pieces.put(wall.mapIds[gx][gy], piece);
                            }
                        }
                        
                        results.put(wall.worldName, pieces);
                        
                    } catch (Exception e) {
                        // Skip
                    }
                }
            }
            
            @Override
            public void onCompletion(Server server) {
                isUpdating = false;
                
                // Batch send packets
                List<PacketData> allPackets = new ArrayList<>();
                
                for (Map.Entry<String, Map<Long, BufferedImage>> entry : results.entrySet()) {
                    String worldName = entry.getKey();
                    Map<Long, BufferedImage> pieces = entry.getValue();
                    List<Player> players = worldPlayerObjects.get(worldName);
                    
                    if (players == null) continue;
                    players.removeIf(p -> !p.isOnline());
                    if (players.isEmpty()) continue;
                    
                    for (Map.Entry<Long, BufferedImage> pieceEntry : pieces.entrySet()) {
                        long mapId = pieceEntry.getKey();
                        BufferedImage image = pieceEntry.getValue();
                        
                        plugin.getMapImages().put(mapId, image);
                        
                        for (Player p : players) {
                            allPackets.add(new PacketData(p, mapId, image));
                        }
                    }
                }
                
                if (!allPackets.isEmpty()) {
                    sendPacketsInBatches(allPackets);
                }
            }
        });
    }
    
    private void sendPacketsInBatches(List<PacketData> packets) {
        final int BATCH_SIZE = 10;
        final int[] index = {0};
        
        plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, new Task() {
            @Override
            public void onRun(int tick) {
                int sent = 0;
                while (sent < BATCH_SIZE && index[0] < packets.size()) {
                    PacketData pd = packets.get(index[0]);
                    if (pd.player.isOnline()) {
                        plugin.sendMapImage(pd.player, pd.mapId, pd.image);
                    }
                    index[0]++;
                    sent++;
                }
                
                if (index[0] >= packets.size()) {
                    this.cancel();
                }
            }
        }, 1);
    }
    
    private BufferedImage getCachedBaseImage(WallInfo wall) {
        String cacheKey = wall.worldName + "_" + wall.size;
        BufferedImage cached = cachedBaseImages.get(cacheKey);
        Long timestamp = cacheTimestamps.get(cacheKey);
        long now = System.currentTimeMillis();
        
        if (cached != null && timestamp != null && (now - timestamp) < CACHE_DURATION) {
            return cached;
        }
        
        try {
            File mapFile = new File(wall.imagePath);
            if (!mapFile.exists()) return null;
            
            BufferedImage source = ImageIO.read(mapFile);
            int targetSize = wall.size * 128;
            
            BufferedImage scaled = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(source, 0, 0, targetSize, targetSize, null);
            g.dispose();
            
            cachedBaseImages.put(cacheKey, scaled);
            cacheTimestamps.put(cacheKey, now);
            
            return scaled;
        } catch (Exception e) {
            return null;
        }
    }
    
    public void clearCache() {
        cachedBaseImages.clear();
        cacheTimestamps.clear();
    }
    
    public void create(Player player, int size) {
        if (size < 1 || size > 100) {
            player.sendMessage("\u00a7cSize must be 1-100");
            return;
        }
        
        Level level = player.getLevel();
        String worldName = level.getName();
        
        File mapFile = new File(plugin.getMapFolder(), worldName + ".png");
        if (!mapFile.exists()) {
            player.sendMessage("\u00a7cNo map for " + worldName);
            player.sendMessage("\u00a77Use /webmap render first");
            return;
        }
        
        BlockFace facing = getPlayerFacing(player);
        int wallX = player.getFloorX();
        int wallY = player.getFloorY();
        int wallZ = player.getFloorZ();
        
        player.sendMessage("\u00a7eCreating " + size + "x" + size + " map wall...");
        
        final int finalSize = size;
        final String playerName = player.getName();
        
        plugin.getServer().getScheduler().scheduleAsyncTask(plugin, new AsyncTask() {
            BufferedImage scaled = null;
            boolean success = false;
            
            @Override
            public void onRun() {
                try {
                    BufferedImage source = ImageIO.read(mapFile);
                    int targetSize = finalSize * 128;
                    
                    scaled = new BufferedImage(targetSize, targetSize, BufferedImage.TYPE_INT_ARGB);
                    Graphics2D g = scaled.createGraphics();
                    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                    g.drawImage(source, 0, 0, targetSize, targetSize, null);
                    g.dispose();
                    success = true;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            
            @Override
            public void onCompletion(Server server) {
                if (!success || scaled == null) {
                    Player p = server.getPlayer(playerName);
                    if (p != null) p.sendMessage("\u00a7cFailed to load map!");
                    return;
                }
                
                createWallInBatches(level, worldName, facing, wallX, wallY, wallZ, finalSize, scaled, playerName);
            }
        });
    }
    
    private void createWallInBatches(Level level, String worldName, BlockFace facing, 
                                      int wallX, int wallY, int wallZ, int size, 
                                      BufferedImage scaled, String playerName) {
        
        WallInfo wall = new WallInfo();
        wall.worldName = worldName;
        wall.size = size;
        wall.mapIds = new long[size][size];
        wall.imagePath = new File(plugin.getMapFolder(), worldName + ".png").getAbsolutePath();
        wall.baseX = wallX;
        wall.baseY = wallY;
        wall.baseZ = wallZ;
        wall.facing = facing;
        
        int dx = 0, dz = 0, fx = 0, fz = 0, meta = 0;
        switch (facing) {
            case SOUTH: dx = -1; fz = 2; meta = 3; break;
            case NORTH: dx = 1; fz = -2; meta = 2; break;
            case EAST: dz = -1; fx = 2; meta = 5; break;
            case WEST: dz = 1; fx = -2; meta = 4; break;
            default: dx = -1; fz = 2; meta = 3;
        }
        
        final int fdx = dx, fdz = dz, ffx = fx, ffz = fz, fmeta = meta;
        int startOffset = -(size - 1) / 2;
        
        for (int gx = 0; gx < size; gx++) {
            for (int gy = 0; gy < size; gy++) {
                wall.mapIds[gx][gy] = nextMapId++;
            }
        }
        
        // Remove old wall for this world if exists
        physicalWalls.remove(worldName);
        physicalWalls.put(worldName, wall);
        
        // Save to file
        saveWalls();
        
        clearCache();
        
        final int batchSize = 10;
        final int totalBlocks = size * size;
        final int[] currentIndex = {0};
        
        plugin.getServer().getScheduler().scheduleRepeatingTask(plugin, new Task() {
            @Override
            public void onRun(int tick) {
                int processed = 0;
                
                while (processed < batchSize && currentIndex[0] < totalBlocks) {
                    int gx = currentIndex[0] % size;
                    int gy = currentIndex[0] / size;
                    
                    int offset = startOffset + gx;
                    int bx = wallX + offset * fdx + ffx;
                    int by = wallY + gy;
                    int bz = wallZ + offset * fdz + ffz;
                    
                    level.setBlock(new Vector3(bx, by, bz), Block.get(Block.QUARTZ_BLOCK), true, false);
                    
                    int frX = bx + (facing == BlockFace.EAST ? -1 : facing == BlockFace.WEST ? 1 : 0);
                    int frZ = bz + (facing == BlockFace.SOUTH ? -1 : facing == BlockFace.NORTH ? 1 : 0);
                    
                    level.setBlock(new Vector3(frX, by, frZ), Block.get(Block.ITEM_FRAME_BLOCK, fmeta), true, false);
                    
                    FullChunk chunk = level.getChunk(frX >> 4, frZ >> 4, true);
                    if (chunk != null) {
                        CompoundTag nbt = new CompoundTag()
                            .putString("id", BlockEntity.ITEM_FRAME)
                            .putInt("x", frX)
                            .putInt("y", by)
                            .putInt("z", frZ);
                        BlockEntity.createBlockEntity(BlockEntity.ITEM_FRAME, chunk, nbt);
                    }
                    
                    int imgX = gx * 128;
                    int imgY = (size - 1 - gy) * 128;
                    BufferedImage piece = scaled.getSubimage(imgX, imgY, 128, 128);
                    
                    long mapId = wall.mapIds[gx][gy];
                    plugin.getMapImages().put(mapId, piece);
                    
                    BlockEntity be = level.getBlockEntity(new Vector3(frX, by, frZ));
                    if (be instanceof BlockEntityItemFrame) {
                        ItemMap mapItem = new ItemMap();
                        CompoundTag mapTag = new CompoundTag();
                        mapTag.putLong("map_uuid", mapId);
                        mapItem.setNamedTag(mapTag);
                        mapItem.setDamage((int)(mapId & 0x7FFFFFFF));
                        mapItem.setImage(piece);
                        ((BlockEntityItemFrame) be).setItem(mapItem);
                    }
                    
                    currentIndex[0]++;
                    processed++;
                }
                
                if (currentIndex[0] % 100 == 0 || currentIndex[0] == totalBlocks) {
                    Player p = plugin.getServer().getPlayer(playerName);
                    if (p != null) {
                        int percent = currentIndex[0] * 100 / totalBlocks;
                        p.sendMessage("\u00a77Progress: " + percent + "%");
                    }
                }
                
                if (currentIndex[0] >= totalBlocks) {
                    Player p = plugin.getServer().getPlayer(playerName);
                    if (p != null) {
                        p.sendMessage("\u00a7aMap wall created! (" + size + "x" + size + ")");
                        p.sendMessage("\u00a77Wall will persist after server restart.");
                    }
                    this.cancel();
                }
            }
        }, 1);
    }
    
    public void onPlayerJoin(Player player) {
        // Send wall images to player
        for (WallInfo wall : physicalWalls.values()) {
            if (wall.worldName.equals(player.getLevel().getName())) {
                sendWallToPlayer(player, wall);
            }
        }
    }
    
    /**
     * Remove a wall
     */
    public void removeWall(String worldName) {
        physicalWalls.remove(worldName);
        saveWalls();
    }
    
    public void updateAll() {
        // Handled by scheduler
    }
    
    private BlockFace getPlayerFacing(Player player) {
        double yaw = player.getYaw();
        if (yaw < 0) yaw += 360;
        if (yaw >= 315 || yaw < 45) return BlockFace.SOUTH;
        if (yaw >= 45 && yaw < 135) return BlockFace.WEST;
        if (yaw >= 135 && yaw < 225) return BlockFace.NORTH;
        return BlockFace.EAST;
    }
    
    public int getWallCount() {
        return physicalWalls.size();
    }
    
    private static class PacketData {
        Player player;
        long mapId;
        BufferedImage image;
        PacketData(Player p, long id, BufferedImage img) {
            this.player = p;
            this.mapId = id;
            this.image = img;
        }
    }
    
    private static class PlayerPos {
        String name;
        int x, z;
        PlayerPos(String name, int x, int z) {
            this.name = name;
            this.x = x;
            this.z = z;
        }
    }
    
    static class WallInfo {
        String worldName;
        int size;
        long[][] mapIds;
        String imagePath;
        int baseX, baseY, baseZ;
        BlockFace facing;
    }
}
