package pl.edu.pwr.wiz.wzorlaboratorium7;

import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.nfc.tech.NdefFormatable;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.IOException;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {
    boolean mWriteMode = false;
    private NfcAdapter mNfcAdapter;
    private PendingIntent mNfcPendingIntent;
    private AlertDialog alertDialog;
    final static int MY_PERMISSIONS_REQUEST_NFC = 0;
    private EditText editText;
    private LinearLayout parentView;
    private TextView textView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        parentView = (LinearLayout) findViewById(R.id.activity_main);
        editText = (EditText) findViewById(R.id.editText);
        textView = (TextView) findViewById(R.id.textView);

        textView.setText("");

        /* Podpinamy obsluge przycisku do zapisu TAGu NFC */
        Button btn = (Button) findViewById(R.id.zapisz_tag);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                initTagWriting(v);
            }
        });

        parseIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        parseIntent(intent);
    }

    private void parseIntent(Intent intent) {
        if (!mWriteMode && intent != null && NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {

            /* Odczyt danych */
            Parcelable[] rawMessages = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
            if (rawMessages != null) {
                for (int i = 0; i < rawMessages.length; i++) {
                    // Pobieramy wszystkie wiadomości
                    NdefMessage message = (NdefMessage) rawMessages[i];

                    // Pobieramy rekordy i przechodzi przez nie
                    NdefRecord[] records = message.getRecords();

                    if (records == null) {
                        continue;   // Pomijamy ta wiadomosc
                    }

                    for (int j = 0; j < records.length; j++) {
                        NdefRecord record = records[j];

                        // Obsluga URI
                        if (isUri(record)) {

                            final String url = String.valueOf(record.toUri());

                            if (url.startsWith("ftp://") || url.startsWith("telnet://")) {
                                Snackbar.make(parentView, url, Snackbar.LENGTH_LONG).show();
                            }

                            Toast.makeText(getApplicationContext(), "uri: " + url, Toast.LENGTH_LONG).show();
                        }
                        // Obsluga text/plain
                        else if (isTextPlain(record)) {
                            String data = new String(record.getPayload());
                            textView.setText("text/plain: " + data);

                            Toast.makeText(getApplicationContext(), "text/plain: " + data, Toast.LENGTH_LONG).show();
                        }
                    }
                }
            }
        } else if (mWriteMode && NfcAdapter.ACTION_TAG_DISCOVERED.equals(intent.getAction())) {
            /* Zapis danych */
            Tag detectedTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG);

            NdefRecord record;
            String data = String.valueOf(editText.getText());

            record = NdefRecord.createUri(data);

            NdefMessage message = new NdefMessage(new NdefRecord[]{record});
            if (writeTag(message, detectedTag)) {
                Toast.makeText(this, "Sukces. Tag został zapisany", Toast.LENGTH_LONG).show();

                mWriteMode = false;
                alertDialog.dismiss();
            }
        }
    }

    private boolean isTextPlain(NdefRecord record) {
        return record.getTnf() == NdefRecord.TNF_MIME_MEDIA;
    }

    private boolean isUri(NdefRecord record) {
        return record.getTnf() == NdefRecord.TNF_WELL_KNOWN && Arrays.equals(record.getType(), NdefRecord.RTD_URI);
    }

    /* Funkcja rozpoczyna proces zapisu tagu po sprawdzeniu uprawnień */
    private void initTagWriting(View view) {
        /* Uruchamiamy proces zapisu tagu */
        mNfcAdapter = NfcAdapter.getDefaultAdapter(MainActivity.this);
        mNfcPendingIntent = PendingIntent.getActivity(MainActivity.this, 0,
                new Intent(MainActivity.this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);

        enableTagWriteMode();

        /* Wyświetlamy okno dialogowe */
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(view.getContext());

        // Tytul
        alertDialogBuilder.setTitle("Zapis TAGu");

        // Ustawiamy dialog
        alertDialogBuilder
                .setMessage("Przytknij TAG w celu jego zapisania")
                .setCancelable(false)
                .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        disableTagWriteMode();
                        dialog.dismiss();
                    }
                });

        // Utworz okno dialogowe i wyswietl je
        alertDialog = alertDialogBuilder.create();
        alertDialog.show();
    }

    private void enableTagWriteMode() {
        mWriteMode = true;
        IntentFilter tagDetected = new IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED);
        IntentFilter[] mWriteTagFilters = new IntentFilter[]{tagDetected};
        mNfcAdapter.enableForegroundDispatch(this, mNfcPendingIntent, mWriteTagFilters, null);
    }

    private void disableTagWriteMode() {
        mWriteMode = false;
        mNfcAdapter.disableForegroundDispatch(this);
    }

    public boolean writeTag(NdefMessage message, Tag tag) {
        int size = message.toByteArray().length;
        try {
            Ndef ndef = Ndef.get(tag);
            if (ndef != null) {
                ndef.connect();
                if (!ndef.isWritable()) {
                    Toast.makeText(getApplicationContext(),
                            "Błąd: tagu nie można zapisywać",
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                if (ndef.getMaxSize() < size) {
                    Toast.makeText(getApplicationContext(),
                            "Błąd: tag zbyt mały",
                            Toast.LENGTH_SHORT).show();
                    return false;
                }
                ndef.writeNdefMessage(message);
                return true;
            } else {
                NdefFormatable format = NdefFormatable.get(tag);
                if (format != null) {
                    try {
                        format.connect();
                        format.format(message);
                        return true;
                    } catch (IOException e) {
                        return false;
                    }
                } else {
                    return false;
                }
            }
        } catch (Exception e) {
            return false;
        }
    }

}
