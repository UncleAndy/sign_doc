package org.gplvote.signdoc;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class ShowInfo extends GetPassActivity implements View.OnClickListener {
    private TextView txtPublicKeyId;
    private Button btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.show_info);

        txtPublicKeyId = (TextView) findViewById(R.id.txtInfoPublicKeyId);

        btnBack = (Button) findViewById(R.id.btnInfoBack);
        btnBack.setOnClickListener(this);

        if (MainActivity.sign != null) {
            txtPublicKeyId.setText(MainActivity.sign.getPublicKeyIdBase64().replaceAll("=+$", ""));
        }
    }

    @Override
    public void onClick(View view) {
        finish();
    }
}
