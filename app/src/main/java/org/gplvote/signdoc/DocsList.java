package org.gplvote.signdoc;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.zxing.integration.android.IntentIntegrator;
import com.google.zxing.integration.android.IntentResult;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DocsList extends GetPassActivity implements View.OnClickListener, GetPassInterface {
    public static final String APP_PREFERENCES = MainActivity.APP_PREFERENCES;

    private static SharedPreferences sPref;
    public static Settings settings;

    private ListView listDocView;
    private DocsListArrayAdapter sAdapter;

    private Button btnSign;
    private Button btnView;
    private Button btnQRRead;
    private Button btnInfo;

    public static DocsList instance;

    private ProgressDialog receiver_pd;

    public static boolean do_destroy;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.docs_list);

        do_destroy = false;

        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        if (sPref == null) { sPref = getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE); }
        settings = Settings.getInstance(this);
        MainActivity.settings = settings;

        btnSign = (Button) findViewById(R.id.btnDocSign);
        btnSign.setOnClickListener(this);
        btnSign.setVisibility(View.GONE);

        btnView = (Button) findViewById(R.id.btnDocView);
        btnView.setOnClickListener(this);
        btnView.setVisibility(View.GONE);

        btnQRRead = (Button) findViewById(R.id.btnQRRead);
        btnQRRead.setOnClickListener(this);

        /*
        btnRegistration = (Button) findViewById(R.id.btnRegistration);
        btnRegistration.setOnClickListener(this);
        */

        btnInfo = (Button) findViewById(R.id.btnInfo);
        btnInfo.setOnClickListener(this);

        listDocView = (ListView) findViewById(R.id.listDocsView);
        listDocView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        Log.d("MAIN", "onCreate");
        if (instance != null && (MainActivity.sign != null) && MainActivity.sign.pvt_key_present()) update_list();
        instance = this;

        checkPasswordDlgShow();
    }

    @Override
    protected void onDestroy() {
        do_destroy = true;
        super.onDestroy();
        if (isFinishing())
            System.exit(0);
    }

    @Override
    public void onClick(View v) {
        Intent intent;

        switch (v.getId()) {
            /*
            case R.id.btnRegistration:
                if (!(null == MainActivity.sign) && MainActivity.sign.pvt_key_present()) {
                    if (MainActivity.isInternetPresent(this)) {
                        intent = new Intent(this, RegisterSign.class);
                        startActivity(intent);
                    } else {
                        MainActivity.error(getString(R.string.err_internet_connection_absent), this);
                    }
                }
                break;
            */
            case R.id.btnDocSign:
                if (!MainActivity.isInternetPresent(this)) {
                    MainActivity.error(getString(R.string.err_internet_connection_absent), this);
                    return;
                }
            case R.id.btnDocView:
                int curPosition = sAdapter.getCurrentPosition();

                if (curPosition < 0) {
                    return;
                }

                intent = new Intent(this, DoSign.class);

                ArrayList<DocSignRequest> documents = new ArrayList<DocSignRequest>();
                DocSignRequest sign_request = new DocSignRequest();

                HashMap<String, Object> item = (HashMap<String, Object>) listDocView.getItemAtPosition(curPosition);
                sign_request.site = (String) item.get("site");
                sign_request.doc_id = (String) item.get("doc_id");

                DocsStorage dbStorage = DocsStorage.getInstance(this);
                SQLiteDatabase db = dbStorage.getWritableDatabase();

                Cursor c = db.query("docs_list", new String[]{"data", "template", "status", "t_receive", "t_set_status", "t_confirm", "sign_url"}, "site = ? AND doc_id = ?", new String[]{sign_request.site, sign_request.doc_id}, null, null, null, "1");
                if (c != null) {
                    if (c.moveToFirst()) {
                        sign_request.dec_data = c.getString(c.getColumnIndex("data"));
                        sign_request.template = c.getString(c.getColumnIndex("template"));
                        sign_request.sign_url = c.getString(c.getColumnIndex("sign_url"));

                        intent.putExtra("DocView_status", c.getString(c.getColumnIndex("status")));
                        intent.putExtra("DocView_t_create", time_to_string(c.getString(c.getColumnIndex("t_receive"))));
                        intent.putExtra("DocView_t_set_status", time_to_string(c.getString(c.getColumnIndex("t_set_status"))));
                        intent.putExtra("DocView_t_confirm", time_to_string(c.getString(c.getColumnIndex("t_confirm"))));
                    }
                }

                documents.add(sign_request);

                Gson gson = new Gson();
                intent.putExtra("DocsList", gson.toJson(documents));
                intent.putExtra("LastRecvTime", "");

                if (v.getId() == R.id.btnDocView) {
                    intent.putExtra("Command", "ViewDoc");
                } else {
                    intent.putExtra("Command", "SignDoc");
                }

                startActivity(intent);
                break;
            /*
            case R.id.btnDocsListRefresh:
                if (MainActivity.isInternetPresent(this)) {
                    if (!(null == MainActivity.sign) && MainActivity.sign.pvt_key_present()) {
                        if (receiver == null) {
                            receiver = new ReceiverTask();
                            receiver.execute();
                        }
                    }
                } else {
                    MainActivity.error(getString(R.string.err_internet_connection_absent), this);
                }
                break;
            */
            case R.id.btnQRRead:
                IntentIntegrator integrator = new IntentIntegrator(this);
                integrator.initiateScan();
                break;
            case R.id.btnInfo:
                intent = new Intent(this, ShowInfo.class);
                startActivity(intent);
                break;
            default:
                break;
        }

    }

    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        Log.d("QRCODE", "onActivityResult");
        IntentResult scanResult = IntentIntegrator.parseActivityResult(requestCode, resultCode, intent);
        if (scanResult != null) {
            String uri = scanResult.getContents();

            if (uri != null) {
                Log.d("SCAN", "Scan result = " + uri);

                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                startActivity(intent);
            }
        }
    }

    @Override
    public boolean onPassword(String password) {
        Log.d("MAIN", "onPassword");
        boolean result = super.onPassword(password);
        if (result) update_list();

        if (result && MainActivity.sign != null) {
            Log.d("ENCRYPT_TEST", "Start small data...");
            String small_data = "test small data";
            String enc_data = MainActivity.sign.encrypt_base64(small_data, MainActivity.sign.getPublicKeyBase64());
            Log.d("ENCRYPT_TEST", "Encrypted small data = "+enc_data);
            Log.d("ENCRYPT_TEST", "Decrypt small data...");
            byte[] dec_data_bytes = MainActivity.sign.decrypt(enc_data);
            String dec_data = new String(dec_data_bytes);
            Log.d("ENCRYPT_TEST", "Decrypted small data = "+dec_data);
            if (dec_data.equals(small_data)) {
                Log.d("ENCRYPT_TEST", "for small data IS OK");
            } else {
                Log.d("ENCRYPT_TEST", "for small data IS BAD!!!");
            }

            Log.d("ENCRYPT_TEST", "Start big data...");
            String big_data = "test big data\ntest big data\ntest big data\ntest big data\ntest big data\n" +
                    "test big data\ntest big data\ntest big data\ntest big data\ntest big data\n" +
                    "test big data\ntest big data\ntest big data\ntest big data\ntest big data\n" +
                    "test big data\ntest big data\ntest big data\ntest big data\ntest big data\n" +
                    "test big data\ntest big data\ntest big data\ntest big data\ntest big data\n" +
                    "test big data\ntest big data\ntest big data\ntest big data\ntest big data\n" +
                    "test big data\ntest big data\ntest big data\ntest big data\ntest big data\n" +
                    "test big data\ntest big data\ntest big data\ntest big data\ntest big data\n" +
                    "test big data\ntest big data\ntest big data\ntest big data\ntest big data\n" +
                    "test big data\ntest big data\ntest big data\ntest big data\ntest big data\n" +
                    "test big data\ntest big data\ntest big data\ntest big data\ntest big data\n";
            enc_data = MainActivity.sign.encrypt_base64(big_data, MainActivity.sign.getPublicKeyBase64());
            Log.d("ENCRYPT_TEST", "Encrypted big data = "+enc_data);
            Log.d("ENCRYPT_TEST", "Decrypt big data...");
            dec_data = new String(MainActivity.sign.decrypt(enc_data));
            Log.d("ENCRYPT_TEST", "Decrypted big data = "+dec_data);
            if (dec_data.equals(big_data)) {
                Log.d("ENCRYPT_TEST", "for big data IS OK");
            } else {
                Log.d("ENCRYPT_TEST", "for gig data IS BAD!!!");
            }
        }

        return(result);
    }

    public static SharedPreferences getPref() {
        return(sPref);
    }

    private void checkPasswordDlgShow() {
        Log.d("MAIN", "checkPasswordDlgShow");
        if (checkPasswordDlgShow(settings)) update_list();
    }

    public void update_list() {
        ArrayList<Map<String, Object>> list = new ArrayList<Map<String, Object>>(100);
        Map<String, Object> m;

        Log.d("MAIN", "Run update_list");

        btnSign.setVisibility(View.GONE);
        btnView.setVisibility(View.GONE);

        // Заполнение m данными из таблицы документов
        DocsStorage dbStorage = DocsStorage.getInstance(this);
        SQLiteDatabase db = dbStorage.getWritableDatabase();

        Log.d("MAIN", "update_list run query");
        Cursor c = db.query("docs_list", new String[]{"t_set_status", "t_confirm", "status", "site", "doc_id", "sign_url"}, null, null, null, null, "t_receive desc", "100");
        if (c != null) {
            Log.d("MAIN", "update_list p1");
            if (c.moveToFirst()) {
                Log.d("MAIN", "update_list p2");
                do {
                    Log.d("MAIN", "update_list p3");
                    m = new HashMap<String, Object>();

                    m.put("t_set_status", c.getString(c.getColumnIndex("t_set_status")));
                    m.put("t_confirm", c.getString(c.getColumnIndex("t_confirm")));
                    m.put("status", c.getString(c.getColumnIndex("status")));
                    m.put("site", c.getString(c.getColumnIndex("site")));
                    m.put("doc_id", c.getString(c.getColumnIndex("doc_id")));
                    m.put("sign_url", c.getString(c.getColumnIndex("sign_url")));

                    list.add(m);
                } while (c.moveToNext());
            }
        }

        listDocView = (ListView) findViewById(R.id.listDocsView);
        listDocView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);
        sAdapter = new DocsListArrayAdapter(this, list);
        listDocView.setAdapter(sAdapter);

        listDocView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {
                HashMap<String, Object> item = (HashMap<String, Object>) listDocView.getItemAtPosition(position);

                sAdapter.setCurrentPosition(position);
                sAdapter.notifyDataSetChanged();

                // Ставим статус кнопки "Подписать" в зависимости от статуса текущего выбранного документа
                if (item.get("site").toString().startsWith("app:")) {
                    btnSign.setVisibility(View.GONE);
                    btnView.setVisibility(View.VISIBLE);
                } else if (item.get("status").equals("sign")) {
                    btnSign.setVisibility(View.GONE);
                    btnView.setVisibility(View.VISIBLE);
                } else if ((item.get("t_set_status") != null) && (!item.get("t_set_status").equals(""))) {
                    btnSign.setVisibility(View.VISIBLE);
                    btnView.setVisibility(View.VISIBLE);
                } else {
                    btnSign.setVisibility(View.GONE);
                    btnView.setVisibility(View.GONE);
                }
            }
        });
    }

    public class DocsListArrayAdapter extends ArrayAdapter<Map<String, Object>> {
        private final Context context;
        private final List<Map<String, Object>> list;
        private int currentPosition = -1;

        public DocsListArrayAdapter(Context context, List<Map<String, Object>> objects) {
            super(context, R.layout.docs_list_item, objects);
            this.context = context;
            this.list = objects;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView;
            Log.d("MAIN", "Start getView for position = "+position);

            if (convertView == null) {
                LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
                rowView = inflater.inflate(R.layout.docs_list_item, parent, false);
            } else {
                rowView = convertView;
            }

            TextView txtDocTime = (TextView) rowView.findViewById(R.id.txtDocTime);
            TextView txtDocStatus = (TextView) rowView.findViewById(R.id.txtDocStatus);
            TextView txtDocSite = (TextView) rowView.findViewById(R.id.txtDocSite);
            TextView txtDocId = (TextView) rowView.findViewById(R.id.txtDocId);
            LinearLayout llhDocsListRow = (LinearLayout) rowView.findViewById(R.id.llhDocsListRow);

            int bg_color = R.color.white;
            if (list.get(position).get("t_set_status") == null) {
                txtDocTime.setText("- no data -");
                txtDocStatus.setText("- no data -");

                bg_color = R.color.wgray;
            } else {
                // Time parse
                Long doc_time_long = Long.parseLong((String) list.get(position).get("t_set_status"));
                txtDocTime.setText(time_to_string(doc_time_long));

                // Status parse
                String t_confirm = (String) list.get(position).get("t_confirm");
                String status = (String) list.get(position).get("status");
                switch (status) {
                    case "new":
                        status = getString(R.string.txtDocStatusNew);
                        break;
                    case "sign":
                        if ((t_confirm == null) || (t_confirm.equals(""))) {
                            status = getString(R.string.txtDocStatusSigned);
                            bg_color = R.color.wyellow;
                        } else {
                            status = getString(R.string.txtDocStatusConfirmed);
                            bg_color = R.color.wgreen;
                        }
                        break;
                    default:
                        status = getString(R.string.txtDocStatusNotSigned);
                        break;
                }
                txtDocStatus.setText(status);

                // Если документ с урл прямого доступа, подчеркиваем сайт
                String sign_url = (String) list.get(position).get("sign_url");
                if ((sign_url != null) && !sign_url.equals("")) {
                    txtDocSite.setPaintFlags(txtDocSite.getPaintFlags() | Paint.UNDERLINE_TEXT_FLAG);
                }
            }
            llhDocsListRow.setBackgroundColor(getResources().getColor(bg_color));
            txtDocSite.setText((String) list.get(position).get("site"));
            txtDocId.setText((String) list.get(position).get("doc_id"));

            CheckedTextView checkedTextView = (CheckedTextView) rowView.findViewById(R.id.radioDocsListSel);
            if (position == currentPosition) {
                checkedTextView.setChecked(true);
            } else {
                checkedTextView.setChecked(false);
            }

            return rowView;
        }

        public void setCurrentPosition(int position) {
            currentPosition = position;
        }

        public int getCurrentPosition() {
            return(currentPosition);
        }
    }

    private String time_to_string(Long time) {
        if (time == null || time <= 0) return("");

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        return(sdf.format(time));
    }

    private String time_to_string(String time) {
        Long time_long;
        try {
            time_long = Long.parseLong(time);
        } catch (Exception e) {
            time_long = 0L;
        }

        return(time_to_string(time_long));
    }

}
