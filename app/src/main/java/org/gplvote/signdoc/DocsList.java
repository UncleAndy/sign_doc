package org.gplvote.signdoc;

import android.app.Activity;
import android.content.Context;
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
import android.widget.CheckedTextView;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;

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
    private int curPosition = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.docs_list);

        listDocView = (ListView) findViewById(R.id.listDocsView);
        listDocView.setChoiceMode(ListView.CHOICE_MODE_SINGLE);

        ArrayList<Map<String, Object>> list = new ArrayList<Map<String, Object>>(100);
        Map<String, Object> m;

        // Заполнение m данными из таблицы документов
        DocsStorage dbStorage = DocsStorage.getInstance(this);
        SQLiteDatabase db = dbStorage.getWritableDatabase();

        Cursor c = db.query("docs_list", new String[]{"t_set_status", "status", "site", "doc_id"}, null, null, null, null, "t_set_status desc", "100");
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

                Object item = listDocView.getItemAtPosition(position);

                Log.d("LIST", "itemClick: item = " + item);

                sAdapter.setCurrentPosition(position);
                sAdapter.notifyDataSetChanged();
            }
        });
    }

    @Override
    public void onClick(View v) {

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
    }
}
