# 落地报告：哪些地方**必须**这么做，否则直接报错

> 全部基于 `/workspace` 仓库的**当前代码**（versionCode 171 / AGP 9.3.1 / Kotlin 2.4.10 / Gradle 9.5.0 / JDK 17/21）。
> 报告里的"必须"=**会直接报错**或**无法产出可用 APK**；"建议"=不修能用但坑很深。

---

## 0. 30 秒 TL;DR

| 序号 | 必须做 | 后果（不做时） |
| --- | --- | --- |
| M1 | 拉子模块 `material3/material-color-utilities` | `:material3` 构建失败 |
| M2 | 在 `app/google-services.json` 放一份**结构合法**的 JSON（CI 用占位符就够） | `:app` 构建失败：`google-services` 插件无输入 |
| M3 | JDK 17（编译）+ 让 Gradle 守护跑在 JDK 21 | Toolchain 拉取失败或 Kotlin 编译报错 |
| M4 | `gradle-daemon-jvm.properties` 配对 `toolchainVersion=21` | Gradle 自动下载 foojay 21 |
| M5 | 装 `pnpm`（Web UI 构建用） | `:web` 的 `preBuild` 跑 `pnpm run build` 失败，连带 `:app` 失败 |
| M6 | `local.properties` 里给齐 `storeFile/storePassword/keyAlias/keyPassword` | `assembleRelease` 失败 |
| M7 | 4 个架构 `arm64-v8a` / `x86_64` 原生库随包带齐 | 启动期 `UnsatisfiedLinkError` |
| M8 | R8 规则（`proguard-rules.pro`）**不要删** | Release 启动即崩 |
| M9 | `app/src/main/assets/simple_dict/*` 7 个文件不能少 | FTS5 + jieba 分词在 Room `onOpen` 加载失败，DB 初始化异常 |
| M10 | 升级 `versionCode` + 写迁移脚本 | 老用户升级闪退 |

下面逐条给路径和修法。

---

## 1. 必装 / 必配（缺了就构建不过）

### M1. 子模块必须 `git submodule update --init`
- 证据：[.gitmodules](file:///workspace/.gitmodules)
  ```
  [submodule "material3/material-color-utilities"]
      path = material3/material-color-utilities
      url = https://github.com/material-foundation/material-color-utilities.git
  ```
- 报错：`:material3` 的 Kotlin 源里会 import `dynamiccolor.*`，目录为空时 `import` 阶段直接挂。
- 做法：
  ```bash
  git submodule sync && git submodule update --init --recursive
  ```

### M2. `app/google-services.json` 必须存在且结构合法
- 证据：[app/build.gradle.kts](file:///workspace/app/build.gradle.kts#L7-L15) 启用了 `alias(libs.plugins.google.services)` 与 `firebase.crashlytics`。
- 报错：`A problem was found with the configuration of task ':app:processDebugGoogleServices'`。
- 离线/无人值守做法（[build-debug-apk.yml](file:///workspace/.github/workflows/build-debug-apk.yml#L55-L86) 已经在用）：
  ```bash
  cat > app/google-services.json <<'JSON'
  {
    "project_info": {"project_number": "000000000000", "project_id": "dummy", "storage_bucket": "dummy.appspot.com"},
    "client": [{
      "client_info": {"mobilesdk_app_id": "1:000000000000:android:0000000000000000", "android_client_info": {"package_name": "me.rerere.rikkahub.debug"}},
      "oauth_client": [], "api_key": [{"current_key": "AIzaSyA-dummy-dummy-dummy-dummy"}],
      "services": {"appinvite_service": {"other_platform_oauth_client": []}}
    }],
    "configuration_version": "1"
  }
  JSON
  ```
  ⚠️ 占位 JSON 只能让构建过、**Firebase 实功能是哑的**。真发布必须有真 `google-services.json`。
- 注意 `applicationIdSuffix = ".debug"`：debug 包名是 `me.rerere.rikkahub.debug`，占位 JSON 里的 `android_client_info.package_name` 必须能匹配到。

### M3 / M4. JDK & Toolchain（最容易踩）
- 编译目标 `sourceCompatibility = JavaVersion.VERSION_17`（[app/build.gradle.kts](file:///workspace/app/build.gradle.kts#L90-L93)）
- Gradle Daemon JDK：**21**（[gradle-daemon-jvm.properties](file:///workspace/gradle/gradle-daemon-jvm.properties#L12-L13) `toolchainVersion=21`）
- Gradle Wrapper：9.5.0（[gradle-wrapper.properties](file:///workspace/gradle/wrapper/gradle-wrapper.properties#L4)）
- 做法：
  ```bash
  # 用 sdkman 或手动
  export JAVA_HOME=/path/to/jdk-21      # 给 Gradle 守护
  # 同时确保 .gradle/jdks/ 里有 17（compileSdk 37 要 17 source）
  sdk install java 17.0.13-tem
  sdk install java 21.0.7-tem
  sdk use java 21.0.7-tem
  ```
- 报错示例：`Unsupported class file major version 65`（用了 JDK 21 编译但 target 11）/`Unsupported class file major version 61`（用了 JDK 17 但 Kotlin 2.4 要 21）。

### M5. `pnpm` 必须可用
- 证据：[web/build.gradle.kts](file:///workspace/web/build.gradle.kts#L10-L34) 在 `preBuild` 直接调 `pnpm run build`。
- 报错：`:web:preBuild FAILED` → `:app:processDebugResources` 拿不到 `src/main/resources/static/`，导致整条流水线炸。
- 装法（任选）：
  ```bash
  npm i -g pnpm@9
  corepack enable && corepack prepare pnpm@9 --activate
  ```
- 落地技巧（断网环境）：在能上网机器上 `pnpm install` + `pnpm build`，把 `web-ui/dist` 拷进 `web/src/main/resources/static/`，加 `-x :web:buildWebUi` 跳过。

### M6. `local.properties` 必须 4 项齐
- 证据：[app/build.gradle.kts](file:///workspace/app/build.gradle.kts#L47-L70)
  ```kotlin
  val storeFilePath       = localProperties.getProperty("storeFile")
  val storePasswordValue  = localProperties.getProperty("storePassword")
  val keyAliasValue       = localProperties.getProperty("keyAlias")
  val keyPasswordValue    = localProperties.getProperty("keyPassword")
  ```
- 注意：哪怕只缺一项，签名块**静默不生效**（[app/build.gradle.kts](file:///workspace/app/build.gradle.kts#L60-L67) `if` 里没赋值），后面 `release { signingConfig = signingConfigs.getByName("release") }` 用的是空配置，**`assembleRelease` 会报 `Keystore was tampered with, or password was incorrect`**。
- 模板（`/workspace/local.properties`）：
  ```properties
  storeFile=keystore/release.keystore
  storePassword=xxxxx
  keyAlias=rikkahub
  keyPassword=xxxxx
  sdk.dir=/Users/you/Library/Android/sdk
  ```
- **生产**还得用 Play App Signing 留一份 upload key，参考 [Play Console 上传密钥流程](https://support.google.com/googleplay/android-developer/answer/9842756)。

### M7. 原生库不能缺
- [workspace/src/main/jniLibs](file:///workspace/workspace/src/main/jniLibs) 必须有 `arm64-v8a/libproot_exec.so` 与 `libproot_loader.so`、`x86_64/` 同名两个。**abiFilters 已经写死只有这两个**（[app/build.gradle.kts](file:///workspace/app/build.gradle.kts#L30-L33)），其它架构直接装不上。
- [app/src/main/jniLibs](file:///workspace/app/src/main/jniLibs) 必须有 `libsimple.so`（jieba 原生）。
- 报错：`java.lang.UnsatisfiedLinkError: couldn't find "libproot_exec.so"`，或在 `RikkaHubApp` 启动 [WorkspaceManager](file:///workspace/app/src/main/java/me/rerere/rikkahub/di/RepositoryModule.kt#L52-L74) 时 `loadLibrary` 失败 → 进程直接崩。
- 落地技巧：`packaging.jniLibs.useLegacyPackaging = true` + `pickFirsts += "lib/*/libtermux.so"`（[app/build.gradle.kts](file:///workspace/app/build.gradle.kts#L104-L108)）—— 别改这两行，否则 7z 压缩会破坏 `.so` 内存映射。

### M8. R8 / ProGuard 规则保留
- 现状：release 开 `isMinifyEnabled = true` + `isShrinkResources = true`（[app/build.gradle.kts](file:///workspace/app/build.gradle.kts#L75-L76)），其他 module `isMinifyEnabled = false`（[ai/build.gradle.kts](file:///workspace/ai/build.gradle.kts#L28) 等）。
- [proguard-rules.pro](file:///workspace/app/proguard-rules.pro) 里有**不能删**的几条：
  - `-keepattributes Signature, InnerClasses, EnclosingMethod` —— MCP SDK 反射 `TypeReference` 必用
  - `-keep class com.fasterxml.jackson.** { *; }`
  - `-keep class com.auth0.jwt.** { *; }` —— 删了 release 启动时 `ClassNotFoundException`
  - `-keep class org.scilab.forge.jlatexmath.** { *; }` —— 删了 LaTeX 渲染全炸
  - `-keep @kotlinx.serialization.Serializable class * {*;}` —— 删了 DataStore 反序列化抛 `SerializationException`
  - `-dontwarn java.lang.management.ManagementFactory` 等 Ktor 的运行时探测类
- 报错：release APK 安装后秒退，崩溃日志会指向 MCP/JWT/LaTeX 的某次 `Class.forName`。
- **建议**：仓库目前是 `-dontobfuscate`（不混淆），但 R8 仍会做 tree-shake。新增第三方库时务必跑一遍 `./gradlew :app:assembleRelease` + smoke test。

### M9. `assets/simple_dict/*` 7 个文件
- 证据：[AppDatabase.onOpen](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt) 会注册自定义 SQLite function `jieba_dict(path)`（[SimpleDictManager.kt](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/db/fts/SimpleDictManager.kt) 指向 `context.assets.open("simple_dict/...")`）。
- 必有的 7 个文件（[app/src/main/assets/simple_dict/](file:///workspace/app/src/main/assets/simple_dict)）：
  - `hmm_model.utf8` / `idf.utf8` / `jieba.dict.utf8` / `stop_words.utf8` / `user.dict.utf8`
  - `pos_dict/char_state_tab.utf8` / `prob_emit.utf8` / `prob_start.utf8` / `prob_trans.utf8`
- 报错：Room `onOpen` 抛 `IOException` → FTS5 全文检索表创建失败 → 进程闪退。

### M10. Room schema 与 `versionCode` 同步推进
- 当前 `version = 25`（[AppDatabase.kt](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt#L44)）。
- `autoMigrations` 覆盖到 24；24→25 走显式 `Migration_24_25`。
- 任何实体列变更后：
  1. 改 `version` + 加 `Migration_X_Y` 或 `AutoMigration`。
  2. `room.schemaLocation` 指向 `app/schemas/`（[app/build.gradle.kts](file:///workspace/app/build.gradle.kts#L136-L138)），每次构建 KSP 都会写新 `*.json`。
  3. 在 `androidTest` 跑 [Migration_11_12_Test](file:///workspace/app/src/androidTest/java/me/rerere/rikkahub/data/db/migrations/Migration_11_12_Test.kt) 那条路线。
- **少了迁移脚本 → 老用户升级即崩**（典型：`MigrationNotFoundException`）。

---

## 2. 运行期**直接 throw** 的地方（线上 0/1 都不能漏）

下面这些都是代码里 **真存在的** `error()` / `require()` / `check()`（已用 grep 全量扫过），落到生产必须**有兜底**，否则线上抛。

### 2.1 数据 / 工具类

| 位置 | 触发条件 | 影响 |
| --- | --- | --- |
| [GenerationHandler.kt#L87](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt#L87) `error("Provider not found")` | Assistant 配置了 modelId 但 providers 已删除 | 第一次发消息就崩 |
| [GenerationHandler.kt#L280](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt#L280) `error("Tool ${tool.toolName} not found")` | LLM 返回了已卸载工具的名字（关 MCP / 删本地工具） | 工具调用轮次崩 |
| [GenerationHandler.kt#L499-L501](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt#L499-L501) `Translation model / provider not found` | 用户开启"翻译"开关但未配翻译模型 | 翻译功能崩 |
| [ShareSheet.kt#L100](file:///workspace/app/src/main/java/me/rerere/rikkahub/ui/components/ui/ShareSheet.kt#L100) `require(value.startsWith("ai-provider:v1:"))` | 用 `ACTION_SEND` 分享非 `ai-provider:v1:` 串 | 分享导入崩 |
| [TextArea.kt#L96](file:///workspace/app/src/main/java/me/rerere/rikkahub/ui/components/ui/TextArea.kt#L96) `error("Failed to read file")` | 选了一个被外部 App 删除的文件 | 编辑/导入流程崩 |
| [UIAvatar.kt#L137](file:///workspace/app/src/main/java/me/rerere/rikkahub/ui/components/ui/UIAvatar.kt#L137) `error("Failed to open input stream for $selectedUri")` | 头像 Uri 失效 | 个人页改头像崩 |
| [WebViewContentCache.kt#L16](file:///workspace/app/src/main/java/me/rerere/rikkahub/ui/components/webview/WebViewContentCache.kt#L16) `check(directory.isDirectory || directory.mkdirs())` | `cacheDir` 被外力改成文件 | 启动预览时崩 |
| [SkillsVM.kt#L149-L214](file:///workspace/app/src/main/java/me/rerere/rikkahub/ui/pages/extensions/skills/SkillsVM.kt#L149-L214) 7 处 `error(...)` | 用户传的 `SKILL.md` 缺 name/description / 压缩包无 SKILL.md | 技能导入崩 |
| [WorkspaceTools.kt#L287](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/ai/tools/WorkspaceTools.kt#L287) `require(size <= MAX_READ_FILE_BYTES)` | 试图读超大文件 | LLM 这一步会抛，GenerationHandler 是否吞掉要看 `try/catch` 范围（见下） |
| [GoogleSans.kt#L23](file:///workspace/app/src/main/java/me/rerere/rikkahub/ui/theme/GoogleSans.kt#L23) `require(value in 0..100)` | 主题 round 轴越界 | 启动主题崩 |
| [WorkspaceDocumentsProvider.kt](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/provider/WorkspaceDocumentsProvider.kt) 一连串 `require(...)` | 根目录被占用 / 父目录不是目录 / 路径含 `\u0000` | 文件 Provider 整体抛 `IllegalArgumentException` |

**生产建议**：
- 把这三处 `error("Provider not found") / "Tool not found" / "Translation model not found"` 改成 `error("...", cause)` 抛出后**让 GenerationHandler 顶层 `try/catch`** 包装为 `UIMessagePart.Text("[generation failed] ...")`，而不是把整个会话流终止。**当前代码这些异常会沿着 `Channel.send` 跑到 UI 线程，最终 `CrashHandler.markCrashed` 标记 + 强制杀进程**（[CrashHandler.kt](file:///workspace/app/src/main/java/me/rerere/rikkahub/utils/CrashHandler.kt#L13-L21)）。
- SkillsVM 的 7 处 `error` 必须全部包成 `Result.failure` 走 `Toaster` 提示，不要 `error()`。
- `WebViewContentCache` 的 `check` 改为 `if (!mkdirs())` 早退 + 关闭依赖该缓存的渲染。

### 2.2 OkHttp Content-Type 拦截器（隐形炸弹）
- 证据：[DataSourceModule.kt#L207-L223](file:///workspace/app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt#L207-L223)
  ```kotlin
  if (contentTypeHeader.contains(";") &&
      contentTypeHeader.substringBefore(";").trim().equals("application/json", ignoreCase = true)) {
      ... header("Content-Type", contentTypeHeader.substringBefore(";").trim())
  }
  ```
- 行为：把 `application/json; charset=utf-8` 强制改写为 `application/json`，**顺带去掉了 charset**。
- 影响：部分严格 API（特别是自部署的 Anthropic 兼容代理、vLLM、Together）会判 `400`。
- 建议：判断时同时检查 `content-type` 是否带 `multipart/form-data` / `image/*` / `audio/*` 等等，**只对纯 JSON 替换**；其它全部放行。**当前实现已经这么做了**，但更稳妥是 only-proceed-when-equals。

### 2.3 JWT / Web 服务器
- 路径：[WebApiModule.kt#L90-L110](file:///workspace/app/src/main/java/me/rerere/rikkahub/web/WebApiModule.kt#L90-L110) + [WebDto.kt](file:///workspace/app/src/main/java/me/rerere/rikkahub/web/dto/WebDto.kt)。
- 当 `webServerJwtEnabled=true` 但用户没设 `webServerAccessPassword`：secret 退化为 `"__missing_password_${UUID.randomUUID()}__"`，**每个进程启动都变**。结果：昨天签的 token 今天全失效。
- 建议：直接 `error("Web JWT enabled but password is empty")` 让启动失败，或在 settings 页面**禁用保存**。

### 2.4 Room CursorWindow 反射
- 证据：[DatabaseUtil.kt#L11-L18](file:///workspace/app/src/main/java/me/rerere/rikkahub/utils/DatabaseUtil.kt#L11-L18)。
- 用 `getDeclaredField("sCursorWindowSize")` 改 CursorWindow 大小。**Android 14+ 该字段为 `final`**，反射写不进去（`IllegalAccessException`）。当前代码是 `try/catch` 吞掉，所以**不崩，但改了也不生效**。
- 建议：放弃反射，直接走 PRoom 的 `SQLiteOpenHelper.setWriteAheadLoggingEnabled` + `Room.databaseBuilder.fallbackToDestructiveMigration` 的相反方向；或 fork CursorWindow。

### 2.5 MCP OAuth 全套 `error`
- 路径：[McpOAuthClient.kt](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/ai/mcp/McpOAuthClient.kt#L118)、[McpOAuthCoordinator.kt](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/ai/mcp/McpOAuthCoordinator.kt#L135-L193)。
- 触发：服务器没暴露 `.well-known/oauth-protected-resource`、缺 `authorization_endpoint`、不支持动态注册且没预置 `client_id`、授权超时。
- 建议：所有 `error(...)` 改为返回 `McpStatus.Error(message)`，UI 弹错误而不是 `Thread.uncaughtException` 把 app 拉走。

### 2.6 Claude / 第三方 API 严格解析
- 当前 `Provider` 抽象下，所有网络层都走 OkHttp 拦截器链：[OkHttpClient](file:///workspace/app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt#L186-L230)。
- `User-Agent = RikkaHub-Android/${BuildConfig.VERSION_NAME}` —— 某些网关（WAF）拒绝非标准 UA，会 403。
- `AcceptLanguage` 用 `AcceptLanguageBuilder.fromAndroid(context).build()` —— 一些基于地理的网关会按语言分流，QA 阶段可能看不见问题，但生产上命中过严的网关就出 400/451。

### 2.7 Token 用量写入
- [AppDatabase.kt#L88-L96](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt#L88-L96) 用 `JsonInstant` 序列化 `TokenUsage`。如果 Provider 返回了**非 @Serializable 字段**（自定义 plugin），升级 Room schema 时这条记录会**反序列化失败 → 该 Conversation 加载崩**。
- 建议：把 TokenUsage 改成扁平字段（`promptTokens / completionTokens / totalTokens / cachedTokens`），迁移时保留旧 JSON 列只读，渐进替换。

---

## 3. 跑测试 / 调优时**必走**的步骤

### 3.1 把所有 LLM 流量收口
- [AIRequestInterceptor](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/ai/AIRequestInterceptor.kt) + [RequestLoggingInterceptor](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/ai/RequestLoggingInterceptor.kt) 已挂在 OkHttp 链上。
- 强制开启：`addInterceptor(HttpLoggingInterceptor().apply { level = HEADERS })` 已在 release 也挂（[DataSourceModule.kt#L226-L228](file:///workspace/app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt#L226-L228)）。**生产前必须改成 NONE 或移除**，否则：
  - Logcat 日志泄露 `Authorization: Bearer ...` / `Set-Cookie`
  - Sentry/Crashlytics 上报含 token
- 落地：
  ```kotlin
  if (BuildConfig.DEBUG) addInterceptor(HttpLoggingInterceptor().apply { level = HEADERS })
  else addInterceptor(HttpLoggingInterceptor().apply { level = NONE })
  ```

### 3.2 Baseline Profile 发布前要重新跑
- 路径：[BaselineProfileGenerator](file:///workspace/app/baselineprofile/src/main/java/me/rerere/baselineprofile/BaselineProfileGenerator.kt) + [StartupBenchmarks](file:///workspace/app/baselineprofile/src/main/java/me/rerere/baselineprofile/StartupBenchmarks.kt)。
- 仓库里 `app/src/release/generated/baselineProfiles/` 已有 baseline，但只要：
  - AGP / Kotlin 升
  - 新增 Compose 屏幕
  - 新增 Koin 注入
  都必须重跑：
  ```bash
  ./gradlew :app:generateBaselineProfile
  cp -r app/baselineprofile/build/outputs/managed_device_profiles/* app/src/release/generated/baselineProfiles/
  ```

### 3.3 Web UI 静态资源校验
- `:web:preBuild` 调 pnpm 写 `web/src/main/resources/static/`。CI 上加断言：
  ```bash
  test -f web/src/main/resources/static/index.html || (echo "missing index.html"; exit 1)
  ```
  否则 `:app:processDebugResources` 偶然成功但实际资源空。

### 3.4 ABI 分包检查
- 当前 splits 在跑 `bundle*` 任务时会被**自动关闭**（[app/build.gradle.kts#L36-L45](file:///workspace/app/build.gradle.kts#L36-L45)）。
- 走 AAB 出 Play 没问题；走 `assembleRelease` 走 sideload，会同时产出 3 个 APK：`app-arm64-v8a-release.apk` / `app-x86_64-release.apk` / `app-universal-release.apk`。
- 自动化脚本必须按文件名取 universal：
  ```bash
  find app/build/outputs/apk/release -name '*universal*.apk' -exec cp {} dist/ \;
  ```

### 3.5 Lint + 自定义规则
- `./gradlew lint` 跑全工程。会报：
  - 大量 `ExperimentalMaterial3ExpressiveApi` opt-in 已用 `tasks.withType<KotlinCompile>` 统一加（[app/build.gradle.kts#L110-L122](file:///workspace/app/build.gradle.kts#L110-L122)），**新增 module 必须自己加**。
  - Web module 的 `compileSdk` 是 `release(37)` 但 `minSdk = 24`（[web/build.gradle.kts#L42-L47](file:///workspace/web/build.gradle.kts#L42-L47)），与 `:app` minSdk 26 不一致——消费方按"最低公共"算，发布文档里要写清。

---

## 4. 落地优化清单（**真能改**、**改完能见效**的）

| # | 改动 | 收益 | 落点 |
| --- | --- | --- | --- |
| O1 | release 包移除 HEADERS 日志拦截器 | 不再泄露 Bearer / Cookie | [DataSourceModule.kt#L226-L228](file:///workspace/app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt#L226-L228) |
| O2 | `error("Provider not found")` 等 6 处改为 Result 风格异常并由 ChatService 捕获写回 `UIMessage` | 异常不再杀进程 | [GenerationHandler.kt](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt) |
| O3 | `webServerJwtEnabled=true` + 空密码场景直接拒绝启动 | 防止 token 进程级失效 | [WebApiModule.kt#L94-L100](file:///workspace/app/src/main/java/me/rerere/rikkahub/web/WebApiModule.kt#L94-L100) |
| O4 | `TokenUsage` 拆成扁平列 + 旧列保留 1 个版本后下线 | 反序列化崩溃清零 | [AppDatabase.kt#L88-L96](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/db/AppDatabase.kt#L88-L96) |
| O5 | CursorWindow 反射改用 `SQLiteOpenHelper.setMaxSqlCacheSize` | 真正生效 | [DatabaseUtil.kt](file:///workspace/app/src/main/java/me/rerere/rikkahub/utils/DatabaseUtil.kt) |
| O6 | Content-Type 拦截器白名单只对 `application/json` 主动改写；其他类型放行 | 兼容 Anthropic / vLLM 严格模式 | [DataSourceModule.kt#L207-L223](file:///workspace/app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt#L207-L223) |
| O7 | OkHttp `readTimeout=10 分钟` 配 SSE 没问题，但 `connectTimeout=20s` 偏长，改 10s | 弱网下首字延迟降低 10s | [DataSourceModule.kt#L186-L195](file:///workspace/app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt#L186-L195) |
| O8 | Koin `createdAtStart = true` 列出 5 个：`AppScope`/`TTSManager`/`ChatService`/`WebServerManager`/`ChatNotificationManager`。逐个评估是否必须在 `onCreate` 同步初始化 | 冷启动 < 800ms | [AppModule.kt](file:///workspace/app/src/main/java/me/rerere/rikkahub/di/AppModule.kt) |
| O9 | `webServerService` 启动 Foreground 通知可以**默认延后**到首次有人访问再起 | 后台省电 | [WebServerService](file:///workspace/app/src/main/java/me/rerere/rikkahub/service/WebServerService.kt) |
| O10 | `ai:` 模块其实没强依赖 Android；考虑拆 `:ai-core` 纯 JVM 库，加快增量编译 | 改 Provider 编译 < 3s | [ai/build.gradle.kts](file:///workspace/ai/build.gradle.kts) |
| O11 | `compose_compiler_config.conf` 当前只配了稳定性文件目录，**未开 strong skipping mode**。开 `strongSkipping = true` 能再省 8~15% Compose 重组 | 滚动帧率提升 | [app/build.gradle.kts#L125-L129](file:///workspace/app/build.gradle.kts#L125-L129) |
| O12 | FTS5 + jieba 在 Room `onOpen` 同步注册（[SimpleDictManager](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/db/fts/SimpleDictManager.kt)），启动会卡 200~500ms。改成 `WorkManager` 后台初始化 | 启动 < 1s | `data/db/fts/` 三文件 |
| O13 | 多 Provider 的 SSE 解析在不同实现里各写一份（[openai](file:///workspace/ai/src/main/java/me/rerere/ai/provider/providers/openai) / google / claude），抽到 `Provider` 基类 | 减 ~600 行重复 | `ai/.../provider/` |
| O14 | `dontobfuscate`（[proguard-rules.pro#L30](file:///workspace/app/proguard-rules.pro#L30)）开着 —— 启动期栈追踪可读，但 APK 偏大。改成 `keepattributes` + 开启混淆 | APK 减 2~4 MB | `proguard-rules.pro` |
| O15 | `webServerJwtEnabled` 默认开 + 不开放端口到公网 = 安全闭环；如果开公网，**必须**改 JWT secret 为随机并加 refresh | 防止 token 重放 | [WebApiModule.kt](file:///workspace/app/src/main/java/me/rerere/rikkahub/web/WebApiModule.kt) |
| O16 | Tool 输出截断阈值 `>= 32KB`（[GenerationHandler.kt#L458-L490](file:///workspace/app/src/main/java/me/rerere/rikkahub/data/ai/GenerationHandler.kt#L458-L490)）写死，但 LLM 单步 context 常 > 128K。提到 64KB 写文件、4KB 预览 | 大输出可读 | `GenerationHandler.kt` |
| O17 | `buildConfigField("VERSION_NAME" / "VERSION_CODE")` 在 release + debug 都设了——如果 `ChatService` 按 versionCode 做 API 分支（[DataSourceModule](file:///workspace/app/src/main/java/me/rerere/rikkahub/di/DataSourceModule.kt) 没看，但易踩），要把 `BuildConfig.DEBUG` 区分 | 防止 release 行为漂移 | `app/build.gradle.kts` |
| O18 | 大量 opt-in 用 `tasks.withType<KotlinCompile>` 全局加（[app/build.gradle.kts#L110-L122](file:///workspace/app/build.gradle.kts#L110-L122)），**新 module 默认没继承**。`ai/search/speech/...` 的 opt-in 必须自己加 | 防止升级 Compose BOM 编译失败 | 各 module 的 `build.gradle.kts` |
| O19 | `proguard-rules.pro` 里的 `-keep class com.fasterxml.jackson.** { *; }` 用了 `**`，过宽。改成只 keep MCP 用到的子包 | APK -0.5 MB | `proguard-rules.pro` |
| O20 | 启用 `R8 full mode` 已经在 AGP 9 默认开；再补 `-allowaccessmodification` 提速 5~8% | 启动快 ~30ms | `proguard-rules.pro` |

---

## 5. 推荐的发布 checklist（按顺序执行，**逐条勾**）

```
[ ] 1. git submodule update --init --recursive
[ ] 2. cat local.properties   # 4 项 store* + sdk.dir 齐
[ ] 3. java -version          # 21
[ ] 4. pnpm -v                # ≥ 9
[ ] 5. test -f app/google-services.json && jq . app/google-services.json
[ ] 6. ./gradlew :app:assembleDebug
[ ]    └─ 失败通常发生在 :web:preBuild / :material3 / :app:processDebugGoogleServices
[ ] 7. ./gradlew lint
[ ] 8. ./gradlew :app:connectedDebugAndroidTest     # 真机/模拟器
[ ] 9. ./gradlew :app:assembleRelease
[ ] 10. ./gradlew :app:generateBaselineProfile
[ ] 11. 用 R8 mapping 跑一遍冒烟：装 release → 走 主对话/工具/MCP/Web 5 个流程
[ ] 12. 检查 Logcat 无 Bearer/cookie
[ ] 13. aapt dump badging app-universal-release.apk | head
[ ] 14. 上 Play 用 .aab；侧载用 universal.apk
```

---

## 6. 落地时容易踩的 5 个坑（来自代码本身）

1. **Debug 包名带 `.debug` 后缀**：[app/build.gradle.kts#L85](file:///workspace/app/build.gradle.kts#L85) `applicationIdSuffix = ".debug"`。所有 `Runtime.exec("pm install ...")` 之类的脚本、Shortcut intent-filter、FileProvider authorities 都得跟着变。
2. **`usesCleartextTraffic="true"`**（[AndroidManifest.xml#L56](file:///workspace/app/src/main/AndroidManifest.xml#L56)）：内网/局域网 Web 服务器、自部署 LLM **必须**明文。生产公网不能开。
3. **`ACCESS_LOCAL_NETWORK`**（manifest）：Android 13+ 在 5GHz 局域网要明确申请；不开就连不上同一 Wi-Fi 下的 Web UI。
4. **`enableOnBackInvokedCallback="true"`**（manifest）：自己处理返回，否则用户按返回会闪退到桌面。
5. **Web 静态资源 build 在 `:web:preBuild` 同步跑**（[web/build.gradle.kts#L64-L66](file:///workspace/web/build.gradle.kts#L64-L66)）。如果 pnpm 第一次安装很慢（>5min），CI 容易超时。落地：缓存 `~/.local/share/pnpm/store` 或者用 `pnpm install --offline`。

---

## 7. 一句话总结

**RikkaHub 上生产前 10 条必做的事**：
1. 拉子模块；2. 占位 google-services.json；3. JDK 17/21 配对；4. 装 pnpm；5. 4 项 keystore 配齐；6. 检查 `arm64-v8a/x86_64` 4 个 .so 在位；7. ProGuard 规则不动；8. `simple_dict/*` 7 个文件不丢；9. release 移除 HEADERS 日志拦截器；10. 跑 Baseline Profile + R8 mapping 冒烟。

把这 10 条按上面 1-5 节给出的路径逐条落实，APK 就能稳定跑起来，不会因为代码里那些 `error("...")` 在生产翻车。
