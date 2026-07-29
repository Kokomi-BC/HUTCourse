import 'dart:async';
import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';
import '../widgets/course_card.dart';
import '../widgets/week_overview.dart';

/// 今日页面 — 简约现代风格
class TodayPage extends StatefulWidget {
  const TodayPage({super.key});
  @override
  State<TodayPage> createState() => _TodayPageState();
}

class _TodayPageState extends State<TodayPage> {
  String _currentTime = '';
  String _greeting = '';
  String _dateLine = '';
  List<TodayCourseItem> _items = [];
  List<Map<String, dynamic>> _weekData = [];
  int _currentWeek = 1;
  int _todayTotal = 0;
  int _todayDone = 0;
  TodayCourseItem? _nextCourse;
  bool _noticeDismissed = false;
  String _weatherLine = '';
  String _todayEmoji = '\u2600\ufe0f';
  IconData _weatherIcon = Icons.wb_sunny;
  List<String> _dailyEmojis = [];
  List<Map<String, dynamic>> _todayAgendas = [];
  Color _accentColor = const Color(0xFF667eea);
  bool _isLoading = true;
  Timer? _clockTimer;

  @override
  void initState() {
    super.initState();
    _tick();
    _clockTimer = Timer.periodic(const Duration(seconds: 1), (_) => _tick());
    _loadData();
  }

  @override
  void dispose() {
    _clockTimer?.cancel();
    super.dispose();
  }

  void _tick() {
    final now = DateTime.now();
    const days = ['\u4e00', '\u4e8c', '\u4e09', '\u56db', '\u4e94', '\u516d', '\u65e5'];
    final h = now.hour;
    setState(() {
      _currentTime = '${now.hour.toString().padLeft(2, '0')}:${now.minute.toString().padLeft(2, '0')}';
      _dateLine = '\u5468${days[now.weekday - 1]} \u00b7 ${now.month}\u6708${now.day}\u65e5';
      _greeting = h < 12 ? '\u65e9\u4e0a\u597d' : h < 18 ? '\u4e0b\u5348\u597d' : '\u665a\u4e0a\u597d';
    });
  }

  // --------------- data loading ---------------
  Future<void> _loadData() async {
    try {
      final results = await Future.wait([
        NativeBridge.getTodayCourses(),
        NativeBridge.getCurrentWeek(),
        NativeBridge.getThemeColor(),
        NativeBridge.getWeather(),
        NativeBridge.getAgendaItems(),
      ]);

      final todayMap = results[0] as Map<String, dynamic>?;
      final systemWeek = results[1] as int;
      final color = results[2] as int;
      final weather = results[3] as Map<String, dynamic>?;
      final agendas = results[4] as List<Map<String, dynamic>>;

      // --- today courses ---
      final raw = todayMap?['courses'] as List<dynamic>? ?? [];
      final items = <TodayCourseItem>[];
      for (final c in raw) {
        if (c is! Map) continue;
        items.add(TodayCourseItem(
          name: c['name'] ?? '',
          teacher: c['teacher'] ?? '',
          location: c['location'] ?? '',
          timeSlot: c['timeSlot'] ?? '',
          isFinished: c['isFinished'] == true,
          isCurrent: c['isCurrent'] == true,
          isNext: c['isNext'] == true,
          startSection: c['startSection'] ?? 0,
          endSection: c['endSection'] ?? 0,
          slotIndex: c['slotIndex'] ?? 0,
        ));
      }
      items.sort((a, b) => a.slotIndex.compareTo(b.slotIndex));

      final done = items.where((i) => i.isFinished).length;
      TodayCourseItem? next;
      for (final it in items) {
        if (it.isNext) { next = it; break; }
      }

      // --- all courses for week overview ---
      final allCourses = await NativeBridge.getCourses();

      // 柱状图直接用系统周次
      final weekData = List.generate(7, (i) {
        final dayCount = allCourses.where((c) {
          if (c['isRemark'] == true) return false;
          if (c['dayOfWeek'] != i + 1) return false;
          final w = c['weeks'] as List<dynamic>?;
          return w != null && w.contains(systemWeek);
        }).length;
        return {'courseCount': dayCount};
      });

      // --- weather ---
      final wDesc = weather?['todayWeather'] as String? ?? '';
      final wTemp = weather?['todayTemp'] as String? ?? '';
      final wWind = weather?['todayWind'] as String? ?? '';
      final wHum = weather?['todayHumidity'] as String? ?? '';
      final wFeel = weather?['feelsLike'] as String? ?? '';
      _weatherIcon = _pickWeatherIcon(wDesc);
      _todayEmoji = _pickWeatherEmoji(wDesc);
      final parts = <String>[];
      if (wFeel.isNotEmpty) parts.add('\u4f53\u611f $wFeel\u00b0');
      if (wTemp.isNotEmpty) parts.add(wTemp.replaceAll('/', '/'));
      if (wWind.isNotEmpty) parts.add(wWind);
      if (wHum.isNotEmpty) parts.add('$wHum%');
      final weatherLine = parts.join(' \u00b7 ');

      // 解析每日预报 emoji
      final forecasts = weather?['forecasts'] as List<dynamic>? ?? [];
      final dailyEmojis = <String>[];
      for (int i = 0; i < forecasts.length && i < 7; i++) {
        final f = forecasts[i] as Map<String, dynamic>?;
        dailyEmojis.add(_pickWeatherEmoji(f?['weather'] as String? ?? ''));
      }
      while (dailyEmojis.length < 7) {
        dailyEmojis.add('\u2600\ufe0f');
      }

      // --- agendas ---
      final now = DateTime.now();
      final todayStr = '${now.year}-${now.month.toString().padLeft(2, '0')}-${now.day.toString().padLeft(2, '0')}';
      final todayAgendas = agendas.where((a) {
        final d = a['date'] as String? ?? '';
        return d == todayStr;
      }).toList()
        ..sort((a, b) {
          final sa = a['startMinute'] as int? ?? 0;
          final sb = b['startMinute'] as int? ?? 0;
          return sa.compareTo(sb);
        });

      setState(() {
        _items = items;
        _accentColor = Color(color);
        _currentWeek = systemWeek;
        _todayTotal = items.length;
        _todayDone = done;
        _nextCourse = next;
        _weekData = weekData;
        _weatherLine = weatherLine;
        _dailyEmojis = dailyEmojis;
        _todayAgendas = todayAgendas;
        _isLoading = false;
      });
    } catch (e) {
      setState(() => _isLoading = false);
    }
  }

  String _pickWeatherEmoji(String desc) {
    if (desc.contains('\u6674')) return '\u2600\ufe0f';
    if (desc.contains('\u591a\u4e91')) return '\u26c5';
    if (desc.contains('\u9634')) return '\u2601\ufe0f';
    if (desc.contains('\u96e8')) return '\ud83c\udf27\ufe0f';
    if (desc.contains('\u96ea')) return '\u2744\ufe0f';
    if (desc.contains('\u96fe') || desc.contains('\u9709')) return '\ud83c\udf2b\ufe0f';
    if (desc.contains('\u98ce')) return '\ud83d\udca8';
    return '\u2600\ufe0f';
  }

  IconData _pickWeatherIcon(String desc) {
    if (desc.contains('\u6674')) return Icons.wb_sunny;
    if (desc.contains('\u4e91') || desc.contains('\u9634')) return Icons.cloud;
    if (desc.contains('\u96e8')) return Icons.water_drop;
    if (desc.contains('\u96ea')) return Icons.ac_unit;
    if (desc.contains('\u96fe') || desc.contains('\u9709')) return Icons.foggy;
    return Icons.wb_sunny;
  }

  // --------------- build ---------------
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    if (_isLoading) {
      return const Scaffold(body: Center(child: CircularProgressIndicator()));
    }
    return Scaffold(
      body: SafeArea(
        child: RefreshIndicator(
          onRefresh: _loadData,
          child: ListView(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            children: [
              const SizedBox(height: 40),
              _buildHeader(theme),
              const SizedBox(height: 24),
              if (_weatherLine.isNotEmpty) _buildWeatherPill(theme),
              if (_weatherLine.isNotEmpty) const SizedBox(height: 24),
              _buildProgressCard(theme),
              const SizedBox(height: 16),
              _buildWeekCard(theme),
              const SizedBox(height: 16),
              if (!_noticeDismissed && _nextCourse != null) _buildNextNotice(theme),
              if (_todayAgendas.isNotEmpty) ...[
                const SizedBox(height: 16),
                _buildAgendaCard(theme),
              ],
              const SizedBox(height: 16),
              _buildCourseList(theme),
              const SizedBox(height: 80),
            ],
          ),
        ),
      ),
    );
  }

  // ---- header ----
  Widget _buildHeader(ThemeData theme) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ShaderMask(
          shaderCallback: (b) => LinearGradient(
            colors: [_accentColor, theme.colorScheme.onSurface],
          ).createShader(b),
          child: Text(_currentTime,
              style: theme.textTheme.headlineLarge?.copyWith(fontWeight: FontWeight.w300, color: Colors.white)),
        ),
        const SizedBox(height: 4),
        Text(_dateLine, style: theme.textTheme.bodyMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
        const SizedBox(height: 2),
        Text(_greeting, style: theme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.w600)),
      ],
    );
  }

  // ---- weather pill ----
  Widget _buildWeatherPill(ThemeData theme) {
    return Center(
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
        decoration: BoxDecoration(
          color: _accentColor.withValues(alpha: 0.1),
          borderRadius: BorderRadius.circular(20),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Text(_todayEmoji, style: const TextStyle(fontSize: 16)),
            const SizedBox(width: 6),
            Flexible(
              child: Text(_weatherLine, style: TextStyle(fontSize: 12, color: _accentColor), overflow: TextOverflow.ellipsis),
            ),
          ],
        ),
      ),
    );
  }

  // ---- course progress card ----
  Widget _buildProgressCard(ThemeData theme) {
    final progress = _todayTotal > 0 ? _todayDone / _todayTotal : 0.0;
    return Container(
      padding: const EdgeInsets.all(20),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: theme.colorScheme.onSurface.withValues(alpha: 0.06)),
      ),
      child: Row(
        children: [
          SizedBox(
            width: 56, height: 56,
            child: Stack(
              alignment: Alignment.center,
              children: [
                CircularProgressIndicator(
                  value: progress, strokeWidth: 4,
                  backgroundColor: theme.colorScheme.onSurface.withValues(alpha: 0.08),
                  valueColor: AlwaysStoppedAnimation(_accentColor),
                ),
                Text('$_todayDone', style: TextStyle(fontSize: 16, fontWeight: FontWeight.w700, color: _accentColor)),
              ],
            ),
          ),
          const SizedBox(width: 16),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text('\u4eca\u65e5\u8fdb\u5ea6', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
                const SizedBox(height: 2),
                Text('\u5171 $_todayTotal \u8282\u8bfe\uff0c\u5df2\u5b8c\u6210 $_todayDone \u8282', style: theme.textTheme.bodySmall),
              ],
            ),
          ),
        ],
      ),
    );
  }

  // ---- week bar card ----
  Widget _buildWeekCard(ThemeData theme) {
    final allZero = _weekData.every((d) => (d['courseCount'] as int?) == 0);
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: theme.colorScheme.onSurface.withValues(alpha: 0.06)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text('\u672c\u5468\u6982\u89c8', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
          const SizedBox(height: 12),
          WeekOverview(currentWeek: _currentWeek, totalWeeks: 20, weekData: _weekData, accentColor: _accentColor, onWeekTap: (_) {}),
          const SizedBox(height: 4),
          Row(
            mainAxisAlignment: MainAxisAlignment.spaceEvenly,
            children: _dailyEmojis.map((e) => Text(e, style: const TextStyle(fontSize: 14))).toList(),
          ),
          if (allZero)
            Padding(
              padding: const EdgeInsets.only(top: 8),
              child: Text('\u6682\u65e0\u8bfe\u7a0b\u6570\u636e', style: TextStyle(fontSize: 11, color: theme.colorScheme.onSurfaceVariant)),
            ),
        ],
      ),
    );
  }

  // ---- next course notice ----
  Widget _buildNextNotice(ThemeData theme) {
    final next = _nextCourse!;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
      decoration: BoxDecoration(
        color: _accentColor.withValues(alpha: 0.12),
        borderRadius: BorderRadius.circular(16),
      ),
      child: Row(
        children: [
          Icon(Icons.notifications_active, size: 18, color: _accentColor),
          const SizedBox(width: 10),
          Expanded(
            child: Text('\u4e0b\u4e00\u8282\uff1a${next.name} @ ${next.location}',
                style: TextStyle(fontSize: 13, color: _accentColor)),
          ),
          GestureDetector(
            onTap: () => setState(() => _noticeDismissed = true),
            child: Icon(Icons.close, size: 16, color: _accentColor.withValues(alpha: 0.5)),
          ),
        ],
      ),
    );
  }

  // ---- agenda card ----
  Widget _buildAgendaCard(ThemeData theme) {
    return Container(
      padding: const EdgeInsets.all(16),
      decoration: BoxDecoration(
        color: theme.colorScheme.surface,
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: theme.colorScheme.onSurface.withValues(alpha: 0.06)),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(children: [
            Icon(Icons.event_note, size: 16, color: _accentColor),
            const SizedBox(width: 6),
            Text('\u4eca\u65e5\u65e5\u7a0b', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
          ]),
          const SizedBox(height: 12),
          ..._todayAgendas.map((a) => _agendaItem(theme, a)),
        ],
      ),
    );
  }

  Widget _agendaItem(ThemeData theme, Map<String, dynamic> a) {
    final title = a['title'] as String? ?? '';
    final desc = a['description'] as String? ?? '';
    final loc = a['location'] as String? ?? '';
    final sm = a['startMinute'] as int? ?? 0;
    final em = a['endMinute'] as int? ?? 0;
    final cv = a['renderColor'] as int?;
    final itemColor = cv != null ? Color(cv) : _accentColor;
    final timeStr = em > 0 ? '${_fmtMin(sm)} - ${_fmtMin(em)}' : _fmtMin(sm);
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: Row(children: [
        Container(width: 3, height: 32, decoration: BoxDecoration(color: itemColor, borderRadius: BorderRadius.circular(2))),
        const SizedBox(width: 10),
        Expanded(child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text(title, style: theme.textTheme.bodyMedium?.copyWith(fontWeight: FontWeight.w500)),
          if (desc.isNotEmpty || loc.isNotEmpty)
            Text([if (loc.isNotEmpty) loc, if (desc.isNotEmpty) desc].join(' \u00b7 '),
                style: theme.textTheme.bodySmall, maxLines: 1, overflow: TextOverflow.ellipsis),
        ])),
        Text(timeStr, style: theme.textTheme.bodySmall),
      ]),
    );
  }

  String _fmtMin(int m) {
    return '${(m ~/ 60).toString().padLeft(2, '0')}:${(m % 60).toString().padLeft(2, '0')}';
  }

  // ---- course list ----
  Widget _buildCourseList(ThemeData theme) {
    if (_items.isEmpty) {
      return Padding(
        padding: const EdgeInsets.symmetric(vertical: 32),
        child: Center(
          child: Column(children: [
            Icon(Icons.self_improvement, size: 48, color: theme.colorScheme.onSurface.withValues(alpha: 0.15)),
            const SizedBox(height: 12),
            Text('\u4eca\u5929\u6ca1\u6709\u8bfe\u7a0b', style: theme.textTheme.bodyMedium),
          ]),
        ),
      );
    }
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text('\u4eca\u65e5\u8bfe\u7a0b', style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600)),
        const SizedBox(height: 12),
        ..._items.map((item) => CourseCard(
              courseName: item.name,
              teacher: item.teacher,
              location: item.location,
              timeSlot: item.timeSlot,
              accentColor: _accentColor,
              isCurrent: item.isCurrent,
            )),
      ],
    );
  }
}

// ---------- data class ----------
class TodayCourseItem {
  final String name, teacher, location, timeSlot;
  final bool isFinished, isCurrent, isNext;
  final int startSection, endSection, slotIndex;
  TodayCourseItem({
    required this.name, required this.teacher, required this.location, required this.timeSlot,
    this.isFinished = false, this.isCurrent = false, this.isNext = false,
    this.startSection = 0, this.endSection = 0, this.slotIndex = 0,
  });
}
