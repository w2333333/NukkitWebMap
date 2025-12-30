<div align="center">

# 🗺️ NukkitWebMap

### A Powerful Web Map Plugin for Nukkit/PowerNukkitX
### Nukkit/PowerNukkitX 强大的网页地图插件

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Nukkit](https://img.shields.io/badge/Nukkit-Compatible-green.svg)](https://github.com/CloudburstMC/Nukkit)
[![PowerNukkitX](https://img.shields.io/badge/PowerNukkitX-Compatible-blue.svg)](https://github.com/PowerNukkitX/PowerNukkitX)
[![Java](https://img.shields.io/badge/Java-8%2B-orange.svg)](https://www.java.com)

**Author / 作者:** w2333333

---

[English](#-features) | [中文](#-功能特性)

</div>

---

## ✨ Features

| Feature | Description |
|:-------:|-------------|
| 🌐 | **Web Map** - View server map in browser with real-time player positions |
| 🖼️ | **In-Game Map Wall** - Create map walls up to 100×100 with live player markers |
| 📍 | **Real-Time Tracking** - Player positions update every 2 seconds (configurable) |
| 🎨 | **High Resolution** - 12 pixels per block with accurate Minecraft colors |
| 🗺️ | **Region Limit** - Restrict map to specific area for better quality |
| ⚡ | **Zero Lag** - Async processing + batched packets = smooth performance |
| 🔧 | **Configurable** - Customize render interval, update frequency, and more |

## ✨ 功能特性

| 特性 | 说明 |
|:----:|------|
| 🌐 | **网页地图** - 浏览器查看服务器地图，实时显示玩家位置 |
| 🖼️ | **游戏内地图墙** - 创建最大100×100的地图墙，带实时玩家标记 |
| 📍 | **实时追踪** - 玩家位置每2秒更新（可配置） |
| 🎨 | **高分辨率** - 每方块12像素，精确的MC颜色 |
| 🗺️ | **区域限制** - 限定渲染范围，获得更好的地图质量 |
| ⚡ | **零卡顿** - 异步处理 + 分批发包 = 流畅体验 |
| 🔧 | **可配置** - 自定义渲染间隔、更新频率等 |

---

## 📸 Screenshots / 截图

<table>
<tr>
<td width="50%">

### 🌐 Web Interface / 网页界面

```
┌──────────────────────────────────┐
│  🗺️ Server Map                   │
│  ┌────────────────────────────┐  │
│  │                            │  │
│  │      [World Map]           │  │
│  │          🔴 Player1        │  │
│  │               🔴 Player2   │  │
│  │                            │  │
│  └────────────────────────────┘  │
│  👥 Online: 2    🔍 Zoom: 100%   │
└──────────────────────────────────┘
```

</td>
<td width="50%">

### 🖼️ In-Game Wall / 游戏内地图墙

```
┌──────────────────────────────────┐
│                                  │
│     ┌─────┬─────┬─────┐          │
│     │     │     │     │          │
│     ├─────┼─────┼─────┤ 3×3 Wall │
│     │     │ 🔴  │     │ 地图墙   │
│     ├─────┼─────┼─────┤          │
│     │     │     │     │          │
│     └─────┴─────┴─────┘          │
│                                  │
└──────────────────────────────────┘
```

</td>
</tr>
</table>

---

## 📋 Commands / 命令

### Basic Commands / 基础命令

| Command | Description | 说明 |
|---------|-------------|------|
| `/webmap` | Show help | 显示帮助 |
| `/webmap render` | Render map now | 立即渲染地图 |
| `/webmap wall <size>` | Create map wall (1-100) | 创建地图墙 (1-100) |

### Region Commands / 区域命令 (OP)

| Command | Description | 说明 |
|---------|-------------|------|
| `/webmap setcenter` | Step 1: Set center | 第一步：设置中心点 |
| `/webmap setradius` | Step 2: Set radius | 第二步：设置半径 |
| `/webmap confirm` | Step 3: Apply & render | 第三步：应用并渲染 |
| `/webmap clearregion` | Remove region limit | 移除区域限制 |
| `/webmap regioninfo` | Show region info | 显示区域信息 |

---

## 🚀 Quick Start / 快速开始

### Installation / 安装

```
1️⃣  Download NukkitWebMap-1.0.0.jar
    下载 NukkitWebMap-1.0.0.jar

2️⃣  Put in server's plugins folder
    放入服务器 plugins 文件夹

3️⃣  Restart server
    重启服务器

4️⃣  Open http://YOUR_IP:8123
    打开 http://服务器IP:8123

5️⃣  Use /webmap wall 3 in-game
    游戏内使用 /webmap wall 3
```

### Create Map Wall / 创建地图墙

```
1️⃣  /webmap render          ← Render the map first / 先渲染地图
2️⃣  Stand facing a wall     ← 面朝墙壁站立
3️⃣  /webmap wall 5          ← Create 5×5 wall / 创建5×5地图墙
```

### Set Region Limit / 设置区域限制

```
1️⃣  Stand at center         ← 站在中心点
2️⃣  /webmap setcenter       ← 设置中心
3️⃣  Fly to edge             ← 飞到边缘
4️⃣  /webmap setradius       ← 设置半径
5️⃣  /webmap confirm         ← 确认并渲染
```

---

## ⚙️ Configuration / 配置

### config.yml

```yaml
# Web server port / 网页端口
web-port: 8123

# Worlds to render / 要渲染的世界
render-worlds:
  - world

# Auto render interval (hours, 0=disabled)
# 自动渲染间隔（小时，0=禁用）
render-interval-hours: 24

# Player marker update (seconds)
# 玩家标记更新间隔（秒）
marker-update-seconds: 2

# Chunk scan radius / 区块扫描半径
scan-radius: 200
```

### Config Options / 配置选项

| Option | Default | Description | 说明 |
|--------|---------|-------------|------|
| `web-port` | 8123 | Web server port | 网页端口 |
| `render-interval-hours` | 24 | Auto render interval | 自动渲染间隔 |
| `marker-update-seconds` | 2 | Player marker update | 玩家标记更新 |
| `scan-radius` | 200 | Chunk scan radius | 扫描半径 |

---

## 🎨 Player Markers / 玩家标记

Player markers on the in-game map wall use the **same proportions** as the web interface:

游戏内地图墙上的玩家标记与网页界面使用**相同比例**：

| Wall Size | Dot Size | Font Size | 墙尺寸 | 标点 | 字体 |
|:---------:|:--------:|:---------:|:------:|:----:|:----:|
| 3×3 | 10px | 12px | 3×3 | 10像素 | 12像素 |
| 10×10 | 32px | 28px | 10×10 | 32像素 | 28像素 |
| 50×50 | 160px | 140px | 50×50 | 160像素 | 140像素 |

---

## ⚡ Performance / 性能优化

### Architecture / 架构

```
┌─────────────────────────────────────────────────────────┐
│  📍 Main Thread (every 2s) / 主线程（每2秒）             │
│  └─► Collect player positions (instant)                 │
│      收集玩家位置（瞬间完成）                            │
└─────────────────────────────────────────────────────────┘
                            ⬇️
┌─────────────────────────────────────────────────────────┐
│  🔄 Async Thread (background) / 异步线程（后台）         │
│  ├─► Load cached base image / 加载缓存底图              │
│  ├─► Draw player markers / 绘制玩家标记                 │
│  └─► Split into 128×128 pieces / 切分成128×128小块      │
└─────────────────────────────────────────────────────────┘
                            ⬇️
┌─────────────────────────────────────────────────────────┐
│  📤 Batched Sending (10/tick) / 分批发送（每tick10个）   │
│  └─► Spread over time = NO LAG / 分散发送 = 不卡        │
└─────────────────────────────────────────────────────────┘
```

### Why No Lag? / 为什么不卡？

```
❌ Before / 之前:
   50×50 wall × 10 players = 2500 packets instantly = LAG! 💥
   50×50墙 × 10玩家 = 2500数据包瞬间发送 = 卡顿！💥

✅ After / 之后:
   2500 packets queued, send 10/tick = 250 ticks = SMOOTH! ✨
   2500数据包排队，每tick发10个 = 250tick = 流畅！✨
```

### Reduce Lag Further / 进一步减少卡顿

```yaml
# config.yml

# Less frequent updates / 降低更新频率
marker-update-seconds: 5

# Disable auto render / 禁用自动渲染
render-interval-hours: 0
```

---

## 📦 Build / 编译

### Windows

```batch
build.bat
```

### Linux / Mac

```bash
chmod +x gradlew
./gradlew build
```

### Output / 输出

```
build/libs/NukkitWebMap-1.0.0.jar
```

---

## 📋 Requirements / 环境要求

| Requirement | Version | 要求 | 版本 |
|-------------|---------|------|------|
| Nukkit / PowerNukkitX | Latest | 最新版 | 最新 |
| Java | 8+ | Java | 8+ |
| Port | 8123 (configurable) | 端口 | 8123（可配置） |

---

## 📜 License / 许可证

MIT License - Feel free to use, modify, and distribute.

MIT 许可证 - 可自由使用、修改和分发。

---

## 🤝 Contributing / 贡献

Issues and pull requests are welcome!

欢迎提交 Issue 和 Pull Request！

---

<div align="center">

### ⭐ Star this repo if you find it useful!
### 觉得有用请点 Star！⭐

---

**Made with ❤️ by w2333333**

</div>
