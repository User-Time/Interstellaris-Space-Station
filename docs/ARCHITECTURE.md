# 复用与代码结构

Java 根包为 `net.time_cloud`。持久化命名空间保持不变。

| 功能 | 使用的已有能力 | 本模组承担的部分 |
|---|---|---|
| 自动导航 | Interstellaris `Navigator`、`NavMode`、`SicPacket`、原有 N 界面及私有 Entry 记录 | 构造空间站目标、追加条目、检查访问权 |
| 计划与执行 | 原生设备、燃料、研究、船员、驾驶、到达和取消流程 | 不另外实现飞行控制或燃料计算 |
| 坐标转换 | Sable `projectOutOfSubLevel` 与船体姿态转换 | 所有环境/核心/发现查询共享同一转换入口 |
| 环境与船内重力 | Interstellaris 原生环境、危险和 `ShipGravity` 接口 | 仅在核心局部范围覆盖参数 |
| 核心状态 | NeoForge/Bukkit 共存环境中的原版方块实体、正常存档 | 核心身份、所有权、可调参数 |
| 访问记录 | 原版 `SavedData`、NeoForge 自定义 payload | 动态核心 UUID 的逐玩家访问状态 |

地图目录、发现、已解锁列表和服务端导航共用 `StationDirectory.canView`，避免私人/公共策略在多处产生分歧。客户端条目不能授予权限，配置目标、规划和航行阶段均保留服务端检查。

原生研究数据表达已有研究项目，不用于存储动态核心访问记录。站主 UI、核心环境和访问台账是空间站附加功能所需的状态；没有借此改动原生研究、玩家背包或舰船数据结构。

`StationDestinations` 创建独立的、已绑定的临时天体 Holder 供原生导航使用，但不会写入世界天体注册表，防止将空间站当成新行星。原生星球仍走原来的路径。

`StationNavigationEntries` 缓存原 N 私有 Entry 构造器，并直接生成原界面条目；这依赖固定 Interstellaris 0.6.3a 的二进制接口。`validate_navigation_api.py` 核对该接口，不能替代客户端 Mixin 与画面验收。

保留必要的服务端目标选择、规划和航行中权限检查，避免为了减少行数而让自定义客户端绕过解锁条件。没有新增控制飞船的客户端网络包。
