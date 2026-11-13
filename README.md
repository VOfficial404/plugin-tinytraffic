# plugin-tinytraffic

Halo 2.x 对 [tinytraffic](https://github.com/egoist/tinytraffic) 的集成。tinytraffic 是一个自托管、不使用 Cookie 的网站统计工具。

安装后，Halo 会自动在站点页面中加入统计脚本，并在控制台多出一个「访问统计」页面：

![Halo 控制台中的「访问统计」页面：指标卡片、访客趋势图，以及来源、页面、地区、设备排行](./screenshots/preview.png)

<sub>图中为演示数据。</sub>

## 特性

- 在主题每个页面的 `head` 标签内注入 tinytraffic 统计脚本，支持脚本的全部选项（站点域名、Hash 路由、出站链接、自定义上报地址）。
- 在 Halo 控制台的「访问统计」页面查看数据：访客、访问次数、浏览量、跳出率、访问时长的趋势，以及来源、页面、地区、设备、目标的排行。点击任意一行即可按它筛选。
- 提供仪表盘小部件，显示过去 24 小时的访客数和当前在线人数。
- 安装了 [RSS 订阅插件](https://github.com/halo-dev/plugin-feed) 时，把 RSS 阅读器中的文章阅读量上报到 tinytraffic，来源记为 `RSS`。
- 提供「查看访问统计」权限，可以单独授予非管理员角色。

> tinytraffic 的仪表盘禁止被其他网站嵌入（`X-Frame-Options: DENY`），所以本插件不像 Umami 插件那样内嵌共享页面，而是通过 tinytraffic 的 API 读取数据后在控制台内展示。API 密钥只保存在 Halo 服务端，不会发送到浏览器。

## 安装与使用

需要 Halo 2.27.0 或更高版本，以及一个已经部署好的 tinytraffic。

1. 在 [Releases](https://github.com/VOfficial404/plugin-tinytraffic/releases) 下载最新的 JAR 文件。
2. 在 Halo 控制台的「插件」中上传 JAR 文件并启用。
3. 在 tinytraffic 中添加你的站点，并在「账户 → API 密钥」中创建一个密钥。
4. 打开插件设置，按下表填写。

### 基本设置

| 设置 | 说明 |
| --- | --- |
| tinytraffic 地址 | tinytraffic 服务的外部访问地址，例如 `https://stats.example.com`。 |
| 站点域名 | 在 tinytraffic 中添加站点时填写的域名，不带 `https://` 和 `www`。留空时，统计脚本使用访客当前访问的域名，数据面板使用 Halo 的外部访问地址。 |

### 统计脚本

| 设置 | 默认 | 说明 |
| --- | --- | --- |
| 注入统计脚本 | 开 | 关闭后不再向页面添加脚本。如果已经通过「代码注入」或主题添加过 tinytraffic 脚本，请只保留一处，否则会重复统计。 |
| 不统计已登录用户 | 关 | 已登录 Halo 的用户访问时不加载统计脚本。 |
| 统计出站链接点击 | 开 | 对应脚本的 `data-outbound`。 |
| 统计 Hash 路由 | 关 | 对应脚本的 `data-hash`。 |
| 统计 RSS 阅读量 | 开 | 需要安装 RSS 订阅插件。 |
| 自定义脚本地址 | 空 | 通过自己的域名反向代理 tinytraffic 时填写，可以是完整地址或以 `/` 开头的站内路径。 |
| 自定义上报地址 | 空 | 对应脚本的 `data-api`。 |

### 数据面板

| 设置 | 说明 |
| --- | --- |
| API 密钥 | 选择或新建一个 Halo 密钥，在其中的 `apiKey` 字段填写 tinytraffic 的 API 密钥（以 `tt_` 开头）。查看数据只需要「只读」权限；如果要让 RSS 阅读量按读者的真实 IP 区分访客和地区，需要「读写」权限。 |
| 允许访问内网地址 | 默认关闭。Halo 服务器默认不会向解析到内网或本机的地址发送请求，只有当 tinytraffic 部署在内网（例如与 Halo 在同一台机器上）时才需要开启。 |

只想注入统计脚本、不需要在控制台查看数据时，可以不配置 API 密钥。

## FAQ

1. **「访问统计」页面提示 tinytraffic 中没有对应的站点。**

   数据面板按「站点域名」在 tinytraffic 中查找站点。请确认该域名与 tinytraffic 中添加的一致，或者直接在插件设置中填写。

2. **提示地址指向内网或本机。**

   tinytraffic 地址解析到了内网地址。确认这是你自己的部署后，在「数据面板」中开启「允许访问内网地址」。

3. **提示地址返回了重定向。**

   插件不会跟随重定向，以免把 API 密钥发送到别的地址。请填写最终地址，例如把 `http://` 改为 `https://`。

4. **自己的访问也被统计了。**

   开启「不统计已登录用户」，或者在站点页面的浏览器控制台执行 `localStorage.tinytraffic_ignore = "true"`。

## 开发

所需环境：JDK 21、Node.js 22.12 以上、pnpm 10，使用 `haloServer` 时还需要 Docker。

```bash
# 启动一个加载了本插件的 Halo
./gradlew haloServer

# 实时构建前端
cd ui
pnpm install
pnpm dev
```

```bash
# 运行全部检查并构建，JAR 位于 build/libs
./gradlew build
```

修改了 `StatsEndpoint` 或它返回的模型后，需要重新生成 `ui/src/api/generated` 中的 API 客户端，不要手动修改该目录：

```bash
./gradlew generateApiClient
# 没有 Docker 时，从一个已经运行着本插件、并开启了接口文档（springdoc.api-docs.enabled=true）
# 的 Halo 读取，地址默认为 http://localhost:8090
./gradlew generateApiClient -PuseExistingServer
```

## 许可证

[GPL-3.0](./LICENSE) © VOfficial404
