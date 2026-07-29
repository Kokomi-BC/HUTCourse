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
      duration: const Duration(milliseconds: 500),
    );
    _slideAnimation = Tween<double>(
      begin: widget.currentIndex.toDouble(),
      end: widget.currentIndex.toDouble(),
    ).animate(CurvedAnimation(
      parent: _slideController,
      curve: Curves.elasticOut,
    ));
    _slideController.value = 1.0;
  }

  @override
  void didUpdateWidget(covariant GlassBottomBar oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.currentIndex != widget.currentIndex) {
      _prevIndex = oldWidget.currentIndex;
      _slideAnimation = Tween<double>(
        begin: _prevIndex.toDouble(),
        end: widget.currentIndex.toDouble(),
      ).animate(CurvedAnimation(
        parent: _slideController,
        curve: Curves.elasticOut,
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
    // 指示器严格跟手，限制在胶囊内 0..2
    final raw = _dragStartIndex.toDouble() + _dragOffset / _tabWidth;
    final clamped = raw.clamp(0.0, 2.0);
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
        ? const Color(0xFF1C1C1E).withValues(alpha: 0.78)
        : const Color(0xFFF2F2F7).withValues(alpha: 0.78);
    final indicatorColor = isDark
        ? const Color(0xFF3A3A3A).withValues(alpha: 0.9)
        : Colors.white.withValues(alpha: 0.94);

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
              const circleSize = 56.0;
              const gap = 10.0;
              final capsuleWidth = totalWidth - circleSize - gap;
              final tabW = (capsuleWidth - 8) / barItems.length;
              _tabWidth = tabW;

              // 拖动时指示器相对于 dragStartIndex 偏移
              final baseLeft = _dragStartIndex.toDouble() * tabW;
              final dragPx = _dragOffset;

              return Row(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  SizedBox(
                    width: capsuleWidth,
                    height: 64,
                    child: _GlassCapsule(
                      barItems: barItems,
                      currentIndex: widget.currentIndex,
                      tabWidth: tabW,
                      isDragging: _isDragging,
                      dragStartIndex: _dragStartIndex,
                      dragPx: _dragOffset,
                      dragProgress: _dragProgress,
                      isDark: isDark,
                      accentColor: accentColor,
                      glassColor: glassColor,
                      indicatorColor: indicatorColor,
                      slideAnimation: _slideAnimation,
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
                      size: circleSize,
                      onTap: () => widget.onTap(3),
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
  final bool isDragging;
  final int dragStartIndex;
  final double dragPx;
  final double dragProgress;
  final bool isDark;
  final Color accentColor;
  final Color glassColor;
  final Color indicatorColor;
  final Animation<double> slideAnimation;
  final ValueChanged<int> onTap;

  const _GlassCapsule({
    required this.barItems,
    required this.currentIndex,
    required this.tabWidth,
    required this.isDragging,
    required this.dragStartIndex,
    required this.dragPx,
    required this.dragProgress,
    required this.isDark,
    required this.accentColor,
    required this.glassColor,
    required this.indicatorColor,
    required this.slideAnimation,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(32),
      clipBehavior: Clip.hardEdge, // 裁剪背景但允许子元素溢出
      child: Stack(
        clipBehavior: Clip.none,
        children: [
          // 毛玻璃背景
          Positioned.fill(
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
              child: Container(
                decoration: BoxDecoration(
                  color: glassColor,
                  borderRadius: BorderRadius.circular(32),
                  border: Border.all(
                    color: Colors.white
                        .withValues(alpha: isDark ? 0.12 : 0.75),
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
              ),
            ),
          ),
          // 指示器（可溢出胶囊）
          if (currentIndex < 3)
            Positioned(
              left: (isDragging
                      ? dragStartIndex * tabWidth + dragPx
                      : currentIndex.toDouble() * tabWidth) +
                  4,
              top: 4,
              child: _IndicatorBar(
                width: tabWidth - 8,
                isDark: isDark,
                indicatorColor: indicatorColor,
                dragProgress: dragProgress,
              ),
            ),
          // 标签行
          Positioned.fill(
            child: Row(
              children: List.generate(barItems.length, (i) {
                return _CapsuleTab(
                  item: barItems[i],
                  selected: currentIndex == i,
                  accentColor: accentColor,
                  isDark: isDark,
                  onTap: () => onTap(i),
                );
              }),
            ),
          ),
        ],
      ),
    );
  }
}

class _IndicatorBar extends StatelessWidget {
  final double width;
  final bool isDark;
  final Color indicatorColor;
  final double dragProgress;

  const _IndicatorBar({
    required this.width,
    required this.isDark,
    required this.indicatorColor,
    required this.dragProgress,
  });

  @override
  Widget build(BuildContext context) {
    // 原始项目 pressedScale = 78/56 ≈ 1.393
    final scale = 1.0 + dragProgress * 0.393;
    // 透镜折射强度
    final lens = 2.0 + dragProgress * 6.0;

    return Transform.scale(
      scale: scale,
      child: Container(
        width: width,
        height: 56,
        decoration: BoxDecoration(
          color: indicatorColor,
          borderRadius: BorderRadius.circular(28),
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
          borderRadius: BorderRadius.circular(28),
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
                  borderRadius: BorderRadius.circular(28),
                  gradient: LinearGradient(
                    begin: Alignment.topCenter,
                    end: Alignment.bottomCenter,
                    colors: [
                      Colors.white
                          .withValues(alpha: isDark ? 0.1 : 0.55),
                      Colors.transparent,
                      Colors.black
                          .withValues(alpha: isDark ? 0.15 : 0.02),
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
  final double size;
  final VoidCallback onTap;

  const _PersonCircle({
    required this.item,
    required this.isSelected,
    required this.accentColor,
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
            filter: ImageFilter.blur(sigmaX: 14, sigmaY: 14),
            child: Container(
              width: size,
              height: size,
              decoration: BoxDecoration(
                color: isSelected
                    ? accentColor.withValues(alpha: 0.84)
                    : (isDark
                        ? const Color(0xFF2C2C2E).withValues(alpha: 0.72)
                        : const Color(0xFFE5E5EA).withValues(alpha: 0.68)),
                shape: BoxShape.circle,
                border: Border.all(
                  color: isSelected
                      ? Colors.white.withValues(alpha: 0.4)
                      : Colors.white
                          .withValues(alpha: isDark ? 0.1 : 0.65),
                  width: 0.8,
                ),
                boxShadow: [
                  BoxShadow(
                    color: isSelected
                        ? accentColor.withValues(alpha: 0.4)
                        : Colors.black.withValues(alpha: 0.14),
                    blurRadius: isSelected ? 16 : 10,
                    offset: const Offset(0, 4),
                  ),
                ],
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
