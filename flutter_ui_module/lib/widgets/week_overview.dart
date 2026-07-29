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

    return SizedBox(
      height: 84,
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceEvenly,
        children: List.generate(7, (index) {
          final dayData = index < weekData.length ? weekData[index] : null;
          final isToday = index == DateTime.now().weekday - 1;
          return _buildDayBar(context, dayIndex: index, dayData: dayData, isToday: isToday);
        }),
      ),
    );
  }

  Widget _buildDayBar(
    BuildContext context, {
    required int dayIndex,
    Map<String, dynamic>? dayData,
    required bool isToday,
  }) {
    final theme = Theme.of(context);
    const dayLabels = ['一', '二', '三', '四', '五', '六', '日'];
    final courseCount = dayData?['courseCount'] ?? 0;
    const maxH = 56.0;
    final barH = courseCount > 0 ? (courseCount / 5.0).clamp(0.0, 1.0) * maxH : 4.0;

    return GestureDetector(
      onTap: () => onWeekTap?.call(dayIndex),
      child: SizedBox(
        width: 36,
        child: Column(
          mainAxisAlignment: MainAxisAlignment.end,
          children: [
            AnimatedContainer(
              duration: const Duration(milliseconds: 300),
              height: barH,
              width: 24,
              decoration: BoxDecoration(
                color: isToday
                    ? accentColor
                    : courseCount > 0
                        ? accentColor.withValues(alpha: 0.35)
                        : theme.colorScheme.onSurface.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(6),
              ),
            ),
            const SizedBox(height: 6),
            Text(dayLabels[dayIndex],
                style: TextStyle(fontSize: 11, fontWeight: isToday ? FontWeight.w700 : FontWeight.w400,
                    color: isToday ? accentColor : theme.colorScheme.onSurfaceVariant)),
            if (isToday)
              Container(
                width: 4, height: 4,
                margin: const EdgeInsets.only(top: 3),
                decoration: BoxDecoration(color: accentColor, shape: BoxShape.circle),
              ),
          ],
        ),
      ),
    );
  }
}
