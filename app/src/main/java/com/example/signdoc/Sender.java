package com.example.signdoc;

import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.google.gson.Gson;

import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

/**
 * Created by andy on 12.12.14.
 */
public class Sender extends LinkStatus {
    public static final String HTTP_SEND_URL = MainActivity.HTTP_SEND_URL;

    private String document;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        document = getIntent().getStringExtra("Doc");
        Log.e("HTTP", "Document: "+document);

        deliver();
    }


    private boolean deliver() {
        txtStatus.setText(R.string.msg_status_start_deliver);

        boolean success = true;

        // Инициируется процесс отправки документа
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost(HTTP_SEND_URL);

        try {
            // Add your data
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
                    txtStatus.setText(R.string.msg_status_delivered);
                    MainActivity.alert(getString(R.string.msg_status_delivered), this);
                } else {
                    txtStatus.setText(R.string.msg_status_http_error);
                    MainActivity.error(getString(R.string.err_http_send), this);
                    Log.e("HTTP", "Error HTTP response: " + body);
                    success = false;
                }
            } else {
                txtStatus.setText(R.string.msg_status_http_error);
                MainActivity.error(getString(R.string.msg_status_http_error), this);
                Log.e("HTTP", "HTTP response: "+http_status+" body: "+body);
                success = false;
            }
        } catch (Exception e) {
            txtStatus.setText(R.string.msg_status_http_error);
            MainActivity.error(getString(R.string.msg_status_http_error), this);
            Log.e("HTTP", "Error HTTP request: ", e);
            success = false;
        };

        prLinkStatus.setVisibility(View.INVISIBLE);
        return(success);
    }


}
