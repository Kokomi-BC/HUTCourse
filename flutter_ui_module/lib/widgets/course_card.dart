import 'package:flutter/material.dart';

class CourseCard extends StatefulWidget {
  final String courseName;
  final String teacher;
  final String location;
  final String timeSlot;
  final Color accentColor;
  final bool isCurrent;

  const CourseCard({
    super.key,
    required this.courseName,
    required this.teacher,
    required this.location,
    required this.timeSlot,
    this.accentColor = const Color(0xFF667eea),
    this.isCurrent = false,
  });

  @override
  State<CourseCard> createState() => _CourseCardState();
}

class _CourseCardState extends State<CourseCard>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _scaleAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      duration: const Duration(milliseconds: 150),
      vsync: this,
    );
    _scaleAnimation = Tween<double>(
      begin: 1.0,
      end: 0.95,
    ).animate(CurvedAnimation(
      parent: _controller,
      curve: Curves.easeInOut,
    ));
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return GestureDetector(
      onTapDown: (_) {
        _controller.forward();
      },
      onTapUp: (_) {
        _controller.reverse();
      },
      onTapCancel: () {
        _controller.reverse();
      },
      child: AnimatedBuilder(
        animation: _scaleAnimation,
        builder: (context, child) {
          return Transform.scale(
            scale: _scaleAnimation.value,
            child: child,
          );
        },
        child: Container(
          margin: const EdgeInsets.only(bottom: 12),
          decoration: BoxDecoration(
            color: theme.cardTheme.color,
            borderRadius: BorderRadius.circular(20),
            border: Border.all(
              color: widget.isCurrent
                  ? widget.accentColor.withValues(alpha: 0.5)
                  : Colors.transparent,
              width: widget.isCurrent ? 2 : 1,
            ),
            boxShadow: widget.isCurrent
                ? [
                    BoxShadow(
                      color: widget.accentColor.withValues(alpha: 0.2),
                      blurRadius: 12,
                      offset: const Offset(0, 4),
                    ),
                  ]
                : null,
          ),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                _buildTimeIndicator(theme),
                const SizedBox(width: 16),
                Expanded(
                  child: _buildCourseInfo(theme),
                ),
                _buildLocationChip(theme),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildTimeIndicator(ThemeData theme) {
    return Container(
      width: 4,
      height: 48,
      decoration: BoxDecoration(
        color: widget.isCurrent ? widget.accentColor : theme.colorScheme.onSurface.withValues(alpha: 0.2),
        borderRadius: BorderRadius.circular(2),
      ),
    );
  }

  Widget _buildCourseInfo(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          widget.courseName,
          style: theme.textTheme.titleMedium?.copyWith(
            fontWeight: FontWeight.w600,
          ),
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
        const SizedBox(height: 4),
        Text(
          '${widget.teacher} · ${widget.timeSlot}',
          style: theme.textTheme.bodySmall,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      ],
    );
  }

  Widget _buildLocationChip(ThemeData theme) {
    return Container(
      padding: const EdgeInsets.symmetric(
        horizontal: 12,
        vertical: 6,
      ),
      decoration: BoxDecoration(
        color: widget.accentColor.withValues(alpha: 0.1),
        borderRadius: BorderRadius.circular(12),
      ),
      child: Text(
        widget.location,
        style: TextStyle(
          fontSize: 12,
          color: widget.accentColor,
          fontWeight: FontWeight.w500,
        ),
      ),
    );
  }
}
