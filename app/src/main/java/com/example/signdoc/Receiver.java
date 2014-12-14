package com.example.signdoc;

import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.View;

import com.google.gson.Gson;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;

import java.util.ArrayList;

/**
 * Created by andy on 12.12.14.
 */
public class Receiver extends LinkStatus implements GetPassInterface{
    public static final String HTTP_GET_URL = MainActivity.HTTP_GET_URL;

    private static Settings settings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        settings = Settings.getInstance();

        DialogFragment dlgPassword = new DlgPassword(this);
        dlgPassword.show(getSupportFragmentManager(), "missiles");
    }

    @Override
    public void onPassword(String password) {
        Sign sign = new Sign(password);
        receive(sign);
    }

    private void receive(Sign sign) {
        String document;
        DocRequestForUser request = new DocRequestForUser();
        ArrayList<DocSignRequest> requests_list = new ArrayList<>();

        txtStatus.setText(R.string.msg_status_start_deliver);

        // Формируем документ для запроса (публичный ключ, идентификатор публичного ключа, время с которого проверять документы)
        request.public_key = sign.getPublicKeyBase64();
        request.public_key_id = sign.getPublicKeyIdBase64();
        request.sign = sign.createBase64(sign.getPublicKeyId());
        request.from_time = (System.currentTimeMillis() / 1000L) - (24 * 3600); // Проверяем за сутки
        document = request.toJson();

        if (request.sign == null) {
            txtStatus.setText(R.string.err_wrong_password);
            MainActivity.error(getString(R.string.err_wrong_password), this);
        } else {

            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost(HTTP_GET_URL);

            try {
                // Add your data
                Log.d("HTTP", "User request: " + document);
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
                        // Загружаем список документов и в цикле вдыаем Activity для запроса подписи
                        if ((json_resp.documents != null) && (json_resp.documents.length > 0)) {
                            for (int i = 0; i < json_resp.documents.length; i++) {
                                DocSignRequest doc = json_resp.documents[i];
                                Log.d("HTTP", "Request sign document: " + doc.toJson());

                                // В doc.data сожержится base64(encrypt(json(['val1', 'val2', ...])))

                                byte[] json_data = sign.decrypt(doc.data);
                                Log.d("DATA", "Data decrypted bytes: " + json_data.length);
                                if (json_data != null) {
                                    doc.dec_data = new String(json_data, "UTF-8");

                                    // Документ и расшифрованные данные будут передаваться в другой Activity в виде строк
                                    // Поэтому конвертацию данных в список переносим в другой Activity, который будет заниматься их показом
                                    // Type collectionType = new TypeToken<Collection<String>>(){}.getType();
                                    // Collection<String> data_collection = gson.fromJson(json_data_str, collectionType);

                                    Log.d("DATA", "Data decrypted array: " + doc.dec_data);

                                    // Собираем все новые запросы на подписание в массив
                                    if (DocsStorage.is_new(this.getApplicationContext(), doc)) {
                                        requests_list.add(doc);
                                    }
                                } else {
                                    Log.e("DECRYPT", "Can not decrypt data from doc: "+doc.data);
                                }
                            }
                            if (requests_list.size() > 0) {
                                txtStatus.setText(R.string.msg_status_received);

                                String json_requests_list = gson.toJson(new JSONArray(requests_list));
                                Log.d("DATA", "Json docs list: " + json_requests_list);

                                Intent intent = new Intent(this, DoSign.class);
                                intent.putExtra("DocsList", json_requests_list);
                                startActivity(intent);
                            } else {
                                txtStatus.setText(R.string.msg_status_no_new);
                            }
                        } else {
                            txtStatus.setText(R.string.msg_status_no_new);
                            MainActivity.alert(getString(R.string.msg_status_no_new), this);
                        }
                    } else {
                        txtStatus.setText(R.string.msg_status_http_error);
                        MainActivity.error(getString(R.string.err_http_send), this);
                        Log.e("HTTP", "Error HTTP response: " + body);
                    }
                } else {
                    txtStatus.setText(R.string.msg_status_http_error);
                    MainActivity.error(getString(R.string.msg_status_http_error), this);
                    Log.e("HTTP", "HTTP response: " + http_status + " body: " + body);
                }
            } catch (Exception e) {
                txtStatus.setText(R.string.msg_status_http_error);
                MainActivity.error(getString(R.string.msg_status_http_error), this);
                Log.e("HTTP", "Error HTTP request: ", e);
            }
        }

        prLinkStatus.setVisibility(View.INVISIBLE);
    }
}
