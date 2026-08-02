# Nimbus HUD and Skija Architecture and Usage Guide

本文档是 Nimbus 自定义 Skija 子系统的架构说明、使用手册和 AI 修改约束。

它描述的是当前仓库中的真实实现，不是对理想架构的猜测。修改 Skija 相关代码前，应同时阅读本文档和对应源码；当文档与源码不一致时，以源码为准，并在同一次修改中更新本文档。

## 1. 设计目标

Nimbus 的 Skija 子系统负责在 Minecraft OpenGL 客户端中提供统一的 2D 矢量渲染能力，包括：

- HUD 绘制。
- 自定义 Screen 绘制。
- 字体测量与文本绘制。
- 图片、Minecraft 纹理和实体纹理访问。
- SVG 图标绘制。
- 圆角、平滑路径、渐变和模糊等效果。
- 场景截图、阴影、纹理和模糊后处理。
- Skija、OpenGL 和 Minecraft GUI 管线之间的状态隔离。

核心原则：

1. `RenderManager` 是进程级所有者。
2. `SkijaRenderer` 是业务代码唯一的 Skija 根入口。
3. 渲染上下文、Layer、缓存和生命周期只能通过 `cc.nimbus.utils.render.skija.api` 进入。
4. `internal` 中的 Backend、缓存、Surface 和工厂不属于功能 API。
5. 每帧资源、Layer target 和进程资源使用不同的生命周期。
6. 不允许功能自行创建第二套 Skija Backend、DirectContext 或全局缓存。

## 2. 唯一入口

所有功能从下面的入口取得渲染器：

```java
SkijaRenderer skija = Core.INSTANCE
        .getRenderManager()
        .skija();
```

禁止绕过 Nimbus 入口直接取得基础设施：

```java
SkijaBackend.INSTANCE.getCanvas();
ImageStore.getResourceImage(...);
FontFamily.getCachedFont(...);
SvgIconStore.draw(...);
```

这些类属于实现细节。若公开 API 缺少必要能力，应先确认现有 API 确实无法表达，再扩展 `skija.api`，不能让功能直接穿透到 `internal`。

这条边界不禁止在 `SkijaFrame` 作用域内使用 Skija 自己的绘图类型。Nimbus 负责取得上下文和管理生命周期，具体绘图仍可以使用：

```java
import io.github.humbleui.skija.Canvas;
import io.github.humbleui.skija.ImageFilter;
import io.github.humbleui.skija.Paint;
import io.github.humbleui.skija.Path;
import io.github.humbleui.types.Point;
import io.github.humbleui.types.Rect;
import io.github.humbleui.types.RRect;
```

准确规则是：

> 所有 Skija 上下文、Layer、缓存和 Minecraft/GL 集成都从 Nimbus `skija.api` 进入；在 `SkijaFrame` 内允许合理使用 Skija 原生绘图 API。

### 2.1 原生 Skija 使用边界

可以直接使用的局部绘图能力：

- `Canvas` 的 save/restore、translate、rotate、clip 和 draw 方法。
- `Paint`、`Path`、`Rect`、`RRect`、`Point` 和矩阵等绘图对象。
- `BlendMode`、stroke、sampling 和 clip 等枚举。
- 只在当前代码块使用的 `ImageFilter`、`ColorFilter` 等效果。

需要谨慎评估的高级能力：

- Paragraph、TextLine、Shaper 和 TextBlob。
- RuntimeEffect 和自定义 Shader。
- 临时 Image、复杂 Filter 和可复用路径。

单次局部需求可以在明确所有权后直接使用；当多个功能重复使用，或者涉及缓存、自动释放和统一语义时，应提升为 Nimbus `api/internal` 能力。

功能代码禁止自行创建或管理：

- `DirectContext`。
- `Surface` 和 `BackendRenderTarget`。
- 全局 `FontMgr`、Typeface 注册表或字体缓存。
- `SVGDOM`、图片缓存和 Minecraft GL texture copy。
- framebuffer、OpenGL 状态栈和另一套 Skija Renderer。

不要为了“完全封装”机械包装 Skija 的每一个 Canvas 方法。Nimbus facade 只包装需要统一语义、生命周期、缓存或 Minecraft 集成的能力。

Skija Java binding 与 Windows x64 native 由 Gradle 依赖提供，版本统一锁定在 `gradle.properties`：

- 修改前应读取该版本的官方源码 JAR 或上游源码，确认真实方法、参数、所有权和关闭语义。
- 不得以旧版文档、记忆或仓库历史中的内嵌副本代替当前依赖源码。
- 功能可以导入合适的公开绘图类型，但 Nimbus 业务逻辑不得写进 `io.github.humbleui` 第三方包。
- 依赖坐标只在 `build.gradle` 声明，版本只在 `gradle.properties` 声明；不要重新复制源码、DLL 或 ICU 数据进仓库。

`RenderManager` 的生命周期：

```text
Core init
  -> RenderManager.init()
    -> SkijaRenderer.init()

Minecraft shutdown
  -> RenderManager.shutdown()
    -> SkijaRenderer.close()
      -> Layers / Backend / caches / native resources close
```

功能代码不得调用 `SkijaRenderer.close()`。

`SkijaRenderer` 是一次性进程资源。`RenderManager.shutdown()` 后重复关闭是幂等的，但不能重新 `init()` 或通过旧 Layer 重新创建 target。关闭流程会尽量释放全部 Layer、字体、缓存、Backend 和 native 资源；单项清理失败不会阻止后续资源继续关闭，最终异常会通过 suppressed errors 保留。

## 3. 架构与包边界

```text
cc.nimbus.managers.RenderManager
└─ cc.nimbus.utils.render.skija.api.SkijaRenderer
   ├─ SkijaHud
   ├─ SkijaLayer
   ├─ SkijaTextMetrics
   ├─ SkijaPostProcess
   └─ named layers
      └─ internal.backend.SkijaRenderTarget
         └─ internal.backend.SkijaBackend
```

### 3.1 `api`

`api` 是功能代码允许使用的表面：

| 类型 | 职责 |
|---|---|
| `SkijaRenderer` | 根入口、命名 Layer、HUD、测量、后处理配置和进程关闭 |
| `SkijaHud` | 收集 HUD 绘制回调 |
| `SkijaLayer` | 一个命名的 Minecraft GUI 合成层 |
| `SkijaFrame` | 单次绘制回调中的上下文 |
| `SkijaDraw` | 基础图形、图片和 SVG 绘制 |
| `SkijaText` | 当前 Canvas 上的文字绘制与测量 |
| `SkijaTextMetrics` | Frame 外的文字测量 |
| `SkijaImages` | Renderer 管理的图片访问 |
| `SkijaEffects` | 自动按帧释放的 Shader 和 MaskFilter |
| `SkijaPostProcess` | 后处理区域收集和绘制 |
| `PostProcessOptions` | 后处理配置值对象 |

### 3.2 `internal.backend`

负责：

- OpenGL `DirectContext`。
- 包装 Minecraft framebuffer。
- 创建颜色纹理和深度模板缓冲。
- 保存、恢复 OpenGL 状态。
- Surface flush 和提交。
- Windows x64/OpenGL 兼容性检查。

只有 Backend 可以直接修改 OpenGL framebuffer、viewport 和 texture binding。

### 3.3 `internal.effect`

包含后处理管线、RuntimeEffect、Gaussian/Kawase blur、Shadow、Texture 和离屏 Surface。

功能只通过 `frame.effects()`、`frame.postProcess()` 和 `renderer.configurePostProcess()` 使用这些能力。

### 3.4 `internal.image`

包含图片缓存、Minecraft GL 纹理复制、场景截图、SVG DOM 缓存和后处理使用的 SVG 栅格缓存。

普通 UI SVG 通过 `frame.draw().svg(...)` 直接矢量绘制，不经过 `SvgTextureCache`。

网络封面、头像和二维码使用 `frame.images().encoded(key, bytes)`。`key` 必须稳定标识逻辑图片；相同 key 和相同内容复用 Image，内容变化时缓存关闭并替换旧 Image。缓存是 Renderer 所有的有界 LRU，调用方只借用返回值，不得手动关闭，也不得在功能里建立第二套 Skija Image 缓存。异步线程只能准备编码字节，解码入口必须在 render thread 的 `SkijaFrame` 内调用。

### 3.5 `internal.text`

包含字体注册、Font 缓存、宽度缓存和统一 baseline 计算。

功能使用 `frame.text()` 或 `renderer.text()`，不得直接取得缓存中的 `Font`。

### 3.6 `internal.resource`

包含有界缓存、classpath 资源读取和单帧原生资源作用域。

缓存上限和关闭策略应在这里统一实现，功能不能自行复制同类缓存。

## 4. 所有权与生命周期

资源问题必须先判断所有者，不能简单地选择“全部关闭”或“全部常驻”。

| 对象 | 所有者 | 生命周期 | 功能是否关闭 |
|---|---|---|---|
| `SkijaRenderer` | `RenderManager` | Minecraft 客户端进程 | 否 |
| `SkijaBackend` / `DirectContext` | `SkijaRenderer` | 进程级 | 否 |
| 命名 `SkijaLayer` | `SkijaRenderer` | 从首次获取到 Renderer shutdown | 否 |
| Layer framebuffer target | 当前 Layer 使用场景 | Screen/HUD 活跃期 | Screen 可 `releaseTarget()` |
| `SkijaFrame` | 渲染子系统 | 单次 callback | 否，不得保存引用 |
| `Shader` / `MaskFilter` from `frame.effects()` | `SkijaFrame` | 单帧 | 否，不得手动关闭或跨帧保存 |
| `Image` from `frame.images()` | `ImageStore` | 有界缓存 | 否 |
| 功能自己 `new Paint/Path/ImageFilter` | 功能 | 当前代码块 | 是，使用 try-with-resources |
| 字体、Typeface 和 FontCollection | 字体子系统 | 进程级缓存 | 否 |

### 4.1 Layer 与 RenderTarget 不是同一个生命周期

`renderer.layer("click-gui")` 按名称缓存并返回同一个 `SkijaLayer`。Layer 是可复用句柄；它内部的全屏 framebuffer 是昂贵且可重建的 target。

```java
SkijaLayer layer = skija.layer("my-screen");
```

规则：

- Layer 名称必须稳定，并代表一个逻辑渲染域。
- 不要每帧生成动态名称。
- 不要让两个同时活跃的消费者使用同一个名称。
- Screen 离开时调用 `releaseTarget()`，下次 `ensure()` 会重建 target。
- `SkijaLayer` 不实现 `AutoCloseable`，功能不能结束命名 Layer 的生命周期。
- Renderer shutdown 会通过内部方法最终销毁所有 Layer target。

### 4.2 为什么不等待 GC

全屏 Layer 包含 RGBA8 颜色纹理和 D24S8 深度模板缓冲。4K 下两者合计可能占用约 66 MB 显存。Java Cleaner 只能作为原生对象兜底，不能提供及时、可靠的 GPU 释放时机。

### 4.3 当前 Screen 生命周期

所有长期维护的 Skija Screen 继承 `cc.nimbus.ui.screen.AbstractSkijaScreen`。基类统一负责：

1. 取得稳定命名的 Layer。
2. 在 `extractRenderState` 中提取 framebuffer 尺寸、鼠标坐标和 Layer 状态。
3. 暂存待渲染帧，并由通用 `GuiRendererMixin` 分发 `renderExtractedFrame()`。
4. Screen 移除时调用 `layer.releaseTarget()`。

子类只实现 `renderSkija(SkijaFrame)`，可按需覆盖 `extractSkijaState(...)`、`removedSkijaScreen()` 和 `requestedSceneCaptureRate(...)`。Mixin 只判断 `AbstractSkijaScreen`，不得再按具体 Screen 类名增加分支。

`removed()` 表示 Screen 当前不再显示，不表示 Java 对象永久销毁。因此基类释放可重建 target，不关闭进程字体、命名 Layer 句柄或共享图片缓存。

## 5. Render Thread 规则

下列操作必须在 Minecraft render thread：

- `SkijaLayer.ensure/clear/render/releaseTarget`。
- Minecraft GL 纹理访问和复制。
- 场景截图。
- Backend 或 Surface 操作。
- 使用 `SkijaFrame` 绘制。

`SkijaHud.submit(...)` 收集的 callback 会稍后在渲染阶段执行。建议在提交前计算并冻结业务数据：

```java
record Entry(String text, float x, float y) {}

List<Entry> entries = buildEntries();
skija.hud().submit(frame -> {
    for (Entry entry : entries) {
        frame.text().draw(FONT, 16f, entry.text(), entry.x(), entry.y(), 0xFFFFFFFF);
    }
});
```

不要把事件对象、可变集合或生命周期不明确的 Minecraft 状态保存到跨帧 callback 中。

## 6. 坐标系统

Skija Layer 使用 framebuffer 物理像素，不是 Minecraft GUI scale 后的逻辑坐标。

```java
int framebufferWidth = minecraft.getWindow().getWidth();
int framebufferHeight = minecraft.getWindow().getHeight();
```

`SkijaFrame.width()` 和 `height()` 也是 framebuffer 尺寸。

Screen 鼠标从 GUI 坐标转换为 framebuffer 坐标：

```java
float framebufferX = guiWidth > 0
        ? mouseX * framebufferWidth / guiWidth
        : (float) mouseX;

float framebufferY = guiHeight > 0
        ? mouseY * framebufferHeight / guiHeight
        : (float) mouseY;
```

不要直接把 `Screen.width/height` 或 GUI mouse 坐标与 Skija framebuffer 图形混用。

## 7. HUD 使用方式

HUD 功能不创建自己的 Layer。所有 HUD 功能共享基础设施持有的 `"hud"` Layer。

```java
@EventTarget
private void onRender2D(EventRender2D event) {
    SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();

    List<Row> rows = buildRows(skija.text());
    skija.hud().submit(frame -> {
        try (Paint paint = new Paint()
                .setAntiAlias(true)
                .setColor(0xFFFFFFFF)) {
            for (Row row : rows) {
                frame.text().draw(NimbusFonts.BODY_MEDIUM, 16f,
                        row.text(), row.x(), row.y(), paint);
            }
        }
    });
}
```

禁止每个 HUD 模块调用：

```java
skija.layer("module-name");
```

独立全屏 Layer 会额外分配 framebuffer，并破坏统一 HUD 合成顺序。HUD 模块只提交绘制内容。

`skija.hud().render(...)` 由渲染基础设施调用，普通功能不要主动调用。

## 8. Screen 使用方式

参考 `cc.nimbus.ui.menu.TitleScreen` 和 `cc.nimbus.ui.clickgui.ClickGuiScreen`：

```java
public final class ExampleScreen extends AbstractSkijaScreen {
    public ExampleScreen() {
        super(Component.literal("Example"), "example-screen");
    }

    @Override
    protected void renderSkija(SkijaFrame frame) {
        frame.draw().roundedRect(20, 20, 180, 60, 8, 0xE0202226);
        frame.text().draw(NimbusFonts.BODY, 16,
                "Example", 36, 36, 0xFFFFFFFF);
    }
}
```

继承基类后无需修改 Mixin。输入事件仍由具体 Screen 接收，并使用基类提供的 `framebufferX/Y(...)` 与渲染所用逻辑变换做逆变换。修改提取和辅助渲染顺序前必须阅读当前 Minecraft 26.2 映射源码与现有 Mixin。

## 9. 文本

### 9.1 已注册字体

当前 `FontFamily` 注册：

- `Geist-SemiBold`
- `Inter-Regular`
- `Inter-Medium`
- `Inter-SemiBold`
- `NotoSans-Regular`
- `NotoSans-Medium`
- `NotoSansCJKsc-Regular`
- `NotoSansCJKsc-Medium`

业务代码优先使用 `cc.nimbus.utils.render.font.NimbusFonts` 的语义角色，不散落 family 字符串。Geist 用于品牌和窗口标题，Inter 用于控件、Module 和数字，Noto Sans 用于音乐内容；每个拉丁字体角色都注册了匹配字重的 Noto Sans CJK SC fallback。

`TextFactory` 按 Unicode code point 选择字体 run。测量与绘制使用同一份有界布局缓存，因此混合文本的 advance、居中、选区和光标不能各自实现另一套宽度算法。缓存只保存 Java 布局数据，Font 和 Typeface 仍由字体子系统统一拥有。

字体名称必须完全匹配。未注册字体会立即抛出异常，不会静默回退到系统字体。

字体资源缺失、字体文件无法解析或 family 重复注册会在字体子系统初始化时直接失败，并在异常中保留资源路径和 family。字号必须是有限正数。Renderer shutdown 会关闭 Font 缓存、Typeface、`TypefaceFontProvider`、`FontCollection` 和文本阴影 Paint。

### 9.2 坐标语义

```java
frame.text().draw(family, size, text, x, y, color);
frame.text().center(family, size, text, centerX, y, color);
```

`draw` 和 `center` 的 `y` 表示文本布局顶部。内部使用 `FontMetrics.ascent` 计算 baseline。

```java
frame.text().leftY(family, size, text, x, y, height, paint);
```

表示左对齐，并在 `[y, y + height]` 中垂直居中。

```java
frame.text().box(family, size, text, x, y, width, height, paint);
```

表示在指定矩形中水平和垂直居中。

Frame 外只测量时使用：

```java
float width = skija.text().width(family, size, text);
float height = skija.text().height(family, size);
```

所有公开文本绘制和测量方法都将 `null` 文本视为空字符串；family 名称和字号错误仍会立即抛出异常。

不要通过 `measureText()` 的单个字符串视觉 bounds 调整每次绘制的 baseline，否则不同文本会产生垂直像素相位变化。

### 9.3 Hinting 注意事项

字体轮廓异常可能只发生在特定字体面和字号。当前字体统一使用 Skija 灰度抗锯齿、subpixel positioning 和 `SLIGHT` hinting；不要根据系统字体预览的 ClearType 结果直接推断 Skija 输出。

处理顺序：

1. 用其他已注册字体面复现。
2. 确认问题是字形轮廓还是字符 advance。
3. 检查真实 `FontHinting`、`FontEdging` 和字体文件。
4. 不要为了一个字体面全局改成 `FontHinting.NONE`。

Windows 字体预览使用 DirectWrite/ClearType，与当前 Skija 灰度抗锯齿不是同一条栅格化管线。

## 10. Paint、Path 和原生对象

颜色重载会在 API 内部创建并关闭 Paint：

```java
frame.draw().rect(x, y, w, h, 0xFFFFFFFF);
frame.text().draw(FONT, 16, text, x, y, 0xFFFFFFFF);
```

若调用方传入 Paint，调用方拥有它：

```java
try (Paint paint = new Paint().setAntiAlias(true).setColor(color)) {
    frame.draw().roundedRect(x, y, w, h, radius, paint);
}
```

使用 raw Canvas 时，必须遵守 Skija 原生资源关闭规则：

```java
int save = frame.canvas().save();
try (Path path = new Path(); Paint paint = new Paint()) {
    // draw
} finally {
    frame.canvas().restoreToCount(save);
}
```

`frame.canvas()` 是逃生口，不是创建另一套渲染工具类的理由。优先扩展 `SkijaDraw` 的通用能力。

## 11. 帧效果

通过 `frame.effects()` 创建的对象由 `FrameResources` 自动释放：

```java
try (Paint paint = new Paint().setAntiAlias(true)) {
    paint.setShader(frame.effects().linearGradient(start, end, colors));
    frame.draw().rect(x, y, w, h, paint);
}
```

规则：

- 不要手动关闭 `frame.effects()` 返回的 Shader 或 MaskFilter。
- 不要将它们保存到字段、静态变量、缓存或下一帧。
- 即使 callback 抛出异常，Frame 的 `finally` 仍会释放它们。

## 12. 图片

Classpath/Minecraft Resource 图片：

```java
Image image = frame.images().resource(identifier);
frame.draw().image(image, x, y, width, height, paint);
```

Minecraft 当前 GL 纹理：

```java
Image image = frame.images().minecraftTexture(identifier);
```

实体纹理：

```java
Identifier texture = frame.images().entityTexture(entity);
Image image = frame.images().minecraftTexture(texture);
```

场景截图：

```java
skija.captureScene(60);
Image scene = frame.images().scene();
```

`captureScene(int targetFps)` 会限制昂贵的场景纹理复制频率。后处理应传入 `PostProcessOptions.blurRefreshRate()`；只有确实要求每个游戏帧更新的直接场景读取才使用无参数 `captureScene()`。

规则：

- `frame.images()` 返回的 Image 属于 Renderer 缓存，不得由功能关闭。
- 使用前允许检查 `image == null || image.isClosed()`。
- Minecraft GL texture 会被复制到 Skija 管理的纹理，不能直接把 Minecraft texture id 包装后长期保存。
- 场景截图必须在正确的 GUI/render 阶段捕获，不能从任意线程调用。

## 13. SVG

普通图标使用 SVG 内容字符串：

```java
try (Paint tint = new Paint().setAntiAlias(true).setColor(0xFFFFFFFF)) {
    frame.draw().svg(svgContent, x, y, size, tint);
}
```

`SvgIconStore` 按 SVG 内容缓存 `SVGDOM`，设置 container size 后直接绘制到当前 Canvas。功能不关闭 DOM，也不直接调用 Store。

SVG 是矢量输入，但最终仍会栅格化到显示像素。例如 `24 x 24` viewBox、`stroke-width="2"` 的图标绘制到 `18 px` 时，实际描边为 `1.5 px`，必然产生灰度抗锯齿边缘。

提高图标清晰度的优先级：

1. 显示尺寸匹配 SVG 设计网格。
2. 使用针对目标尺寸设计的图标资产。
3. 对齐静态图标的坐标和描边。
4. 最后才考虑离屏采样。

不要全局修改 Surface、字体或 Minecraft sampler 来修复单个图标资产。

## 14. 后处理

后处理配置由基础设施设置：

```java
skija.configurePostProcess(options);
```

HUD 功能在提交绘制前添加区域：

```java
skija.postProcess().add(RRect.makeXYWH(x, y, width, height, radius));
skija.hud().submit(frame -> {
    // Draw foreground content.
});
```

HUD 的后处理绘制由 Backend 在 HUD callback 前统一执行。普通 HUD 功能不要主动调用 `hud().render(...)`。

Screen 内可使用：

```java
frame.postProcess().add(RRect.makeXYWH(x, y, width, height, radius));
frame.postProcess().draw();
```

模糊需要有效的场景截图。当前 `GuiRendererMixin` 会根据 `PostProcessOptions` 在正确阶段调用 `captureScene(options.blurRefreshRate())`；新增 Screen 集成时必须同步处理该顺序和刷新率，不能在受限模糊模式下仍然每帧复制整张场景。

每帧结束时区域由 `finishFrame()` 清理。不要把后处理区域当成持久配置。

`PostProcessOptions` 在构造时验证 blur mode、半径、pass、采样、刷新率和 opacity。非法值会在配置进入渲染管线前抛出明确参数异常。

## 15. 平台和失败行为

当前 Backend 明确支持：

- Windows x64。
- Minecraft OpenGL backend。

使用前由基础设施调用：

```java
skija.validateSupport();
if (!skija.isSupported()) {
    // Show fallback screen with skija.unsupportedReason().
}
```

功能不要捕获兼容性异常后静默继续画空界面。平台不支持时应使用普通 Minecraft GUI 明确提示。

## 16. AI 修改约束

AI 修改此子系统时必须遵守：

1. 不猜 Skija API。先读取 `gradle.properties` 锁定版本对应的官方源码 JAR 或上游源码。
2. 不猜 Minecraft GUI 生命周期。先阅读当前版本源码、Mixin 和现有调用链。
3. 不从功能包导入 `cc.nimbus.utils.render.skija.internal`；这不禁止在 Frame 内使用 `io.github.humbleui.skija` 绘图类型。
4. 不创建第二个 Renderer、Backend、DirectContext、FontManager 或图片缓存。
5. 不删除现有 API 能力来换取表面整洁。
6. 不添加空的 `beginFrame/endFrame` 协议；Frame 清理由渲染入口的 `finally` 保证。
7. 不让每个 HUD 模块拥有独立全屏 Layer。
8. 不缓存 Frame、Canvas 或帧效果对象。
9. 不关闭 Renderer 管理的 Image、字体或 Layer。
10. 不用 GC 代替明确的 GPU target 生命周期。
11. 不用全局渲染参数掩盖单个字体或 SVG 资产问题。
12. 修改公共语义时搜索并迁移所有调用点。
13. 保持 `api/internal` 边界，并优先复用现有入口。
14. 完成后至少运行 `compileJava` 和 `git diff --check`。
15. 涉及视觉或生命周期时必须列出人工验证路径，不能仅凭编译成功宣称正确。

判断是否需要新增 Nimbus 包装时遵循：

1. 单次、局部、所有权明确的绘图操作可直接使用原生 Skija。
2. 多处重复且需要统一语义时扩展 `skija.api`。
3. 涉及缓存、Minecraft texture、GL 状态或跨帧生命周期时必须放进 `internal` 统一管理。
4. 不为没有真实调用者的原生方法提前创建 facade。

AI 开始修改前应搜索：

```text
Core.INSTANCE.getRenderManager().skija()
.layer(
.hud().submit(
.releaseTarget(
frame.text()
frame.draw()
frame.images()
frame.effects()
postProcess().add(
captureScene()
```

## 17. 新功能检查表

新增 HUD 功能：

- 使用 `skija.hud().submit()`。
- 在 callback 外准备不可变绘制数据。
- 使用 framebuffer 坐标。
- 不创建 Layer。
- 正确管理自己创建的 Paint/Path。
- 不关闭 Frame 提供的资源。

新增 Screen：

- 继承 `AbstractSkijaScreen` 并使用稳定且唯一的 Layer 名称。
- 只在 `renderSkija(...)` 使用当前 `SkijaFrame`。
- 输入与渲染使用同一坐标变换及其逆变换。
- 不修改 `GuiRendererMixin` 增加具体 Screen 分支。
- 若使用 blur，先接入 scene capture。

扩展公共 API：

- 确认至少有真实调用者。
- 能用现有 `SkijaFrame` 能力表达时不新增入口。
- API 不暴露 Backend、缓存或 GL id。
- 资源所有权必须能从方法名和返回类型判断。
- 更新本文档中的 API、所有权和示例。

## 18. 验证清单

基础验证：

```powershell
.\gradlew.bat compileJava --console=plain
git diff --check
```

人工验证：

- HUD 在无 Screen 和 ChatScreen 中正常显示。
- HUD 在其他 Screen 中按预期隐藏。
- 重复打开、关闭自定义 Screen 不崩溃。
- 主菜单进入子页面再返回时 Layer 能重建。
- 调整窗口大小后 framebuffer 和鼠标坐标一致。
- 字体名称错误时有明确异常。
- 文本 `draw/center/leftY/box` 的 baseline 一致。
- SVG 在目标尺寸下没有非预期缩放。
- Resource image、Minecraft texture 和实体纹理能刷新。
- Blur 开启和关闭时 scene capture 顺序正确。
- 客户端退出时 Renderer shutdown 不发生重复关闭异常。

## 19. 当前已知边界

- `AbstractSkijaScreen` 只统一 Layer、帧提取、坐标和 target 生命周期；具体 UI 输入状态仍属于各 Screen。
- Resource image 尚未接入 Minecraft resource reload 失效通知；Minecraft texture copy 目前只在底层 GL texture id 改变时刷新。
- SVG 最终清晰度仍受实际显示像素和资产网格限制。
- 字体栅格化效果依赖具体字体面的 outline/hinting 数据。

这些边界应在真实需求出现时处理，不要预先创造没有调用者的接口。
