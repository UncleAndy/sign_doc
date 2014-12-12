package com.example.signdoc;

import android.app.Activity;
import android.os.Bundle;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ResourceBundle;

public class LinkStatus extends Activity {
	protected TextView txtStatus;
    protected ProgressBar prLinkStatus;

	@Override
	  protected void onCreate(Bundle savedInstanceState) {
	    super.onCreate(savedInstanceState);
	    setContentView(R.layout.link_status);

        txtStatus = (TextView) findViewById(R.id.txtLinkStatus);
        prLinkStatus = (ProgressBar) findViewById(R.id.prLinkStatus);
	  }
}
