package cn.edu.hut.course;

import android.content.Context;

import cn.edu.hut.course.data.CampusBuildingStore;

import java.util.List;
import java.util.Locale;

public final class NavigationSkillManager {

    private NavigationSkillManager() {
    }

    public static String listAllPlaces(Context context) {
        List<String> names = CampusBuildingStore.getBuildingNames(context);
        if (names.isEmpty()) {
            return "地点列表为空";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("地点列表(").append(names.size()).append("):\n");
        for (int i = 0; i < names.size(); i++) {
            sb.append(i + 1).append(". ").append(names.get(i));
            if (i < names.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public static String searchPlace(Context context, String keyword, boolean highResolution) {
        String key = keyword == null ? "" : keyword.trim();
        if (key.isEmpty()) {
            return "查询失败：地点关键词为空";
        }

        List<CampusBuildingStore.BuildingSearchResult> results = CampusBuildingStore.searchBuildings(context, key);
        if (results.isEmpty()) {
            return "未找到匹配地点（关键词=" + key + "）";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("地点查询（关键词=").append(key).append("）:\n");
        for (int i = 0; i < results.size(); i++) {
            CampusBuildingStore.BuildingSearchResult one = results.get(i);
            sb.append(i + 1).append(". ").append(one.buildingName);
            if (one.matchedAlias != null && !one.matchedAlias.isEmpty() && !one.buildingName.equals(one.matchedAlias)) {
                sb.append("（匹配别名=").append(one.matchedAlias).append("）");
            }
            if (highResolution) {
                sb.append(" | 坐标 ")
                        .append(String.format(Locale.getDefault(), "%.8f", one.lat))
                        .append(",")
                        .append(String.format(Locale.getDefault(), "%.8f", one.lng));
            }
            if (i < results.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public static String getCurrentUserCoordinate(Context context) {
        CampusBuildingStore.DeviceLocationInfo info = CampusBuildingStore.getCurrentDeviceLocation(context, true);
        if (info.available) {
            return "用户当前位置坐标："
                    + String.format(Locale.getDefault(), "%.8f", info.lat)
                    + ","
                    + String.format(Locale.getDefault(), "%.8f", info.lng);
        }
        if (info.isNoPermission()) {
            return "用户未启用位置权限";
        }
        return "暂未获取到当前定位";
    }

    public static String estimateRoute(Context context, String destinationRaw) {
        String destination = destinationRaw == null ? "" : destinationRaw.trim();
        if (destination.isEmpty()) {
            return "路线计算失败：目的地为空";
        }

        if (!CampusBuildingStore.hasLocationPermission(context)) {
            return "用户未启用位置权限";
        }

        CampusBuildingStore.DistanceInfo info = CampusBuildingStore.estimateDistanceFromDevice(context, destination, true);
        if (info.available) {
            return "前往"
                    + info.buildingName
                    + "：距离约"
                    + formatDistanceMeters(info.meters)
                    + "，预计步行"
                    + info.etaMinutes
                    + "分钟";
        }
        if (info.isNoLocation()) {
            return "暂未获取到当前定位，无法计算路线";
        }
        if (info.isNoCoordinate()) {
            return "未找到目的地坐标，请检查地名或别名";
        }
        return "路线计算失败";
    }

    private static String formatDistanceMeters(float meters) {
        if (meters < 1000f) {
            return Math.round(meters) + "米";
        }
        return String.format(Locale.getDefault(), "%.1f公里", meters / 1000f);
    }
}
