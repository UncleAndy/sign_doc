package org.gplvote.signdoc;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

/**
 * Created by andy on 14.12.14.
 */
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
        super(context, "SignDoc", null, 1);
    }

    public static boolean is_new(Context context, DocSignRequest doc) {
        SQLiteDatabase db = getInstance(context).getReadableDatabase();

        Cursor c = db.query("docs_list", new String[]{"id"}, "site = ? AND doc_id = ?", new String[]{doc.site, doc.doc_id}, null, null, null, "1");
        boolean result = c.moveToFirst();
        c.close();
        getInstance(context).close();
        return(!result);
    }

    public static boolean add_doc(Context context, String site, String doc_id) {
        Log.d("DB", "Save doc_id = "+doc_id);
        SQLiteDatabase db = getInstance(context).getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("site", site);
        cv.put("doc_id", doc_id);
        long row_id = db.insert("docs_list", null, cv);
        Log.d(LOG_TAG, "row inserted, ID = " + row_id);
        getInstance(context).close();
        return(row_id != -1);
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

    }
}
