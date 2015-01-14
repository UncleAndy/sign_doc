package org.gplvote.signdoc;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.widget.EditText;

import java.lang.ref.Reference;

public class DlgPassword extends DialogFragment {
    private EditText edPassword;
    private static GetPassInterface eventPassword;
    private static Dialog dlgPassword;
    private static boolean password_correct = true;

    @SuppressLint("ValidFragment")
    public DlgPassword(GetPassInterface eventPass) {
        eventPassword = eventPass;
    };
    public DlgPassword() { };

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (dlgPassword == null) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
            LayoutInflater inflater = getActivity().getLayoutInflater();

            builder.setView(inflater.inflate(R.layout.dlg_password, null))
                    // Add action buttons
                    .setPositiveButton(R.string.btn_ready, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int id) {
                            //
                            edPassword = (EditText) DlgPassword.this.getDialog().findViewById(R.id.edPassword);
                            String password = edPassword.getText().toString();
                            password_correct = eventPassword.onPassword(password);
                        }
                    })
                    .setNegativeButton(R.string.btn_cancel, new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int id) {
                            DlgPassword.this.getDialog().cancel();
                            System.exit(0);
                        }
                    })
                    .setOnKeyListener(new Dialog.OnKeyListener() {
                        @Override
                        public boolean onKey(DialogInterface arg0, int keyCode, KeyEvent event) {
                            if (keyCode == KeyEvent.KEYCODE_BACK) {
                                System.exit(0);
                                return true;
                            }
                            return false;
                        }
                    });
            dlgPassword = builder.create();
        }
        return(dlgPassword);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d("DlgPassword", "onDestroy()");
        dlgPassword = null;

        if (DocsList.do_destroy)
            return;

        if ((!password_correct || (MainActivity.sign == null) || !MainActivity.sign.pvt_key_present())) {
            DlgPassword dlg = new DlgPassword(eventPassword);
            dlg.show(getFragmentManager(), "missiles");
        }
    }
}
