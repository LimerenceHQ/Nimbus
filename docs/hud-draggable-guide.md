# Nimbus HUD 拖拽系统使用指南

最后同步：2026-08-07。本文只说明通用 HUD 布局契约，不记录单个 HUD Module 的外观和动画。

## 职责划分

- HUD 模块负责计算未经缩放的内容尺寸，并绘制自己的内容。
- `HudViewport` 负责窗口自适应缩放与全局 `HUD Scale`。
- `Draggable` 负责归一化位置、四区锚点和组件自身缩放状态。
- `HudManager` 负责编辑交互、命中检测、边界限制和动画。
- `BasicModule` 自动发现模块中的 `Draggable` 字段。
- `ConfigManager` 自动保存和恢复 `x`、`y` 与 `scale`。

不要再创建第二套 HUD 注册表或独立的拖拽配置系统。

## 声明方式

在 `BasicModule` 中声明一个 `Draggable` 字段即可，不需要手动注册。

```java
private final Draggable drag = new Draggable("Watermark", 0.02, 0.02) {
    @Override
    public HudSize getSize() {
        return new HudSize(contentWidth(), contentHeight());
    }
};
```

`x` 和 `y` 是取值范围为 `0.0` 至 `1.0` 的归一化屏幕坐标。字段名有意保持简短，
但它们保存的不是 framebuffer 像素坐标。

`getSize()` 必须返回组件局部逻辑坐标中的未经缩放尺寸，不要乘以 `drag.getScale()`、
`HudViewport.scale()` 或 framebuffer/gui scale。最终缩放统一为：

```text
render scale = HudViewport.scale × Draggable.scale
```

`HudManager.viewport(width, height)` 会基于 `1000 × 620` 的最低逻辑视口计算窗口自适应
比例，再乘以 `ClientSettings.hudScale`。因此缩小 Minecraft 窗口时 HUD 会保持相对位置并
整体缩放，而不是重新保存一套位置。

## 渲染方式

通过共享 HUD 入口提交渲染，让 `Draggable.render()` 统一应用位置和缩放。组件内部
使用从 `(0, 0)` 开始的局部坐标绘制。

```java
SkijaRenderer skija = Core.INSTANCE.getRenderManager().skija();
HudViewport viewport = Core.INSTANCE.getHudManager().viewport(skija.width(), skija.height());
skija.hud().submit(frame -> drag.render(frame, viewport, local -> {
    local.draw().rect(0, 0, contentWidth(), contentHeight(), 0xCC171A1E);
    local.text().draw(FONT, 16, "Nimbus", 8, 6, 0xFFFFFFFF);
}));
```

不要在回调中再次平移到 `drag.getRect().x()/y()`，也不要再次调用 `canvas.scale()`。
`Draggable.render()` 已经完成这两项操作，并会在 `finally` 中恢复 Canvas 状态。

需要添加后处理区域时，应在提交渲染前取得最终的 framebuffer 边界：

```java
HudViewport viewport = Core.INSTANCE.getHudManager().viewport(skija.width(), skija.height());
HudRect bounds = drag.getRect(viewport);
float renderScale = drag.getRenderScale(viewport);
skija.postProcess().add(RRect.makeXYWH(
        bounds.x(), bounds.y(), bounds.width(), bounds.height(), radius * renderScale
));
```

后处理区域使用最终 framebuffer 边界，前景内容仍使用局部逻辑坐标。不要把两套坐标混在
同一绘制回调里。

## 四区锚定

锚点根据归一化的 `x` 和 `y` 自动判断：

| 所在区域 | 使用的锚点 | 尺寸增长方向 |
| --- | --- | --- |
| 左上区域 | 左上角 | 向右、向下增长 |
| 右上区域 | 右上角 | 向左、向下增长 |
| 左下区域 | 左下角 | 向右、向上增长 |
| 右下区域 | 右下角 | 向左、向上增长 |

当可变宽度容器中的内容需要右对齐时，可以使用 `drag.getAnchor().isRight()`
判断当前是否位于右侧区域。容器外框的锚点由 `Draggable` 统一维护，因此内容尺寸
发生变化时，固定的一侧不会移动。

`moveTo(left, top, viewport)` 接收最终 framebuffer 左上角，并根据组件中心所在象限重新
计算归一化锚点。拖动跨过屏幕中心时保存值会改变，但当前可见矩形不应跳动。

## 编辑方式

打开 Minecraft 聊天界面即可进入 HUD 编辑模式。

- 使用鼠标左键拖动组件。
- 按住 `Ctrl` 并滚动鼠标滚轮，可以缩放鼠标指向的组件。
- 组件会平滑跟随鼠标，松开后自然收敛到最终位置。
- 鼠标移入和移出组件时，编辑描边会平滑淡入和淡出。
- 拖动组件跨越屏幕中心时会自动切换锚点，但可见矩形不会跳动。

编辑产生的变化会复用模块原有的状态监听器，并由现有的延迟配置保存机制自动持久化；
模块自身不得主动调用 `ConfigManager.saveConfig()`。
