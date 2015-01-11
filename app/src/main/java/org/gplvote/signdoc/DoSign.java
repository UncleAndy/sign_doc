package org.gplvote.signdoc;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.text.Html;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;

public class DoSign extends FragmentActivity implements View.OnClickListener {
    private class DocsListObject {
        DocSignRequest[] values;
    }

    private static Settings settings;
    ArrayList<DocSignRequest> documents;
    private int current_doc_idx = 0;
    private Gson gson;
    private Long last_recv_time = null;

    private TextView textTitleSignDoc;
    private TextView textDocSite;
    private TextView textDocId;
    private TextView textHtmlDoc;
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

        textHtmlDoc = (TextView) findViewById(R.id.textHtmlDoc);
        btnSign = (Button) findViewById(R.id.btnSign);
        btnSign.setOnClickListener(this);
        btnSignCancel = (Button) findViewById(R.id.btnSignCancel);
        btnSignCancel.setOnClickListener(this);

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
                        DocsStorage.add_doc(getApplicationContext(), current_document().site, current_document().doc_id, current_document().dec_data, current_document().template, "cancel", null);
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

                DocsStorage.add_doc(DoSign.this.getApplicationContext(), doc_sign.site, doc_sign.doc_id, doc.dec_data, doc.template, "sign", doc_sign.sign);
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
