package com.webmap;

import cn.nukkit.Player;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class WebServer {
    
    private final WebMapPlugin plugin;
    private final int port;
    private HttpServer server;
    
    public WebServer(WebMapPlugin plugin, int port) {
        this.plugin = plugin;
        this.port = port;
    }
    
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", new MainHandler());
        server.createContext("/map/", new MapHandler());
        server.createContext("/api/players", new PlayersHandler());
        server.createContext("/api/maps", new MapsHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
    }
    
    public void stop() {
        if (server != null) server.stop(0);
    }
    
    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
    
    class MainHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String html = getHtml();
            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(200, bytes.length);
            OutputStream os = ex.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
    
    class MapHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            String worldName = java.net.URLDecoder.decode(
                path.replace("/map/", "").replace(".png", ""), 
                StandardCharsets.UTF_8.name()
            );
            
            File mapFile = new File(plugin.getMapFolder(), worldName + ".png");
            if (mapFile.exists()) {
                byte[] data = readFile(mapFile);
                ex.getResponseHeaders().set("Content-Type", "image/png");
                ex.getResponseHeaders().set("Cache-Control", "no-cache");
                ex.sendResponseHeaders(200, data.length);
                OutputStream os = ex.getResponseBody();
                os.write(data);
                os.close();
            } else {
                String msg = "Not found";
                byte[] bytes = msg.getBytes(StandardCharsets.UTF_8);
                ex.sendResponseHeaders(404, bytes.length);
                ex.getResponseBody().write(bytes);
            }
            ex.close();
        }
    }
    
    class PlayersHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            StringBuilder json = new StringBuilder("{\"players\":[");
            boolean first = true;
            for (Player p : plugin.getOnlinePlayers()) {
                if (!first) json.append(",");
                json.append("{\"name\":\"").append(escapeJson(p.getName())).append("\"");
                json.append(",\"world\":\"").append(escapeJson(p.getLevel().getName())).append("\"");
                json.append(",\"x\":").append(p.getFloorX());
                json.append(",\"y\":").append(p.getFloorY());
                json.append(",\"z\":").append(p.getFloorZ()).append("}");
                first = false;
            }
            json.append("]}");
            
            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, bytes.length);
            OutputStream os = ex.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
    
    class MapsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            StringBuilder json = new StringBuilder("{\"maps\":[");
            boolean first = true;
            for (WebMapPlugin.MapInfo info : plugin.getRenderedMaps().values()) {
                if (!first) json.append(",");
                json.append("{\"name\":\"").append(escapeJson(info.worldName)).append("\"");
                json.append(",\"width\":").append(info.width);
                json.append(",\"height\":").append(info.height);
                json.append(",\"centerX\":").append(info.centerX);
                json.append(",\"centerZ\":").append(info.centerZ);
                json.append(",\"minX\":").append(info.blockMinX);
                json.append(",\"maxX\":").append(info.blockMaxX);
                json.append(",\"minZ\":").append(info.blockMinZ);
                json.append(",\"maxZ\":").append(info.blockMaxZ);
                json.append(",\"lastUpdate\":").append(info.lastUpdate).append("}");
                first = false;
            }
            json.append("]}");
            
            byte[] bytes = json.toString().getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.sendResponseHeaders(200, bytes.length);
            OutputStream os = ex.getResponseBody();
            os.write(bytes);
            os.close();
        }
    }
    
    private byte[] readFile(File file) throws IOException {
        FileInputStream fis = new FileInputStream(file);
        byte[] data = new byte[(int) file.length()];
        fis.read(data);
        fis.close();
        return data;
    }
    
    private String getHtml() {
        return "<!DOCTYPE html>\n" +
"<html lang=\"zh-CN\">\n" +
"<head>\n" +
"<meta charset=\"UTF-8\">\n" +
"<meta http-equiv=\"Content-Type\" content=\"text/html; charset=utf-8\">\n" +
"<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n" +
"<title>Server Map</title>\n" +
"<style>\n" +
"*{margin:0;padding:0;box-sizing:border-box}\n" +
"body{font-family:Arial,sans-serif;background:#1a1a2e;color:#fff;min-height:100vh}\n" +
".header{background:linear-gradient(180deg,#4a7c2f,#2d5a1a);padding:15px;text-align:center;border-bottom:4px solid #1a1a1a}\n" +
".header h1{font-size:28px;text-shadow:2px 2px #1a1a1a}\n" +
".info{color:#90ff90;font-size:16px;margin-top:5px}\n" +
".container{max-width:98%;margin:15px auto;padding:0 10px}\n" +
".mc-btn{font-size:16px;padding:8px 16px;background:linear-gradient(180deg,#7a7a7a,#4a4a4a);border:3px solid #2a2a2a;color:#fff;cursor:pointer;margin:3px}\n" +
".mc-btn:hover{background:linear-gradient(180deg,#8a8a8a,#5a5a5a)}\n" +
".mc-btn.active{background:linear-gradient(180deg,#5a9a5a,#3a7a3a)}\n" +
".map-box{background:#0a0a15;border:4px solid #333;border-radius:4px;position:relative}\n" +
".map-wrap{position:relative;overflow:auto;height:75vh;width:100%;cursor:grab;background:#0a0a15}\n" +
".map-wrap:active{cursor:grabbing}\n" +
"#mapImg{display:block;image-rendering:pixelated;image-rendering:-moz-crisp-edges;image-rendering:crisp-edges;transform-origin:0 0}\n" +
"#markers{position:absolute;top:0;left:0;pointer-events:none}\n" +
".player{position:absolute;width:12px;height:12px;background:#ff3333;border:2px solid #fff;border-radius:50%;transform:translate(-50%,-50%);z-index:100;pointer-events:auto;box-shadow:0 0 6px rgba(255,0,0,0.8)}\n" +
".player .tag{position:absolute;bottom:100%;left:50%;transform:translateX(-50%);background:rgba(0,0,0,.9);padding:3px 8px;white-space:nowrap;font-size:13px;margin-bottom:4px;border-radius:3px}\n" +
".ctrl{position:absolute;top:10px;right:10px;z-index:200;display:flex;flex-direction:column;gap:5px}\n" +
".ctrl .mc-btn{width:40px;height:40px;font-size:22px;padding:0}\n" +
".status{position:absolute;bottom:10px;left:10px;background:rgba(0,0,0,.85);padding:6px 12px;font-size:14px;border-radius:4px}\n" +
".zoom-info{position:absolute;bottom:10px;right:10px;background:rgba(0,0,0,.85);padding:6px 12px;font-size:14px;border-radius:4px}\n" +
".sidebar{position:fixed;right:15px;top:90px;width:200px;background:#252540;border:2px solid #3a3a5a;border-radius:6px;padding:10px;max-height:60vh;overflow-y:auto}\n" +
".sidebar h3{font-size:18px;color:#90ff90;margin-bottom:10px;border-bottom:1px solid #3a3a5a;padding-bottom:6px}\n" +
".plist{list-style:none}\n" +
".pitem{padding:6px 8px;background:#1a1a30;margin-bottom:4px;border:1px solid #2a2a4a;border-radius:3px;cursor:pointer;font-size:13px}\n" +
".pitem:hover{background:#2a2a50}\n" +
".pitem small{color:#888;font-size:11px}\n" +
"@media(max-width:900px){.sidebar{display:none}}\n" +
"</style>\n" +
"</head>\n" +
"<body>\n" +
"<div class=\"header\">\n" +
"<h1>Server Map</h1>\n" +
"<div class=\"info\">Online: <span id=\"cnt\">0</span></div>\n" +
"</div>\n" +
"<div class=\"container\">\n" +
"<div id=\"worlds\" style=\"margin-bottom:10px\"></div>\n" +
"<div class=\"map-box\">\n" +
"<div class=\"ctrl\">\n" +
"<button class=\"mc-btn\" onclick=\"zoomIn()\" title=\"Zoom In\">+</button>\n" +
"<button class=\"mc-btn\" onclick=\"zoomOut()\" title=\"Zoom Out\">-</button>\n" +
"<button class=\"mc-btn\" onclick=\"resetZoom()\" title=\"Reset\">R</button>\n" +
"</div>\n" +
"<div class=\"map-wrap\" id=\"wrap\">\n" +
"<img id=\"mapImg\" onload=\"onImgLoad()\">\n" +
"<div id=\"markers\"></div>\n" +
"</div>\n" +
"<div class=\"status\">X: <span id=\"posX\">-</span>, Z: <span id=\"posZ\">-</span></div>\n" +
"<div class=\"zoom-info\">Zoom: <span id=\"zm\">100</span>%</div>\n" +
"</div>\n" +
"</div>\n" +
"<div class=\"sidebar\">\n" +
"<h3>Players</h3>\n" +
"<ul class=\"plist\" id=\"plist\"></ul>\n" +
"</div>\n" +
"<script>\n" +
"var world='',info={},zoom=1,players=[],imgW=0,imgH=0;\n" +
"function loadMaps(){\n" +
"  fetch('/api/maps').then(function(r){return r.json();}).then(function(d){\n" +
"    var w=document.getElementById('worlds');\n" +
"    w.innerHTML='';\n" +
"    d.maps.forEach(function(m,i){\n" +
"      info[m.name]=m;\n" +
"      var b=document.createElement('button');\n" +
"      b.className='mc-btn'+(i===0?' active':'');\n" +
"      b.textContent=m.name;\n" +
"      b.onclick=function(){sel(m.name);};\n" +
"      w.appendChild(b);\n" +
"      if(i===0){world=m.name;loadImg();}\n" +
"    });\n" +
"  });\n" +
"}\n" +
"function sel(n){\n" +
"  world=n;\n" +
"  var btns=document.querySelectorAll('#worlds .mc-btn');\n" +
"  btns.forEach(function(b){b.classList.toggle('active',b.textContent===n);});\n" +
"  loadImg();\n" +
"}\n" +
"function loadImg(){\n" +
"  document.getElementById('mapImg').src='/map/'+encodeURIComponent(world)+'.png?t='+Date.now();\n" +
"}\n" +
"function onImgLoad(){\n" +
"  var img=document.getElementById('mapImg');\n" +
"  imgW=img.naturalWidth;\n" +
"  imgH=img.naturalHeight;\n" +
"  resetZoom();\n" +
"}\n" +
"function resetZoom(){\n" +
"  var wrap=document.getElementById('wrap');\n" +
"  var maxW=wrap.clientWidth-20;\n" +
"  var maxH=wrap.clientHeight-20;\n" +
"  zoom=Math.min(maxW/imgW,maxH/imgH,1);\n" +
"  if(zoom<0.05)zoom=0.05;\n" +
"  applyZoom();\n" +
"}\n" +
"function updatePlayers(){\n" +
"  fetch('/api/players').then(function(r){return r.json();}).then(function(d){\n" +
"    players=d.players;\n" +
"    document.getElementById('cnt').textContent=players.length;\n" +
"    var l=document.getElementById('plist');\n" +
"    l.innerHTML='';\n" +
"    players.forEach(function(p){\n" +
"      var li=document.createElement('li');\n" +
"      li.className='pitem';\n" +
"      li.innerHTML='<b>'+p.name+'</b><br><small>'+p.world+': '+p.x+', '+p.y+', '+p.z+'</small>';\n" +
"      li.onclick=function(){focusPlayer(p);};\n" +
"      l.appendChild(li);\n" +
"    });\n" +
"    updateMarkers();\n" +
"  });\n" +
"}\n" +
"function updateMarkers(){\n" +
"  var c=document.getElementById('markers');\n" +
"  c.innerHTML='';\n" +
"  var m=info[world];\n" +
"  if(!m||!imgW)return;\n" +
"  var img=document.getElementById('mapImg');\n" +
"  c.style.width=img.style.width;\n" +
"  c.style.height=img.style.height;\n" +
"  var rangeX=m.maxX-m.minX,rangeZ=m.maxZ-m.minZ;\n" +
"  players.filter(function(p){return p.world===world;}).forEach(function(p){\n" +
"    var px=(p.x-m.minX)/rangeX*100;\n" +
"    var pz=(p.z-m.minZ)/rangeZ*100;\n" +
"    if(px>=0&&px<=100&&pz>=0&&pz<=100){\n" +
"      var d=document.createElement('div');\n" +
"      d.className='player';\n" +
"      d.style.left=px+'%';\n" +
"      d.style.top=pz+'%';\n" +
"      d.innerHTML='<div class=\"tag\">'+p.name+'</div>';\n" +
"      c.appendChild(d);\n" +
"    }\n" +
"  });\n" +
"}\n" +
"function focusPlayer(p){\n" +
"  if(p.world!==world)sel(p.world);\n" +
"  var m=info[world];\n" +
"  if(!m)return;\n" +
"  var wrap=document.getElementById('wrap');\n" +
"  var px=(p.x-m.minX)/(m.maxX-m.minX);\n" +
"  var pz=(p.z-m.minZ)/(m.maxZ-m.minZ);\n" +
"  zoom=2;\n" +
"  applyZoom();\n" +
"  setTimeout(function(){\n" +
"    wrap.scrollLeft=px*imgW*zoom-wrap.clientWidth/2;\n" +
"    wrap.scrollTop=pz*imgH*zoom-wrap.clientHeight/2;\n" +
"  },50);\n" +
"}\n" +
"function zoomIn(){zoom=Math.min(8,zoom*1.5);applyZoom();}\n" +
"function zoomOut(){zoom=Math.max(0.1,zoom/1.5);applyZoom();}\n" +
"function applyZoom(){\n" +
"  var img=document.getElementById('mapImg');\n" +
"  var w=Math.round(imgW*zoom),h=Math.round(imgH*zoom);\n" +
"  img.style.width=w+'px';\n" +
"  img.style.height=h+'px';\n" +
"  document.getElementById('zm').textContent=Math.round(zoom*100);\n" +
"  updateMarkers();\n" +
"}\n" +
"document.getElementById('wrap').onmousemove=function(e){\n" +
"  var m=info[world];\n" +
"  if(!m)return;\n" +
"  var img=document.getElementById('mapImg');\n" +
"  var r=img.getBoundingClientRect();\n" +
"  var x=(e.clientX-r.left)/r.width;\n" +
"  var z=(e.clientY-r.top)/r.height;\n" +
"  var wx=Math.floor(m.minX+x*(m.maxX-m.minX));\n" +
"  var wz=Math.floor(m.minZ+z*(m.maxZ-m.minZ));\n" +
"  document.getElementById('posX').textContent=wx;\n" +
"  document.getElementById('posZ').textContent=wz;\n" +
"};\n" +
"loadMaps();\n" +
"updatePlayers();\n" +
"setInterval(updatePlayers,2000);\n" +
"setInterval(loadImg,180000);\n" +
"</script>\n" +
"</body>\n" +
"</html>";
    }
}
