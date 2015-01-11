package org.gplvote.signdoc;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

import com.google.gson.Gson;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by andy on 31.12.14.
 */
public class DocsList extends Activity implements View.OnClickListener {

    private ListView listDocView;
    private DocsListArrayAdapter sAdapter;

    private Button btnSign;
    private Button btnView;

    public static DocsList instance;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.docs_list);

        instance = this;

        btnSign = (Button) findViewById(R.id.btnDocSign);
        btnSign.setOnClickListener(this);
        btnSign.setEnabled(false);
        btnView = (Button) findViewById(R.id.btnDocView);
        btnView.setOnClickListener(this);
        btnView.setEnabled(false);

        listDocView = (ListView) findViewById(R.id.listDocsView);
        listDocView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        update_list();
    }

    @Override
    public void onClick(View v) {
        Intent intent;

        Log.d("LIST", "onClick id = " + v.getId());
        Log.d("LIST", "onClick R.id.btnDocSign = " + R.id.btnDocSign);

        switch (v.getId()) {
            case R.id.btnDocSign:
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

                Cursor c = db.query("docs_list", new String[]{"data", "template"}, "site = ? AND doc_id = ?", new String[]{sign_request.site, sign_request.doc_id}, null, null, null, "1");
                if (c != null) {
                    if (c.moveToFirst()) {
                        sign_request.dec_data = c.getString(c.getColumnIndex("data"));
                        sign_request.template = c.getString(c.getColumnIndex("template"));
                    }
                }

                documents.add(sign_request);

                Gson gson = new Gson();
                intent.putExtra("DocsList", gson.toJson(documents));
                intent.putExtra("LastRecvTime", "");

                startActivity(intent);
                break;
            case R.id.btnDocView:
                break;
            default:
                break;
        }

    }

    public void update_list() {
        ArrayList<Map<String, Object>> list = new ArrayList<Map<String, Object>>(100);
        Map<String, Object> m;

        // Заполнение m данными из таблицы документов
        DocsStorage dbStorage = DocsStorage.getInstance(this);
        SQLiteDatabase db = dbStorage.getWritableDatabase();

        Cursor c = db.query("docs_list", new String[]{"t_set_status", "status", "site", "doc_id"}, null, null, null, null, "t_receive desc", "100");
        if (c != null) {
            if (c.moveToFirst()) {
                do {
                    m = new HashMap<String, Object>();

                    m.put("t_set_status", c.getString(c.getColumnIndex("t_set_status")));
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
                    btnSign.setEnabled(false);
                    btnView.setEnabled(true);
                } else if ((item.get("t_set_status") != null) && (!item.get("t_set_status").equals(""))) {
                    btnSign.setEnabled(true);
                    btnView.setEnabled(true);
                } else {
                    btnSign.setEnabled(false);
                    btnView.setEnabled(false);
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
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View rowView = inflater.inflate(R.layout.docs_list_item, parent, false);

            TextView txtDocTime = (TextView) rowView.findViewById(R.id.txtDocTime);
            TextView txtDocStatus = (TextView) rowView.findViewById(R.id.txtDocStatus);
            TextView txtDocSite = (TextView) rowView.findViewById(R.id.txtDocSite);
            TextView txtDocId = (TextView) rowView.findViewById(R.id.txtDocId);

            if (list.get(position).get("t_set_status") == null) {
                txtDocTime.setText("- no data -");
                txtDocStatus.setText("- no data -");
            } else {
                // Time parse
                Long doc_time_long = Long.parseLong((String) list.get(position).get("t_set_status"));
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                String doc_time = sdf.format(doc_time_long);
                txtDocTime.setText(doc_time);

                // Status parse
                String status = (String) list.get(position).get("status");
                switch (status) {
                    case "new":
                        status = getString(R.string.txtDocStatusNew);
                        break;
                    case "sign":
                        status = getString(R.string.txtDocStatusSigned);
                        break;
                    default:
                        status = getString(R.string.txtDocStatusNotSigned);
                        break;
                }
                txtDocStatus.setText(status);
            }
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
}
