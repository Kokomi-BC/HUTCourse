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
    final sm = slot * 2 * 60;
    final em = (slot + 1) * 2 * 60;
    final hasDates = _weekDates.length == 7 && day >= 1 && day <= 7;
    final cellDate = hasDates ? _weekDates[day - 1] : '';

    return _agendas.where((a) {
      final startMin = a['startMinute'] as int? ?? 0;
      if (startMin < sm || startMin >= em) return false;
      final ad = a['date'] as String? ?? '';
      final rr = a['repeatRule'] as String? ?? '';

      if (rr.isEmpty || rr == 'none') {
        return hasDates && ad == cellDate;
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
                  onTap: () { setState(() => _currentWeek = w); Navigator.pop(ctx); },
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
    final theme = Theme.of(context);
    if (_isLoading) return const Scaffold(body: Center(child: CircularProgressIndicator()));
    return Scaffold(
      body: GestureDetector(
        behavior: HitTestBehavior.translucent,
        onHorizontalDragEnd: (d) {
          if (d.primaryVelocity == null) return;
          if (d.primaryVelocity! < -300 && _currentWeek < _totalWeeks) {
            setState(() { _currentWeek++; });
          } else if (d.primaryVelocity! > 300 && _currentWeek > 1) {
            setState(() { _currentWeek--; });
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
              Text('${_courses.length}\u95e8\u8bfe', style: theme.textTheme.bodySmall),
            ]),
          ),
          // 诊断栏
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 20),
            child: Container(
              width: double.infinity,
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 6),
              decoration: BoxDecoration(
                color: _courses.isEmpty
                    ? Colors.orange.withValues(alpha: 0.12)
                    : _accentColor.withValues(alpha: 0.08),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                _courses.isEmpty
                    ? '\u26a0 \u8bfe\u7a0b\u6570\u636e\u4e3a\u7a7a\uff0c\u8bf7\u5148\u5728\u8d26\u53f7\u8bbe\u7f6e\u4e2d\u5bfc\u5165\u8bfe\u8868'
                    : '\u5df2\u52a0\u8f7d ${_courses.length} \u95e8\u8bfe\u7a0b\uff0c\u7b2c$_currentWeek\u5468',
                style: TextStyle(fontSize: 11, color: _courses.isEmpty ? Colors.orange.shade700 : _accentColor),
              ),
            ),
          ),
          const SizedBox(height: 4),
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
                    duration: const Duration(milliseconds: 250),
                    transitionBuilder: (child, anim) {
                      return SlideTransition(
                        position: Tween<Offset>(
                          begin: const Offset(0.15, 0),
                          end: Offset.zero,
                        ).animate(CurvedAnimation(parent: anim, curve: Curves.easeOut)),
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
    );
  }

  Widget _buildGrid(ThemeData theme, double availWidth, double availHeight) {
    final headerH = 32.0;
    final osv = theme.colorScheme.onSurfaceVariant;
    final ol = theme.colorScheme.onSurface.withValues(alpha: 0.08);
    final today = DateTime.now();
    final todayDay = today.weekday;

    return ClipRect(
      child: Column(children: [
        SizedBox(
          height: headerH,
          child: Row(children: [
            const SizedBox(width: 44),
            ...List.generate(7, (i) {
              final isToday = i + 1 == todayDay;
              return Expanded(
                child: Container(
                  decoration: BoxDecoration(
                    color: isToday ? _accentColor.withValues(alpha: 0.12) : Colors.transparent,
                    border: Border.all(color: ol, width: 0.5),
                    borderRadius: isToday ? BorderRadius.circular(6) : null,
                  ),
                  alignment: Alignment.center,
                  child: Text('\u5468${_dayLabels[i]}',
                      style: TextStyle(fontSize: 12, color: isToday ? _accentColor : osv,
                          fontWeight: isToday ? FontWeight.bold : FontWeight.normal)),
                ),
              );
            }),
          ]),
        ),
        // 5 大节各占 1/5 高度，Expanded 完美自适应
        Expanded(
          child: Column(children: List.generate(5, (slot) {
            return Expanded(
              child: Row(children: [
                Container(
                  width: 44,
                  decoration: BoxDecoration(border: Border.all(color: ol, width: 0.5)),
                  alignment: Alignment.center,
                  child: Text('${slot + 1}\n${_slotTimeShort(slot)}',
                      textAlign: TextAlign.center,
                      style: TextStyle(fontSize: 10, color: osv, height: 1.3)),
                ),
                ...List.generate(7, (day) {
                  final cellCourses = _coursesForCell(_currentWeek, day + 1, slot);
                  final cellAgendas = _agendasForCell(_currentWeek, day + 1, slot);
                  final isToday = day + 1 == todayDay && _currentWeek == _actualWeek;
                  final hasContent = cellCourses.isNotEmpty || cellAgendas.isNotEmpty;

                  return Expanded(
                    child: GestureDetector(
                      onTap: hasContent
                          ? () => _showCourseDetail(cellCourses, cellAgendas, day + 1, slot)
                          : null,
                      child: Container(
                        decoration: BoxDecoration(
                          color: isToday ? _accentColor.withValues(alpha: 0.06) : null,
                          border: Border.all(color: ol, width: 0.5),
                          borderRadius: BorderRadius.circular(3),
                        ),
                        padding: const EdgeInsets.all(2),
                        child: hasContent
                            ? Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                ...cellCourses.map((c) {
                                  final ck = c['name'] as String? ?? '';
                                  return Container(
                                    margin: const EdgeInsets.only(bottom: 1),
                                    padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 1),
                                    decoration: BoxDecoration(
                                      color: _accentColor.withValues(alpha: 0.12),
                                      borderRadius: BorderRadius.circular(3),
                                    ),
                                    child: Text(ck,
                                        style: TextStyle(fontSize: 12, color: _accentColor, fontWeight: FontWeight.w600),
                                        maxLines: 2, overflow: TextOverflow.ellipsis),
                                  );
                                }),
                                ...cellAgendas.map((a) {
                                  final t = a['title'] as String? ?? '';
                                  final cv = a['renderColor'] as int?;
                                  final ac = cv != null ? Color(cv) : Colors.orange;
                                  return Container(
                                    margin: const EdgeInsets.only(bottom: 1),
                                    padding: const EdgeInsets.symmetric(horizontal: 2, vertical: 1),
                                    decoration: BoxDecoration(
                                        color: ac.withValues(alpha: 0.2),
                                        borderRadius: BorderRadius.circular(3),
                                        border: Border.all(color: ac.withValues(alpha: 0.5), width: 0.5)),
                                    child: Text(t,
                                        style: TextStyle(fontSize: 10, color: ac, fontWeight: FontWeight.w500),
                                        maxLines: 1, overflow: TextOverflow.ellipsis),
                                  );
                                }),
                              ])
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
