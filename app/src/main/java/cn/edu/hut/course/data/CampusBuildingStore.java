package cn.edu.hut.course.data;

import android.Manifest;
import android.content.ContentValues;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CampusBuildingStore {

    private static final String DB_NAME = "campus_geo.db";
    private static final int DB_VERSION = 2;

    private static final String TABLE_BUILDING = "campus_building";
    private static final String TABLE_ALIAS = "campus_building_alias";

    private static final Pattern ROOM_PATTERN = Pattern.compile("(\\d+)");

    private static final String REASON_NO_LOCATION = "NO_LOCATION";
    private static final String REASON_NO_COORDINATE = "NO_COORDINATE";

    private static long lastDeviceLocationFetchMs = 0L;
    private static double[] cachedDeviceLocation = null;

    private CampusBuildingStore() {
    }

    public static List<String> getBuildingNames(Context context) {
        List<String> names = new ArrayList<>();
        DbHelper helper = new DbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor cursor = null;
        try {
            cursor = db.query(TABLE_BUILDING, new String[]{"building_name"}, null, null, null, null, "id ASC");
            while (cursor.moveToNext()) {
                names.add(cursor.getString(0));
            }
        } finally {
            if (cursor != null) cursor.close();
            db.close();
        }
        return names;
    }

    public static ResolvedLocation resolveLocation(Context context, String rawLocation) {
        String location = normalizeLocation(rawLocation);
        if (location.isEmpty()) {
            return null;
        }

        if (location.startsWith("公共") && !location.startsWith("公共楼")) {
            location = location.replaceFirst("公共", "公共楼");
        }

        DbHelper helper = new DbHelper(context.getApplicationContext());
        SQLiteDatabase db = helper.getReadableDatabase();
        Cursor aliasCursor = null;
        try {
            aliasCursor = db.query(TABLE_ALIAS,
                    new String[]{"alias", "building_name"},
                    null, null, null, null,
                    "LENGTH(alias) DESC");

            String matchedAlias = null;
            String matchedBuilding = null;
            while (aliasCursor.moveToNext()) {
                String alias = normalizeToken(aliasCursor.getString(0));
                String building = aliasCursor.getString(1);
                if (alias.isEmpty()) {
                    continue;
                }
                if (location.startsWith(alias) || location.contains(alias)) {
                    matchedAlias = alias;
                    matchedBuilding = building;
                    break;
                }
            }

            if (matchedBuilding == null) {
                return null;
            }

            String remainder = location;
            if (matchedAlias != null && !matchedAlias.isEmpty()) {
                int idx = remainder.indexOf(matchedAlias);
                if (idx >= 0) {
                    remainder = remainder.substring(idx + matchedAlias.length());
                }
            }
            String room = extractRoomNumber(remainder);

            Cursor buildingCursor = db.query(TABLE_BUILDING,
                    new String[]{"lat", "lng"},
                    "building_name=?",
                    new String[]{matchedBuilding},
                    null, null, null);
            try {
                if (!buildingCursor.moveToFirst()) {
                    return new ResolvedLocation(matchedBuilding, room, false, 0d, 0d);
                }
                double lat = buildingCursor.getDouble(0);
                double lng = buildingCursor.getDouble(1);
                return new ResolvedLocation(matchedBuilding, room, true, lat, lng);
            } finally {
                buildingCursor.close();
            }
        } finally {
            if (aliasCursor != null) aliasCursor.close();
            db.close();
        }
    }

    public static String buildLocationText(String buildingName, String roomNumber) {
        String building = normalizeToken(buildingName);
        if (building.isEmpty() || "未定".equals(building)) {
            return "";
        }
        if ("公共".equals(building)) {
            building = "公共楼";
        }
        String room = normalizeRoom(roomNumber);
        return room.isEmpty() ? building : (building + "-" + room);
    }

    public static String toStandardLocation(Context context, String rawLocation) {
        String normalized = normalizeLocation(rawLocation);
        if (normalized.isEmpty()) {
            return "未定";
        }

        ResolvedLocation resolved = resolveLocation(context, rawLocation);
        if (resolved == null) {
            return normalized;
        }
        String merged = buildLocationText(resolved.buildingName, resolved.roomNumber);
        return merged.isEmpty() ? resolved.buildingName : merged;
    }

    public static DistanceInfo estimateDistanceFromDevice(Context context, String rawLocation) {
        double[] device = getLastKnownDeviceLocation(context);
        if (device == null) {
            return DistanceInfo.unavailable(REASON_NO_LOCATION);
        }
        return estimateDistance(context, rawLocation, device[0], device[1]);
    }

    public static DistanceInfo estimateDistance(Context context, String rawLocation, double userLat, double userLng) {
        ResolvedLocation resolved = resolveLocation(context, rawLocation);
        if (resolved == null || !resolved.hasCoordinate) {
            return DistanceInfo.unavailable(REASON_NO_COORDINATE);
        }

        float[] result = new float[1];
        Location.distanceBetween(userLat, userLng, resolved.lat, resolved.lng, result);
        float meters = result[0];
        int etaMinutes = (int) Math.ceil((meters / 1.35f) / 60f);
        if (etaMinutes < 1) {
            etaMinutes = 1;
        }
        return DistanceInfo.available(resolved.buildingName, meters, etaMinutes);
    }

    private static double[] getLastKnownDeviceLocation(Context context) {
        long now = System.currentTimeMillis();
        if (cachedDeviceLocation != null && now - lastDeviceLocationFetchMs < 15_000L) {
            return cachedDeviceLocation;
        }

        if (!hasLocationPermission(context)) {
            return null;
        }

        LocationManager manager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            return null;
        }

        Location best = null;
        try {
            List<String> providers = manager.getProviders(true);
            for (String provider : providers) {
                Location one = manager.getLastKnownLocation(provider);
                if (one == null) {
                    continue;
                }
                if (best == null || one.getTime() > best.getTime()) {
                    best = one;
                }
            }
        } catch (SecurityException ignored) {
            return null;
        }

        if (best == null) {
            return null;
        }

        cachedDeviceLocation = new double[]{best.getLatitude(), best.getLongitude()};
        lastDeviceLocationFetchMs = now;
        return cachedDeviceLocation;
    }

    public static boolean hasLocationPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    private static String extractRoomNumber(String raw) {
        Matcher matcher = ROOM_PATTERN.matcher(raw == null ? "" : raw);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private static String normalizeLocation(String raw) {
        String text = raw == null ? "" : raw.trim();
        while (text.startsWith("@") || text.startsWith("＠")) {
            text = text.substring(1).trim();
        }
        return normalizeToken(text);
    }

    private static String normalizeToken(String token) {
        String text = token == null ? "" : token.trim();
        text = text.replace(" ", "").replace("　", "");
        if ("公共".equals(text)) {
            return "公共楼";
        }
        return text;
    }

    private static String normalizeRoom(String room) {
        String raw = room == null ? "" : room.trim();
        Matcher matcher = ROOM_PATTERN.matcher(raw);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    public static final class ResolvedLocation {
        public final String buildingName;
        public final String roomNumber;
        public final boolean hasCoordinate;
        public final double lat;
        public final double lng;

        ResolvedLocation(String buildingName, String roomNumber, boolean hasCoordinate, double lat, double lng) {
            this.buildingName = buildingName;
            this.roomNumber = roomNumber;
            this.hasCoordinate = hasCoordinate;
            this.lat = lat;
            this.lng = lng;
        }
    }

    public static final class DistanceInfo {
        public final boolean available;
        public final String buildingName;
        public final float meters;
        public final int etaMinutes;
        public final String reason;

        private DistanceInfo(boolean available, String buildingName, float meters, int etaMinutes, String reason) {
            this.available = available;
            this.buildingName = buildingName;
            this.meters = meters;
            this.etaMinutes = etaMinutes;
            this.reason = reason;
        }

        static DistanceInfo available(String buildingName, float meters, int etaMinutes) {
            return new DistanceInfo(true, buildingName, meters, etaMinutes, null);
        }

        static DistanceInfo unavailable(String reason) {
            return new DistanceInfo(false, null, -1f, -1, reason);
        }

        public boolean isNoLocation() {
            return REASON_NO_LOCATION.equals(reason);
        }

        public boolean isNoCoordinate() {
            return REASON_NO_COORDINATE.equals(reason);
        }
    }

    private static final class BuildingSeed {
        final String buildingName;
        final String aliases;
        final double lat;
        final double lng;

        BuildingSeed(String buildingName, String aliases, double lngFirst, double latSecond) {
            this.buildingName = buildingName;
            this.aliases = aliases;
            // Source data is provided as "longitude, latitude".
            this.lat = latSecond;
            this.lng = lngFirst;
        }
    }

    private static final BuildingSeed[] SEEDS = new BuildingSeed[]{
            new BuildingSeed("公共楼", "公共楼,崇学楼,公共教字楼,公共", 113.101581, 27.822194),
            new BuildingSeed("崇礼楼", "崇礼楼,冶金与材料工程学院,马克思主义学院,理学院", 113.098953, 27.820945),
            new BuildingSeed("崇真楼", "崇真楼,机械工程学院,土木工程字院,土木工程学院", 113.099073, 27.819869),
            new BuildingSeed("崇信楼", "崇信楼,商字院,商学院,经济与贸易学院,法字院,法学院", 113.101093, 27.819759),
            new BuildingSeed("崇文楼", "崇文楼,文字与新闻传播字院,文学与新闻传播学院,外国语学院", 113.101054, 27.820870),
            new BuildingSeed("崇材楼", "崇材楼,包装与材料工程学院教学楼", 113.102413, 27.818490),
            new BuildingSeed("崇智楼", "崇智楼,包装与材料工程学院实验楼", 113.103482, 27.819078),
            new BuildingSeed("崇美楼", "崇美楼,包装设计艺术学院", 113.102343, 27.819678),
            new BuildingSeed("崇仁楼", "崇仁楼,综合实验楼,生命科学与化学学院", 113.101945, 27.821037),
            new BuildingSeed("崇德楼", "崇德楼,科技楼", 113.100753, 27.818077),
            new BuildingSeed("崇慧楼", "崇慧楼,计通楼,计算机与人工智能字院,计算机与人工智能学院,电气与信息工程学院", 113.098957, 27.818962),
            new BuildingSeed("崇善楼", "崇善楼,城市与环境工程字院,城市与环境工程学院,交通工程学院", 113.097680, 27.820863),
            new BuildingSeed("崇实楼", "崇实楼,工程实训楼", 113.097763, 27.819929),
            new BuildingSeed("崇乐楼", "崇乐楼,音乐学院", 113.104313, 27.823315),
            new BuildingSeed("崇业楼", "崇业楼,创新创业学院", 113.105155, 27.821998),
            new BuildingSeed("西苑宿舍区", "西苑宿舍区", 113.094544, 27.818469),
            new BuildingSeed("西苑食堂", "西苑食堂", 113.094486, 27.819316),
            new BuildingSeed("西苑田径场", "西苑田径场,第二田径场", 113.095008, 27.821279),
            new BuildingSeed("萃苑", "萃苑", 113.098766, 27.825169),
            new BuildingSeed("北苑宿舍区", "北苑宿舍区", 113.100356, 27.825736),
            new BuildingSeed("图书馆", "图书馆,校史馆", 113.100125, 27.823617),
            new BuildingSeed("北苑食堂", "北苑食堂", 113.100765, 27.827595),
            new BuildingSeed("东苑宿舍区", "东苑宿舍区", 113.107464, 27.822106),
            new BuildingSeed("东苑食堂", "东苑食堂", 113.106298, 27.822070),
            new BuildingSeed("南苑宿舍区", "南苑宿舍区", 113.102336, 27.817367),
            new BuildingSeed("南苑食堂", "南苑食堂", 113.105774, 27.817471)
    };

    private static final class DbHelper extends SQLiteOpenHelper {

        DbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_BUILDING + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "building_name TEXT NOT NULL UNIQUE,"
                    + "aliases TEXT NOT NULL,"
                    + "lat REAL NOT NULL,"
                    + "lng REAL NOT NULL"
                    + ")");

            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_ALIAS + " ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "alias TEXT NOT NULL UNIQUE,"
                    + "building_name TEXT NOT NULL"
                    + ")");

            seedBuildings(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_ALIAS);
            db.execSQL("DROP TABLE IF EXISTS " + TABLE_BUILDING);
            onCreate(db);
        }

        private void seedBuildings(SQLiteDatabase db) {
            for (BuildingSeed seed : SEEDS) {
                ContentValues values = new ContentValues();
                values.put("building_name", seed.buildingName);
                values.put("aliases", seed.aliases);
                values.put("lat", seed.lat);
                values.put("lng", seed.lng);
                db.insert(TABLE_BUILDING, null, values);

                for (String aliasRaw : seed.aliases.split("[,]")) {
                    String alias = normalizeToken(aliasRaw);
                    if (alias.isEmpty()) {
                        continue;
                    }
                    ContentValues aliasValues = new ContentValues();
                    aliasValues.put("alias", alias);
                    aliasValues.put("building_name", seed.buildingName);
                    db.insertWithOnConflict(TABLE_ALIAS, null, aliasValues, SQLiteDatabase.CONFLICT_IGNORE);
                }
                ContentValues canonicalAlias = new ContentValues();
                canonicalAlias.put("alias", normalizeToken(seed.buildingName));
                canonicalAlias.put("building_name", seed.buildingName);
                db.insertWithOnConflict(TABLE_ALIAS, null, canonicalAlias, SQLiteDatabase.CONFLICT_IGNORE);
            }
        }
    }
}
