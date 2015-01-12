package org.gplvote.signdoc;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.format.DateFormat;
import android.util.Log;

import java.sql.Date;
import java.text.SimpleDateFormat;
import java.util.Locale;

public class DocsStorage extends SQLiteOpenHelper {
    private static final String LOG_TAG = "DB";

    private static DocsStorage instance;

    public static DocsStorage getInstance(Context context) {
        synchronized (DocsStorage.class) {
            if (instance == null) {
                try {
                    instance = new DocsStorage(context);
                } catch (Exception e) {
                    Log.e("Settings/Singleton", e.getMessage(), e);
                    instance = new DocsStorage(context);
                }
            }
            return instance;
        }
    }

    public DocsStorage(Context context) {
        super(context, "SignDoc", null, 3);
    }

    public static boolean is_new(Context context, DocSignRequest doc) {
        SQLiteDatabase db = getInstance(context).getReadableDatabase();

        Cursor c = db.query("docs_list", new String[]{"id"}, "site = ? AND doc_id = ?", new String[]{doc.site, doc.doc_id}, null, null, null, "1");
        boolean result = c.moveToFirst();
        c.close();
        getInstance(context).close();
        return(!result);
    }

    public static boolean add_doc(Context context, String site, String doc_id, String data, String template, String status, String sign) {
        Log.d("DB", "Save doc_id = "+doc_id);
        SQLiteDatabase db = getInstance(context).getWritableDatabase();

        // Сначала проверяем нет-ли уже такого документа в локальной базе
        Cursor c = db.query("docs_list", new String[]{"id"}, "site = ? AND doc_id = ?", new String[]{site, doc_id}, null, null, null, "1");
        boolean doc_present = c.moveToFirst();
        c.close();

        if (doc_present)
            Log.d(LOG_TAG, "DOCUMENT PRESENT!");

        boolean result = false;
        if (doc_present) {
            if ((sign != null) && (!sign.equals(""))) {
                set_sign(context, site, doc_id, sign);
            }
            result = true;
        } else {
            ContentValues cv = new ContentValues();
            cv.put("site", site);
            cv.put("doc_id", doc_id);
            cv.put("data", data);
            cv.put("template", template);
            cv.put("t_receive", currentTime());
            if ((status != null) && (!status.equals(""))) {
                cv.put("status", status);
                cv.put("t_set_status", currentTime());
            }
            if ((sign != null) && (!sign.equals(""))) {
                cv.put("sign", sign);
            }

            long row_id = db.insert("docs_list", null, cv);

            Log.d(LOG_TAG, "row inserted, ID = " + row_id);
            result = (row_id != -1);
        }
        getInstance(context).close();
        return (result);
    }

    public static void set_sign(Context context, String site, String doc_id, String sign) {
        Log.d("DB", "Set sign for doc_id = "+doc_id);
        SQLiteDatabase db = getInstance(context).getWritableDatabase();

        ContentValues cv = new ContentValues();
        cv.put("sign", sign);
        cv.put("status", "sign");
        cv.put("t_set_status", currentTime());

        db.update("docs_list", cv, "site = ? AND doc_id = ?", new String[] { site, doc_id });
    }

    public static void set_confirm(Context context, String site, String doc_id) {
        Log.d("DB", "Set confirm for doc_id = "+doc_id);
        SQLiteDatabase db = getInstance(context).getWritableDatabase();

        ContentValues cv = new ContentValues();
        cv.put("t_confirm", currentTime());

        db.update("docs_list", cv, "site = ? AND doc_id = ?", new String[] { site, doc_id });
    }

    public static boolean clear_docs(Context context) {
        SQLiteDatabase db = getInstance(context).getWritableDatabase();
        int delCount = db.delete("docs_list", null, null);
        Log.d(LOG_TAG, "All documents deleted: "+delCount+" docs");
        getInstance(context).close();
        return(true);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(LOG_TAG, "--- onCreate database ---");
        db.execSQL("create table docs_list ("
                + "id integer primary key autoincrement,"
                + "site text,"
                + "doc_id text" + ");");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 2 && newVersion >= 2) {
            db.beginTransaction();
            db.execSQL("alter table docs_list add column data text;");
            db.execSQL("alter table docs_list add column template text;");
            db.execSQL("alter table docs_list add column status text DEFAULT 'new';");
            db.execSQL("alter table docs_list add column t_receive INTEGER;");
            db.execSQL("alter table docs_list add column t_set_status INTEGER;");
            db.execSQL("alter table docs_list add column sign text;");
            db.setTransactionSuccessful();
            db.endTransaction();
        }
        if (oldVersion < 3 && newVersion >= 3) {
            db.beginTransaction();
            db.execSQL("alter table docs_list add column t_confirm INTEGER;");
            db.setTransactionSuccessful();
            db.endTransaction();
        }
    }

    private static long currentTime() {
        return(System.currentTimeMillis());
    }
}
