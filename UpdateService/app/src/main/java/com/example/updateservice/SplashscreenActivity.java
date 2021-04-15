package com.example.updateservice;


import android.app.Activity;
import android.content.Intent;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;


public class SplashscreenActivity extends Activity {
    /** Splash screen duration time in milliseconds */
    private static final int DELAY = 1000;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splashscreen);

        // Jump to SensorsActivity after DELAY milliseconds
        new Handler().postDelayed(() -> {
            final Intent newIntent = new Intent(SplashscreenActivity.this, FeaturesActivity.class);
            newIntent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);

            // Handle NFC message, if app was opened using NFC AAR record
            final Intent intent = getIntent();
            if (NfcAdapter.ACTION_NDEF_DISCOVERED.equals(intent.getAction())) {
                final Parcelable[] rawMsgs = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES);
                if (rawMsgs != null) {
                    for (Parcelable rawMsg : rawMsgs) {
                        final NdefMessage msg = (NdefMessage) rawMsg;
                        final NdefRecord[] records = msg.getRecords();

                        for (NdefRecord record : records) {
                            if (record.getTnf() == NdefRecord.TNF_MIME_MEDIA) {
                                switch (record.toMimeType()) {
                                    case FeaturesActivity.EXTRA_APP:
                                        newIntent.putExtra(FeaturesActivity.EXTRA_APP, new String(record.getPayload()));
                                        break;
                                    case FeaturesActivity.EXTRA_ADDRESS:
                                        newIntent.putExtra(FeaturesActivity.EXTRA_ADDRESS, invertEndianness(record.getPayload()));
                                        break;
                                }
                            }
                        }
                    }
                }
            }
            startActivity(newIntent);
            finish();
        }, DELAY);
    }

    @Override
    public void onBackPressed() {
        // do nothing. Protect from exiting the application when splash screen is shown
    }

    /**
     * Inverts endianness of the byte array.
     * @param bytes input byte array
     * @return byte array in opposite order
     */
    private byte[] invertEndianness(final byte[] bytes) {
        if (bytes == null)
            return null;
        final int length = bytes.length;
        final byte[] result = new byte[length];
        for (int i = 0; i < length; i++)
            result[i] = bytes[length - i - 1];
        return result;
    }
}
