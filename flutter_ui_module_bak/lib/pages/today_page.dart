import 'dart:async';
import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';
import '../widgets/glass_card.dart';
import '../widgets/course_card.dart';
import '../widgets/week_overview.dart';
import '../utils/animations.dart';

class TodayPage extends StatefulWidget {
  const TodayPage({super.key});

  @override
  State<TodayPage> createState() => _TodayPageState();
}

class _TodayPageState extends State<TodayPage> {
  String _currentTime = '';
  String _greeting = '';
  int _currentWeek = 1;
  Color _accentColor = const Color(0xFF667eea);
  List<Map<String, dynamic>> _todayCourses = [];
  List<Map<String, dynamic>> _weekData = [];
  bool _isLoading = true;
  Timer? _clockTimer;

  @override
  void initState() {
    super.initState();
    _updateClock();
    _clockTimer = Timer.periodic(const Duration(seconds: 1), (_) => _updateClock());
    _loadData();
  }

  @override
  void dispose() {
    _clockTimer?.cancel();
    super.dispose();
  }

  void _updateClock() {
    final now = DateTime.now();
    setState(() {
      _currentTime = '${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}';
      _greeting = _getGreeting(now.hour);
    });
  }

  String _getGreeting(int hour) {
    if (hour >= 5 && hour < 12) return '早上好 👋';
    if (hour >= 12 && hour < 18) return '下午好 👋';
    return '晚上好 👋';
  }

  Future<void> _loadData() async {
    try {
      final courses = await NativeBridge.getTodayCourses();
      final week = await NativeBridge.getCurrentWeek();
      final color = await NativeBridge.getThemeColor();

      setState(() {
        _todayCourses = courses?['courses'] ?? [];
        _currentWeek = week;
        _accentColor = Color(color);
        _weekData = List.generate(7, (index) => {
          'courseCount': (index < _todayCourses.length) ? 2 : 0,
        });
        _isLoading = false;
      });
    } catch (e) {
      setState(() => _isLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final now = DateTime.now();
    final dayLabels = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'];
    final dayLabel = dayLabels[now.weekday - 1];
    final dateLabel = '${now.month}月${now.day}日';

    return Scaffold(
      body: SafeArea(
        child: _isLoading
            ? const Center(child: CircularProgressIndicator())
            : RefreshIndicator(
                onRefresh: _loadData,
                child: SingleChildScrollView(
                  physics: const AlwaysScrollableScrollPhysics(),
                  padding: const EdgeInsets.symmetric(horizontal: 20),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const SizedBox(height: 40),
                      _buildHeader(theme, dayLabel, dateLabel),
                      const SizedBox(height: 20),
                      _buildWeekOverviewCard(theme),
                      const SizedBox(height: 16),
                      _buildNextCourseNotice(theme),
                      const SizedBox(height: 16),
                      _buildTodayCourses(theme),
                      const SizedBox(height: 100),
                    ],
                  ),
                ),
              ),
      ),
    );
  }

  Widget _buildHeader(ThemeData theme, String dayLabel, String dateLabel) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ShaderMask(
                shaderCallback: (bounds) => LinearGradient(
                  colors: [_accentColor, theme.colorScheme.onSurface],
                ).createShader(bounds),
                child: Text(
                  _currentTime,
                  style: theme.textTheme.headlineLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                    color: Colors.white,
                  ),
                ),
              ),
              const SizedBox(height: 4),
              Row(
                children: [
                  Text(
                    dayLabel,
                    style: theme.textTheme.titleMedium?.copyWith(
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                  const SizedBox(width: 8),
                  Text(
                    dateLabel,
                    style: theme.textTheme.bodyMedium,
                  ),
                ],
              ),
              const SizedBox(height: 4),
              Text(
                _greeting,
                style: theme.textTheme.titleMedium?.copyWith(
                  fontWeight: FontWeight.w600,
                ),
              ),
            ],
          ),
        ),
        _buildWeatherWidget(),
      ],
    );
  }

  Widget _buildWeatherWidget() {
    return GlassCard(
      padding: const EdgeInsets.all(12),
      child: Column(
        children: [
          Icon(
            Icons.wb_sunny,
            color: _accentColor,
            size: 24,
          ),
          const SizedBox(height: 4),
          Text(
            '25°C',
            style: TextStyle(
              fontWeight: FontWeight.w600,
              color: _accentColor,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildWeekOverviewCard(ThemeData theme) {
    return GlassCard(
      child: Column(
        children: [
          WeekOverview(
            currentWeek: _currentWeek,
            totalWeeks: 20,
            weekData: _weekData,
            accentColor: _accentColor,
          ),
          const SizedBox(height: 12),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Text(
                '共${_todayCourses.length}节课',
                style: theme.textTheme.bodyMedium,
              ),
              Text(
                '已上${_todayCourses.where((c) => c['isFinished'] == true).length}节',
                style: theme.textTheme.bodyMedium,
              ),
            ],
          ),
        ],
      ),
    );
  }

  Widget _buildNextCourseNotice(ThemeData theme) {
    final nextCourse = _todayCourses.firstWhere(
      (c) => c['isNext'] == true,
      orElse: () => {},
    );

    if (nextCourse.isEmpty) return const SizedBox.shrink();

    return GlassCard(
      child: Row(
        children: [
          Icon(
            Icons.access_time,
            color: _accentColor,
            size: 20,
          ),
          const SizedBox(width: 12),
          Expanded(
            child: Text(
              '下一节课：${nextCourse['name']} @ ${nextCourse['location']}',
              style: theme.textTheme.bodyMedium?.copyWith(
                fontWeight: FontWeight.w500,
              ),
            ),
          ),
          IconButton(
            icon: const Icon(Icons.close, size: 18),
            onPressed: () {},
            style: IconButton.styleFrom(
              foregroundColor: theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildTodayCourses(ThemeData theme) {
    if (_todayCourses.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(40),
          child: Column(
            children: [
              Icon(
                Icons.event_available,
                size: 64,
                color: theme.colorScheme.onSurface.withValues(alpha: 0.2),
              ),
              const SizedBox(height: 16),
              Text(
                '今天没有课程',
                style: theme.textTheme.titleMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                ),
              ),
              const SizedBox(height: 8),
              Text(
                '享受你的自由时光吧！',
                style: theme.textTheme.bodyMedium,
              ),
            ],
          ),
        ),
      );
    }

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          '今日课程',
          style: theme.textTheme.titleMedium?.copyWith(
            fontWeight: FontWeight.w600,
          ),
        ),
        const SizedBox(height: 12),
        AnimatedList(
          initialItemCount: _todayCourses.length,
          itemBuilder: (context, index, animation) {
            final course = _todayCourses[index];
            return AppAnimations.combinedFadeSlide(
              CourseCard(
                courseName: course['name'] ?? '',
                teacher: course['teacher'] ?? '',
                location: course['location'] ?? '',
                timeSlot: course['timeSlot'] ?? '',
                accentColor: _accentColor,
                isCurrent: course['isCurrent'] ?? false,
              ),
              animation,
            );
          },
        ),
      ],
    );
  }
}
