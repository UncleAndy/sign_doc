package com.example.signdoc;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.View;
import android.widget.ListView;
import android.widget.SimpleAdapter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DoSign extends FragmentActivity implements View.OnClickListener, GetPassInterface {
    private DocSignRequest document;
    private String[] data;
    private Gson gson;

    private ListView listData;

	@Override
	  protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.do_sign);

        listData = (ListView) findViewById(R.id.listData);

        gson = new Gson();

        String json_doc = getIntent().getStringExtra("Doc");
        String json_data = getIntent().getStringExtra("Data");

        setDoc(json_doc);
        setData(json_data);

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
                finish();
                break;
            default:
                break;
        }
    }

    // PRIVATE

    private void setDoc(String json_doc) {
        document = gson.fromJson(json_doc, DocSignRequest.class);
    }

    private void setData(String json_data) {
        data = gson.fromJson(json_data, String[].class);
    }

    private void showData() {
        // Формируем вывод данных документа в диалог
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











    }
}
