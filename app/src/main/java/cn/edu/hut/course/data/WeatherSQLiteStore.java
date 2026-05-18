package cn.edu.hut.course.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * SQLite store for daily weather forecasts.
 * Each row is one date's forecast. Only dates >= today are upserted on fetch;
 * past dates are preserved. Rows older than 14 days from today are pruned.
 */
public final class WeatherSQLiteStore {

    private static final String DB_NAME = "weather_store.db";
    private static final int DB_VERSION = 3;

    private static final String TABLE_WEATHER = "weather_daily";
    private static final String COL_DATE = "date_value";       // "YYYY-MM-DD"
    private static final String COL_WEATHER = "weather";       // e.g. "多云"
    private static final String COL_TEMPERATURE = "temperature"; // e.g. "28/18"
    private static final String COL_WIND = "wind";             // e.g. "南风 3-5级"
    private static final String COL_HUMIDITY = "humidity";     // e.g. "65"
    private static final String COL_FEELS_LIKE = "feels_like"; // e.g. "31"
    private static final String COL_FETCHED_AT = "fetched_at"; // epoch millis

    private WeatherSQLiteStore() {}

    // ---- public API ----

    /**
     * Upsert one day's forecast. Only writes when date >= today, so past days are never
     * overwritten by a new fetch.
     */
    public static void upsertDay(Context context, @NonNull String date,
                                 @NonNull String weather, @NonNull String temperature,
                                 @NonNull String wind, @NonNull String humidity,
                                 @NonNull String feelsLike, long fetchedAt) {
        if (!isDateGteToday(date)) return;
        SQLiteDatabase db = new DbHelper(context.getApplicationContext()).getWritableDatabase();
        try {
            ContentValues cv = new ContentValues();
            cv.put(COL_DATE, date);
            cv.put(COL_WEATHER, weather);
            cv.put(COL_TEMPERATURE, temperature);
            cv.put(COL_WIND, wind);
            cv.put(COL_HUMIDITY, humidity);
            cv.put(COL_FEELS_LIKE, feelsLike);
            cv.put(COL_FETCHED_AT, fetchedAt);

            int updated = db.update(TABLE_WEATHER, cv, COL_DATE + "=?", new String[]{date});
            if (updated == 0) {
                db.insertWithOnConflict(TABLE_WEATHER, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
            }
        } finally {
            db.close();
        }
    }

    /**
     * Bulk upsert a list of daily entries from a fresh fetch. Also prunes rows older than 14 days.
     * Returns the fetch timestamp written into each row.
     */
    public static long upsertDays(Context context, @NonNull List<DailyEntry> entries) {
        long now = System.currentTimeMillis();
        SQLiteDatabase db = new DbHelper(context.getApplicationContext()).getWritableDatabase();
        try {
            db.beginTransaction();
            for (DailyEntry entry : entries) {
                if (!isDateGteToday(entry.date)) continue;
                ContentValues cv = new ContentValues();
                cv.put(COL_DATE, entry.date);
                cv.put(COL_WEATHER, entry.weather);
                cv.put(COL_TEMPERATURE, entry.temperature);
                cv.put(COL_WIND, entry.wind);
                cv.put(COL_HUMIDITY, entry.humidity);
                cv.put(COL_FEELS_LIKE, entry.feelsLike);
                cv.put(COL_FETCHED_AT, now);

                int updated = db.update(TABLE_WEATHER, cv, COL_DATE + "=?", new String[]{entry.date});
                if (updated == 0) {
                    db.insertWithOnConflict(TABLE_WEATHER, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
                }
            }
            // Prune rows older than 14 days
            String fourteenDaysAgo = DateUtils.daysAgoDate(14);
            db.delete(TABLE_WEATHER, COL_DATE + "<?", new String[]{fourteenDaysAgo});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
            db.close();
        }
        return now;
    }

    /**
     * Read all forecasts for the given date range (inclusive). Returns empty list if none.
     */
    @NonNull
    public static List<DailyEntry> queryRange(Context context, @NonNull String fromDate,
                                               @NonNull String toDate) {
        List<DailyEntry> result = new ArrayList<>();
        SQLiteDatabase db = new DbHelper(context.getApplicationContext()).getReadableDatabase();
        try (Cursor c = db.query(TABLE_WEATHER, null,
                COL_DATE + ">=? AND " + COL_DATE + "<=?",
                new String[]{fromDate, toDate},
                null, null, COL_DATE + " ASC")) {
            while (c.moveToNext()) {
                result.add(new DailyEntry(
                        c.getString(c.getColumnIndexOrThrow(COL_DATE)),
                        c.getString(c.getColumnIndexOrThrow(COL_WEATHER)),
                        c.getString(c.getColumnIndexOrThrow(COL_TEMPERATURE)),
                        c.getString(c.getColumnIndexOrThrow(COL_WIND)),
                        c.getString(c.getColumnIndexOrThrow(COL_HUMIDITY)),
                        c.getString(c.getColumnIndexOrThrow(COL_FEELS_LIKE)),
                        c.getLong(c.getColumnIndexOrThrow(COL_FETCHED_AT))
                ));
            }
        } finally {
            db.close();
        }
        return result;
    }

    /**
     * Read one forecast for the given date. Returns null if not found.
     */
    @Nullable
    public static DailyEntry queryByDate(Context context, @NonNull String date) {
        SQLiteDatabase db = new DbHelper(context.getApplicationContext()).getReadableDatabase();
        try (Cursor c = db.query(TABLE_WEATHER, null,
                COL_DATE + "=?", new String[]{date},
                null, null, null)) {
            if (c.moveToFirst()) {
                return new DailyEntry(
                        c.getString(c.getColumnIndexOrThrow(COL_DATE)),
                        c.getString(c.getColumnIndexOrThrow(COL_WEATHER)),
                        c.getString(c.getColumnIndexOrThrow(COL_TEMPERATURE)),
                        c.getString(c.getColumnIndexOrThrow(COL_WIND)),
                        c.getString(c.getColumnIndexOrThrow(COL_HUMIDITY)),
                        c.getString(c.getColumnIndexOrThrow(COL_FEELS_LIKE)),
                        c.getLong(c.getColumnIndexOrThrow(COL_FETCHED_AT))
                );
            }
        } finally {
            db.close();
        }
        return null;
    }

    /**
     * Check whether any stored row has a date strictly before today.
     */
    public static boolean hasDataBeforeToday(Context context) {
        String today = DateUtils.todayDate();
        SQLiteDatabase db = new DbHelper(context.getApplicationContext()).getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM " + TABLE_WEATHER + " WHERE " + COL_DATE + "<?",
                new String[]{today})) {
            if (c.moveToFirst()) {
                return c.getInt(0) > 0;
            }
        } finally {
            db.close();
        }
        return false;
    }

    /**
     * Return the latest fetched_at epoch millis across all rows, or 0 if table is empty.
     */
    public static long getLastFetchTime(Context context) {
        SQLiteDatabase db = new DbHelper(context.getApplicationContext()).getReadableDatabase();
        try (Cursor c = db.rawQuery(
                "SELECT MAX(" + COL_FETCHED_AT + ") FROM " + TABLE_WEATHER, null)) {
            if (c.moveToFirst() && !c.isNull(0)) {
                return c.getLong(0);
            }
        } finally {
            db.close();
        }
        return 0L;
    }

    // ---- helpers ----

    private static boolean isDateGteToday(@NonNull String date) {
        return date.compareTo(DateUtils.todayDate()) >= 0;
    }

    // ---- model ----

    public static final class DailyEntry {
        public final String date;        // "YYYY-MM-DD"
        public final String weather;
        public final String temperature;
        public final String wind;
        public final String humidity;
        public final String feelsLike;
        public final long fetchedAt;

        public DailyEntry(String date, String weather, String temperature,
                          String wind, String humidity, String feelsLike, long fetchedAt) {
            this.date = date;
            this.weather = weather;
            this.temperature = temperature;
            this.wind = wind;
            this.humidity = humidity;
            this.feelsLike = feelsLike;
            this.fetchedAt = fetchedAt;
        }

        @NonNull
        @Override
        public String toString() {
            return date + " " + weather + " " + temperature + " " + wind + " "
                    + humidity + " " + feelsLike;
        }
    }

    // ---- DateUtils (public helper) ----

    public static final class DateUtils {
        private DateUtils() {}

        @NonNull
        public static String todayDate() {
            java.time.LocalDate now = java.time.LocalDate.now();
            return format(now);
        }

        @NonNull
        public static String daysAgoDate(int days) {
            java.time.LocalDate date = java.time.LocalDate.now().minusDays(days);
            return format(date);
        }

        @NonNull
        public static String daysFromNowDate(int days) {
            java.time.LocalDate date = java.time.LocalDate.now().plusDays(days);
            return format(date);
        }

        @NonNull
        public static String format(@NonNull java.time.LocalDate date) {
            return String.format(java.util.Locale.US,
                    "%04d-%02d-%02d", date.getYear(), date.getMonthValue(), date.getDayOfMonth());
        }
    }

    // ---- SQLiteOpenHelper ----

    private static final class DbHelper extends SQLiteOpenHelper {

        DbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_WEATHER + " ("
                    + COL_DATE + " TEXT PRIMARY KEY,"
                    + COL_WEATHER + " TEXT NOT NULL,"
                    + COL_TEMPERATURE + " TEXT NOT NULL,"
                    + COL_WIND + " TEXT NOT NULL,"
                    + COL_HUMIDITY + " TEXT NOT NULL DEFAULT '',"
                    + COL_FEELS_LIKE + " TEXT NOT NULL DEFAULT '',"
                    + COL_FETCHED_AT + " INTEGER NOT NULL"
                    + ")");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                try {
                    db.execSQL("ALTER TABLE " + TABLE_WEATHER + " ADD COLUMN " + COL_HUMIDITY + " TEXT NOT NULL DEFAULT ''");
                } catch (Exception ignored) {
                }
            }
            if (oldVersion < 3) {
                try {
                    db.execSQL("ALTER TABLE " + TABLE_WEATHER + " ADD COLUMN " + COL_FEELS_LIKE + " TEXT NOT NULL DEFAULT ''");
                } catch (Exception ignored) {
                }
            }
        }
    }
}
