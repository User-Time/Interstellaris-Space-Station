# Interstellaris: Space Stations

Interstellaris 的非官方空间站附加模组，由 **User-Time** 维护。

为固定空间站提供可设置的氧气、温度、玩家重力、地图标记，以及舰船信息中心（N）的原生导航目的地。当前版本 **2.1.1 预览版**；客户端和服务端都需要安装。

## 功能

- **空间站核心**：放置后右键设置；站主及管理员可以修改。
- **公共 / 私人**：公共站所有玩家可见，私人站仅站主可见。
- **环境范围**：分别设置氧气和重力球形范围。
- **玩家重力**：无重力、范围内完全重力、脚下有可站立方块时的吸附重力；可调整倍率和吸附距离，默认距离 4 格。
- **温度**：支持 −100～120°C 之间的数值。
- **地图**：空间站可显示在原有 M 地图中；支持白色半透明球形边界效果。
- **原生舰船导航**：在 N 原有目的地列表中选择空间站，沿用 Interstellaris 的推进、脉冲和曲速模式。
- **首次访问解锁**：每位玩家进入启用且可见的核心范围并停留约 40 游戏刻后解锁该站导航；访问记录会保存。

首次访问范围取氧气/重力半径的较大值，最低 8 格。旁观者和 FakePlayer 不计入。重命名空间站不会清除访问记录，拆下重新放置的新核心需要重新访问。

## 依赖与安装

本版本针对以下组合开发、编译和验证：

| 组件 | 版本 |
|---|---|
| Minecraft | 1.21.1 |
| NeoForge | 21.1.264 |
| Interstellaris | 0.6.3a |
| Sable | 2.0.5 |
| 已执行服务端组件验证的核心 | Youer 2dd45af8 |

[下载候选 JAR](artifacts/interstellaris-space-stations-2.1.1.jar)，放入客户端和服务端各自的 `mods` 目录；更新时移走旧 JAR，只保留一个本模组版本。

本仓库不包含 Minecraft、Youer、NeoForge、Interstellaris 或 Sable 的代码/JAR；依赖应自行获取。2.1.x 使用网络协议 4，不能与 2.0.0 客户端混用。

## 导航行为

先到访空间站完成解锁，再打开 N，在空间站所属恒星系统的原目的地列表选择目标，并使用原有导航按钮。

设备、燃料、研究、驾驶权限、跨系统限制和到达判定均沿用 Interstellaris。当前原生导航在目标范围外约 **942 格**判定到达，并非自动停靠到站台。未添加新的飞行控制器、近距离泊靠或避障系统。

目标停用、删除或收回私人访问权限后，无法新建或继续前往该站的导航。只有空间站核心进入导航目录，旧版通过命令创建的纯环境球不会自动变为目的地。

## 更新兼容

Java 包名按 `time-cloud.net` 的反向域名写为 **`net.time_cloud`**。Java 标识符不能使用连字符；项目和 JAR 文件名使用 `interstellaris-space-stations`。

为保留已有存档，继续使用历史 `potg_station_habitat` 注册命名空间、`potgcraft-station-habitats.json` 配置文件，以及默认世界的 `data/potg_station_visits.dat` 访问记录。包名和显示名称变更不要求重建核心或世界。

从 2.0.0 更新前应正常保存、关闭并备份模组配置和世界。从 2.1.0 更新不改变网络协议或存档格式。[回滚说明](docs/ROLLBACK.md)。

## 开发与验证

本仓库保留实际使用的、基于版本固定依赖的 Python/Docker 构建流程；目前不是标准 Gradle/ModDevGradle 项目。

需要 Python 3.10+、Docker，以及已有目标服务端安装目录中的 `libraries/` 和 `mods/`。本地提供 `eclipse-temurin:25-jdk-jammy` Docker 镜像后，编译容器本身不联网，输出 Java 21 字节码。

```bash
python3 prepare_client.py --server-root /path/to/server
python3 build.py --server-root /path/to/server
python3 build_tests.py --server-root /path/to/server
python3 validate_resources.py
python3 validate_navigation_api.py --server-root /path/to/server
```

`prepare_client.py` 从 Mojang 下载客户端及映射并核对 SHA-1，仅用于编译，不能重新分发游戏类。所有命令也支持通过 `MC_SERVER_ROOT` 指定依赖安装目录。

构建结果位于 `dist/`。`build_tests.py` 还会生成只供隔离环境使用的探针 JAR；**不要安装到正式服，探针会在测试完成后关闭它所在的服务器**。

[测试范围](docs/TESTING.md) · [复用与代码结构](docs/ARCHITECTURE.md) · [MIT License](LICENSE)

## 状态

这是预览候选，尚未完成图形客户端中 N 界面、客户端握手与真实舰船全过程验收。服务端组件测试不能代替真实航行验证。详情及准确验证范围见测试文档。
