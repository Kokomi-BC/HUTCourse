package cn.edu.hut.course.flutter

import io.flutter.embedding.android.FlutterActivity
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodChannel

class FlutterHostActivity : FlutterActivity() {
    private val CHANNEL = "cn.edu.hut.course/native"

    override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
        super.configureFlutterEngine(flutterEngine)

        MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
            .setMethodCallHandler { call, result ->
                when (call.method) {
                    "getCourses" -> {
                        result.success(getCoursesData())
                    }
                    "getTodayCourses" -> {
                        result.success(getTodayCoursesData())
                    }
                    "getCurrentWeek" -> {
                        result.success(getCurrentWeek())
                    }
                    "getProfile" -> {
                        result.success(getProfileData())
                    }
                    "getThemeColor" -> {
                        result.success(getThemeColor())
                    }
                    "getAgendaItems" -> {
                        result.success(getAgendaItemsData())
                    }
                    "openSettings" -> {
                        openSettings()
                        result.success(null)
                    }
                    "openExam" -> {
                        openExam()
                        result.success(null)
                    }
                    else -> {
                        result.notImplemented()
                    }
                }
            }
    }

    private fun getCoursesData(): List<Map<String, Any>> {
        // TODO: 实现从CourseStorageManager获取课程数据
        return emptyList()
    }

    private fun getTodayCoursesData(): Map<String, Any> {
        // TODO: 实现获取今日课程数据
        return mapOf(
            "courses" to emptyList<Any>()
        )
    }

    private fun getCurrentWeek(): Int {
        // TODO: 实现获取当前周数
        return 1
    }

    private fun getProfileData(): Map<String, String?> {
        // TODO: 实现获取用户个人信息
        return mapOf(
            "name" to null,
            "studentId" to null,
            "className" to null,
            "college" to null
        )
    }

    private fun getThemeColor(): Int {
        // TODO: 实现获取主题颜色
        return 0xFF667eea.toInt()
    }

    private fun getAgendaItemsData(): List<Map<String, Any>> {
        // TODO: 实现获取日程数据
        return emptyList()
    }

    private fun openSettings() {
        // TODO: 实现打开设置页面
    }

    private fun openExam() {
        // TODO: 实现打开考试页面
    }
}
