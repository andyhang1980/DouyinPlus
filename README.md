# 抖音小能手 (DouyinPlus)

抖音 LSPosed 模块，支持自适应适配（自动发现不同版本的目标类名）。

## 功能

- 去广告（启动页广告、信息流广告）
- 视频/图片无水印下载
- 音频下载
- 复制链接
- **自适应适配** -- 抖音更新混淆后自动发现目标类，无需人工适配

## 自适应系统

模块包含三层适配策略：

1. **缓存优先** -- 上次成功发现的类名会缓存，下次启动直接使用
2. **硬编码回退** -- 已知版本的类名直接匹配
3. **结构指纹扫描** -- 新版本自动扫描类加载器，根据继承关系、字段、方法签名定位目标类

核心文件：

- `adaptive/ClassScanner.kt` -- 按结构指纹扫描 Dex 文件发现目标类
- `adaptive/HookCache.kt` -- 持久化缓存，版本后缀隔离
- `adaptive/AutoHook.kt` -- 统一的自动 Hook API

## 安装

1. 确保已安装 LSPosed 框架
2. 下载最新 APK 安装
3. 在 LSPosed 中启用模块，勾选 抖音/抖音极速版
4. 重启抖音

## 支持版本

- 抖音 `com.ss.android.ugc.aweme`
- 抖音极速版 `com.ss.android.ugc.aweme.lite`

## 编译

```bash
./gradlew assembleRelease
```

或使用 GitHub Actions 自动构建。

## 许可

仅用于学习研究，请勿用于商业用途。
