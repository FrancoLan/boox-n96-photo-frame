# BOOX N96 相框交接手册

## 2026-10-02 无线电池诊断（待设备安装验证）

- APK 1.2.5 / code 11 在状态 detail 中加入电量、充电状态和电源连接信息；详细 diagnose 增加电池温度（0.1°C）和电压（mV）。读取 API 15 的系统电池快照，不增加权限或轮询频率；未知读数明确标为 unknown。
- 修正 ControlService 一直回报 1.2.2 的旧版本常量，本包回报 1.2.5。保留 1.2.3 的照片全屏刷新。Kindle 已有电池诊断，本次补齐 BOOX。
- 完整 scripts/check.sh 和 git diff --check 通过，APK 的 v1/v2/v3 签名验证通过，签名证书与既有部署包一致；设备安装与电池读数尚待确认。无线 APK 更新仍需用户在设备上确认安装。


## 2026-09-28 换图深度刷新（APK 1.2.3，真机已验证）

- 按用户要求，每次照片切换绘制完成后约 100 ms 请求一次全屏深度刷新；首次展示和回到前台也刷新。自动轮播、新照片优先队列、管理命令及按键/触摸共用同一绘制路径。快速连续切图会取消上一张尚未执行的刷新，暂停或 View 脱离窗口时取消回调。
- N96 Android 4.0.4 / API 15 的固件提供公开但不在标准 Android SDK 内的 `View.fullRefreshScreen()`；通过反射调用，底层进入 `ViewRootImpl.fullRefreshScreen()`。无需额外 Onyx SDK。固件缺少接口或调用失败时保留照片展示并输出 `BooxFullRefresh` 警告。
- APK 版本为 `1.2.3` / `versionCode=10`；完整 `./scripts/check.sh` 与 APK 签名校验通过。已通过 USB 覆盖安装，确认版本和前台 Activity，并测试下一张、上一张、方向键与离开/返回相框。日志确认全屏刷新调用成功；用户明确确认「一样，残影也清除了」，即与右上实体设置键效果相同。
- 本次是真机验收安装，源码位于 `feat/boox-full-refresh-on-photo-change`，通过 PR 归入 `main`；未创建 Release。Kindle 保留已确认的手动快速切图后 10 秒补全刷策略；这是本次 BOOX 专项要求，见 `CROSS_DEVICE_PARITY.md`。
- **签名注意**：本工作区 BOOX 仓库默认的 `android/build/debug.keystore` 与设备安装版不匹配。本次使用聚合工作区 `implementation/boox/build/debug.keystore`（通过 `BOOX_SIGNING_KEYSTORE` 指定），证书与安装包匹配。切勿依赖新生成的默认调试密钥覆盖升级；先比对安装包与待安装 APK 的 signer。旧 APK 已在本机临时目录保留作诊断副本。

## 2026-09-28 Git 工作流约定

- `main` 是可部署和发布分支，不直接在其上开发。所有改动（包括文档）都使用短期 `fix/*`、`feat/*` 或 `chore/*` 分支，通过 PR 合并。
- `main` 已启用分支保护：必须通过 `Project checks`，必须解决 PR 对话，禁止强推和删除；单人维护允许 0 个批准和管理员应急绕过。若为恢复设备服务而应急绕过，随后必须立即用 `hotfix/*` PR 对齐已部署的精确改动。
- CI 固定使用 macOS 26，安装 Java 17、Android SDK Platform 36 / Build Tools 36.0.0，并运行 `git diff --check` 与完整 `./scripts/check.sh`。它验证脚本、Node/JSON、原生 Mac 工具、渲染测试、隐私扫描及可签名 Android 构建，但不能代替 BOOX N96 的墨水屏、按键、触摸、休眠唤醒、旧版 Android 网络或安装升级验收。
- 涉及触摸、按键、显示、同步、安装、签名、更新或恢复的改动，在发布前必须完成 BOOX N96 真机验证。CI 使用临时调试密钥生成的 APK 仅用于构建验证，不作为设备部署或 Release 资产。
- 与 Kindle 共用的行为应在两个仓库分别创建并互相链接 PR；任何有意差异记录到 `docs/CROSS_DEVICE_PARITY.md`。
- Release 只从已合并、检查和真机验证的 `main` 创建；版本标签不可移动，并同步记录版本、检查结果、设备部署状态及实际使用的签名来源。

## 2026-09-28 发布 v1.2.4

GitHub Release [`v1.2.4`](https://github.com/FrancoLan/boox-n96-photo-frame/releases/tag/v1.2.4) 已发布，Release 准备提交为 `620f4c9`。该版汇总自 `v1.2.0` 后的共享 Mac 同步与照片渲染变更：新照片优先播放、每分钟相册检查、单次 EXIF 方向归一化、完整地址和星期元数据、暗部自适应提亮。BOOX Android APK 未改动，设备端版本仍为 `1.2.2`。`./scripts/check.sh` 全部通过，包含 APK 签名校验。

## 2026-09-27 修复照片方向回归（已验证渲染；设备清单重发待完成）

此前 v9 同步器先用 `sips -r` 按 EXIF 旋转像素，而元数据渲染器的 ImageIO 解码又启用了 EXIF transform，造成部分竖图被旋转两次。现统一为 ImageIO 在 `metadata-overlay` 中单次归一化，移除同步器的额外旋转，并将缓存版本升至 `voyage-1072x1448-gray-face-edge-fill-weekday-two-line-shadow-adaptive-orientation-once-v10`。EXIF Orientation 6 与 8 的两张本地实拍已在修复版下成功渲染为 1072×1448；两仓库 `./scripts/check.sh` 均通过，并新增防双重旋转检查。Mac 正式运行目录已部署 v10 渲染器并保留回滚备份。正式同步现已发布 44 张，manifest `ebb68e6254cfa084`；BOOX `sync` 命令 `86941956-1e7b-41e4-8c36-7c46db6b9a16` 成功回执、cache=44。Kindle `restart` 命令 `86bf61c0-c7fd-4fab-a23a-5edf97829942` 已发出，但设备心跳在 2026-09-26 14:41:22 UTC 后中断，局域网 ping 无响应，尚不能确认 Kindle 已拉取 v10。GitHub 修复提交 `9dd97ee` 已推送至 BOOX 仓库 `main`。

## 2026-09-27 暗部直方图自适应提亮（已部署）

Mac 共用照片渲染器现在在添加元数据前统计源照片灰度直方图。仅当亮度中位数低于 40/255 且 P95 不低于 80/255 时，应用温和的阴影提亮曲线；保留纯黑与白场，并避免把接近全黑的画面整体抬灰。正常曝光和 2024-11-17 01:10 参考照不触发。渲染版本升至 `voyage-1072x1448-gray-face-edge-fill-weekday-two-line-shadow-adaptive-v9`，下次服务端同步会重建缓存中的所有照片，Kindle/BOOX 仍共享同一批渲染图。环境变量 `PHOTOFRAME_DEBUG_TONE=1` 可输出 P5/P50/P95 与是否应用曲线。

两个独立仓库的 `./scripts/check.sh` 已通过，含曲线阈值测试；用暗调样片实跑渲染得到 1072×1448 PNG，确认触发提亮。Kindle GitHub `64876ba`、BOOX GitHub `de68d5b` 已推送到 `main`。随后已备份正式服务原始渲染器并部署新版本到 `~/Library/Application Support/KindlePhotoframe/runtime/`，Mac 服务保持运行。正式同步成功重新渲染并发布 44 张，manifest `4c78d24e6f4712b6`，renderer v9。BOOX `sync` 命令 `931da6be-43fc-4a77-afff-3098ce31b484` 已回报成功、cache=44；Kindle `restart` 命令 `a6a4c297-8164-4bad-bf80-f8d534ae2c3e` 已回报成功，两端仍运行。墨水屏上的最终观感待用户目视确认。此次部署交接记录已更新，GitHub 记录待推送。

## 2026-09-26 MapKit 弃用 API 清理

Mac 端 `server/reverse-geocode.swift` 已迁移到 `MKMapItem.addressRepresentations`：优先展示完整格式地址（可包含街道、城区和城市），不可用时回退到城市名；地址和拍摄时间分两行，日期后加英文缩写星期，例如 `2026-09-25 Fri 20:01`，日期时间使用不可断空格。44 张图已发布为 manifest `879a25fc0cd39bec`（render v8）。BOOX `sync` 命令 `1be92472-ff35-4300-b3e2-9b0b4791b69e` 成功，设备回报 cache=50。Kindle `restart` 命令 `1951a842-f593-4c51-b08b-5bc90edb378f` 仍待设备确认，最后收到其 `running` heartbeat 为 13:25:41 UTC。`./scripts/check.sh` 完整检查通过。

## 当前能力

这套系统由 Mac 服务端和 BOOX N96 客户端组成。Mac 每 10 分钟读取同一个 iCloud 共享相册，生成适合 1072×1448 墨水屏的灰度图片；BOOX 在局域网内同步并离线轮播。

1.2.2（`versionCode=9`）将设备前台自动复查间隔改为 1 分钟。匹配旧版签名构建通过，更新命令 `b657a396-ea64-45e0-b8a9-db7136435465` 已下发，等待设备端安装确认。Mac 源端检查也已改为每分钟，并已重新加载 LaunchAgent。BOOX 上一版本 1.2.1 的安装与 37 张缓存同步已确认。

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

直接修改 iCloud 共享相册。Mac 每分钟检查并发布新清单；BOOX 相框前台运行时每分钟检查清单，新图下载完成后立即优先显示。离线设备在恢复联网并执行下一次检查后更新。

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

安装脚本会构建 APK、写入服务器地址与令牌、推送配置并启动相框。升级前用 `BOOX_SIGNING_KEYSTORE` 指定与已安装 APK 证书匹配的原始私有密钥；默认 `android/build/debug.keystore` 仅用于本地构建，不能假定与设备一致。不要把密钥提交到 GitHub。

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
