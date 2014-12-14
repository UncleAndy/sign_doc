package com.example.signdoc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DoSign extends FragmentActivity implements View.OnClickListener, GetPassInterface {
    private class DocsListObject {
        DocSignRequest[] values;
    }

    private DocSignRequest[] documents;
    private int current_doc_idx = 0;
    private Gson gson;

    private ListView listData;
    private TextView textTitleSignDoc;
    private TextView textDocSite;
    private TextView textDocId;
    private Button btnSign;
    private Button btnSignCancel;

	@Override
	  protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.do_sign);


        textTitleSignDoc = (TextView) findViewById(R.id.textTitleSignDoc);
        textDocSite = (TextView) findViewById(R.id.textDocSite);
        textDocId = (TextView) findViewById(R.id.textDocId);

        listData = (ListView) findViewById(R.id.listData);
        btnSign = (Button) findViewById(R.id.btnSign); btnSign.setOnClickListener(this);
        btnSignCancel = (Button) findViewById(R.id.btnSignCancel); btnSignCancel.setOnClickListener(this);

        gson = new Gson();

        String json_docs_list = getIntent().getStringExtra("DocsList");

        setDocs(json_docs_list);

        showData();
    }

    @Override
    public void onPassword(String password) {
        Sign sign = new Sign(password);

        sign_doc(sign);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnSign:
                DialogFragment dlgPassword = new DlgPassword(this);
                dlgPassword.show(getSupportFragmentManager(), "missiles");
                break;
            case R.id.btnSignCancel:
                DocsStorage.add_doc(this.getApplicationContext(), current_document().site, current_document().doc_id);
                show_next_document();
                break;
            default:
                break;
        }
    }

    // PRIVATE

    private void setDocs(String json_doc) {
        DocsListObject doc_list_object;
        doc_list_object = gson.fromJson(json_doc, DocsListObject.class);
        documents = doc_list_object.values;
    }

    private void showData() {
        // Формируем вывод данных документа в диалог
        textTitleSignDoc.setText(getString(R.string.title_sign_doc)+" "+(current_doc_idx+1)+"/"+documents.length);

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

    private void sign_doc(Sign sign) {
        // Формируем подпись документа (sha256(данные+шаблон)) и отправляем ее на сервер
        DocSign doc_sign = new DocSign();

        DocSignRequest doc = current_document();
        String sign_data = doc.site + ":" + doc.doc_id + ":" + doc.dec_data + ":" + doc.template;
        Log.d("SIGN", "Sign data: "+sign_data);

        doc_sign.site = doc.site;
        doc_sign.doc_id = doc.doc_id;
        try {
            doc_sign.sign = sign.createBase64(sign_data.getBytes("UTF-8"));
        } catch (Exception e) {
            Log.e("SIGN", "Wrong password error: ", e);
        }

        if (doc_sign.sign != null) {
            Log.d("SIGN", "Sign: "+doc_sign.sign);
            // Запускаем отправку если все в норме
            Intent intent = new Intent(this, Sender.class);
            intent.putExtra("Doc", doc_sign.toJson());
            startActivity(intent);
            Log.d("SIGN", "Sign doc: "+doc_sign.toJson());

            DocsStorage.add_doc(this.getApplicationContext(), doc_sign.site, doc_sign.doc_id);

            show_next_document();
        } else {
            MainActivity.error(getString(R.string.err_wrong_password), this);
            showData();
        }
    }

    private void show_next_document() {
        if (current_doc_idx < (documents.length - 1)) {
            current_doc_idx++;
            showData();
        } else {
            finish();
        }
    }

    private DocSignRequest current_document() {
        return(documents[current_doc_idx]);
    }
}
