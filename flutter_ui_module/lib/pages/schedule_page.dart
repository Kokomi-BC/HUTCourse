import 'package:flutter/material.dart';
import '../bridge/native_bridge.dart';

/// Schedule Page - Modern Minimalist
class SchedulePage extends StatefulWidget {
  const SchedulePage({super.key});
  @override
  State<SchedulePage> createState() => _SchedulePageState();
}

class _SchedulePageState extends State<SchedulePage> {
  int _currentWeek = 1;
  int _actualWeek = 1;
  int _prevWeek = 1;
  int _totalWeeks = 30;
  Color _accentColor = const Color(0xFF667eea);
  List<Map<String, dynamic>> _courses = [];
  List<Map<String, dynamic>> _agendas = [];
  List<String> _weekDates = [];
  bool _isLoading = true;

  static const _dayLabels = ['\u4e00', '\u4e8c', '\u4e09', '\u56db', '\u4e94', '\u516d', '\u65e5'];

  @override void initState() { super.initState(); _loadData(); }

  Future<void> _loadData() async {
    try {
      final results = await Future.wait([
        NativeBridge.getCourses(),
        NativeBridge.getCurrentWeek(),
        NativeBridge.getThemeColor(),
        NativeBridge.getAgendaItems(),
        NativeBridge.getCurrentActualWeek(),
      ]);
      final systemWeek = results[1] as int;
      final actualWeek = results[4] as int;
      final weekDates = await NativeBridge.getWeekDates(systemWeek);
      final courses = List<Map<String, dynamic>>.from(results[0] as List);

      // 始终使用系统周次，不做自适应跳转
      final displayWeek = systemWeek;

      setState(() {
        _courses = courses;
        _currentWeek = displayWeek;
        _accentColor = Color(results[2] as int);
        _agendas = List<Map<String, dynamic>>.from(results[3] as List);
        _actualWeek = actualWeek;
        _weekDates = weekDates;
        _isLoading = false;
      });
    } catch (e) {
      setState(() => _isLoading = false);
    }
  }

  Future<void> _changeWeek(int week) async {
    setState(() {
      _prevWeek = _currentWeek;
      _currentWeek = week;
      _isLoading = true;
    });
    try {
      final dates = await NativeBridge.getWeekDates(week);
      if (mounted) setState(() { _weekDates = dates; _isLoading = false; });
    } catch (_) {
      if (mounted) setState(() => _isLoading = false);
    }
  }

  // 获取用于过滤课程的周次：用户手动选择时用精确值，初始自适应时才用 effective
  int _filterWeek() => _currentWeek;

  List<Map<String, dynamic>> _coursesForCell(int week, int day, int slot) {
    final cs = slot * 2 + 1;
    final ce = cs + 1;
    return _courses.where((c) {
      if (c['dayOfWeek'] != day) return false;
      if (c['isRemark'] == true) return false;
      final w = c['weeks'] as List<dynamic>?;
      if (w == null || !w.contains(_currentWeek)) return false;
      final s = c['startSection'] as int? ?? 0;
      final e = c['endSection'] as int? ?? s;
      return s <= ce && e >= cs;
    }).toList();
  }

  List<Map<String, dynamic>> _agendasForCell(int week, int day, int slot) {
    // 五大节实际时间（分钟）：8:00/10:00/14:00/16:00/19:00，每节2小时窗口
    const slotStarts = [480, 600, 840, 960, 1140];
    final sm = slotStarts[slot];
    final em = sm + 120;
    final hasDates = _weekDates.length == 7 && day >= 1 && day <= 7;
    final cellDate = hasDates ? _weekDates[day - 1] : '';

    return _agendas.where((a) {
      final startMin = a['startMinute'] as int? ?? 0;
      // 无具体时间的日程（startMin==0）仅在第一节显示；有时间则匹配对应时隙
      if (startMin > 0 && (startMin < sm || startMin >= em)) return false;
      final ad = a['date'] as String? ?? '';
      final rr = a['repeatRule'] as String? ?? '';

      if (rr.isEmpty || rr == 'none') {
        // 日期匹配：优先用 _weekDates，兜底用星期几解析
        if (hasDates) return ad == cellDate;
        if (ad.isEmpty) return false;
        try {
          final p = ad.split('-');
          return DateTime(int.parse(p[0]), int.parse(p[1]), int.parse(p[2])).weekday == day;
        } catch (_) { return false; }
      }
      if (rr == 'daily') {
        return hasDates ? ad.compareTo(cellDate) <= 0 : true;
      }
      if (rr == 'weekly') {
        if (ad.isEmpty) return false;
        try {
          final p = ad.split('-');
          final parsed = DateTime(int.parse(p[0]), int.parse(p[1]), int.parse(p[2]));
          final dateOk = hasDates ? ad.compareTo(cellDate) <= 0 : true;
          return parsed.weekday == day && dateOk;
        } catch (_) { return false; }
      }
      if (rr == 'monthly') {
        if (!hasDates || cellDate.isEmpty) return false;
        try {
          final p = ad.split('-');
          final anchor = DateTime(int.parse(p[0]), int.parse(p[1]), int.parse(p[2]));
          if (ad.compareTo(cellDate) > 0) return false;
          final cp = cellDate.split('-');
          final cd = DateTime(int.parse(cp[0]), int.parse(cp[1]), int.parse(cp[2]));
          final anchorDay = anchor.day;
          final maxDay = DateTime(cd.year, cd.month + 1, 0).day;
          final ms = a['monthlyStrategy'] as String? ?? 'skip';
          if (anchorDay <= maxDay) {
            return cd.day == anchorDay;
          }
          // anchorDay > maxDay: 根据策略决定
          if (ms == 'month_end') {
            return cd.day == maxDay;
          }
          // skip 策略：跳过该月
          return false;
        } catch (_) { return false; }
      }
      return false;
    }).toList();
  }

  void _showWeekPicker() {
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      builder: (ctx) {
        final theme = Theme.of(ctx);
        return Container(
          decoration: BoxDecoration(
            color: theme.colorScheme.surface,
            borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
          ),
          padding: const EdgeInsets.all(20),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(child: Container(width: 40, height: 4, decoration: BoxDecoration(
                color: theme.colorScheme.onSurface.withValues(alpha: 0.2),
                borderRadius: BorderRadius.circular(2),
              ))),
              const SizedBox(height: 16),
              Text('\u9009\u62e9\u5468\u6b21', style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold)),
              const SizedBox(height: 16),
              Wrap(spacing: 8, runSpacing: 8, children: List.generate(_totalWeeks, (i) {
                final w = i + 1;
                final sel = w == _currentWeek;
                return GestureDetector(
                  onTap: () { _changeWeek(w); Navigator.pop(ctx); },
                  child: Container(
                    width: 56, height: 40,
                    decoration: BoxDecoration(
                      color: sel ? _accentColor.withValues(alpha: 0.2) : theme.colorScheme.surface,
                      borderRadius: BorderRadius.circular(14),
                      border: Border.all(color: sel ? _accentColor : theme.colorScheme.onSurface.withValues(alpha: 0.12), width: 1),
                    ),
                    alignment: Alignment.center,
                    child: Text('$w', style: TextStyle(fontWeight: sel ? FontWeight.bold : FontWeight.normal, color: sel ? _accentColor : theme.colorScheme.onSurface)),
                  ),
                );
              })),
              const SizedBox(height: 20),
            ],
          ),
        );
      },
    );
  }

  void _showCourseDetail(List<Map<String, dynamic>> courses, List<Map<String, dynamic>> agendas, int day, int slot) {
    final theme = Theme.of(context);
    showModalBottomSheet(
      context: context,
      backgroundColor: Colors.transparent,
      isScrollControlled: true,
      builder: (ctx) => Container(
        constraints: BoxConstraints(maxHeight: MediaQuery.of(ctx).size.height * 0.6),
        decoration: BoxDecoration(
          color: theme.colorScheme.surface,
          borderRadius: const BorderRadius.vertical(top: Radius.circular(24)),
        ),
        padding: const EdgeInsets.all(20),
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Center(child: Container(width: 40, height: 4, decoration: BoxDecoration(
                  color: theme.colorScheme.onSurface.withValues(alpha: 0.2),
                  borderRadius: BorderRadius.circular(2)))),
              const SizedBox(height: 16),
              Text('\u5468${_dayLabels[day - 1]} \u7b2c${slot + 1}\u5927\u8282',
                  style: theme.textTheme.titleLarge?.copyWith(fontWeight: FontWeight.bold)),
              const SizedBox(height: 16),
              if (courses.isNotEmpty) ...[
                Text('\u8bfe\u7a0b', style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                const SizedBox(height: 8),
                ...courses.map((c) => _courseDetailCard(theme, c)),
              ],
              if (agendas.isNotEmpty) ...[
                const SizedBox(height: 12),
                Text('\u65e5\u7a0b', style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
                const SizedBox(height: 8),
                ...agendas.map((a) => _agendaDetailCard(theme, a)),
              ],
              const SizedBox(height: 20),
            ],
          ),
        ),
      ),
    );
  }

  Widget _courseDetailCard(ThemeData theme, Map<String, dynamic> c) {
    final name = c['name'] as String? ?? '';
    final teacher = c['teacher'] as String? ?? '';
    final location = c['location'] as String? ?? '';
    final ss = c['startSection'] as int? ?? 0;
    final es = c['endSection'] as int? ?? 0;
    final weeks = c['weeks'] as List<dynamic>? ?? [];
    final weeksStr = weeks.isNotEmpty
        ? '${weeks.first}-${weeks.last}\u5468'
        : '';

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: _accentColor.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(name, style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600, color: _accentColor)),
        const SizedBox(height: 8),
        _detailRow(Icons.person_outline, teacher),
        const SizedBox(height: 4),
        _detailRow(Icons.location_on_outlined, location),
        const SizedBox(height: 4),
        _detailRow(Icons.schedule, '\u7b2c$ss-${es}\u5c0f\u8282  $weeksStr'),
      ]),
    );
  }

  Widget _agendaDetailCard(ThemeData theme, Map<String, dynamic> a) {
    final title = a['title'] as String? ?? '';
    final desc = a['description'] as String? ?? '';
    final loc = a['location'] as String? ?? '';
    final sm = a['startMinute'] as int? ?? 0;
    final em = a['endMinute'] as int? ?? 0;
    final cv = a['renderColor'] as int?;
    final ac = cv != null ? Color(cv) : Colors.orange;
    final timeStr = em > 0
        ? '${_fmtMin(sm)} - ${_fmtMin(em)}'
        : _fmtMin(sm);

    return Container(
      margin: const EdgeInsets.only(bottom: 8),
      padding: const EdgeInsets.all(14),
      decoration: BoxDecoration(
        color: ac.withValues(alpha: 0.08),
        borderRadius: BorderRadius.circular(14),
      ),
      child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(title, style: theme.textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w600, color: ac)),
        if (desc.isNotEmpty || loc.isNotEmpty) ...[
          const SizedBox(height: 8),
          if (loc.isNotEmpty) _detailRow(Icons.location_on_outlined, loc),
          if (desc.isNotEmpty) Padding(
            padding: const EdgeInsets.only(top: 4),
            child: Text(desc, style: theme.textTheme.bodySmall),
          ),
        ],
        const SizedBox(height: 4),
        _detailRow(Icons.schedule, timeStr),
      ]),
    );
  }

  Widget _detailRow(IconData icon, String text) {
    return Row(children: [
      Icon(icon, size: 14, color: Theme.of(context).colorScheme.onSurfaceVariant),
      const SizedBox(width: 6),
      Expanded(child: Text(text, style: Theme.of(context).textTheme.bodySmall)),
    ]);
  }

  String _fmtMin(int m) {
    return '${(m ~/ 60).toString().padLeft(2, '0')}:${(m % 60).toString().padLeft(2, '0')}';
  }

  String _slotLabel(int slot) {
    const labels = ['\u7b2c\u4e00\u5927\u8282', '\u7b2c\u4e8c\u5927\u8282', '\u7b2c\u4e09\u5927\u8282', '\u7b2c\u56db\u5927\u8282', '\u7b2c\u4e94\u5927\u8282'];
    return labels[slot];
  }

  String _weekDateRange() {
    if (_weekDates.length != 7) return '';
    try {
      final p0 = _weekDates.first.split('-');
      final p1 = _weekDates.last.split('-');
      return '${int.parse(p0[1])}\u6708${int.parse(p0[2])}\u65e5 - ${int.parse(p1[1])}\u6708${int.parse(p1[2])}\u65e5';
    } catch (_) {
      return '';
    }
  }

  String _dayDateShort(int i) {
    if (_weekDates.length != 7) return '';
    try {
      final p = _weekDates[i].split('-');
      return '${int.parse(p[1])}/${int.parse(p[2])}';
    } catch (_) {
      return '';
    }
  }

  Widget _weekArrow(IconData icon, bool enabled, VoidCallback onTap) {
    return GestureDetector(
      onTap: enabled ? onTap : null,
      child: Icon(icon, size: 22,
          color: enabled
              ? _accentColor
              : Theme.of(context).colorScheme.onSurface.withValues(alpha: 0.2)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context).copyWith(
      colorScheme: Theme.of(context).colorScheme.copyWith(primary: _accentColor),
    );
    if (_isLoading) return const Scaffold(backgroundColor: Colors.transparent, body: Center(child: CircularProgressIndicator()));
    return Scaffold(
      backgroundColor: Colors.transparent,
      body: Container(
        color: theme.scaffoldBackgroundColor,
        child: GestureDetector(
          behavior: HitTestBehavior.translucent,
          onHorizontalDragEnd: (d) {
            if (d.primaryVelocity == null) return;
            if (d.primaryVelocity! < -300 && _currentWeek < _totalWeeks) {
              _changeWeek(_currentWeek + 1);
            } else if (d.primaryVelocity! > 300 && _currentWeek > 1) {
              _changeWeek(_currentWeek - 1);
            }
          },
          child: SafeArea(
          child: Column(children: [
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
            child: Row(children: [
              GestureDetector(
                onTap: _showWeekPicker,
                child: Text('\u7b2c$_currentWeek\u5468\u8bfe\u8868',
                    style: theme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold)),
              ),
              const SizedBox(width: 12),
              Container(width: 1, height: 22,
                  color: theme.colorScheme.onSurface.withValues(alpha: 0.2)),
              const SizedBox(width: 12),
              GestureDetector(
                onTap: () => NativeBridge.openAgenda(),
                child: Text('\u65e5\u7a0b',
                    style: theme.textTheme.headlineMedium?.copyWith(fontWeight: FontWeight.bold)),
              ),
              const Spacer(),
            ]),
          ),
          Expanded(
            child: _courses.isEmpty
                ? Center(
                    child: Column(mainAxisSize: MainAxisSize.min, children: [
                      Icon(Icons.grid_view_rounded, size: 48, color: theme.colorScheme.onSurface.withValues(alpha: 0.15)),
                      const SizedBox(height: 12),
                      Text('\u6682\u65e0\u8bfe\u7a0b\u6570\u636e', style: theme.textTheme.bodyMedium),
                      const SizedBox(height: 16),
                      ElevatedButton.icon(
                        onPressed: () => NativeBridge.openAccountSettings(),
                        icon: const Icon(Icons.sync, size: 18),
                        label: const Text('\u524d\u5f80\u5bfc\u5165\u8bfe\u8868'),
                        style: ElevatedButton.styleFrom(
                          backgroundColor: _accentColor,
                          foregroundColor: Colors.white,
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                      ),
                    ]),
                  )
                : AnimatedSwitcher(
                    duration: const Duration(milliseconds: 350),
                    transitionBuilder: (child, anim) {
                      final slideDir = _currentWeek > _prevWeek ? 1.0 : -1.0;
                      return SlideTransition(
                        position: Tween<Offset>(
                          begin: Offset(slideDir * 0.25, 0),
                          end: Offset.zero,
                        ).animate(CurvedAnimation(parent: anim, curve: Curves.easeOutCubic)),
                        child: FadeTransition(opacity: anim, child: child),
                      );
                    },
                    child: LayoutBuilder(
                      key: ValueKey(_currentWeek),
                      builder: (ctx, constraints) {
                        return _buildGrid(theme, constraints.maxWidth, constraints.maxHeight);
                      },
                    ),
                  ),
          ),
        ]),
      ),
      ),
      ),
    );
  }

  Widget _buildGrid(ThemeData theme, double availWidth, double availHeight) {
    final headerH = 44.0;
    final osv = theme.colorScheme.onSurfaceVariant;
    final ol = theme.colorScheme.onSurface.withValues(alpha: 0.08);
    final rowAlt = theme.colorScheme.onSurface.withValues(alpha: 0.025);
    final today = DateTime.now();
    final todayDay = today.weekday;

    // 预计算合并单元格：同天同课程名连续出现则合并
    // spanMap[day][slot] = >0 首格(span数), -1 延续格, 0 空
    final spanMap = <int, Map<int, int>>{};
    for (int day = 1; day <= 7; day++) {
      spanMap[day] = {};
      for (int slot = 0; slot < 5; slot++) {
        if (spanMap[day]!.containsKey(slot)) continue;
        final courses = _coursesForCell(_currentWeek, day, slot);
        if (courses.isEmpty) { spanMap[day]![slot] = 0; continue; }
        final name = (courses.first['name'] as String? ?? '').trim();
        if (name.isEmpty) { spanMap[day]![slot] = 1; continue; }
        int span = 1;
        for (int s = slot + 1; s < 5; s++) {
          final nc = _coursesForCell(_currentWeek, day, s);
          if (nc.isEmpty) break;
          if ((nc.first['name'] as String? ?? '').trim() == name) {
            span++;
            spanMap[day]![s] = -1;
          } else { break; }
        }
        spanMap[day]![slot] = span;
      }
    }

    return ClipRect(
      child: Column(children: [
        // 表头：星期 + 日期
        Container(
          height: headerH,
          decoration: BoxDecoration(
            border: Border(bottom: BorderSide(color: ol, width: 1)),
          ),
          child: Row(children: [
            const SizedBox(width: 48),
            ...List.generate(7, (i) {
              final isToday = i + 1 == todayDay;
              final ds = _dayDateShort(i);
              return Expanded(
                child: Container(
                  decoration: BoxDecoration(
                    color: isToday ? _accentColor.withValues(alpha: 0.12) : Colors.transparent,
                    borderRadius: isToday ? BorderRadius.circular(6) : null,
                  ),
                  alignment: Alignment.center,
                  child: Column(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Text('\u5468${_dayLabels[i]}',
                          style: TextStyle(fontSize: 11, color: isToday ? _accentColor : osv,
                              fontWeight: isToday ? FontWeight.bold : FontWeight.normal)),
                      if (ds.isNotEmpty)
                        Text(ds,
                            style: TextStyle(fontSize: 9, color: isToday
                                ? _accentColor.withValues(alpha: 0.7)
                                : osv.withValues(alpha: 0.5))),
                    ],
                  ),
                ),
              );
            }),
          ]),
        ),
        // 5 大节，无分割线，交替行底色
        Expanded(
          child: Column(children: List.generate(5, (slot) {
            final isEvenSlot = slot % 2 == 0;
            return Expanded(
              child: Row(children: [
                Container(
                  width: 48,
                  color: isEvenSlot ? Colors.transparent : rowAlt,
                  alignment: Alignment.center,
                  child: Text(_slotLabel(slot),
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 10, color: osv, height: 1.3)),
                ),
                ...List.generate(7, (day) {
                  final dayNum = day + 1;
                  final cellCourses = _coursesForCell(_currentWeek, dayNum, slot);
                  final cellAgendas = _agendasForCell(_currentWeek, dayNum, slot);
                  final isToday = dayNum == todayDay && _currentWeek == _actualWeek;
                  final isMergedCont = spanMap[dayNum]![slot] == -1;
                  // 延续格不显示课程（已在上方首格显示）
                  final showCourses = isMergedCont ? <Map<String, dynamic>>[] : cellCourses;
                  final hasContent = showCourses.isNotEmpty || cellAgendas.isNotEmpty;

                  return Expanded(
                    child: GestureDetector(
                      onTap: hasContent
                          ? () => _showCourseDetail(cellCourses, cellAgendas, dayNum, slot)
                          : null,
                      child: Container(
                        clipBehavior: Clip.hardEdge,
                        decoration: BoxDecoration(
                          color: isToday
                              ? _accentColor.withValues(alpha: 0.08)
                              : (isEvenSlot ? Colors.transparent : rowAlt),
                          borderRadius: BorderRadius.circular(3),
                        ),
                        padding: const EdgeInsets.all(1),
                        child: hasContent
                            ? Column(
                                crossAxisAlignment: CrossAxisAlignment.stretch,
                                children: [
                                  if (showCourses.isNotEmpty)
                                    ...showCourses.map((c) {
                                      final ck = c['name'] as String? ?? '';
                                      final teacher = c['teacher'] as String? ?? '';
                                      final loc = c['location'] as String? ?? '';
                                      return Expanded(
                                        child: Container(
                                          margin: const EdgeInsets.only(bottom: 1),
                                          padding: const EdgeInsets.symmetric(horizontal: 1, vertical: 1),
                                          decoration: BoxDecoration(
                                            color: _accentColor.withValues(alpha: 0.12),
                                            borderRadius: BorderRadius.circular(3),
                                          ),
                                          child: Column(
                                            mainAxisAlignment: MainAxisAlignment.center,
                                            children: [
                                              Flexible(
                                                flex: 2,
                                                child: Text(ck,
                                                    textAlign: TextAlign.center,
                                                    style: TextStyle(fontSize: 11, color: _accentColor, fontWeight: FontWeight.w600),
                                                    maxLines: 2, overflow: TextOverflow.ellipsis),
                                              ),
                                              if (teacher.isNotEmpty)
                                                Flexible(
                                                  flex: 1,
                                                  child: Text(teacher,
                                                      textAlign: TextAlign.center,
                                                      style: TextStyle(fontSize: 9, color: _accentColor.withValues(alpha: 0.65)),
                                                      maxLines: 1, overflow: TextOverflow.ellipsis),
                                                ),
                                              if (loc.isNotEmpty)
                                                Flexible(
                                                  flex: 1,
                                                  child: Text(loc,
                                                      textAlign: TextAlign.center,
                                                      style: TextStyle(fontSize: 9, color: _accentColor.withValues(alpha: 0.55)),
                                                      maxLines: 1, overflow: TextOverflow.ellipsis),
                                                ),
                                            ],
                                          ),
                                        ),
                                      );
                                    }),
                                  if (cellAgendas.isNotEmpty)
                                    ...cellAgendas.map((a) {
                                      final t = a['title'] as String? ?? '';
                                      final loc = a['location'] as String? ?? '';
                                      final cv = a['renderColor'] as int?;
                                      final ac = cv != null && cv != 0 ? Color(cv) : Colors.orange;
                                      return Expanded(
                                        child: Container(
                                          margin: const EdgeInsets.only(bottom: 1),
                                          padding: const EdgeInsets.symmetric(horizontal: 1, vertical: 1),
                                          decoration: BoxDecoration(
                                              color: ac.withValues(alpha: 0.2),
                                              borderRadius: BorderRadius.circular(3),
                                              border: Border.all(color: ac.withValues(alpha: 0.5), width: 0.5)),
                                          child: Column(
                                            mainAxisAlignment: MainAxisAlignment.center,
                                            children: [
                                              Flexible(
                                                flex: 2,
                                                child: Text(t,
                                                    textAlign: TextAlign.center,
                                                    style: TextStyle(fontSize: 10, color: ac, fontWeight: FontWeight.w500),
                                                    maxLines: 2, overflow: TextOverflow.ellipsis),
                                              ),
                                              if (loc.isNotEmpty)
                                                Flexible(
                                                  flex: 1,
                                                  child: Text(loc,
                                                      textAlign: TextAlign.center,
                                                      style: TextStyle(fontSize: 9, color: ac.withValues(alpha: 0.65)),
                                                      maxLines: 1, overflow: TextOverflow.ellipsis),
                                                ),
                                            ],
                                          ),
                                        ),
                                      );
                                    }),
                                ],
                              )
                            : const SizedBox.shrink(),
                      ),
                    ),
                  );
                }),
              ]),
            );
          })),
        ),
      ]),
    );
  }

  String _slotTimeShort(int slot) {
    const times = ['8:00-9:40', '10:00-11:40', '14:00-15:40', '16:00-17:40', '19:00-20:40'];
    return times[slot];
  }

  String _slotTime(int slot) {
    const times = ['8:00\n9:40', '10:00\n11:40', '14:00\n15:40', '16:00\n17:40', '19:00\n20:40'];
    return times[slot];
  }
}
