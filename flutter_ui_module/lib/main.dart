import 'dart:async';
import 'package:flutter/material.dart';
import 'bridge/native_bridge.dart';
import 'pages/today_page.dart';
import 'pages/schedule_page.dart';
import 'pages/ai_chat_page.dart';
import 'pages/profile_page.dart';
import 'utils/theme.dart';
import 'widgets/glass_bottom_bar.dart';

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
    return Scaffold(
      extendBody: true, // 允许底栏玻璃效果采样背后的页面内容
      body: IndexedStack(
        index: _currentIndex,
        children: _pages,
      ),
      bottomNavigationBar: GlassBottomBar(
        currentIndex: _currentIndex,
        onTap: _onTabChanged,
        items: const [
          GlassBottomBarItem(
            icon: Icons.home_outlined,
            activeIcon: Icons.home,
            label: '今日',
          ),
          GlassBottomBarItem(
            icon: Icons.star_outline,
            activeIcon: Icons.star,
            label: 'AI',
          ),
          GlassBottomBarItem(
            icon: Icons.date_range_outlined,
            activeIcon: Icons.date_range,
            label: '课表',
          ),
          GlassBottomBarItem(
            icon: Icons.person_outline,
            activeIcon: Icons.person,
            label: '个人',
          ),
        ],
      ),
    );
  }
}

