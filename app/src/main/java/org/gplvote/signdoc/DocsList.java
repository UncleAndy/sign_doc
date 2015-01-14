package org.gplvote.signdoc;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.text.format.Time;
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
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.google.gson.Gson;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DocsList extends FragmentActivity implements View.OnClickListener, GetPassInterface {
    public static final String PREF_ENC_PRIVATE_KEY = MainActivity.PREF_ENC_PRIVATE_KEY;
    public static final String PREF_PUBLIC_KEY = MainActivity.PREF_PUBLIC_KEY;
    public static final String APP_PREFERENCES = MainActivity.APP_PREFERENCES;
    public static final String HTTP_SEND_URL = MainActivity.HTTP_SEND_URL;
    public static final String HTTP_GET_URL = MainActivity.HTTP_GET_URL;

    private static SharedPreferences sPref;
    public static Settings settings;

    private ListView listDocView;
    private DocsListArrayAdapter sAdapter;

    private Button btnSign;
    private Button btnView;
    private Button btnRefresh;
    private Button btnRegistration;

    private static DialogFragment dlgPassword = null;

    public static DocsList instance;

    private ReceiverTask receiver;
    private ProgressDialog receiver_pd;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.docs_list);

        instance = this;

        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        if (sPref == null) { sPref = getSharedPreferences(APP_PREFERENCES, MODE_PRIVATE); };
        settings = Settings.getInstance();
        MainActivity.settings = settings;

        btnSign = (Button) findViewById(R.id.btnDocSign);
        btnSign.setOnClickListener(this);
        btnSign.setVisibility(View.GONE);

        btnView = (Button) findViewById(R.id.btnDocView);
        btnView.setOnClickListener(this);
        btnView.setVisibility(View.GONE);

        btnRefresh = (Button) findViewById(R.id.btnDocsListRefresh);
        btnRefresh.setOnClickListener(this);

        btnRegistration = (Button) findViewById(R.id.btnRegistration);
        btnRegistration.setOnClickListener(this);

        listDocView = (ListView) findViewById(R.id.listDocsView);
        listDocView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        checkPasswordDlgShow();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (isFinishing())
            System.exit(0);
    }

    @Override
    public void onClick(View v) {
        Intent intent;

        Log.d("LIST", "onClick id = " + v.getId());
        Log.d("LIST", "onClick R.id.btnDocSign = " + R.id.btnDocSign);

        switch (v.getId()) {
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

                Cursor c = db.query("docs_list", new String[]{"data", "template", "status", "t_receive", "t_set_status", "t_confirm"}, "site = ? AND doc_id = ?", new String[]{sign_request.site, sign_request.doc_id}, null, null, null, "1");
                if (c != null) {
                    if (c.moveToFirst()) {
                        sign_request.dec_data = c.getString(c.getColumnIndex("data"));
                        sign_request.template = c.getString(c.getColumnIndex("template"));

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
                    intent.putExtra("ViewMode", "1");
                }

                startActivity(intent);
                break;
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
            default:
                break;
        }

    }

    @Override
    public boolean onPassword(String password) {
        if (MainActivity.sign == null) {
            MainActivity.sign = new Sign();
        } else {
            MainActivity.sign.cache_reset();
        }
        Log.d("DocsList", "setPassword");
        MainActivity.sign.setPassword(password);
        if (MainActivity.sign.pvt_key_present()) {
            Log.d("DocsList", "pvt_key_present true");
            update_list();
            return(true);
        } else {
            Log.d("DocsList", "Error about wrong password");

            return(false);
        }
    }

    public static SharedPreferences getPref() {
        return(sPref);
    }

    private void checkPasswordDlgShow() {
        if ((!settings.get(PREF_ENC_PRIVATE_KEY).equals("")) && (MainActivity.sign == null || !MainActivity.sign.pvt_key_present())) {
            if (dlgPassword == null)
                dlgPassword = new DlgPassword(this);
            dlgPassword.show(getSupportFragmentManager(), "missiles");
        } else {
            // Initialization
            Intent intent;
            intent = new Intent(this, InitKey.class);
            startActivity(intent);
        }
    }

    public void update_list() {
        ArrayList<Map<String, Object>> list = new ArrayList<Map<String, Object>>(100);
        Map<String, Object> m;

        btnSign.setVisibility(View.GONE);
        btnView.setVisibility(View.GONE);

        // Заполнение m данными из таблицы документов
        DocsStorage dbStorage = DocsStorage.getInstance(this);
        SQLiteDatabase db = dbStorage.getWritableDatabase();

        Cursor c = db.query("docs_list", new String[]{"t_set_status", "t_confirm", "status", "site", "doc_id"}, null, null, null, null, "t_receive desc", "100");
        if (c != null) {
            if (c.moveToFirst()) {
                do {
                    m = new HashMap<String, Object>();

                    m.put("t_set_status", c.getString(c.getColumnIndex("t_set_status")));
                    m.put("t_confirm", c.getString(c.getColumnIndex("t_confirm")));
                    m.put("status", c.getString(c.getColumnIndex("status")));
                    m.put("site", c.getString(c.getColumnIndex("site")));
                    m.put("doc_id", c.getString(c.getColumnIndex("doc_id")));

                    list.add(m);
                } while (c.moveToNext());
            }
        }

        sAdapter = new DocsListArrayAdapter(this, list);
        listDocView.setAdapter(sAdapter);

        listDocView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view,
                                    int position, long id) {

                Log.d("LIST", "itemClick: position = " + position + ", id = "
                        + id);

                HashMap<String, Object> item = (HashMap<String, Object>) listDocView.getItemAtPosition(position);

                Log.d("LIST", "itemClick: item = " + item);

                sAdapter.setCurrentPosition(position);
                sAdapter.notifyDataSetChanged();

                // Ставим статус кнопки "Подписать" в зависимости от статуса текущего выбранного документа
                if (item.get("status").equals("sign")) {
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

    class ReceiverResult {
        String error_str = null;
        ArrayList<DocSignRequest> documents = new ArrayList<DocSignRequest>();
        Long time = null;
    }

    class ReceiverTask extends AsyncTask<Void, Void, ReceiverResult> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            // Показываем Progress
            receiver_pd = new ProgressDialog(DocsList.this);
            receiver_pd.setTitle(getString(R.string.title_get_new_docs));
            receiver_pd.setMessage(getString(R.string.msg_status_received));
            receiver_pd.show();
        }

        @Override
        protected ReceiverResult doInBackground(Void... params) {
            ReceiverResult result = new ReceiverResult();
            String document;
            DocRequestForUser request = new DocRequestForUser();

            // Формируем документ для запроса (публичный ключ, идентификатор публичного ключа, время с которого проверять документы)
            request.public_key = MainActivity.sign.getPublicKeyBase64();
            request.public_key_id = MainActivity.sign.getPublicKeyIdBase64();
            request.sign = MainActivity.sign.createBase64(MainActivity.sign.getPublicKeyId());
            request.from_time = (System.currentTimeMillis() / 1000L) - (24 * 3600); // Проверяем за сутки

            // Если есть последнее время с сервера - с него
            String last_recv_time = MainActivity.settings.get("last_recv_time");
            if ((last_recv_time != null) && (!last_recv_time.equals(""))) {
                try {
                    Log.d("MAIN", "LastRecvTime = ["+last_recv_time+"]");
                    request.from_time = Long.parseLong(last_recv_time);
                } catch (Exception e) {
                    request.from_time = 0L;
                }
            }

            document = request.toJson();

            if (request.sign == null) {
                result.error_str = getString(R.string.err_wrong_password);
            } else {

                HttpClient httpclient = new DefaultHttpClient();
                HttpPost httppost = new HttpPost(HTTP_GET_URL);

                try {
                    // Add your data
                    Log.d("AsyncHTTP", "User request: " + document);
                    StringEntity se = new StringEntity(document);
                    httppost.setEntity(se);
                    httppost.setHeader("Accept", "application/json");
                    httppost.setHeader("Content-type", "application/json");

                    // Execute HTTP Post Request
                    HttpResponse response = httpclient.execute(httppost);

                    String body = EntityUtils.toString(response.getEntity(), "UTF-8");
                    int http_status = response.getStatusLine().getStatusCode();
                    if (http_status == 200) {
                        Gson gson = new Gson();
                        JsonResponse json_resp = gson.fromJson(body, JsonResponse.class);
                        if (json_resp.status == 0) {
                            result.time = json_resp.time;
                            if ((json_resp.documents != null) && (json_resp.documents.length > 0)) {
                                for (int i = 0; i < json_resp.documents.length; i++) {
                                    DocSignRequest doc = json_resp.documents[i];
                                    Log.d("AsyncHTTP", "Request sign document: " + doc.toJson());

                                    // В doc.data сожержится base64(encrypt(json(['val1', 'val2', ...])))

                                    if (doc.type.equals("SIGN_REQUEST")) {
                                        byte[] json_data = MainActivity.sign.decrypt(doc.data);
                                        if (json_data != null) {
                                            doc.dec_data = new String(json_data, "UTF-8");

                                            // NO SECURE: Log.d("AsyncReceiver", "Doc after decode: " + gson.toJson(doc));

                                            // Собираем все новые запросы на подписание в массив
                                            if (DocsStorage.is_new(DocsList.this.getApplicationContext(), doc)) {
                                                result.documents.add(doc);
                                            } else {
                                                Log.d("AsyncReceiver", "Already present - NOT ADD");
                                            }
                                        } else {
                                            Log.e("AsyncDECRYPT", "Can not decrypt data from doc: " + doc.data);
                                        }
                                    } else if (doc.type.equals("SIGN_CONFIRM")) {
                                        result.documents.add(doc);
                                    }
                                }

                                // String json_requests_list = gson.toJson(result.documents);
                                // NO SECURE: Log.d("AsyncDATA", "Json docs list: " + json_requests_list);
                            }
                        } else {
                            result.error_str = getString(R.string.err_http_send);
                            Log.e("AsyncHTTP", "Error HTTP response: " + body);
                        }
                    } else {
                        result.error_str = getString(R.string.msg_status_http_error);
                        Log.e("AsyncHTTP", "HTTP response: " + http_status + " body: " + body);
                    }
                } catch (Exception e) {
                    result.error_str = getString(R.string.msg_status_http_error);
                    Log.e("AsyncHTTP", "Error HTTP request: ", e);
                }
            }

            return(result);
        }

        @Override
        protected void onPostExecute(ReceiverResult result) {
            super.onPostExecute(result);

            if (receiver_pd != null) receiver_pd.hide();
            receiver_pd = null;

            if (result.error_str == null) {
                // Сначала обрабатываем подтверждения об обработке
                boolean confirms_present = false;
                if (result.documents.size() > 0) {
                    for (int i = 0; i < result.documents.size(); i++) {
                        DocSignRequest doc = result.documents.get(i);
                        if (doc.type.equals("SIGN_CONFIRM")) {
                            result.documents.remove(i);
                            i--;

                            Log.d("RECEIVER", "SIGN_CONFIRM document present " + doc.doc_id);
                            DocsStorage.set_confirm(DocsList.this, doc.site, doc.doc_id);

                            confirms_present = true;
                        }
                    }
                }

                if (result.documents.size() > 0) {
                    Gson gson = new Gson();
                    Intent intent = new Intent(DocsList.this, DoSign.class);
                    intent.putExtra("DocsList", gson.toJson(result.documents));
                    intent.putExtra("LastRecvTime", result.time.toString());
                    startActivity(intent);
                } else if (confirms_present) {
                    MainActivity.settings.set("last_recv_time", result.time.toString());
                    DocsList.this.update_list();
                } else {
                    MainActivity.settings.set("last_recv_time", result.time.toString());
                    MainActivity.alert(getString(R.string.msg_status_no_new), DocsList.this);
                }
            } else {
                MainActivity.error(result.error_str, DocsList.this);
            }

            receiver = null;
        }
    }

}
