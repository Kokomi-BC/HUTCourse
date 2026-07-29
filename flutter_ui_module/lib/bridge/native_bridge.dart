import 'dart:async';
import 'dart:convert';
import 'package:flutter/services.dart';

class NativeBridge {
  static const MethodChannel _channel = MethodChannel(
    'cn.edu.hut.course/native',
  );

  static bool _initialized = false;

  // AI 流式输出
  static final _aiChunkController = StreamController<String>.broadcast();
  static final _aiDoneController = StreamController<void>.broadcast();
  static Stream<String> get aiChunks => _aiChunkController.stream;
  static Stream<void> get aiDone => _aiDoneController.stream;

  static void initialize() {
    if (_initialized) return;
    _initialized = true;

    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'updateTheme':
          return {'success': true};
        case 'aiChunk':
          final text = call.arguments as String? ?? '';
          _aiChunkController.add(text);
          return null;
        case 'aiDone':
          _aiDoneController.add(null);
          return null;
        default:
          throw MissingPluginException('Unknown method: ${call.method}');
      }
    });
  }

  /// 获取全部课程 — 通过 JSON 字符串传输，绕过 MethodChannel List<Map> 序列化问题
  static Future<List<Map<String, dynamic>>> getCourses() async {
    try {
      final rawJson = await _channel.invokeMethod('getCoursesJson');
      final jsonStr = rawJson as String? ?? '[]';
      print('[NativeBridge] getCourses: JSON len=${jsonStr.length}');
      final List<dynamic> parsed = json.decode(jsonStr);
      final result = <Map<String, dynamic>>[];
      for (final item in parsed) {
        if (item is! Map) continue;
        final map = Map<String, dynamic>.from(item);
        // 原生端 JSON 用 sectionSpan，转为 endSection 供 Flutter 使用
        if (map['isRemark'] == true) continue;
        final start = (map['startSection'] as int?) ?? 1;
        final span = (map['sectionSpan'] as int?) ?? 2;
        map['endSection'] = start + span - 1;
        // 确保 weeks 是 List
        if (map['weeks'] is! List) map['weeks'] = <int>[];
        result.add(map);
      }
      print('[NativeBridge] getCourses: parsed ${result.length} courses from JSON');
      return result;
    } catch (e, stack) {
      print('[NativeBridge] getCourses ERROR: $e');
      print('[NativeBridge] stack: $stack');
      return [];
    }
  }

  /// 诊断：获取原始课程 JSON 字符串（绕过 Map 序列化排查问题）
  static Future<String> getCoursesJson() async {
    try {
      final result = await _channel.invokeMethod('getCoursesJson');
      return result as String? ?? '[]';
    } catch (e) {
      print('[NativeBridge] getCoursesJson ERROR: $e');
      return '[]';
    }
  }

  /// 诊断：获取课程数量
  static Future<int> getCourseCount() async {
    try {
      final result = await _channel.invokeMethod('getCourseCount');
      return result as int? ?? 0;
    } catch (e) {
      print('[NativeBridge] getCourseCount ERROR: $e');
      return 0;
    }
  }

  /// 诊断：获取当前活跃课表 ID
  static Future<int> getTableId() async {
    try {
      final result = await _channel.invokeMethod('getTableId');
      return result as int? ?? 0;
    } catch (e) {
      print('[NativeBridge] getTableId ERROR: $e');
      return 0;
    }
  }

  static Future<Map<String, dynamic>?> getTodayCourses() async {
    try {
      print('[NativeBridge] getTodayCourses: invoking...');
      final result = await _channel.invokeMethod('getTodayCourses');
      print('[NativeBridge] getTodayCourses: result=$result');
      return result != null ? Map<String, dynamic>.from(result) : null;
    } catch (e, stack) {
      print('[NativeBridge] getTodayCourses ERROR: $e');
      print('[NativeBridge] stack: $stack');
      return null;
    }
  }

  static Future<int> getCurrentWeek() async {
    try {
      final result = await _channel.invokeMethod('getCurrentWeek');
      return result as int;
    } catch (e) {
      print('Error getting current week: $e');
      return 1;
    }
  }

  static Future<Map<String, dynamic>?> getProfile() async {
    try {
      final result = await _channel.invokeMethod('getProfile');
      return result != null ? Map<String, dynamic>.from(result) : null;
    } catch (e) {
      print('Error getting profile: $e');
      return null;
    }
  }

  static Future<int> getThemeColor() async {
    try {
      final result = await _channel.invokeMethod('getThemeColor');
      return result as int;
    } catch (e) {
      print('Error getting theme color: $e');
      return 0xFF667eea;
    }
  }

  static Future<List<Map<String, dynamic>>> getAgendaItems() async {
    try {
      final result = await _channel.invokeMethod('getAgendaItems');
      return List<Map<String, dynamic>>.from(result);
    } catch (e) {
      print('Error getting agenda items: $e');
      return [];
    }
  }

  static Future<void> openSettings() async {
    try {
      await _channel.invokeMethod('openSettings');
    } catch (e) {
      print('Error opening settings: $e');
    }
  }

  static Future<void> openAccountSettings() async {
    try {
      await _channel.invokeMethod('openAccountSettings');
    } catch (e) {
      print('Error opening account settings: $e');
    }
  }

  static Future<void> openDisplaySettings() async {
    try {
      await _channel.invokeMethod('openDisplaySettings');
    } catch (e) {
      print('Error opening display settings: $e');
    }
  }

  static Future<void> openDataSettings() async {
    try {
      await _channel.invokeMethod('openDataSettings');
    } catch (e) {
      print('Error opening data settings: $e');
    }
  }

  static Future<void> openAiSettings() async {
    try {
      await _channel.invokeMethod('openAiSettings');
    } catch (e) {
      print('Error opening AI settings: $e');
    }
  }

  static Future<void> openExam() async {
    try {
      await _channel.invokeMethod('openExam');
    } catch (e) {
      print('Error opening exam: $e');
    }
  }

  /// 打开日程总览页
  static Future<void> openAgenda() async {
    try {
      await _channel.invokeMethod('openAgenda');
    } catch (e) {
      print('Error opening agenda: $e');
    }
  }

  /// 发送消息到 AI 网关，返回 AI 回复文本
  static Future<String?> sendAiMessage(String message) async {
    try {
      final result = await _channel.invokeMethod('sendAiMessage', {
        'message': message,
      });
      return result as String?;
    } catch (e) {
      print('Error sending AI message: $e');
      return null;
    }
  }

  /// 启动流式 AI 对话（文本通过 aiChunks / aiDone 流返回）
  static Future<bool> startAiStream(String message) async {
    try {
      final result = await _channel.invokeMethod('startAiStream', {
        'message': message,
      });
      return result == true;
    } catch (e) {
      print('Error starting AI stream: $e');
      return false;
    }
  }

  /// 加载对话历史
  static Future<List<dynamic>?> loadChatHistory() async {
    try {
      final result = await _channel.invokeMethod('loadChatHistory');
      return result as List<dynamic>?;
    } catch (e) {
      print('Error loading chat history: $e');
      return null;
    }
  }

  /// 获取天气数据
  static Future<Map<String, dynamic>?> getWeather() async {
    try {
      final result = await _channel.invokeMethod('getWeather');
      return result != null ? Map<String, dynamic>.from(result) : null;
    } catch (e) {
      print('Error getting weather: $e');
      return null;
    }
  }

  /// 获取指定周的7天日期列表 (YYYY-MM-DD)
  static Future<List<String>> getWeekDates(int week) async {
    try {
      final result = await _channel.invokeMethod('getWeekDates', {'week': week});
      return List<String>.from(result);
    } catch (e) {
      print('Error getting week dates: $e');
      return [];
    }
  }

  /// 设置主题颜色（int color value）
  static Future<bool> setThemeColor(int color) async {
    try {
      final result = await _channel.invokeMethod('setThemeColor', {'color': color});
      return result == true;
    } catch (e) {
      print('Error setting theme color: $e');
      return false;
    }
  }

  /// 获取 AI 模型配置
  static Future<Map<String, dynamic>?> getAiConfig() async {
    try {
      final result = await _channel.invokeMethod('getAiConfig');
      return result != null ? Map<String, dynamic>.from(result) : null;
    } catch (e) {
      print('Error getting AI config: $e');
      return null;
    }
  }

  /// 获取当前实际周数（用于课表"今天"高亮）
  static Future<int> getCurrentActualWeek() async {
    try {
      final result = await _channel.invokeMethod('getCurrentActualWeek');
      return result as int;
    } catch (e) {
      print('Error getting current actual week: $e');
      return 1;
    }
  }
}
