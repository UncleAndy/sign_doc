package org.gplvote.signdoc;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.StrictMode;
import android.support.v4.app.FragmentActivity;
import android.text.Html;
import android.text.Layout;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;

public class DoSign extends GetPassActivity implements View.OnClickListener {
    private class DocsListObject {
        DocSignRequest[] values;
    }

    private static Settings settings;
    ArrayList<DocSignRequest> documents;
    ArrayList<DocSignRequest> documents_for_post_decode;
    private int current_doc_idx = 0;
    private Gson gson;
    private Long last_recv_time = null;

    private TextView textTitleSignDoc;
    private TextView textDocSite;
    private TextView textDocId;
    private TextView textHtmlDoc;

    private TextView txtViewDocStatus;
    private TextView txtViewDocStatusTime;
    private TextView txtViewDocCreateTime;
    private TextView txtViewDocConfirmTime;

    private Button btnSign;
    private Button btnSignCancel;
    private Button btnBack;
    private TableLayout tblViewInfo;
    private LinearLayout llyForBack;

    private ProgressDialog send_pd;
    private DoSignTask send_task;

    private ArrayList<DocSign> sign_docs;

    private boolean view_mode = false;

	@Override
	  protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);

        if (android.os.Build.VERSION.SDK_INT > 9) {
            StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
            StrictMode.setThreadPolicy(policy);
        }

        gson = new Gson();

        String command = getIntent().getStringExtra("Command");

        if ((command != null) && (command.equals("SendConfirms"))) {
            String json_confirms_list = getIntent().getStringExtra("DocsList");
            setDocs(json_confirms_list);

            for (int i = 0; i < documents.size(); i++) {
                DocSignRequest doc = documents.get(i);

                if (doc.type.equals("SIGN_CONFIRM")) {
                    Log.d("APP:SIGN_CONFIRM", "SIGN_CONFIRM document present " + doc.doc_id);
                    DocsStorage.set_confirm(DoSign.this, doc.site, doc.doc_id);
                }
            }

            finish();
            return;
        }

        if ((command != null) && (command.equals("GetPublicKeyId"))) {
            if (MainActivity.sign == null) {
                MainActivity.sign = new Sign(this);
            }

            Intent intent = new Intent();
            intent.putExtra("PUBLIC_KEY_ID", MainActivity.sign.getPublicKeyIdBase64());
            intent.putExtra("PUBLIC_KEY", MainActivity.sign.getPublicKeyBase64());
            intent.putExtra("CANCEL_PUBLIC_KEY_ID", MainActivity.sign.getCancelPublicKeyIdBase64());
            intent.putExtra("CANCEL_PUBLIC_KEY", MainActivity.sign.getCancelPublicKeyBase64());
            setResult(RESULT_OK, intent);
            finish();
            return;
        }

        setContentView(R.layout.do_sign);

        settings = Settings.getInstance(this);

        textTitleSignDoc = (TextView) findViewById(R.id.textTitleSignDoc);
        textDocSite = (TextView) findViewById(R.id.textDocSite);
        textDocId = (TextView) findViewById(R.id.textDocId);

        llyForBack = (LinearLayout) findViewById(R.id.llyForBack);

        tblViewInfo = (TableLayout) findViewById(R.id.tblViewInfo);
        txtViewDocStatus = (TextView) findViewById(R.id.txtViewDocStatus);
        txtViewDocStatusTime = (TextView) findViewById(R.id.txtViewDocStatusTime);
        txtViewDocCreateTime = (TextView) findViewById(R.id.txtViewDocCreateTime);
        txtViewDocConfirmTime = (TextView) findViewById(R.id.txtViewDocConfirmTime);

        textHtmlDoc = (TextView) findViewById(R.id.textHtmlDoc);
        btnSign = (Button) findViewById(R.id.btnSign);
        btnSign.setOnClickListener(this);
        btnSignCancel = (Button) findViewById(R.id.btnSignCancel);
        btnSignCancel.setOnClickListener(this);
        btnBack = (Button) findViewById(R.id.btnViewDocBack);
        btnBack.setOnClickListener(this);

        sign_docs = new ArrayList<DocSign>();

        String sLastRecvTime = getIntent().getStringExtra("LastRecvTime");
        if (sLastRecvTime != null && (!sLastRecvTime.equals(""))) {
            try {
                Log.d("DOSIGN", "LastRecvTime = ["+sLastRecvTime+"]");
                last_recv_time = Long.parseLong(sLastRecvTime);
            } catch (Exception e) {
                Log.e("DOSIGN", "Long parse error: "+e.getLocalizedMessage());
                last_recv_time = 0L;
            }
        } else {
            last_recv_time = null;
        }

        view_mode = (command != null && command.equals("ViewDoc"));
        if (view_mode) {
            view_mode = true;
            btnSign.setVisibility(View.GONE);
            btnSignCancel.setVisibility(View.GONE);
            tblViewInfo.setVisibility(View.VISIBLE);
            llyForBack.setVisibility(View.VISIBLE);
        } else {
            tblViewInfo.setVisibility(View.GONE);
            llyForBack.setVisibility(View.GONE);
        }

        String json_docs_list = getIntent().getStringExtra("DocsList");
        if (json_docs_list != null && !json_docs_list.equals("")) {
            setDocs(json_docs_list);

            checkPasswordDlgShow(settings);

            showData();
        } else {
            // Проверяем не передан-ли документ через URL
            Intent i = getIntent();
            Uri d = null;
            if (i != null) d = i.getData();

            if (d != null && d.getScheme().equals("signdoc")) {
                Log.d("DOSIGN", "Sign doc URL = "+d.toString());

                if (!MainActivity.isInternetPresent(this)) {
                    MainActivity.error(getString(R.string.err_internet_connection_absent), this, true);
                    return;
                }

                HttpClient httpclient = new DefaultHttpClient();

                String url_query = d.getQuery();
                String host = d.getHost().replaceAll("_", "-");
                int port = d.getPort();
                String port_str = "";
                if (port != 80 && port > 0) {
                    port_str = ":" + Integer.toString(port);
                }

                Log.d("DOSIGN", "Get doc URL = "+"http://"+host+port_str+d.getPath()+"?"+url_query);
                HttpGet httpget = null;
                try {
                    httpget = new HttpGet(new URI("http://"+host+port_str+d.getPath()+"?"+url_query));
                } catch (URISyntaxException e) {
                    e.printStackTrace();
                }

                try {
                    // Execute HTTP Post Request
                    HttpResponse response = httpclient.execute(httpget);

                    String body = EntityUtils.toString(response.getEntity(), "UTF-8");
                    json_docs_list = "["+body+"]";
                    Log.d("DOSIGN", "Json docs list = "+json_docs_list);
                    int http_status = response.getStatusLine().getStatusCode();
                    if (http_status == 200) {
                        Log.d("DOSIGN", "Set docs from "+json_docs_list);
                        setDocsForPostDecode(json_docs_list);

                        if ((MainActivity.sign != null) && MainActivity.sign.pvt_key_present()) {
                            processDocsForPostDecode();
                        } else {
                            checkPasswordDlgShow(settings);
                        }
                    } else {
                        Log.e("AsyncHTTP", "Error HTTP response status "+http_status);
                        finish();
                        return;
                    }
                } catch (Exception e) {
                    Log.e("AsyncHTTP", "Error HTTP request: ", e);
                    finish();
                    return;
                }
            }
        }
    }

    @Override
    public void onClick(View v) {
        boolean app_sign_mode = current_document().site.substring(0, 4).equals("app:");
        switch (v.getId()) {
            case R.id.btnSign:
                if (!app_sign_mode && !MainActivity.isInternetPresent(this)) {
                    MainActivity.error(getString(R.string.err_internet_connection_absent), this);
                    return;
                }
                if (app_sign_mode) {
                    Log.d("DOSIGN", "Sign in APP MODE");

                    // Подписание в режиме приложения
                    DocSignRequest doc = current_document();
                    DocSign doc_sign = new DocSign();
                    doc_sign.site = doc.site;
                    doc_sign.doc_id = doc.doc_id;

                    String sign_data = doc.site + ":" + doc.doc_id + ":" + doc.dec_data + ":" + doc.template;

                    Log.d("DOSIGN", "Signed data: "+sign_data);

                    try {
                        doc_sign.sign = MainActivity.sign.createBase64(sign_data.getBytes("UTF-8"));
                        sign_docs.add(doc_sign);
                        DocsStorage.add_doc(DoSign.this.getApplicationContext(), doc_sign.site, doc_sign.doc_id, doc.dec_data, doc.template, "sign", doc_sign.sign, doc.sign_url);
                    } catch (UnsupportedEncodingException e) {
                        MainActivity.error(getString(R.string.err_wrong_password), this);
                        e.printStackTrace();
                    }

                    appModeNextDocProcess();
                } else {
                    if (send_task == null) {
                        send_task = new DoSignTask();
                        send_task.execute(current_document());
                    }
                }
                break;
            case R.id.btnSignCancel:
                DocsStorage.add_doc(getApplicationContext(), current_document().site, current_document().doc_id, current_document().dec_data, current_document().template, "cancel", "", current_document().sign_url);
                if (app_sign_mode) {
                    appModeNextDocProcess();
                } else {
                    if (is_last_document()) {
                        // Сохраняем серверное время текущего запроса
                        if (!app_sign_mode && last_recv_time != null)
                            settings.set("last_recv_time", last_recv_time.toString());

                        finish();
                        if (DocsList.instance != null) DocsList.instance.update_list();
                    } else {
                        show_next_document();
                    }
                }
                break;
            case R.id.btnViewDocBack:
                finish();
                break;
            default:
                break;
        }
    }

    private void appModeNextDocProcess() {
        if (is_last_document()) {
            Intent intent = new Intent();
            intent.putExtra("SIGNS", gson.toJson(sign_docs));
            setResult(RESULT_OK, intent);
            finish();
        } else {
            show_next_document();
        }
    }

    private void setDocs(String json_doc) {
        documents = gson.fromJson(json_doc, new TypeToken<ArrayList<DocSignRequest>>(){}.getType());
    }

    private void setDocsForPostDecode(String json_doc) {
        documents_for_post_decode = gson.fromJson(json_doc, new TypeToken<ArrayList<DocSignRequest>>() {
        }.getType());
    }

    private void processDocsForPostDecode() {
        Log.d("DOSIGN", "processDocsForPostDecode for public_key_id = "+MainActivity.sign.getPublicKeyIdBase64());
        if ((documents_for_post_decode != null) && (documents_for_post_decode.size() > 0)) {
            Log.d("DOSIGN", "processDocsForPostDecode docs present");
            boolean is_new_docs = false;
            for(int i = 0; i < documents_for_post_decode.size(); i++) {
                DocSignRequest doc = documents_for_post_decode.get(i);
                Log.d("DOSIGN", "processDocsForPostDecode decrypt data = "+doc.data);

                if (doc.dec_data != null && !doc.dec_data.equals("") && (DocsStorage.is_new(DoSign.this.getApplicationContext(), doc) || DocsStorage.is_not_signed(DoSign.this.getApplicationContext(), doc))) {
                    Log.d("DOSIGN", "processDocsForPostDecode decrypted data present");
                    if (documents == null) documents = new ArrayList<DocSignRequest>();
                    documents.add(doc);
                    is_new_docs = true;
                } else {
                    byte[] json_data = MainActivity.sign.decrypt(doc.data);
                    if (json_data != null) {
                        try {
                            doc.dec_data = new String(json_data, "UTF-8");
                            Log.d("DOSIGN", "processDocsForPostDecode decrypted = " + doc.dec_data);
                        } catch (UnsupportedEncodingException e) {
                            e.printStackTrace();
                        }

                        // Собираем все новые запросы на подписание в массив
                        Log.d("DOSIGN", "processDocsForPostDecode check for new doc");
                        if ((doc.dec_data != null) && !doc.dec_data.equals("") && (DocsStorage.is_new(DoSign.this.getApplicationContext(), doc) || DocsStorage.is_not_signed(DoSign.this.getApplicationContext(), doc))) {
                            Log.d("DOSIGN", "processDocsForPostDecode add doc in list");
                            if (documents == null) documents = new ArrayList<DocSignRequest>();
                            documents.add(doc);
                            is_new_docs = true;
                        } else {
                            if (doc.dec_data == null || doc.dec_data.equals(""))
                                Log.d("DOSIGN", "processDocsForPostDecode no dec_data in document");
                            if (!DocsStorage.is_new(DoSign.this.getApplicationContext(), doc))
                                Log.d("DOSIGN", "processDocsForPostDecode doc already in DB");
                        }
                    }
                }
            }
            if (is_new_docs) {
                Log.d("DOSIGN", "processDocsForPostDecode show data");
                showData();
            } else {
                MainActivity.alert(getString(R.string.err_document_already_signed), DoSign.this, true);
            }
        }
    }

    @Override
    public boolean onPassword(String password) {
        boolean result;
        if (result = super.onPassword(password)) {
            processDocsForPostDecode();
        }
        return(result);
    }

    private void showData() {
        // Формируем вывод данных документа в диалог
        DocSignRequest document = current_document();

        if (view_mode) {
            textTitleSignDoc.setText(R.string.title_view_doc);

            txtViewDocCreateTime.setText(getIntent().getStringExtra("DocView_t_create"));
            txtViewDocStatusTime.setText(getIntent().getStringExtra("DocView_t_set_status"));
            txtViewDocConfirmTime.setText(getIntent().getStringExtra("DocView_t_confirm"));

            if (getIntent().getStringExtra("DocView_t_confirm").equals("")) {
                if (getIntent().getStringExtra("DocView_status").equals("sign")) {
                    txtViewDocStatus.setText(R.string.txtDocStatusSigned);
                } else {
                    txtViewDocStatus.setText(R.string.txtDocStatusNotSigned);
                }
            } else {
                txtViewDocStatus.setText(R.string.txtDocStatusConfirmed);
            }
        } else if (document != null) {
            textTitleSignDoc.setText(getString(R.string.title_sign_doc) + " " + (current_doc_idx + 1) + "/" + documents.size());
        }


        if (document == null) {
            MainActivity.alert(getString(R.string.err_document_bad_format), DoSign.this, true);
            Log.d("DOSIGN", "Document absent");
            return;
        } else if ((document.dec_data == null) || (document.template == null)) {
            MainActivity.alert(getString(R.string.err_document_bad_format), DoSign.this, is_last_document());
            Log.d("DOSIGN", "Data or template absent");
            show_next_document();
            return;
        }

        textDocSite.setText(document.site);
        textDocId.setText(document.doc_id);

        String[] data = gson.fromJson(document.dec_data, String[].class);

        String[] tpl_lines = document.template.split("\n");

        String html = "";
        switch (tpl_lines[0]) {
            case "LIST":
                for (int i = 0; i < data.length; i++) {
                    if ((i+1) < tpl_lines.length) {
                        String title = tpl_lines[i + 1];
                        title = title.replaceAll("/<a [^>]+>/", "");
                        title = title.replaceAll("/<a.*>/", "");
                        html += "<p><b>"+title+"</b><br>";
                    }
                    else
                        html += "<p><b>"+getString(R.string.msg_no_data_description)+"</b><br>";

                    String data_row = data[i].replace("\n", "<br>");
                    data_row = data_row.replaceAll("<a [^>]+>", "");
                    data_row = data_row.replaceAll("</a.*>", "");
                    html += data_row+"</p><hr>";
                }
                textHtmlDoc.setText(Html.fromHtml(html));
                break;
            case "HTML":
                tpl_lines[0] = "";
                html = TextUtils.join("\n", tpl_lines);
                String plain_data = "";
                for (int i = 0; i < data.length; i++) {
                    String data_row = data[i].replace("\n", "<br>");
                    data_row = data_row.replaceAll("<a [^>]+>", "");
                    data_row = data_row.replaceAll("</a.*>", "");
                    String old_html = html;
                    html = html.replace("<%data_"+i+"%>", data_row);
                    if (html.equals(old_html))
                        plain_data += "["+i+"]: "+data_row+"<br>\n";
                }
                // Все не учтенные данные указываем в первой строке
                if (!plain_data.equals("")) {
                    html = "<p><b>"+getString(R.string.msg_no_data_description)+"<br>"+ plain_data + "</b>---</p>" + html;
                }
                textHtmlDoc.setText(Html.fromHtml(html));
                break;
        }
    }

    private void show_next_document() {
        if (!is_last_document()) {
            current_doc_idx++;
            showData();
        }
    }

    private boolean is_last_document() {
        return(current_doc_idx >= (documents.size() - 1));
    }

    private DocSignRequest current_document() {
        if (documents == null) return(null);
        return(documents.get(current_doc_idx));
    }

    class DoSignTask extends AsyncTask<DocSignRequest, Void, String> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            // Показываем Progress
            send_pd = new ProgressDialog(DoSign.this);
            send_pd.setTitle(getString(R.string.title_send));
            send_pd.setMessage(getString(R.string.msg_status_start_deliver));
            send_pd.show();
        }

        @Override
        protected String doInBackground(DocSignRequest... params) {
            // Формируем подпись документа и отправляем ее на сервер
            String result = null;

            DocSign doc_sign = new DocSign();

            DocSignRequest doc = params[0];
            String sign_data = doc.site + ":" + doc.doc_id + ":" + doc.dec_data + ":" + doc.template;

            Log.d("DOSIGN", "Signed data: "+sign_data);

            doc_sign.site = doc.site;
            doc_sign.doc_id = doc.doc_id;
            if (doc.user_key_id == null || doc.user_key_id.equals("")) {
                Log.d("SIGN", "Public doc - add public key in sign");
                doc_sign.public_key = MainActivity.sign.getPublicKeyBase64();
            }
            try {
                doc_sign.sign = MainActivity.sign.createBase64(sign_data.getBytes("UTF-8"));
            } catch (Exception e) {
                Log.e("SIGN", "Wrong password error: ", e);
            }

            if (doc_sign.sign != null) {
                DocsStorage.add_doc(DoSign.this.getApplicationContext(), doc_sign.site, doc_sign.doc_id, doc.dec_data, doc.template, "sign_deliver", "", doc.sign_url);
                result = HTTPActions.deliver(doc_sign.toJson(), DoSign.this, doc.sign_url);
                if (((result == null) || result.equals("")) && ((doc.sign_url != null) && !doc.sign_url.equals("")))
                    DocsStorage.set_confirm(DoSign.this, doc.site, doc.doc_id);
            } else {
                result = getString(R.string.err_wrong_password);
            }

            return(result);
        }

        @Override
        protected void onPostExecute(String result) {
            super.onPostExecute(result);

            send_pd.dismiss();

            if (result == null) {
                if (DoSign.this.last_recv_time == null) {
                    if (DocsList.instance != null) DocsList.instance.update_list();
                }
                if (!is_last_document()) {
                    show_next_document();
                } else {
                    if (last_recv_time != null) {
                        // Сохраняем серверное время текущего запроса
                        settings.set("last_recv_time", last_recv_time.toString());
                        DocsList.instance.update_list();
                    }
                    DoSign.this.finish();
                }
            } else {
                MainActivity.error(result, DoSign.this);
            }

            send_task = null;
        }
    }
}
