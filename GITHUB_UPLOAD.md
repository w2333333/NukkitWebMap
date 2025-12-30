# 📤 GitHub 上传指南 / Upload Guide

---

## 🚀 覆盖上传（强制推送）/ Force Push

如果仓库已存在，使用以下命令**覆盖上传**：

```bash
# 1. 进入项目文件夹
cd NukkitWebMap

# 2. 初始化 Git（如果还没有）
git init

# 3. 添加所有文件
git add .

# 4. 提交
git commit -m "Update: NukkitWebMap v1.0.0"

# 5. 设置主分支
git branch -M main

# 6. 添加远程仓库（替换为你的用户名）
git remote add origin https://github.com/w2333333/NukkitWebMap.git

# 7. 强制推送（覆盖远程所有内容）
git push -f origin main
```

### ⚠️ 一键覆盖命令 / One-Line Force Push

```bash
cd NukkitWebMap && git init && git add . && git commit -m "Update: NukkitWebMap v1.0.0" && git branch -M main && git remote add origin https://github.com/w2333333/NukkitWebMap.git 2>/dev/null; git push -f origin main
```

### 如果提示 remote 已存在 / If remote already exists

```bash
git remote set-url origin https://github.com/w2333333/NukkitWebMap.git
git push -f origin main
```

---

## 📝 首次上传 / First Time Upload

### 方法一：命令行 / Command Line

```bash
# 1. 创建 GitHub 仓库（在网页上）
#    https://github.com/new
#    仓库名: NukkitWebMap

# 2. 在本地执行
cd NukkitWebMap
git init
git add .
git commit -m "Initial commit: NukkitWebMap v1.0.0"
git branch -M main
git remote add origin https://github.com/w2333333/NukkitWebMap.git
git push -u origin main
```

### 方法二：网页上传 / Web Upload

```
1️⃣  打开 https://github.com/new
2️⃣  创建仓库 NukkitWebMap
3️⃣  点击 Add file → Upload files
4️⃣  拖入所有文件
5️⃣  点击 Commit changes
```

---

## 🔐 登录问题 / Authentication

### Personal Access Token（推荐）

```
1️⃣  打开 https://github.com/settings/tokens
2️⃣  Generate new token (classic)
3️⃣  勾选 repo 权限
4️⃣  生成并复制 token
5️⃣  推送时用 token 作为密码
```

### SSH 密钥

```bash
# 生成密钥
ssh-keygen -t ed25519 -C "your-email@example.com"

# 复制公钥
cat ~/.ssh/id_ed25519.pub

# 添加到 GitHub: Settings → SSH Keys → New SSH Key

# 改用 SSH 地址
git remote set-url origin git@github.com:w2333333/NukkitWebMap.git
git push -f origin main
```

---

## 📦 发布 Release / Create Release

```
1️⃣  编译: build.bat 或 ./gradlew build
2️⃣  打开仓库 → Releases → Create new release
3️⃣  Tag: v1.0.0
4️⃣  Title: NukkitWebMap v1.0.0
5️⃣  上传 build/libs/NukkitWebMap-1.0.0.jar
6️⃣  发布
```

### Release 描述模板

```markdown
## 🎉 NukkitWebMap v1.0.0

### ✨ Features / 功能
- 🌐 Web map with real-time player tracking / 网页地图实时追踪
- 🖼️ In-game map wall up to 100×100 / 游戏内地图墙
- ⚡ Zero lag async processing / 异步处理零卡顿

### 📥 Installation / 安装
1. Download `NukkitWebMap-1.0.0.jar`
2. Put in `plugins` folder
3. Restart server
4. Open `http://SERVER:8123`

### 📋 Commands / 命令
- `/webmap render` - Render map
- `/webmap wall <size>` - Create map wall
```

---

## 🔄 后续更新 / Future Updates

```bash
# 修改代码后
git add .
git commit -m "描述你的更改"
git push

# 强制覆盖
git push -f origin main
```

---

## ❓ 常见问题 / FAQ

### Q: Permission denied
```bash
# 检查是否设置了正确的用户
git config --global user.name "w2333333"
git config --global user.email "your-email@example.com"
```

### Q: rejected - non-fast-forward
```bash
# 强制推送
git push -f origin main
```

### Q: remote origin already exists
```bash
# 更新远程地址
git remote set-url origin https://github.com/w2333333/NukkitWebMap.git
```

### Q: fatal: not a git repository
```bash
# 初始化
git init
```

---

**祝上传顺利！🚀**
