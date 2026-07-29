import 'package:flutter/material.dart';
import 'bridge/native_bridge.dart';
import 'pages/today_page.dart';
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
      home: const TodayPage(),
    );
  }
}

class TodayPageHost extends StatefulWidget {
  const TodayPageHost({super.key});

  @override
  State<TodayPageHost> createState() => _TodayPageHostState();
}

class _TodayPageHostState extends State<TodayPageHost> {
  @override
  void initState() {
    super.initState();
    NativeBridge.initialize();
  }

  @override
  Widget build(BuildContext context) {
    return const TodayPage();
  }
}
