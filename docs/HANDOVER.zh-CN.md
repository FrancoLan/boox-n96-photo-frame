# BOOX N96 维护交接

更新：2026-10-05。本文记录最后一次已验证状态；实时状态必须查询新的设备回报。此前过程保留在 Git 历史，私人部署记录不公开。

## 当前版本与待办

- 正式 [v1.2.6](https://github.com/FrancoLan/boox-n96-photo-frame/releases/tag/v1.2.6) 已从 `main` 发布，附原签名 APK 与 SHA-256；versionCode 12，PR #3 合并提交 `281b096`。
- 设备为 N96，Android 4.0.4 / API 15、固件 1.9.1。电池百分比和供电状态通过心跳回报，诊断另含温度、电压、level/scale；缺失读数保持未知。
- 完整项目检查、Java 备用地址回归、APK 构建与 CI 通过；真机确认换图全屏刷新，以及拔网线后备用地址新诊断、接回后主地址新诊断。
- 最终双接口配置尚未再次重启验收；路由器地址保留/排除需单独实查，不能用无响应 ping 代替。

## 共享行为与硬件差异

同一 iCloud 共享相册、每分钟清单检查、10–20 分钟随机换图、离线缓存、完整性校验和旧缓存清理。新增照片优先播放一次后恢复原顺序；单次 EXIF 方向转换、人脸优先，必要时完整显示，留白采用边缘深色；地点与拍摄地当地时间分行、日期含星期，暗部温和提亮。

BOOX 触摸和实体键翻页，支持开机启动；照片绘制后约 100 ms 调用固件 `View.fullRefreshScreen()`，恢复前台也刷新，切换或暂停取消旧回调。用户已确认效果与实体设置键相同。Kindle 保留手动快速切图后十秒补全刷。普通 N96 无前光，不模拟 Kindle 前光策略。差异见 [CROSS_DEVICE_PARITY.md](CROSS_DEVICE_PARITY.md)。

## 网络与自动充电

- Mac 有线和 Wi-Fi 使用不同稳定 LAN 地址，有线优先；核实路由器保留。服务器 `listenHosts` 只包含明确地址，地址消失后撤销监听，返回后自动恢复。
- `config.properties` 的 `server_url` 是主地址，可选 `server_fallback_url` 为备用。仅支持 HTTP 源站，不含路径、查询或用户信息；保留原 token。
- 管理轮询与照片同步先探测认证 manifest；主地址失败才用备用，后续周期重试主地址。两个地址都不可达时保留缓存并重试。其他项目需要各自备用配置。
- 用真实拔线/接回后的新诊断和服务器记录的 `localAddress` 验收；旧心跳不能证明实时连通。未鉴权 HTTP 401 表示端点可达。
- 共享 Mac 充电控制严格低于 40% 开、高于 80% 关，40–80 保持；每分钟检查、十分钟读数有效期。两设备独立私人 JSONL 记录秒级命令结果、充电变化及充电中五分钟采样，失联不重复旧读数。
- 四条充电开关、后台关与锁屏关均通过实测；stdin EOF 修复解决了后台超时。Mac 必须常开、登录、联网且不睡眠。详见 [Kindle CHARGING.md](https://github.com/FrancoLan/kindle-voyage-photo-frame/blob/main/docs/CHARGING.md)。

## 安装、签名与排障

升级必须显式用 `BOOX_SIGNING_KEYSTORE` 指向与旧 APK 匹配的原私有密钥，并比较签名证书。仓库默认或 CI 临时调试密钥不能作为覆盖升级包；不得提交密钥。更新包经大小/SHA-256 校验后，仍需设备端确认 Android 安装器；不启用无线 ADB。

使用现有 `scripts/manage.sh status` 查看 receivedAt、appState、版本、电量和 localAddress；失联先检查设备 Wi-Fi、Mac 地址/监听和休眠，再查看服务日志。需要证据时下发一次 diagnose，等待处理，避免覆盖待执行命令。有图不更新时检查 Mac 同步，再执行 sync 并查询新状态。

USB 存储导出时 Android sdcard 可能不可读；在 Mac 挂载卷修改配置后回读验证，安全弹出再启动 Photoframe。不要通过删除整个 sdcard 或恢复出厂设置解决普通同步问题。清缓存前保留 config.properties，服务端和设备 token 必须一致。

## 维护、备份与发布

- 先识别正式运行目录与 LaunchAgent；公开安装器和既有部署可能不同，不直接重装或启动重复服务。周期任务正常退出不代表故障。
- 备份配置、原签名、代码和日志并保存校验清单；按需恢复，保留当前网络配置和 token，重新验证新回报。
- `main` 为发布分支；短期分支经 PR、完整 `scripts/check.sh`、CI 和对应真机验收后合并。不能强推主分支或移动 Release 标签。
- 共同行为同步维护，通过关联 Kindle PR 记录；新用户可见差异先确认。共享服务器、充电日志与备用监听见 [Kindle v0.4.11](https://github.com/FrancoLan/kindle-voyage-photo-frame/releases/tag/v0.4.11)。
- 公开仅保留代码、测试、脱敏说明和已验证发行资产；实际网络配置、相册链接、照片、token、诊断、电量日志、完整设备标识和私钥只留本地。不得改动 iCloud 原照片。
- 后续直接修订对应状态与待办，详细过程保留 Git 或私有记录，避免追加互相矛盾的“当前状态”。
