package cn.edu.hut.course;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.location.Location;
import android.net.Uri;
import android.os.Build;

import cn.edu.hut.course.data.CampusBuildingStore;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class NavigationSkillManager {

    private static final String AMAP_PACKAGE = "com.autonavi.minimap";
    private static final String AMAP_SOURCE_APP = "湖南工业大学课表";
    private static final String SYSTEM_CARD_PREFIX = "CARD_JSON:";
    private static final String SYSTEM_CARD_TYPE_NAVIGATION = "navigation";
    private static final String SYSTEM_CARD_ACTION_OPEN_AMAP_NAVIGATION = "open_amap_navigation";

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
            return "用户位置坐标："
                    + String.format(Locale.getDefault(), "%.8f", info.lat)
                    + ","
                    + String.format(Locale.getDefault(), "%.8f", info.lng);
        }
        if (info.isNoPermission()) {
            return "用户未启用位置权限";
        }
        return "暂未获取到定位";
    }

    public static String locateUserInCampus(Context context) {
        if (!CampusBuildingStore.hasLocationPermission(context)) {
            return "用户未启用位置权限";
        }

        CampusBuildingStore.DeviceLocationInfo device = CampusBuildingStore.getCurrentDeviceLocation(context, true);
        if (!device.available) {
            return "暂未获取到定位";
        }

        if (!CampusBuildingStore.isWithinCampusBounds(device.lat, device.lng)) {
            return "你在校外";
        }

        List<CampusBuildingStore.BuildingSearchResult> allBuildings = CampusBuildingStore.searchBuildings(context, "");
        if (allBuildings.isEmpty()) {
            return "你在校内";
        }

        List<BuildingDistance> distances = new ArrayList<>();
        for (CampusBuildingStore.BuildingSearchResult one : allBuildings) {
            float[] result = new float[1];
            Location.distanceBetween(device.lat, device.lng, one.lat, one.lng, result);
            distances.add(new BuildingDistance(one.buildingName, result[0]));
        }
        Collections.sort(distances, Comparator.comparingDouble(one -> one.meters));
        BuildingDistance nearest = distances.get(0);

        List<BuildingDistance> within100 = new ArrayList<>();
        List<BuildingDistance> in100To200 = new ArrayList<>();
        for (BuildingDistance one : distances) {
            if (one.meters < 100f) {
                within100.add(one);
            } else if (one.meters > 100f && one.meters < 200f) {
                in100To200.add(one);
            }
        }

        if (!within100.isEmpty()) {
            return "你在" + nearest.name + "内";
        }

        if (!in100To200.isEmpty()) {
            if (in100To200.size() == 1) {
                return "你在" + in100To200.get(0).name + "附近";
            }
            return "你在" + in100To200.get(0).name + "与" + in100To200.get(1).name + "之间";
        }

        return "最近的建筑是" + nearest.name + "，距离" + Math.round(nearest.meters) + "m";
    }

    public static String estimateRoute(Context context, String destinationRaw) {
        String destination = destinationRaw == null ? "" : destinationRaw.trim();
        if (destination.isEmpty()) {
            return "路线计算失败：目的地为空";
        }

        if (!CampusBuildingStore.hasLocationPermission(context)) {
            return "用户未启用位置权限";
        }

        CampusBuildingStore.DeviceLocationInfo user = CampusBuildingStore.getCurrentDeviceLocation(context, true);
        if (!user.available) {
            return user.isNoPermission() ? "用户未启用位置权限" : "暂未获取到定位，无法计算路线";
        }

        DestinationCandidate target = resolveDestinationCandidate(context, destinationRaw, user);
        if (target == null) {
            return "未找到目的地坐标，请检查地名或别名";
        }

        float[] result = new float[1];
        Location.distanceBetween(user.lat, user.lng, target.lat, target.lng, result);
        float meters = result[0];
        int etaMinutes = (int) Math.ceil((meters / 1.35f) / 60f);
        if (etaMinutes < 1) {
            etaMinutes = 1;
        }
        return "前往"
                + target.displayName
                + "：距离约"
                + formatDistanceMeters(meters)
                + "，预计步行"
                + etaMinutes
                + "分钟";
    }

    public static String openAmapNavigation(Context context, String destinationRaw) {
        String destination = destinationRaw == null ? "" : destinationRaw.trim();
        if (destination.isEmpty()) {
            return "导航失败：目的地为空";
        }

        DestinationCandidate target = resolveDestinationCandidate(context, destinationRaw, null);
        if (target == null) {
            return "导航失败：未找到目的地坐标，请检查地名或别名";
        }

        return openAmapNavigationByCoordinate(context, target.displayName, target.lat, target.lng);
    }

    public static String buildAmapNavigationCard(Context context, String destinationRaw) {
        String destination = destinationRaw == null ? "" : destinationRaw.trim();
        if (destination.isEmpty()) {
            return "导航失败：目的地为空";
        }

        DestinationCandidate target = resolveDestinationCandidate(context, destinationRaw, null);
        if (target == null) {
            return "导航失败：未找到目的地坐标，请检查地名或别名";
        }

        try {
            JSONObject obj = new JSONObject();
            obj.put("type", SYSTEM_CARD_TYPE_NAVIGATION);
            obj.put("title", "导航指引");
            obj.put("description", "目的地：" + target.displayName + "。点击下方按钮后将使用高德地图开始导航。");
            obj.put("action", SYSTEM_CARD_ACTION_OPEN_AMAP_NAVIGATION);
            obj.put("actionText", "点击导航至" + target.displayName);
            obj.put("targetName", target.displayName);
            obj.put("targetLat", target.lat);
            obj.put("targetLng", target.lng);
            return SYSTEM_CARD_PREFIX + obj;
        } catch (Exception ignored) {
            return "导航失败：生成导航卡片失败";
        }
    }

    public static String openAmapNavigationByCoordinate(Context context,
                                                        String targetName,
                                                        double targetLat,
                                                        double targetLng) {
        String displayName = targetName == null ? "目的地" : targetName.trim();
        if (displayName.isEmpty()) {
            displayName = "目的地";
        }
        if (Double.isNaN(targetLat) || Double.isNaN(targetLng)) {
            return "导航失败：目的地坐标无效";
        }

        Context appContext = context.getApplicationContext();
        if (!isAvailable(appContext, AMAP_PACKAGE)) {
            try {
                Intent marketIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + AMAP_PACKAGE));
                marketIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                appContext.startActivity(marketIntent);
                return "您尚未安装高德地图，已为你打开应用市场下载页";
            } catch (Exception ignored) {
                try {
                    Intent webIntent = new Intent(Intent.ACTION_VIEW, Uri.parse("https://uri.amap.com/navigation"));
                    webIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    appContext.startActivity(webIntent);
                    return "您尚未安装高德地图，已为你打开高德下载页面";
                } catch (Exception e) {
                    return "您尚未安装高德地图";
                }
            }
        }

       try {
    Uri uri = new Uri.Builder()
            .scheme("androidamap")
            .authority("route")
            .appendQueryParameter("sourceApplication", AMAP_SOURCE_APP)
            .appendQueryParameter("sid", "") // 起点ID（可选）
            .appendQueryParameter("sname", "") // 起点名称（可选，空则默认为我的位置）
            .appendQueryParameter("slat", "") // 起点纬度（可选，空则默认为我的位置）
            .appendQueryParameter("slon", "") // 起点经度（可选，空则默认为我的位置）
            .appendQueryParameter("did", "") // 终点ID
            .appendQueryParameter("dname", displayName) // 终点名称
            .appendQueryParameter("dlat", String.format(Locale.US, "%.8f", targetLat)) // 终点纬度
            .appendQueryParameter("dlon", String.format(Locale.US, "%.8f", targetLng)) // 终点经度
            .appendQueryParameter("dev", "1") // 坐标系：1=GPS，0=高德
            // t=0:驾车, t=4:步行, t=5:公交
            .appendQueryParameter("t", "4")
            .build();

    Intent intent = new Intent(Intent.ACTION_VIEW, uri);
    intent.setPackage(AMAP_PACKAGE);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    appContext.startActivity(intent);
    return "已打开高德地图，正在规划步行路线到 " + displayName;
} catch (Exception e) {
    e.printStackTrace(); // 建议打印错误日志以便调试
    return "打开高德地图失败，请稍后重试";
}
    }

    private static DestinationCandidate resolveDestinationCandidate(Context context,
                                                                    String destinationRaw,
                                                                    CampusBuildingStore.DeviceLocationInfo userInfo) {
        String source = destinationRaw == null ? "" : destinationRaw.trim();
        if (source.isEmpty()) {
            return null;
        }

        String normalized = normalizeDestinationKeyword(source);
        if (normalized.isEmpty()) {
            return null;
        }

        CampusBuildingStore.ResolvedLocation resolved = CampusBuildingStore.resolveLocation(context, normalized);
        if (resolved != null && resolved.hasCoordinate) {
            String display = CampusBuildingStore.buildLocationText(resolved.buildingName, resolved.roomNumber);
            if (display.isEmpty()) {
                display = resolved.buildingName;
            }
            return new DestinationCandidate(display, resolved.lat, resolved.lng);
        }

        List<CampusBuildingStore.BuildingSearchResult> matches = CampusBuildingStore.searchBuildings(context, normalized);
        if (matches.isEmpty() && !normalized.equals(source)) {
            matches = CampusBuildingStore.searchBuildings(context, source);
        }
        if (matches.isEmpty()) {
            return null;
        }

        CampusBuildingStore.BuildingSearchResult picked = pickBestMatch(matches, normalized, userInfo);
        return new DestinationCandidate(picked.buildingName, picked.lat, picked.lng);
    }

    private static CampusBuildingStore.BuildingSearchResult pickBestMatch(List<CampusBuildingStore.BuildingSearchResult> matches,
                                                                           String normalized,
                                                                           CampusBuildingStore.DeviceLocationInfo userInfo) {
        if (matches.size() == 1) {
            return matches.get(0);
        }

        String token = normalized == null ? "" : normalized.trim();
        for (CampusBuildingStore.BuildingSearchResult one : matches) {
            if (token.equals(one.buildingName)
                    || (one.matchedAlias != null && token.equals(one.matchedAlias))) {
                return one;
            }
        }

        if (userInfo != null && userInfo.available) {
            CampusBuildingStore.BuildingSearchResult nearest = matches.get(0);
            float nearestMeters = Float.MAX_VALUE;
            for (CampusBuildingStore.BuildingSearchResult one : matches) {
                float[] result = new float[1];
                Location.distanceBetween(userInfo.lat, userInfo.lng, one.lat, one.lng, result);
                if (result[0] < nearestMeters) {
                    nearestMeters = result[0];
                    nearest = one;
                }
            }
            return nearest;
        }

        return matches.get(0);
    }

    private static String normalizeDestinationKeyword(String raw) {
        String text = raw == null ? "" : raw.trim();
        text = text.replace("，", " ")
                .replace("。", " ")
                .replace(",", " ")
                .replace(".", " ")
                .replace("?", " ")
                .replace("？", " ")
                .replace("!", " ")
                .replace("！", " ");
        String[] noise = new String[]{
                "怎么走", "怎么去", "怎么到", "如何去", "去", "到", "前往", "导航到", "导航去",
                "路线", "在哪", "在哪里", "位置", "我想去", "带我去", "请导航", "导航", "走到", "到达"
        };
        for (String n : noise) {
            text = text.replace(n, " ");
        }
        return text.replace(" ", "").replace("　", "").trim();
    }

    private static boolean isAvailable(Context context, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return false;
        }
        PackageManager pm = context.getPackageManager();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0));
            } else {
                pm.getPackageInfo(packageName, 0);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    private static String formatDistanceMeters(float meters) {
        if (meters < 1000f) {
            return Math.round(meters) + "米";
        }
        return String.format(Locale.getDefault(), "%.1f公里", meters / 1000f);
    }

    private static final class BuildingDistance {
        final String name;
        final float meters;

        BuildingDistance(String name, float meters) {
            this.name = name;
            this.meters = meters;
        }
    }

    private static final class DestinationCandidate {
        final String displayName;
        final double lat;
        final double lng;

        DestinationCandidate(String displayName, double lat, double lng) {
            this.displayName = displayName;
            this.lat = lat;
            this.lng = lng;
        }
    }
}
