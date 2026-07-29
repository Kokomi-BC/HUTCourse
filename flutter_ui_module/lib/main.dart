import 'dart:ui';
import 'package:flutter/material.dart';
import 'bridge/native_bridge.dart';
import 'pages/today_page.dart';
import 'pages/schedule_page.dart';
import 'pages/ai_chat_page.dart';
import 'pages/profile_page.dart';
import 'utils/theme.dart';

void main() {
  runApp(const FlutterUIModule());
}

class FlutterUIModule extends StatelessWidget {
  const FlutterUIModule({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'HUT Course',
      debugShowCheckedModeBanner: false,
      theme: AppTheme.lightTheme,
      darkTheme: AppTheme.darkTheme,
      themeMode: ThemeMode.system,
      home: const MainScaffold(),
    );
  }
}

class MainScaffold extends StatefulWidget {
  const MainScaffold({super.key});

  @override
  State<MainScaffold> createState() => _MainScaffoldState();
}

class _MainScaffoldState extends State<MainScaffold> {
  int _currentIndex = 0;

  final List<Widget> _pages = const [
    TodayPage(),
    AiChatPage(),
    SchedulePage(),
    ProfilePage(),
  ];

  @override
  void initState() {
    super.initState();
    NativeBridge.initialize();
  }

  void _onTabChanged(int index) {
    setState(() => _currentIndex = index);
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final isDark = theme.brightness == Brightness.dark;
    final primaryColor = theme.colorScheme.primary;
    final surfaceColor = isDark
        ? const Color(0xFF1E1E1E)
        : const Color(0xFFFFFFFF);
    final onSurfaceVariant = theme.colorScheme.onSurfaceVariant;

    return Scaffold(
      body: IndexedStack(
        index: _currentIndex,
        children: _pages,
      ),
      bottomNavigationBar: _buildBottomNav(
        primaryColor,
        surfaceColor,
        onSurfaceVariant,
        isDark,
      ),
    );
  }

  Widget _buildBottomNav(
    Color primaryColor,
    Color surfaceColor,
    Color onSurfaceVariant,
    bool isDark,
  ) {
    return Container(
      margin: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      decoration: BoxDecoration(
        color: surfaceColor.withValues(alpha: 0.85),
        borderRadius: BorderRadius.circular(28),
        boxShadow: [
          BoxShadow(
            color: Colors.black.withValues(alpha: isDark ? 0.3 : 0.12),
            blurRadius: 20,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(28),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
          child: BottomNavigationBar(
            currentIndex: _currentIndex,
            onTap: _onTabChanged,
            type: BottomNavigationBarType.fixed,
            backgroundColor: Colors.transparent,
            elevation: 0,
            selectedItemColor: primaryColor,
            unselectedItemColor: onSurfaceVariant,
            selectedFontSize: 12,
            unselectedFontSize: 12,
            iconSize: 24,
            selectedLabelStyle: const TextStyle(fontWeight: FontWeight.w600),
            items: const [
              BottomNavigationBarItem(
                icon: Icon(Icons.today),
                activeIcon: _TabIcon(Icons.today),
                label: '今日',
              ),
              BottomNavigationBarItem(
                icon: Icon(Icons.smart_toy_outlined),
                activeIcon: _TabIcon(Icons.smart_toy),
                label: 'AI',
              ),
              BottomNavigationBarItem(
                icon: Icon(Icons.grid_view_rounded),
                activeIcon: _TabIcon(Icons.grid_view_rounded),
                label: '课表',
              ),
              BottomNavigationBarItem(
                icon: Icon(Icons.person_outline),
                activeIcon: _TabIcon(Icons.person),
                label: '个人',
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// 选中时带下划线指示器的图标 (复刻 Java BottomTabLineDrawable)
class _TabIcon extends StatelessWidget {
  final IconData icon;
  const _TabIcon(this.icon);

  @override
  Widget build(BuildContext context) {
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        Icon(icon, size: 24),
        const SizedBox(height: 2),
        Container(
          width: 22,
          height: 3,
          decoration: BoxDecoration(
            color: Theme.of(context).colorScheme.primary,
            borderRadius: BorderRadius.circular(2),
          ),
        ),
      ],
    );
  }
}

