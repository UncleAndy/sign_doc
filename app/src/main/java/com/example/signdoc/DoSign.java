package com.example.signdoc;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.view.View;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;
import java.util.Collection;

public class DoSign extends FragmentActivity implements View.OnClickListener, GetPassInterface {
    private DocSignRequest document;
    private Collection<String> data;
    private Gson gson;

	@Override
	  protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.do_sign);

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
        DialogFragment dlgPassword = new DlgPassword(this);
        dlgPassword.show(getSupportFragmentManager(), "missiles");
    }

    // PRIVATE

    private void setDoc(String json_doc) {
        document = gson.fromJson(json_doc, DocSignRequest.class);
    }

    private void setData(String json_data) {
        Type collectionType = new TypeToken<Collection<String>>(){}.getType();
        data = gson.fromJson(json_data, collectionType);
    }

    private void showData() {
        // Формируем вывод данных документа в диалог






    }

    private void sign_doc(Sign sign) {
        // Формируем подпись документа (sha256(данные+шаблон)) и отправляем ее на сервер
        DocSign doc_sign = new DocSign();











    }
}
