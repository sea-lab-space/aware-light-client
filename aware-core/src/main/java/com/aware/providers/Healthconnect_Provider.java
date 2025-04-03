package com.aware.providers;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.util.Log;

import com.aware.Aware;
import com.aware.utils.DatabaseHelper;

import java.util.HashMap;

/**
 * Healthconnect content provider，define healthconnect database operations
 */
public class Healthconnect_Provider extends ContentProvider {

    public static final int DATABASE_VERSION = 1;
    public static String AUTHORITY = "com.aware.provider.healthconnect";

    private static final int HEALTHCONNECT = 1;
    private static final int HEALTHCONNECT_ID = 2;

    public static final class Healthconnect_Data implements BaseColumns {
        private Healthconnect_Data() {}

        public static final Uri CONTENT_URI = Uri.parse("content://"
                + Healthconnect_Provider.AUTHORITY + "/healthconnect");
        public static final String CONTENT_TYPE = "vnd.android.cursor.dir/vnd.aware.healthconnect";
        public static final String CONTENT_ITEM_TYPE = "vnd.android.cursor.item/vnd.aware.healthconnect";

        // database fields：id, timestamp, device_id, type, value
        public static final String _ID = "_id";
        public static final String TIMESTAMP = "timestamp";
        public static final String DEVICE_ID = "device_id";
        public static final String TYPE = "type";
        public static final String VALUE = "value";
    }

    public static String DATABASE_NAME = "healthconnect.db";
    public static final String[] DATABASE_TABLES = {"healthconnect"};

    public static final String[] TABLES_FIELDS = {
            Healthconnect_Data._ID + " integer primary key autoincrement," +
                    Healthconnect_Data.TIMESTAMP + " real default 0," +
                    Healthconnect_Data.DEVICE_ID + " text default ''," +
                    Healthconnect_Data.TYPE + " text default ''," +
                    Healthconnect_Data.VALUE + " text default ''"
    };

    private UriMatcher sUriMatcher = null;
    private HashMap<String, String> healthconnectMap = null;

    private DatabaseHelper dbHelper;
    private static SQLiteDatabase database;

    private void initialiseDatabase() {
        if (dbHelper == null)
            dbHelper = new DatabaseHelper(getContext(), DATABASE_NAME, null, DATABASE_VERSION, DATABASE_TABLES, TABLES_FIELDS);
        if (database == null)
            database = dbHelper.getWritableDatabase();
    }

    @Override
    public boolean onCreate() {
        AUTHORITY = getContext().getPackageName() + ".provider.healthconnect";

        sUriMatcher = new UriMatcher(UriMatcher.NO_MATCH);
        sUriMatcher.addURI(Healthconnect_Provider.AUTHORITY, DATABASE_TABLES[0], HEALTHCONNECT);
        sUriMatcher.addURI(Healthconnect_Provider.AUTHORITY, DATABASE_TABLES[0] + "/#", HEALTHCONNECT_ID);

        healthconnectMap = new HashMap<>();
        healthconnectMap.put(Healthconnect_Data._ID, Healthconnect_Data._ID);
        healthconnectMap.put(Healthconnect_Data.TIMESTAMP, Healthconnect_Data.TIMESTAMP);
        healthconnectMap.put(Healthconnect_Data.DEVICE_ID, Healthconnect_Data.DEVICE_ID);
        healthconnectMap.put(Healthconnect_Data.TYPE, Healthconnect_Data.TYPE);
        healthconnectMap.put(Healthconnect_Data.VALUE, Healthconnect_Data.VALUE);

        return true;
    }

    public static String getAuthority(Context context) {
        AUTHORITY = context.getPackageName() + ".provider.healthconnect";
        return AUTHORITY;
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case HEALTHCONNECT:
                return Healthconnect_Data.CONTENT_TYPE;
            case HEALTHCONNECT_ID:
                return Healthconnect_Data.CONTENT_ITEM_TYPE;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public synchronized Uri insert(Uri uri, ContentValues initialValues) {
        initialiseDatabase();
        ContentValues values = (initialValues != null) ? new ContentValues(initialValues) : new ContentValues();
        database.beginTransaction();
        switch (sUriMatcher.match(uri)) {
            case HEALTHCONNECT:
                long id = database.insertWithOnConflict(DATABASE_TABLES[0],
                        Healthconnect_Data.VALUE, values, SQLiteDatabase.CONFLICT_IGNORE);
                if (id > 0) {
                    Uri newUri = ContentUris.withAppendedId(Healthconnect_Data.CONTENT_URI, id);
                    getContext().getContentResolver().notifyChange(newUri, null, false);
                    database.setTransactionSuccessful();
                    database.endTransaction();
                    return newUri;
                }
                database.endTransaction();
                throw new SQLException("Failed to insert row into " + uri);
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
    }

    @Override
    public synchronized int delete(Uri uri, String selection, String[] selectionArgs) {
        initialiseDatabase();
        database.beginTransaction();
        int count;
        switch (sUriMatcher.match(uri)) {
            case HEALTHCONNECT:
                count = database.delete(DATABASE_TABLES[0], selection, selectionArgs);
                break;
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        database.setTransactionSuccessful();
        database.endTransaction();
        getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }

    @Override
    public synchronized int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        initialiseDatabase();
        database.beginTransaction();
        int count;
        switch (sUriMatcher.match(uri)) {
            case HEALTHCONNECT:
                count = database.update(DATABASE_TABLES[0], values, selection, selectionArgs);
                break;
            default:
                database.endTransaction();
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        database.setTransactionSuccessful();
        database.endTransaction();
        getContext().getContentResolver().notifyChange(uri, null, false);
        return count;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection,
                        String[] selectionArgs, String sortOrder) {
        initialiseDatabase();
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setStrict(true);
        switch (sUriMatcher.match(uri)) {
            case HEALTHCONNECT:
                qb.setTables(DATABASE_TABLES[0]);
                qb.setProjectionMap(healthconnectMap);
                break;
            default:
                throw new IllegalArgumentException("Unknown URI " + uri);
        }
        Cursor c = qb.query(database, projection, selection, selectionArgs, null, null, sortOrder);
        c.setNotificationUri(getContext().getContentResolver(), uri);
        return c;
    }
}
