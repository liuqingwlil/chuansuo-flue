# 穿梭后续功能实现方案

更新日期：2026-11-05。1.13.0 已实现本方案第一阶段：收藏分组、线路编辑与播放、GPX 导入导出、构建时独立安装实例，无账号或付费权限体系。

实现落点：LibraryRepository、LibraryActivity、RouteActivity、Coordinate、SimulationEngine/RoutePlayer、GpxCodec 和 productFlavors。资料库独立存储，旧历史不自动变成收藏；用户通过“保存为收藏”显式保存，避免改变原有历史含义。历史数据库保持原样，不执行破坏性迁移。

地点与线路使用统一 items 表、points 表以及 groups_ 表，分别记录类型、关联与排序，提供与下文逻辑模型等价的收藏关系。当前数据库首次建库版本为 1；未来变更必须提供增量迁移，缺失迁移时拒绝升级而不删表。

尚未实现的后续增强：道路规划、往返模式、按 GPX 原始时间播放、手机内任意包名的动态重打包。以下保留设计细节供后续维护参考。

## 1. SDK 与基础架构

现有 Java + XML、百度地图 SDK、SQLite 和前台定位服务足以支持第一版线路、GPX 和收藏，无需再接入另一套地图 SDK。

百度 SDK 可用于地点搜索和地图折线。第一版线路按节点直连；若需要沿实际道路生成路径，再接入同一 SDK 的步行/骑行/驾车路线规划，先检查当前 JAR 的对应类、Key 权限和额度。

本次已确认随附 JAR 包含 SuggestionSearch 与 PoiSearch。输入提示可能没有坐标，需要配合 POI 检索：[百度官方说明](https://lbsyun.baidu.com/docs/android?title=androidsdk%2Fguide%2Fsearch%2Fsuggestion)。

## 2. 数据层

先保留 SQLiteOpenHelper，引入 Repository 隔离 SQL 与界面，避免一次迁移到 Room 扩大改动。新建统一业务库，将原历史记录事务导入，记录迁移版本与源 ID，防止重复导入。

| 表 | 关键字段 |
| --- | --- |
| saved_place | id、name、longitude_wgs84、latitude_wgs84、altitude、created_at |
| route | id、name、movement_mode、speed_mps、loop_mode、created_at、updated_at |
| route_point | route_id、sequence、longitude_wgs84、latitude_wgs84、altitude、source_time、segment_id；route_id + sequence 唯一 |
| favorite_group | id、name、sort_order |
| place_favorite | place_id、group_id、pinned、sort_order；同组不重复 |
| route_favorite | route_id、group_id、pinned、sort_order；同组不重复 |

内部统一保存 WGS84，显示到百度地图时再转 BD-09。引入具名 Coordinate 类型，明确经纬度顺序和坐标系。数据库升级使用增量迁移，不能继续采用旧 onUpgrade 的删表重建。收藏与运行历史分开，历史过期清理不影响收藏。

## 3. 线路编辑与播放

新增 RouteActivity：地图点选、搜索添加节点、列表排序和删除、命名保存。使用 Marker 和 Polyline 展示，计算距离及预计时长。至少两个有效节点，过滤重复点，保留 GPX 分段边界。

从 ServiceGo 抽取 SimulationEngine，统一 FIXED、JOYSTICK、ROUTE 模式与 STOPPED、RUNNING、PAUSED 状态，三种模式共同输出当前位置，避免多个模块同时改写坐标。

RoutePlayer 以单调时钟计算 speed × elapsedTime，按线路累计距离推进，用球面距离与方向插值；一帧跨多段时继续消费剩余距离，不能只推进一个节点。先支持启动、暂停、继续、停止，再加入循环、往返和变速。输出仍交给现有 GPS/NETWORK 模拟接口。

验收：重复节点、短线段跨越、暂停恢复、速度变化、循环边界、线路切摇杆和停止服务。通知显示进度、暂停与停止；进程恢复不得静默重启旧线路。

## 4. GPX 导入导出

使用系统 XmlPullParser/XmlSerializer，无需第三方 SDK。支持 GPX 1.1 的 wpt、rte/rtept、trk/trkseg/trkpt，坐标按 WGS84 处理，ele/time 可选。wpt 导入地点，rte/trk 导入线路。多个 track 供用户分别选择，不能直接拼接；保留轨迹分段。

通过 ACTION_OPEN_DOCUMENT 导入、ACTION_CREATE_DOCUMENT 导出，使用 ContentResolver 流读取，无需所有文件访问权限。后台解析，限制文件大小与节点数，拒绝非有限或越界坐标，禁用 DTD/外部实体；先预览，再事务写入，失败回滚。

导出写正确命名空间、creator 和 WGS84 坐标，避免重复转换。默认按设置速度播放；原始时间节奏作为可选模式，处理缺失、重复和逆序时间戳。

验收：导入导出往返保持坐标和节点顺序；多轨迹、分段、无海拔、损坏文件、超大文件、越界点和用户取消均可正确处理。

参考：[GPX 1.1 Schema](https://www.topografix.com/GPX/1/1/)、[Android 文件选择器](https://developer.android.com/training/data-storage/shared/documents-files)。

## 5. 收藏与分组

新增收藏入口。地点和线路复用分组界面，使用独立关联表和外键。支持分组创建/改名/排序、移动收藏、置顶、搜索；删除组时默认移入未分组，不直接删除地点或线路。

历史可另存为收藏，使用数据对象而不是从 TextView 反解析坐标。先完成地点收藏，再接入线路。选择收藏只做预览，启动模拟由独立操作触发。

## 6. 独立安装实例

第一版推荐构建时 productFlavors，例如 com.chuansuo.app.instance1、instance2。每个实例拥有独立名称、应用 ID、数据和百度 Key。FileProvider 已使用 ${applicationId}，后续组件同样避免固定 authority。各实例可共用自有发布签名，但要分别登记应用 ID 对应的地图授权。

无需第三方多开 SDK。此方案提供本应用的多套配置，不承诺每个第三方应用得到独立模拟位置；现有 GPS/NETWORK Provider 属于系统共享状态，按单一运行实例设计，切换前停止旧服务并重新选择系统模拟位置应用。

若要求在应用内输入任意包名并生成 APK，则需增加 Manifest/资源重写、重打包、签名、校验、安装与每包地图授权。发布签名材料不宜内置在可分发 APK 中，工程复杂度显著增加，建议后续独立评估，先通过构建时多实例验证需求。

参考：[Android Product Flavors](https://developer.android.com/build/build-variants)、[模拟位置 Provider](https://developer.android.google.cn/reference/android/location/LocationManager)、[百度包名与签名配置](https://lbsyun.baidu.com/docs/guide?title=android-guide%2Fcreate-project%2Fak)。

## 7. 实施顺序

1. Repository、具名坐标类型与数据库增量迁移；地点收藏分组。
2. 线路实体、编辑器与 SimulationEngine/RoutePlayer；通知操作。
3. GPX 导入导出。
4. 线路收藏、循环/往返和道路规划。
5. 构建时独立实例；有明确需求后评估应用内动态打包。

重点自动测试：迁移不丢数据、线路时钟与距离、GPX 往返及多实例构建。SDK 授权、真实联网与模拟定位另用 ARM64 设备验证。本次修复的真实地点检索同样需要有效 Key 与设备验收。
