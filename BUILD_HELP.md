# 🔧 编译帮助 / Build Help

如果遇到编译失败，请按照以下步骤操作。

---

## ❌ 常见错误：仓库连接失败

```
Could not resolve cn.nukkit:Nukkit:MOT-SNAPSHOT
Could not resolve com.github.PowerNukkitX:PowerNukkitX
```

---

## ✅ 解决方案：使用本地 JAR

### 步骤 1：下载服务端 JAR

从以下地址下载你使用的服务端核心：

| 服务端 | 下载地址 |
|--------|----------|
| **PowerNukkitX** | https://github.com/PowerNukkitX/PowerNukkitX/releases |
| **Nukkit-MOT** | https://github.com/MemoriesOfTime/Nukkit-MOT/releases |
| **Nukkit** | https://ci.opencollab.dev/job/NukkitX/job/Nukkit/job/master/ |

### 步骤 2：放入 libs 文件夹

将下载的 JAR 文件重命名为 `nukkit.jar`，放入 `libs` 文件夹：

```
NukkitWebMap/
├── libs/
│   └── nukkit.jar    ← 放这里
├── src/
├── build.gradle
└── ...
```

### 步骤 3：修改 build.gradle

编辑 `build.gradle`，注释掉在线依赖，启用本地依赖：

```gradle
dependencies {
    // 注释掉这行
    // compileOnly 'com.github.PowerNukkitX:PowerNukkitX:2.0.0'
    
    // 启用这行
    compileOnly files('libs/nukkit.jar')
}
```

### 步骤 4：重新编译

```bash
# Windows
build.bat

# Linux/Mac
./gradlew build
```

---

## 📝 完整的 build.gradle（本地版本）

```gradle
plugins {
    id 'java'
}

group = 'com.webmap'
version = '1.0.0'

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

repositories {
    mavenCentral()
}

dependencies {
    // 使用本地 JAR
    compileOnly files('libs/nukkit.jar')
}

jar {
    archiveBaseName = 'NukkitWebMap'
}
```

---

## ⚠️ Java 版本问题

如果提示 Java 版本错误：

```
Unsupported class file major version 61
```

请确保使用 **Java 17** 或更高版本：

```bash
java -version
```

下载 Java 17: https://adoptium.net/

---

## 🎯 编译成功后

输出文件位于：
```
build/libs/NukkitWebMap-1.0.0.jar
```

将此文件复制到服务器的 `plugins` 文件夹即可。

---

## ❓ 还有问题？

1. 确保网络畅通
2. 尝试使用 VPN
3. 使用本地 JAR 方式（最可靠）
4. 在 GitHub 提交 Issue
