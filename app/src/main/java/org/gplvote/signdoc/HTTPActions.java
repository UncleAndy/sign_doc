package org.gplvote.signdoc;

import android.app.Activity;
import android.content.Context;
import android.util.Log;

import com.google.gson.Gson;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class HTTPActions {
    public static final String HTTP_SEND_URL = MainActivity.HTTP_SEND_URL;

    public static void deliver(String document, Activity activity, boolean close_parent) {
        // txtStatus.setText(R.string.msg_status_start_deliver);

        // Инициируется процесс отправки документа
        HttpClient httpclient = new DefaultHttpClient();
        HttpPost httppost = new HttpPost(HTTP_SEND_URL);

        try {
            // Add your data
            Log.d("HTTP", "DBG1 "+document);
        StringEntity se = null;
        se = new StringEntity(document);
        httppost.setEntity(se);
            httppost.setHeader("Accept", "application/json");
            httppost.setHeader("Content-type", "application/json");

            // Execute HTTP Post Request
            Log.d("HTTP", "DBG2");
            HttpResponse response = httpclient.execute(httppost);

            Log.d("HTTP", "DBG3");
            String body = EntityUtils.toString(response.getEntity(), "UTF-8");
            int http_status = response.getStatusLine().getStatusCode();
            if (http_status == 200) {
                Log.d("HTTP", "DBG4 "+body);
                Gson gson = new Gson();
                JsonResponse json_resp = gson.fromJson(body, JsonResponse.class);
                if (json_resp.status == 0) {
                    Log.d("HTTP", "DBG5");
                    DocSign doc = gson.fromJson(document, DocSign.class);
                    Log.d("REGDOC", doc.toJson());

                    Log.d("HTTP", "DBG6");
                    if (doc.type == "SIGN") {
                        Log.d("HTTP", "DBG7");
                        DocsStorage.add_doc(activity.getApplicationContext(), doc.site, doc.doc_id);
                    };

                    //txtStatus.setText(R.string.msg_status_delivered);
                    Log.d("HTTP", "DBG8");
                    MainActivity.alert(activity.getString(R.string.msg_status_delivered), activity, close_parent);
                    Log.d("HTTP", "DBG9");
                } else {
                    //txtStatus.setText(R.string.msg_status_http_error);
                    MainActivity.error(activity.getString(R.string.err_http_send), activity);
                    Log.e("HTTP", "Error HTTP response: " + body);
                }
            } else {
                //txtStatus.setText(R.string.msg_status_http_error);
                MainActivity.error(activity.getString(R.string.msg_status_http_error), activity);
                Log.e("HTTP", "HTTP response: "+http_status+" body: "+body);
            }
            Log.d("HTTP", "DBG10");
        } catch (UnsupportedEncodingException e) {
            //txtStatus.setText(R.string.msg_status_http_error);
            e.printStackTrace();
            MainActivity.error(activity.getString(R.string.msg_status_http_error), activity);
            Log.e("HTTP", "Error HTTP request: ", e);
        } catch (ClientProtocolException e) {
            e.printStackTrace();
            MainActivity.error(activity.getString(R.string.msg_status_http_error), activity);
            Log.e("HTTP", "Error HTTP request: ", e);
        } catch (IOException e) {
            e.printStackTrace();
            MainActivity.error(activity.getString(R.string.msg_status_http_error), activity);
            Log.e("HTTP", "Error HTTP request: ", e);
        }

        //prLinkStatus.setVisibility(View.INVISIBLE);
    }
}
