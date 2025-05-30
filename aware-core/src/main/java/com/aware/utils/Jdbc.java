package com.aware.utils;

import android.content.Context;
import android.text.TextUtils;
import android.util.Log;

import com.aware.Aware;
import com.aware.Aware_Preferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * This class will encapsulate the processes between the client and a MySQL database via JDBC API.
 */
public class Jdbc {
    private final static String TAG = "JDBC";
    private static Connection connection;
    private static int transactionCount = 0;

    private static class JdbcConnectionException extends Exception {
        private JdbcConnectionException(String message) {
            super(message);
        }
    }

    /**
     * Inserts data into a remote database table.
     *
     * @param context application context
     * @param table name of table to insert data into
     * @param rows list of the rows of data to insert
     * @return true if the data is inserted successfully, false otherwise
     */
    public static boolean insertData(Context context, String table, JSONArray rows) {
        if (rows.length() == 0) return true;

        try {
            Jdbc.transactionCount++;
            List<String> fields = new ArrayList<>();
            Iterator<String> fieldIterator = rows.getJSONObject(0).keys();
            while (fieldIterator.hasNext()) {
                fields.add(fieldIterator.next());
            }
            Jdbc.insertBatch(context, table, fields, rows);
        } catch (JSONException | SQLException | JdbcConnectionException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /**
     * Test if a connection to a database can be established.
     * @param host db host
     * @param port db port
     * @param name db name
     * @param username db username
     * @param password db password
     * @return true if a connection was established, false otherwise.
     */
    public static boolean  testConnection(String host, String port, String name, String username, String password, Boolean config_without_password, String input_password) {
        String connectionUrl = String.format("jdbc:mysql://%s:%s/%s", host, port, name);
        Log.i(TAG, "Establishing connection to remote database...");



        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();
            Log.i(TAG, "Connected to remote database...");

            if (config_without_password == false){
                Log.i(TAG, "No input password. Default password: " + password);
                connection = DriverManager.getConnection(connectionUrl, username, password);
            }else{
                Log.i(TAG, "Input password needed: " + input_password);
                connection = DriverManager.getConnection(connectionUrl, username, input_password);
            }

            connection.close();
            return true;
        } catch (Exception e) {
            Log.e(TAG, "Failed to establish connection to database, reason: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Test if API server is reachable.
     *
     * @param url API endpoint URL
     * @return true if server responds with 200, false otherwise
     */
    public static boolean testApiConnection(String url) {
        try {
            URL apiUrl = new URL(url);
            HttpURLConnection connection = (HttpURLConnection) apiUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);  // 5 seconds
            connection.setReadTimeout(5000);
            int responseCode = connection.getResponseCode();
            connection.disconnect();
            return responseCode == 200;
        } catch (Exception e) {
            Log.e(TAG, "Failed to connect to API, reason: " + e.getMessage());
            return false;
        }
    }

    /**
     * Establish a connection to the database of the currently joined study.
     * @param context application context
     */
    private static void connect(Context context) throws JdbcConnectionException {
        String connectionUrl = String.format("jdbc:mysql://%s:%s/%s?rewriteBatchedStatements=true",
                Aware.getSetting(context, Aware_Preferences.DB_HOST),
                Aware.getSetting(context, Aware_Preferences.DB_PORT),
                Aware.getSetting(context, Aware_Preferences.DB_NAME));
        Log.i(TAG, "Establishing connection to remote database...");

        try {
            Class.forName("com.mysql.jdbc.Driver").newInstance();

            connection = DriverManager.getConnection(connectionUrl,
                    Aware.getSetting(context, Aware_Preferences.DB_USERNAME),
                    Aware.getSetting(context, Aware_Preferences.DB_PASSWORD));
            Log.i(TAG, "Connected to remote database...");
        } catch (Exception e) {
            Log.e(TAG, "Failed to establish connection to database, reason: " + e.getMessage());
            e.printStackTrace();
            throw new JdbcConnectionException(e.getMessage());
        }
    }

    /**
     * Closes the current database connection.
     */
    private static void disconnect() {
        try {
            Log.i(TAG, "Closing connection to remote database...");
            if (connection != null && !connection.isClosed()) Jdbc.connection.close();
            Log.i(TAG, "Closed connection to remote database.");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    /**
     * Batch inserts data into a remote database table.
     *
     * @param context application context
     * @param table name of table to batch insert data into
     * @param fields list of the table fields
     * @param rows list of the rows of data to insert
     * @throws JdbcConnectionException
     * @throws JSONException
     */
    private static synchronized void insertBatch(Context context, String table, List<String> fields,
                                                 JSONArray rows)
            throws JdbcConnectionException, JSONException, SQLException {
        try {
            if (Jdbc.connection == null || Jdbc.connection.isClosed()) {
                Jdbc.transactionCount = 1; // reset transaction count if this is the first INSERT
                connect(context);
            }
            Log.i(TAG, "# " + Jdbc.transactionCount + " Inserting " + rows.length() +
                    " row(s) of data into remote table '" + table + "'...");

            List<String> fieldsWithBacktick = new ArrayList<>();  // in case of reserved keywords
            List<Character> sqlParamPlaceholder = new ArrayList<>();
            for (int i = 0; i < fields.size(); i ++) {
                fieldsWithBacktick.add("`" + fields.get(i) + "`");
                sqlParamPlaceholder.add('?');
            }

            String sqlStatement = String.format("INSERT INTO %s (%s) VALUES (%s)", table,
                    TextUtils.join(",", fieldsWithBacktick),
                    TextUtils.join(",", sqlParamPlaceholder));
            PreparedStatement ps = Jdbc.connection.prepareStatement(sqlStatement);

            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.getJSONObject(i);
                int paramIndex = 1;

                for (String field: fields) {
                    ps.setString(paramIndex, row.getString(field));
                    paramIndex++;
                }
                ps.addBatch();
            }

            ps.executeBatch();
            Log.i(TAG, "Inserted " + rows.length() + " row(s) of data into remote table '" + table);
        } finally {
            Jdbc.transactionCount--;
            if (Jdbc.transactionCount == 0) disconnect();
        }
    }

    /**
     * Inserts data into a remote database table via API.
     *
     * @param context application context
     * @param baseUrl base URL of the API (e.g. https://awaremicro.sensing-ehr.org)
     * @param studyNumber study number, specified in the aware micro server configuration file
     * @param studyKey study key, specified in the aware micro server configuration file
     * @param table name of table to insert data into
     * @param rows list of the rows of data to insert
     * @return true if the data is inserted successfully, false otherwise
     */
    public static boolean insertDataViaApi(Context context, String baseUrl, String studyNumber, String studyKey, String table, JSONArray rows) {
        try {
            if (rows.length() == 0) return true;

            // get full URL like: https://server.com/index.php/1/abc123/steps/insert
            String fullUrl = baseUrl;
            if (!fullUrl.endsWith("/")) fullUrl += "/";
            fullUrl += "index.php/" + studyNumber + "/" + studyKey + "/" + table + "/insert";

            Log.i("ApiUtils", "post to url: " + fullUrl);

            URL apiUrl = new URL(fullUrl);
            HttpURLConnection conn = (HttpURLConnection) apiUrl.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
            conn.setDoOutput(true);

            String deviceId = Aware.getSetting(context, Aware_Preferences.DEVICE_ID);
            String formBody = "device_id=" + URLEncoder.encode(deviceId, "UTF-8") +
                    "&data=" + rows.toString();

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = formBody.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            conn.disconnect();

            if (responseCode == 200) {
                Log.i("ApiUtils", "Successfully inserted data to API.");
                return true;
            } else {
                Log.e("ApiUtils", "API returned error code: " + responseCode);
                return false;
            }
        } catch (Exception e) {
            Log.e("ApiUtils", "Error sending data to API: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

}
