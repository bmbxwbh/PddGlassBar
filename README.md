# PddGlassBar · 拼多多悬浮玻璃底栏

将拼多多首页底栏替换为悬浮式液态玻璃底栏(Liquid Glass), 基于 miuix-blur 渲染管线。
UI 组件移植自 [WeKit](https://github.com/cwuom/WeKit)(Apache-2.0), 其上游为
compose-miuix-ui / Kyant0 AndroidLiquidGlass / top.yukonga.miuix.kmp.blur。

## 功能

- 原生底栏(PddTabView)/分割线/骨架屏整体隐藏, 注入 ComposeView 玻璃栏 overlay
- 图标**复用应用原生图标**: hook `PddTabView.j(url, bitmap)` 加载回调捕获位图,
  自动跟随服务端换肤与节日图标(动图取静帧)
- 点击经动态代理路由回原生 `PddTabView$g_1` 监听器 —— 页面切换语义零破坏,
  `onTabDoubleTap` 等原生行为保留
- ViewBackdrop 直接录制内容区原生 View 像素进 GraphicsLayer, 玻璃后可见真实商品流
- vibrancy + blur(18dp) + lens 折射 + 重力高光(API 33+), 低版本自动降级

## 构建(GitHub Actions)

推送到 GitHub 即可。工作流 `.github/workflows/build.yml`:
JDK21 + Gradle 9.7.0 + platform 37, 产出 standard(libxposed 入口)与
legacy(de.robv 入口)两个 flavor 的 Release APK。

**依赖要点**: miuix(`top.yukonga.miuix.kmp`)托管在 GitHub Packages
(`compose-miuix-ui/miuix`), 公开包也强制鉴权。CI 中由内置
`GITHUB_ACTOR`/`GITHUB_TOKEN`(需 packages:read 权限, 工作流已声明)自动完成;
本地构建请配置 `gpr.user`/`gpr.key` gradle 属性或同名环境变量。

```bash
# 本地构建示例
export GITHUB_ACTOR=<你的GitHub用户名>
export GITHUB_TOKEN=<具有 read:packages 的PAT>
gradle :app:assembleStandardRelease :app:assembleLegacyRelease   # 或先 gradle wrapper
```

## 安装

LSPosed 中勾选作用域 `com.xunmeng.pinduoduo`, 冷重启拼多多。

## 已知边界

| 场景 | 行为 |
|---|---|
| 直播 tab / 多多视频页 | 内容为硬件 Surface, 玻璃后采样为空白(View.draw 固有限制) |
| 节日 GIF 图标 | 取静态帧 |
| 角标 | v1 仅红点(showRedDot 同步), 未读数字角标待接事件总线 |
| API < 31 | 无 RenderEffect 模糊; < 33 无 lens 折射, 自动降级 |

## 结构

```
loader/    双入口抽象(HookBridge) + legacy 实现        standard/ libxposed 入口+META-INF/xposed
core/      GlassBarHooks —— H1注入/H2 setTabs/H3监听器包裹/H4图标捕获
ui/        BarState 状态中枢 · GlassOverlay 安装器 · GlassBarHost 组合
ui/content FloatingBottomBar + ViewBackdrop + liquid/*(自 WeKit 移植)
```
