import 'package:flutter/material.dart';

class WeekOverview extends StatelessWidget {
  final int currentWeek;
  final int totalWeeks;
  final List<Map<String, dynamic>> weekData;
  final Color accentColor;
  final Function(int)? onWeekTap;

  const WeekOverview({
    super.key,
    required this.currentWeek,
    required this.totalWeeks,
    required this.weekData,
    this.accentColor = const Color(0xFF667eea),
    this.onWeekTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          mainAxisAlignment: MainAxisAlignment.spaceBetween,
          children: [
            Text(
              '本周概览',
              style: theme.textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w600,
              ),
            ),
            TextButton(
              onPressed: () => onWeekTap?.call(currentWeek),
              child: Text(
                '第$currentWeek周',
                style: TextStyle(
                  color: accentColor,
                  fontWeight: FontWeight.w500,
                ),
              ),
            ),
          ],
        ),
        const SizedBox(height: 12),
        SizedBox(
          height: 100,
          child: ListView.builder(
            scrollDirection: Axis.horizontal,
            itemCount: 7,
            itemBuilder: (context, index) {
              final dayData = index < weekData.length ? weekData[index] : null;
              final isToday = index == DateTime.now().weekday - 1;

              return _buildDayBar(
                context,
                dayIndex: index,
                dayData: dayData,
                isToday: isToday,
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _buildDayBar(
    BuildContext context, {
    required int dayIndex,
    Map<String, dynamic>? dayData,
    required bool isToday,
  }) {
    final theme = Theme.of(context);
    final dayLabels = ['一', '二', '三', '四', '五', '六', '日'];
    final courseCount = dayData?['courseCount'] ?? 0;
    const maxHeight = 70.0;
    final barHeight = courseCount > 0 ? (courseCount / 5.0) * maxHeight : 4.0;

    return GestureDetector(
      onTap: () => onWeekTap?.call(dayIndex),
      child: AnimatedContainer(
        duration: const Duration(milliseconds: 300),
        width: 40,
        margin: const EdgeInsets.symmetric(horizontal: 4),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            AnimatedContainer(
              duration: const Duration(milliseconds: 300),
              height: barHeight,
              decoration: BoxDecoration(
                color: isToday
                    ? accentColor
                    : courseCount > 0
                        ? accentColor.withValues(alpha: 0.3)
                        : theme.colorScheme.onSurface.withValues(alpha: 0.1),
                borderRadius: BorderRadius.circular(8),
              ),
            ),
            const SizedBox(height: 8),
            Text(
              dayLabels[dayIndex],
              style: TextStyle(
                fontSize: 12,
                fontWeight: isToday ? FontWeight.w600 : FontWeight.normal,
                color: isToday ? accentColor : theme.colorScheme.onSurfaceVariant,
              ),
            ),
            if (isToday) ...[
              const SizedBox(height: 4),
              Container(
                width: 6,
                height: 6,
                decoration: BoxDecoration(
                  color: accentColor,
                  shape: BoxShape.circle,
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}
