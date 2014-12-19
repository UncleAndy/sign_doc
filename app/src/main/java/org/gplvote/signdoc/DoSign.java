package org.gplvote.signdoc;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class DoSign extends FragmentActivity implements View.OnClickListener {
    private class DocsListObject {
        DocSignRequest[] values;
    }

    private static Settings settings;
    ArrayList<DocSignRequest> documents;
    private int current_doc_idx = 0;
    private Gson gson;
    private Long last_recv_time = null;

    private ListView listData;
    private TextView textTitleSignDoc;
    private TextView textDocSite;
    private TextView textDocId;
    private Button btnSign;
    private Button btnSignCancel;

    private ProgressDialog send_pd;
    private DoSignTask send_task;

	@Override
	  protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.do_sign);

        settings = Settings.getInstance();

        textTitleSignDoc = (TextView) findViewById(R.id.textTitleSignDoc);
        textDocSite = (TextView) findViewById(R.id.textDocSite);
        textDocId = (TextView) findViewById(R.id.textDocId);

        listData = (ListView) findViewById(R.id.listData);
        btnSign = (Button) findViewById(R.id.btnSign); btnSign.setOnClickListener(this);
        btnSignCancel = (Button) findViewById(R.id.btnSignCancel); btnSignCancel.setOnClickListener(this);

        gson = new Gson();

        String sLastRecvTime = getIntent().getStringExtra("LastRecvTime");
        if (sLastRecvTime != null && (!sLastRecvTime.equals(""))) {
            try {
                Log.d("DOSIGN", "LastRecvTime = ["+sLastRecvTime+"]");
                last_recv_time = Long.parseLong(sLastRecvTime);
            } catch (Exception e) {
                Log.e("DOSIGN", "Long parse error: "+e.getLocalizedMessage());
                last_recv_time = 0L;
            }
        }

        String json_docs_list = getIntent().getStringExtra("DocsList");

        setDocs(json_docs_list);

        showData();
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnSign:
                if (send_task == null) {
                    send_task = new DoSignTask();
                    send_task.execute(current_document());
                }
                break;
            case R.id.btnSignCancel:
                getCancelConfirmation ();
                break;
            default:
                break;
        }
    }

    private void getCancelConfirmation () {
        DialogInterface.OnClickListener dialogClickListener = new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                switch (which) {
                    case DialogInterface.BUTTON_POSITIVE:
                        DocsStorage.add_doc(getApplicationContext(), current_document().site, current_document().doc_id);
                        if (is_last_document()) {
                            // Сохраняем серверное время текущего запроса
                            if (last_recv_time != null)
                                settings.set("last_recv_time", last_recv_time.toString());

                            finish();
                        } else {
                            show_next_document();
                        }
                        break;
                    case DialogInterface.BUTTON_NEGATIVE:
                        break;
                }
            }
        };

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.title_need_confirm)
                .setIcon(R.drawable.confirm)
                .setMessage(getString(R.string.msg_cansel_sure))
                .setPositiveButton(getString(R.string.btn_cancel_yes), dialogClickListener)
                .setNegativeButton(getString(R.string.btn_no), dialogClickListener).show();
    }


    private void setDocs(String json_doc) {
        ArrayList<DocSignRequest> doc_list_object;
        documents = gson.fromJson(json_doc, new TypeToken<ArrayList<DocSignRequest>>(){}.getType());
    }

    private void showData() {
        // Формируем вывод данных документа в диалог
        textTitleSignDoc.setText(getString(R.string.title_sign_doc)+" "+(current_doc_idx+1)+"/"+documents.size());

        DocSignRequest document = current_document();

        textDocSite.setText(document.site);
        textDocId.setText(document.doc_id);

        String[] data = gson.fromJson(document.dec_data, String[].class);

        String[] tpl_lines = document.template.split("\n");

        switch (tpl_lines[0]) {
            case "LIST":
                ArrayList<Map<String, Object>> list = new ArrayList<Map<String, Object>>(data.length);
                Map<String, Object> m;
                for (int i = 0; i < data.length; i++) {
                    m = new HashMap<String, Object>();
                    m.put("title", tpl_lines[i+1]+":");
                    m.put("text", data[i]);
                    list.add(m);
                }

                String[] attrs = {"title", "text"};
                int[] to = { R.id.textDocDataTitle, R.id.textDocDataText };

                SimpleAdapter sAdapter = new SimpleAdapter(this, list, R.layout.doc_data_line, attrs, to);

                listData.setAdapter(sAdapter);

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

            doc_sign.site = doc.site;
            doc_sign.doc_id = doc.doc_id;
            try {
                doc_sign.sign = MainActivity.sign.createBase64(sign_data.getBytes("UTF-8"));
            } catch (Exception e) {
                Log.e("SIGN", "Wrong password error: ", e);
            }

            if (doc_sign.sign != null) {
                result = HTTPActions.deliver(doc_sign.toJson(), DoSign.this);

                DocsStorage.add_doc(DoSign.this.getApplicationContext(), doc_sign.site, doc_sign.doc_id);
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
                MainActivity.alert(getString(R.string.msg_status_delivered), DoSign.this, is_last_document());
                if (!is_last_document())
                    show_next_document();
                else if (last_recv_time != null)
                    // Сохраняем серверное время текущего запроса
                    settings.set("last_recv_time", last_recv_time.toString());
            } else {
                MainActivity.error(result, DoSign.this);
            }

            send_task = null;
        }
    }
}
