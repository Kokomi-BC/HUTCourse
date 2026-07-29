import 'package:flutter/services.dart';

class NativeBridge {
  static const MethodChannel _channel = MethodChannel(
    'cn.edu.hut.course/native',
  );

  static bool _initialized = false;

  static void initialize() {
    if (_initialized) return;
    _initialized = true;

    _channel.setMethodCallHandler((call) async {
      switch (call.method) {
        case 'updateTheme':
          return {'success': true};
        default:
          throw MissingPluginException('Unknown method: ${call.method}');
      }
    });
  }

  static Future<List<Map<String, dynamic>>> getCourses() async {
    try {
      final result = await _channel.invokeMethod('getCourses');
      return List<Map<String, dynamic>>.from(result);
    } catch (e) {
      print('Error getting courses: $e');
      return [];
    }
  }

  static Future<Map<String, dynamic>?> getTodayCourses() async {
    try {
      final result = await _channel.invokeMethod('getTodayCourses');
      return result != null ? Map<String, dynamic>.from(result) : null;
    } catch (e) {
      print('Error getting today courses: $e');
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

  static Future<void> openExam() async {
    try {
      await _channel.invokeMethod('openExam');
    } catch (e) {
      print('Error opening exam: $e');
    }
  }
}
