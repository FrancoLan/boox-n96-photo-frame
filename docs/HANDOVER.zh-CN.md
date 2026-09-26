# BOOX N96 相框交接手册

## 当前能力

这套系统由 Mac 服务端和 BOOX N96 客户端组成。Mac 每 10 分钟读取同一个 iCloud 共享相册，生成适合 1072×1448 墨水屏的灰度图片；BOOX 在局域网内同步并离线轮播。

1.2.1 更新已构建并下发，包含新照片优先播放逻辑，并将 Android `versionCode` 升至 8，以确保高于设备当前的 1.2.0。`./scripts/check.sh` 通过；安装命令 `d40024de-a8de-4e14-8b83-6fac5dffaea0` 的 APK 已验证，等待设备端 Android 安装确认。此前相同版本的 APK 被设备报告“未安装”。BOOX 需要在屏幕上确认安装，Mac 的 ADB 当前未发现 BOOX；安装后再核对设备版本与播放行为。

主要行为：

- 每张照片随机停留 10–20 分钟。
- 同步完成后立即优先播放新加入的照片，之后从同步时被打断照片的下一张恢复原有播放顺序。
- 图片右下角显示拍摄地点与当地拍摄时间。
- 自动检测人脸并移动裁切框；多人横向分布过宽时完整显示。完整显示产生的上下或左右空白采用照片相邻边缘的深色化平均色，取色失败时使用黑色，不再使用白底。
- 设备断网或 Mac 休眠时继续使用本地缓存。
- 同步成功后删除已经不在最新清单中的旧渲染缓存。
- 无线管理只允许固定命令，不提供 Shell，也不需要无线 ADB。

## 日常操作

```sh
./scripts/manage.sh status       # 查看最后在线状态
./scripts/manage.sh next         # 下一张
./scripts/manage.sh previous     # 上一张
./scripts/manage.sh sync         # 立即同步
./scripts/manage.sh restart      # 重载播放列表并同步
./scripts/manage.sh disable      # 退出相框，控制服务继续在线
./scripts/manage.sh enable       # 重新打开相框
./scripts/manage.sh diagnose     # 上传最小化诊断信息
```

命令最多需要约 60 秒执行，这是为了降低旧设备长期联网的耗电。

## 新增或删除照片

直接修改 iCloud 共享相册。下一次 Mac 同步生成新清单，随后 BOOX 在启动、每小时自动同步或收到 `sync` 命令时更新缓存。

如需立即生效：

```sh
./scripts/sync-now.sh
./scripts/manage.sh sync
```

## 修正单张照片取景

先在 `data/public/v1/manifest.json` 中按地点或时间找到照片记录的 `id`，然后加入 `server/config.local.json`：

```json
"fitPhotoIds": ["PHOTO-RECORD-ID"]
```

运行 `sync-now.sh` 和远程 `sync`。新版本客户端会自动清理旧裁切文件。

## 重新安装客户端

1. 用 USB 连接 BOOX。
2. 在 BOOX 打开开发者选项和 USB 调试。
3. 接受设备上出现的电脑授权提示。
4. 运行：

```sh
./scripts/install-android.sh
```

安装脚本会构建 APK、写入服务器地址与令牌、推送配置并启动相框。签名密钥位于被忽略的 `android/build/debug.keystore`；不要把它提交到 GitHub。

## 故障排查

### `status` 长时间没有更新

- 确认 BOOX 已连接 Wi-Fi。
- 确认 Mac 没有休眠，并且服务正在监听配置的 LAN 地址和端口。
- BOOX 浏览器不适合验证带令牌接口；优先看 Mac 服务日志或使用 USB ADB。
- 如果路由器给 Mac 分配了新地址，修改 `listenHost` 后重新安装设备配置。

### BOOX 有图但不再更新

- 运行 `sync-now.sh`，确认没有 iCloud 下载错误。
- 运行 `manage.sh sync`，约一分钟后再看 `status`。
- 使用 `diagnose` 检查应用版本、缓存数量和存储空间。

### 出现旧裁切或重复照片

确认客户端至少为 1.1.2。该版本起会在完整同步后将缓存严格收敛到最新清单。

### 更新 APK

`manage.sh update` 会构建 APK、发布其 SHA-256 和大小，并要求 BOOX 上手动确认 Android 安装界面。更新过程中不会开启无线 ADB。

## 恢复原则

- 不要删除整个 `/sdcard` 或恢复出厂设置来处理普通同步问题。
- 先保留 `config.properties`，再清理 `/sdcard/BooxPhotoframe/cache/`。
- 服务端的 `data/server-token` 与设备配置必须一致；更换令牌后需重新运行安装脚本。
- 公开 GitHub 前必须确保本地配置、令牌、缓存、日志、诊断和签名密钥均未被跟踪。
- Kindle Voyage 与 BOOX 的共享相框功能默认同步更新；如果受硬件限制或产品选择影响需要出现用户可见差异，必须先单独取得用户确认并记录到 `CROSS_DEVICE_PARITY.md`。
