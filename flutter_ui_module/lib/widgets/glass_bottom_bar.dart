import 'dart:ui';
import 'package:flutter/material.dart';

/// 玻璃态底栏 — iOS 风格分离式布局。
///
/// 前三项共用玻璃胶囊，第四项（"个人"）为独立圆形按钮，两者居中排列。
/// 支持手指拖动指示器丝滑切换标签。
class GlassBottomBar extends StatefulWidget {
  final int currentIndex;
  final ValueChanged<int> onTap;
  final List<GlassBottomBarItem> items;

  const GlassBottomBar({
    super.key,
    required this.currentIndex,
    required this.onTap,
    required this.items,
  });

  @override
  State<GlassBottomBar> createState() => _GlassBottomBarState();
}

class _GlassBottomBarState extends State<GlassBottomBar>
    with TickerProviderStateMixin {
  late AnimationController _slideController;
  late Animation<double> _slideAnimation;
  int _prevIndex = 0;
  bool _isClickAnimating = false;

  // 拖动跟踪
  double _dragOffset = 0;
  double _tabWidth = 0;
  double _dragProgress = 0;
  int _dragStartIndex = 0;
  bool _isDragging = false;

  @override
  void initState() {
    super.initState();
    _prevIndex = widget.currentIndex;
    _slideController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 600),
    );
    _slideController.addStatusListener((status) {
      if (status == AnimationStatus.completed ||
          status == AnimationStatus.dismissed) {
        _isClickAnimating = false;
      }
    });
    _slideAnimation = Tween<double>(
      begin: widget.currentIndex.toDouble(),
      end: widget.currentIndex.toDouble(),
    ).animate(CurvedAnimation(
      parent: _slideController,
      curve: Curves.easeInOutCubic,
    ));
    _slideController.value = 1.0;
  }

  @override
  void didUpdateWidget(covariant GlassBottomBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.currentIndex != widget.currentIndex) {
      _prevIndex = oldWidget.currentIndex;
      _isClickAnimating = true;
      _slideAnimation = Tween<double>(
        begin: _prevIndex.toDouble(),
        end: widget.currentIndex.toDouble(),
      ).animate(CurvedAnimation(
        parent: _slideController,
        curve: Curves.easeInOutCubic,
      ));
      _slideController.reset();
      _slideController.forward();
    }
  }

  void _onDragStart(DragStartDetails d) {
    _dragOffset = 0;
    _dragProgress = 0;
    _dragStartIndex = widget.currentIndex;
    _isDragging = true;
    _slideController.stop();
  }

  void _onDragUpdate(DragUpdateDetails d) {
    if (_tabWidth <= 0) return;
    _dragOffset += d.delta.dx;
    // 指示器严格跟手，限制在 0..3（含个人按钮）
    final raw = _dragStartIndex.toDouble() + _dragOffset / _tabWidth;
    final clamped = raw.clamp(0.0, 3.0);
    _dragOffset = (clamped - _dragStartIndex.toDouble()) * _tabWidth;
    _dragProgress = (_dragOffset.abs() / _tabWidth).clamp(0.0, 1.0);
    setState(() {});
  }

  void _onDragEnd(DragEndDetails d) {
    _isDragging = false;
    final v = d.primaryVelocity ?? 0;
    final raw =
        _dragStartIndex.toDouble() + _dragOffset / _tabWidth + v / 800;
    final target = raw.round().clamp(0, widget.items.length - 1);

    _dragOffset = 0;
    _dragProgress = 0;

    if (target != widget.currentIndex) {
      widget.onTap(target);
    }
  }

  @override
  void dispose() {
    _slideController.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final accentColor =
        isDark ? const Color(0xFF0091FF) : const Color(0xFF0088FF);
    final glassColor = isDark
        ? const Color(0xFF1C1C1E).withValues(alpha: 0.25)
        : const Color(0xFFF2F2F7).withValues(alpha: 0.30);
    final indicatorColor = isDark
        ? const Color(0xFF3A3A3A).withValues(alpha: 0.20)
        : Colors.white.withValues(alpha: 0.25);

    final bottomSafe = MediaQuery.of(context).padding.bottom;
    final barItems = widget.items.take(3).toList();

    return GestureDetector(
      onHorizontalDragStart: _onDragStart,
      onHorizontalDragUpdate: _onDragUpdate,
      onHorizontalDragEnd: _onDragEnd,
      behavior: HitTestBehavior.opaque,
      child: Padding(
        padding: EdgeInsets.fromLTRB(20, 8, 20, 8 + bottomSafe),
        child: SizedBox(
          height: 64,
          child: LayoutBuilder(
            builder: (context, constraints) {
              final totalWidth = constraints.maxWidth;
              const gap = 6.0;
              // 四项均分宽度：前三项在胶囊内，第四项独立
              final tabW = (totalWidth - 8 - gap) / 4;
              final circleSize = tabW;
              final capsuleWidth = 3 * tabW + 8;
              // 胶囊内三个 Expanded 实际均分宽度
              final actualTabW = capsuleWidth / 3;
              _tabWidth = actualTabW;
              // 各标签指示器左边缘 X 坐标（相对于 Row 内部）
              final posTab0 = 4.0;
              final posTab1 = actualTabW + 4;
              final posTab2 = actualTabW * 2 + 4;
              // 指示器宽度：胶囊内 tabW-8，圆形按钮略小于按钮
              final indicatorW = tabW - 8;
              final circleIndicatorW = circleSize * 0.55;
              final circleIndicatorX = capsuleWidth + gap + (circleSize - circleIndicatorW) / 2;
              final tabPositions = [posTab0, posTab1, posTab2, circleIndicatorX];

              // 当前指示器位置（支持拖动到个人按钮）
              final isCircleMode = widget.currentIndex == 3 && !_isDragging && !_isClickAnimating;
              double indicatorX;
              double animProgress = 0;
              if (_isDragging) {
                final raw = _dragStartIndex.toDouble() +
                    _dragOffset / _tabWidth;
                final clamped = raw.clamp(0.0, 3.0);
                indicatorX = tabPositions[clamped.round()] +
                    (clamped - clamped.round()) * tabPositions[1];
              } else if (_isClickAnimating) {
                animProgress = _slideAnimation.value;
                final fromIdx = _prevIndex;
                final toIdx = widget.currentIndex;
                // 液态流动：lerp 插值 + clamp 防越界偏移
                final rawT = (animProgress - fromIdx) / (toIdx - fromIdx);
                final t = rawT.clamp(0.0, 1.0);
                indicatorX = lerpDouble(
                    tabPositions[fromIdx], tabPositions[toIdx], t)!;
              } else {
                indicatorX = tabPositions[widget.currentIndex];
              }

              // 指示器垂直位置：胶囊模式下顶对齐，圆形模式下与按钮居中对齐
              final indicatorTop = isCircleMode ? (64 - circleIndicatorW) / 2 : 4.0;

              // 液态流动指示器宽度（个人按钮时匹配圆形尺寸）
              double currentIndicatorW;
              if (isCircleMode) {
                currentIndicatorW = circleIndicatorW;
              } else if (!_isDragging && _isClickAnimating && widget.currentIndex != 3) {
                final flowStretch = 1.0 + (1.0 - (animProgress - _prevIndex).abs()) * 0.5;
                currentIndicatorW = indicatorW * flowStretch;
              } else {
                currentIndicatorW = indicatorW;
              }

              return Stack(
                clipBehavior: Clip.none,
                children: [
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      SizedBox(
                        width: capsuleWidth,
                        height: 64,
                        child: _GlassCapsule(
                          barItems: barItems,
                          currentIndex: widget.currentIndex,
                          tabWidth: tabW,
                          isDark: isDark,
                          glassColor: glassColor,
                          onTap: (i) => widget.onTap(i),
                        ),
                      ),
                      const SizedBox(width: gap),
                      SizedBox(
                        width: circleSize,
                        height: 64,
                        child: _PersonCircle(
                          item: widget.items.last,
                          isSelected: widget.currentIndex == 3,
                          accentColor: accentColor,
                          glassColor: glassColor,
                          size: circleSize,
                          onTap: () => widget.onTap(3),
                        ),
                      ),
                    ],
                  ),
                  // 指示器（覆盖在底栏上方，可移动到个人按钮）
                  Positioned(
                    left: indicatorX,
                    top: indicatorTop,
                    child: _IndicatorBar(
                      width: currentIndicatorW,
                      circleIndicatorSize: circleIndicatorW,
                      isDark: isDark,
                      indicatorColor: indicatorColor,
                      dragProgress: _dragProgress,
                      isCircle: isCircleMode,
                    ),
                  ),
                ],
              );
            },
          ),
        ),
      ),
    );
  }
}

// ── 玻璃胶囊 ───────────────────────────────────────────────────────

class _GlassCapsule extends StatelessWidget {
  final List<GlassBottomBarItem> barItems;
  final int currentIndex;
  final double tabWidth;
  final bool isDark;
  final Color glassColor;
  final ValueChanged<int> onTap;

  const _GlassCapsule({
    required this.barItems,
    required this.currentIndex,
    required this.tabWidth,
    required this.isDark,
    required this.glassColor,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(32),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
        child: Container(
          decoration: BoxDecoration(
            color: glassColor,
            borderRadius: BorderRadius.circular(32),
            border: Border.all(
              color: Colors.white
                  .withValues(alpha: isDark ? 0.10 : 0.20),
              width: 0.8,
            ),
            boxShadow: [
              BoxShadow(
                color: Colors.black
                    .withValues(alpha: isDark ? 0.4 : 0.12),
                blurRadius: 28,
                offset: const Offset(0, 6),
              ),
            ],
          ),
          foregroundDecoration: BoxDecoration(
            borderRadius: BorderRadius.circular(32),
            gradient: LinearGradient(
              begin: Alignment.topLeft,
              end: Alignment.bottomRight,
              colors: [
                const Color(0x08FFFFFF),
                Colors.transparent,
                isDark
                    ? const Color(0x081A237E)
                    : const Color(0x08FF8A65),
              ],
            ),
          ),
          child: Row(
            children: List.generate(barItems.length, (i) {
              return _CapsuleTab(
                item: barItems[i],
                selected: currentIndex == i,
                accentColor: isDark
                    ? const Color(0xFF0091FF)
                    : const Color(0xFF0088FF),
                isDark: isDark,
                onTap: () => onTap(i),
              );
            }),
          ),
        ),
      ),
    );
  }
}

class _IndicatorBar extends StatelessWidget {
  final double width;
  final double circleIndicatorSize;
  final bool isDark;
  final Color indicatorColor;
  final double dragProgress;
  final bool isCircle;

  const _IndicatorBar({
    required this.width,
    required this.circleIndicatorSize,
    required this.isDark,
    required this.indicatorColor,
    required this.dragProgress,
    this.isCircle = false,
  });

  @override
  Widget build(BuildContext context) {
    // 拖动时轻微放大，避免过度膨胀导致不透明
    final scale = 1.0 + dragProgress * 0.12;
    // 透镜折射强度（拖动时微增，模拟液态拉伸）
    final lens = 1.0 + dragProgress * 1.5;

    return Transform.scale(
      scale: scale,
      child: Container(
        width: isCircle ? circleIndicatorSize : width,
        height: isCircle ? circleIndicatorSize : 56,
        decoration: BoxDecoration(
          color: indicatorColor,
          borderRadius: BorderRadius.circular(isCircle ? circleIndicatorSize / 2 : 24),
          border: Border.all(
            color: isDark
                ? Colors.white.withValues(alpha: 0.14)
                : Colors.black.withValues(alpha: 0.04),
            width: 0.5,
          ),
          boxShadow: [
            BoxShadow(
              color: Colors.black
                  .withValues(alpha: isDark ? 0.3 : 0.08),
              blurRadius: 12 + dragProgress * 8,
              offset: Offset(0, 3 + dragProgress * 2),
            ),
          ],
        ),
        child: ClipRRect(
          borderRadius: BorderRadius.circular(isCircle ? circleIndicatorSize / 2 : 24),
          child: Stack(
            children: [
              // 透镜折射：增强模糊模拟玻璃透镜
              BackdropFilter(
                filter: ImageFilter.blur(sigmaX: lens, sigmaY: lens),
                child: Container(color: Colors.transparent),
              ),
              // 玻璃高光
              Container(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(isCircle ? circleIndicatorSize / 2 : 24),
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      Colors.white
                          .withValues(alpha: isDark ? 0.08 : 0.22),
                      Colors.transparent,
                      Colors.black
                          .withValues(alpha: isDark ? 0.10 : 0.02),
                    ],
                    stops: const [0, 0.35, 1],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _CapsuleTab extends StatelessWidget {
  final GlassBottomBarItem item;
  final bool selected;
  final Color accentColor;
  final bool isDark;
  final VoidCallback onTap;

  const _CapsuleTab({
    required this.item,
    required this.selected,
    required this.accentColor,
    required this.isDark,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final color = selected
        ? accentColor
        : (isDark ? const Color(0xFFAAAAAA) : const Color(0xFF888888));
    return Expanded(
      child: GestureDetector(
        onTap: onTap,
        behavior: HitTestBehavior.opaque,
        child: Center(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(selected ? item.activeIcon : item.icon,
                  size: 22, color: color),
              const SizedBox(height: 2),
              Text(
                item.label,
                style: TextStyle(
                  fontSize: 10,
                  fontWeight: selected ? FontWeight.w600 : FontWeight.normal,
                  color: color,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

// ── 独立圆形按钮（"个人"）───────────────────────────────────────────

class _PersonCircle extends StatelessWidget {
  final GlassBottomBarItem item;
  final bool isSelected;
  final Color accentColor;
  final Color glassColor;
  final double size;
  final VoidCallback onTap;

  const _PersonCircle({
    required this.item,
    required this.isSelected,
    required this.accentColor,
    required this.glassColor,
    required this.size,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    return GestureDetector(
      onTap: onTap,
      child: Center(
        child: ClipOval(
          child: BackdropFilter(
            filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
            child: Container(
              width: size,
              height: size,
              decoration: BoxDecoration(
                color: isSelected
                    ? accentColor.withValues(alpha: 0.45)
                    : glassColor,
                shape: BoxShape.circle,
                border: Border.all(
                  color: isSelected
                      ? Colors.white.withValues(alpha: 0.3)
                      : Colors.white
                          .withValues(alpha: isDark ? 0.10 : 0.20),
                  width: 0.8,
                ),
              ),
              foregroundDecoration: BoxDecoration(
                shape: BoxShape.circle,
                gradient: LinearGradient(
                  begin: Alignment.topLeft,
                  end: Alignment.bottomRight,
                  colors: [
                    const Color(0x08FFFFFF),
                    Colors.transparent,
                    isDark
                        ? const Color(0x081A237E)
                        : const Color(0x08FF8A65),
                  ],
                ),
              ),
              child: Icon(
                isSelected ? item.activeIcon : item.icon,
                size: 24,
                color: isSelected ? Colors.white : accentColor,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// 底栏标签项数据
class GlassBottomBarItem {
  final IconData icon;
  final IconData activeIcon;
  final String label;

  const GlassBottomBarItem({
    required this.icon,
    required this.activeIcon,
    required this.label,
  });
}
