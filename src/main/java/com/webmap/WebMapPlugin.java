package com.webmap;

import cn.nukkit.Player;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.event.EventHandler;
import cn.nukkit.event.Listener;
import cn.nukkit.event.player.PlayerJoinEvent;
import cn.nukkit.event.player.PlayerMapInfoRequestEvent;
import cn.nukkit.item.ItemMap;
import cn.nukkit.level.Level;
import cn.nukkit.network.protocol.ClientboundMapItemDataPacket;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.scheduler.Task;
import cn.nukkit.utils.TextFormat;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.*;

public class WebMapPlugin extends PluginBase implements Listener {

    private WebServer webServer;
    private MapRenderer mapRenderer;
    private InGameMapWall inGameMapWall;
    private File mapFolder;
    private int webPort = 8123;
    private int renderIntervalHours = 24;
    private int markerUpdateSeconds = 2;
    
    private Map<String, MapInfo> renderedMaps = new HashMap<>();
    private Map<Long, BufferedImage> mapImages = new HashMap<>();
    
    // Region settings per world
    private Map<String, RegionConfig> regionConfigs = new HashMap<>();
    
    // Temporary storage for setcenter command
    private Map<String, int[]> pendingCenters = new HashMap<>();
    
    // Worlds to render
    private List<String> renderWorlds = new ArrayList<>();
    
    public static class RegionConfig {
        public int centerX, centerZ;
        public int radius; // Half of side length
        public boolean enabled = false;
        
        public RegionConfig() {}
        
        public RegionConfig(int cx, int cz, int r) {
            this.centerX = cx;
            this.centerZ = cz;
            this.radius = r;
            this.enabled = true;
        }
    }
    
    @Override
    public void onEnable() {
        saveDefaultConfig();
        
        webPort = getConfig().getInt("web-port", 8123);
        renderIntervalHours = getConfig().getInt("render-interval-hours", 24);
        markerUpdateSeconds = getConfig().getInt("marker-update-seconds", 2);
        
        // Validate
        if (renderIntervalHours < 0) renderIntervalHours = 0;
        if (markerUpdateSeconds < 1) markerUpdateSeconds = 1;
        if (markerUpdateSeconds > 60) markerUpdateSeconds = 60;
        
        mapFolder = new File(getDataFolder(), "maps");
        if (!mapFolder.exists()) {
            mapFolder.mkdirs();
        }
        
        // Load region configs from config
        loadRegionConfigs();
        
        // Load worlds to render
        loadRenderWorlds();
        
        mapRenderer = new MapRenderer(this);
        inGameMapWall = new InGameMapWall(this);
        
        getServer().getPluginManager().registerEvents(this, this);
        
        try {
            webServer = new WebServer(this, webPort);
            webServer.start();
            getLogger().info("WebMap started: http://localhost:" + webPort);
        } catch (Exception e) {
            getLogger().error("WebServer failed: " + e.getMessage());
        }
        
        inGameMapWall.startScheduler();
        
        // Auto render on startup (delay 5 seconds for world loading)
        getServer().getScheduler().scheduleDelayedTask(this, new Task() {
            @Override
            public void onRun(int tick) {
                getLogger().info("Auto rendering maps on startup...");
                renderAllWorlds();
            }
        }, 100); // 5 seconds delay
        
        // Auto render based on config interval (0 = disabled)
        if (renderIntervalHours > 0) {
            int intervalTicks = renderIntervalHours * 60 * 60 * 20; // hours to ticks
            getServer().getScheduler().scheduleDelayedRepeatingTask(this, new Task() {
                @Override
                public void onRun(int tick) {
                    getLogger().info("Scheduled auto render (" + renderIntervalHours + "h interval)...");
                    renderAllWorlds();
                    inGameMapWall.clearCache();
                }
            }, intervalTicks, intervalTicks);
            getLogger().info("Auto render: every " + renderIntervalHours + " hours");
        } else {
            getLogger().info("Auto render: disabled (manual only)");
        }
        
        getLogger().info("Player marker update: every " + markerUpdateSeconds + " seconds");
        getLogger().info("NukkitWebMap enabled!");
    }
    
    public int getMarkerUpdateSeconds() {
        return markerUpdateSeconds;
    }
    
    private void loadRegionConfigs() {
        if (getConfig().exists("regions")) {
            for (String world : getConfig().getSection("regions").getKeys(false)) {
                int cx = getConfig().getInt("regions." + world + ".centerX", 0);
                int cz = getConfig().getInt("regions." + world + ".centerZ", 0);
                int r = getConfig().getInt("regions." + world + ".radius", 500);
                boolean enabled = getConfig().getBoolean("regions." + world + ".enabled", false);
                
                RegionConfig rc = new RegionConfig(cx, cz, r);
                rc.enabled = enabled;
                regionConfigs.put(world, rc);
                
                if (enabled) {
                    getLogger().info("Region config loaded for " + world + ": center(" + cx + "," + cz + ") radius=" + r);
                }
            }
        }
    }
    
    private void loadRenderWorlds() {
        renderWorlds.clear();
        List<String> worlds = getConfig().getStringList("render-worlds");
        if (worlds != null && !worlds.isEmpty()) {
            renderWorlds.addAll(worlds);
            getLogger().info("Worlds to render: " + String.join(", ", renderWorlds));
        } else {
            // Default to "world" if not configured
            renderWorlds.add("world");
            getLogger().info("Worlds to render: world (default)");
        }
    }
    
    private void saveRegionConfig(String world, RegionConfig rc) {
        getConfig().set("regions." + world + ".centerX", rc.centerX);
        getConfig().set("regions." + world + ".centerZ", rc.centerZ);
        getConfig().set("regions." + world + ".radius", rc.radius);
        getConfig().set("regions." + world + ".enabled", rc.enabled);
        saveConfig();
    }
    
    @EventHandler
    public void onMapRequest(PlayerMapInfoRequestEvent event) {
        if (event.getMap() instanceof ItemMap) {
            ItemMap map = (ItemMap) event.getMap();
            long mapId = map.getMapId();
            BufferedImage img = mapImages.get(mapId);
            if (img != null) {
                sendMapImage(event.getPlayer(), mapId, img);
            }
        }
    }
    
    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        getServer().getScheduler().scheduleDelayedTask(this, new Task() {
            @Override
            public void onRun(int tick) {
                inGameMapWall.onPlayerJoin(player);
            }
        }, 100);
    }
    
    public void sendMapImage(Player player, long mapId, BufferedImage img) {
        try {
            ClientboundMapItemDataPacket pk = new ClientboundMapItemDataPacket();
            pk.mapId = mapId;
            pk.update = 2;
            pk.scale = 0;
            pk.width = 128;
            pk.height = 128;
            pk.offsetX = 0;
            pk.offsetZ = 0;
            pk.image = img;
            player.dataPacket(pk);
        } catch (Exception e) {
            getLogger().error("Send map failed: " + e.getMessage());
        }
    }
    
    @Override
    public void onDisable() {
        if (webServer != null) webServer.stop();
    }
    
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("webmap")) return false;
        
        if (args.length == 0) {
            sender.sendMessage(TextFormat.GREEN + "=== NukkitWebMap ===");
            sender.sendMessage(TextFormat.YELLOW + "Web: http://SERVER:" + webPort);
            sender.sendMessage("/webmap render - Render map");
            sender.sendMessage("/webmap wall <size> - Create map wall (1-100)");
            sender.sendMessage(TextFormat.AQUA + "--- Region Limit ---");
            sender.sendMessage("/webmap setcenter - Step 1: Set center point");
            sender.sendMessage("/webmap setradius - Step 2: Set radius point");
            sender.sendMessage("/webmap confirm - Step 3: Apply and render");
            sender.sendMessage("/webmap clearregion - Remove region limit");
            sender.sendMessage("/webmap regioninfo - Show region info");
            return true;
        }
        
        String subCmd = args[0].toLowerCase();
        
        if (subCmd.equals("render")) {
            if (!sender.isOp()) {
                sender.sendMessage(TextFormat.RED + "OP only");
                return true;
            }
            sender.sendMessage(TextFormat.YELLOW + "Rendering...");
            getServer().getScheduler().scheduleTask(this, new Task() {
                @Override
                public void onRun(int tick) {
                    renderAllWorlds();
                    sender.sendMessage(TextFormat.GREEN + "Done!");
                    inGameMapWall.clearCache(); // Clear cache so walls reload
                }
            });
            return true;
        }
        
        if (subCmd.equals("wall") && sender instanceof Player) {
            if (!sender.isOp()) {
                sender.sendMessage(TextFormat.RED + "OP only");
                return true;
            }
            int size = args.length > 1 ? Integer.parseInt(args[1]) : 3;
            if (size < 1 || size > 100) {
                sender.sendMessage(TextFormat.RED + "Size: 1-100");
                return true;
            }
            inGameMapWall.create((Player) sender, size);
            return true;
        }
        
        // ============ Region Commands ============
        
        if (subCmd.equals("setcenter") && sender instanceof Player) {
            if (!sender.isOp()) {
                sender.sendMessage(TextFormat.RED + "OP only");
                return true;
            }
            Player p = (Player) sender;
            String world = p.getLevel().getName();
            int x = p.getFloorX();
            int z = p.getFloorZ();
            
            pendingCenters.put(p.getName(), new int[]{x, z});
            
            sender.sendMessage(TextFormat.GREEN + "Center point set: " + TextFormat.WHITE + "(" + x + ", " + z + ")");
            sender.sendMessage(TextFormat.YELLOW + "Now fly to the edge and use /webmap setradius");
            return true;
        }
        
        if (subCmd.equals("setradius") && sender instanceof Player) {
            if (!sender.isOp()) {
                sender.sendMessage(TextFormat.RED + "OP only");
                return true;
            }
            Player p = (Player) sender;
            String playerName = p.getName();
            
            if (!pendingCenters.containsKey(playerName)) {
                sender.sendMessage(TextFormat.RED + "Please use /webmap setcenter first!");
                return true;
            }
            
            int[] center = pendingCenters.get(playerName);
            int cx = center[0];
            int cz = center[1];
            int px = p.getFloorX();
            int pz = p.getFloorZ();
            
            // Calculate distance (radius = half of side length)
            int dx = Math.abs(px - cx);
            int dz = Math.abs(pz - cz);
            int radius = Math.max(dx, dz);
            
            if (radius < 16) {
                sender.sendMessage(TextFormat.RED + "Radius too small! (min 16 blocks)");
                return true;
            }
            
            String world = p.getLevel().getName();
            RegionConfig rc = new RegionConfig(cx, cz, radius);
            rc.enabled = false; // Not enabled until confirm
            regionConfigs.put(world, rc);
            saveRegionConfig(world, rc);
            
            pendingCenters.remove(playerName);
            
            int sideLength = radius * 2;
            sender.sendMessage(TextFormat.GREEN + "Region configured for " + TextFormat.WHITE + world);
            sender.sendMessage(TextFormat.GRAY + "Center: (" + cx + ", " + cz + ")");
            sender.sendMessage(TextFormat.GRAY + "Size: " + sideLength + " x " + sideLength + " blocks");
            sender.sendMessage(TextFormat.GRAY + "From (" + (cx - radius) + ", " + (cz - radius) + ") to (" + (cx + radius) + ", " + (cz + radius) + ")");
            sender.sendMessage(TextFormat.YELLOW + "Use /webmap confirm to apply and render!");
            return true;
        }
        
        if (subCmd.equals("confirm") && sender instanceof Player) {
            if (!sender.isOp()) {
                sender.sendMessage(TextFormat.RED + "OP only");
                return true;
            }
            Player p = (Player) sender;
            String world = p.getLevel().getName();
            RegionConfig rc = regionConfigs.get(world);
            
            if (rc == null) {
                sender.sendMessage(TextFormat.RED + "No region configured! Use /webmap setcenter first.");
                return true;
            }
            
            rc.enabled = true;
            saveRegionConfig(world, rc);
            
            int sideLength = rc.radius * 2;
            sender.sendMessage(TextFormat.GREEN + "Region enabled for " + world + "!");
            sender.sendMessage(TextFormat.GRAY + "Size: " + sideLength + " x " + sideLength + " blocks");
            sender.sendMessage(TextFormat.YELLOW + "Rendering map...");
            
            getServer().getScheduler().scheduleTask(this, new Task() {
                @Override
                public void onRun(int tick) {
                    renderWorld(p.getLevel());
                    sender.sendMessage(TextFormat.GREEN + "Map rendered!");
                    inGameMapWall.clearCache(); // Force reload fresh images
                }
            });
            return true;
        }
        
        if (subCmd.equals("clearregion") && sender instanceof Player) {
            if (!sender.isOp()) {
                sender.sendMessage(TextFormat.RED + "OP only");
                return true;
            }
            Player p = (Player) sender;
            String world = p.getLevel().getName();
            
            RegionConfig rc = regionConfigs.get(world);
            if (rc != null) {
                rc.enabled = false;
                saveRegionConfig(world, rc);
            }
            regionConfigs.remove(world);
            
            sender.sendMessage(TextFormat.GREEN + "Region limit removed for " + world);
            sender.sendMessage(TextFormat.YELLOW + "Use /webmap render to apply");
            return true;
        }
        
        if (subCmd.equals("regioninfo")) {
            if (sender instanceof Player) {
                Player p = (Player) sender;
                String world = p.getLevel().getName();
                RegionConfig rc = regionConfigs.get(world);
                
                if (rc != null && rc.enabled) {
                    int side = rc.radius * 2;
                    sender.sendMessage(TextFormat.GREEN + "=== Region: " + world + " ===");
                    sender.sendMessage(TextFormat.GRAY + "Center: (" + rc.centerX + ", " + rc.centerZ + ")");
                    sender.sendMessage(TextFormat.GRAY + "Size: " + side + " x " + side + " blocks");
                    sender.sendMessage(TextFormat.GRAY + "X: " + (rc.centerX - rc.radius) + " to " + (rc.centerX + rc.radius));
                    sender.sendMessage(TextFormat.GRAY + "Z: " + (rc.centerZ - rc.radius) + " to " + (rc.centerZ + rc.radius));
                } else {
                    sender.sendMessage(TextFormat.YELLOW + "No region limit for " + world);
                    sender.sendMessage(TextFormat.GRAY + "Map will render entire explored world");
                }
            } else {
                sender.sendMessage("All region configs:");
                for (Map.Entry<String, RegionConfig> e : regionConfigs.entrySet()) {
                    RegionConfig rc = e.getValue();
                    if (rc.enabled) {
                        sender.sendMessage(e.getKey() + ": center(" + rc.centerX + "," + rc.centerZ + ") radius=" + rc.radius);
                    }
                }
            }
            return true;
        }
        
        return false;
    }
    
    public void renderAllWorlds() {
        for (String worldName : renderWorlds) {
            Level level = getServer().getLevelByName(worldName);
            if (level != null) {
                renderWorld(level);
            } else {
                getLogger().warning("World not found: " + worldName);
            }
        }
    }
    
    public void renderWorld(Level level) {
        String worldName = level.getName();
        getLogger().info("Rendering: " + worldName);
        
        try {
            RegionConfig rc = regionConfigs.get(worldName);
            MapRenderer.RenderResult result;
            
            if (rc != null && rc.enabled) {
                getLogger().info("Using region limit: center(" + rc.centerX + "," + rc.centerZ + ") radius=" + rc.radius);
                result = mapRenderer.renderRegion(level, rc.centerX, rc.centerZ, rc.radius);
            } else {
                result = mapRenderer.renderLevelWithInfo(level);
            }
            
            if (result != null && result.image != null) {
                File imageFile = new File(mapFolder, worldName + ".png");
                ImageIO.write(result.image, "PNG", imageFile);
                
                MapInfo info = new MapInfo();
                info.worldName = worldName;
                info.width = result.image.getWidth();
                info.height = result.image.getHeight();
                info.lastUpdate = System.currentTimeMillis();
                info.blockMinX = result.minBlockX;
                info.blockMaxX = result.maxBlockX;
                info.blockMinZ = result.minBlockZ;
                info.blockMaxZ = result.maxBlockZ;
                info.centerX = (result.minBlockX + result.maxBlockX) / 2;
                info.centerZ = (result.minBlockZ + result.maxBlockZ) / 2;
                renderedMaps.put(worldName, info);
                
                getLogger().info("Rendered: " + result.image.getWidth() + "x" + result.image.getHeight());
            }
        } catch (Exception e) {
            getLogger().error("Render failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    public File getMapFolder() { return mapFolder; }
    public Map<String, MapInfo> getRenderedMaps() { return renderedMaps; }
    public Collection<Player> getOnlinePlayers() { return getServer().getOnlinePlayers().values(); }
    public Map<Long, BufferedImage> getMapImages() { return mapImages; }
    
    public static class MapInfo {
        public String worldName;
        public int width, height;
        public long lastUpdate;
        public int centerX, centerZ;
        public int blockMinX, blockMaxX, blockMinZ, blockMaxZ;
    }
}
