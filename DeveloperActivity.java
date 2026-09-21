package com.hussein.songzone;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.Toast;

public class DeveloperActivity extends Activity {
    @Override protected void onCreate(Bundle s) {
        super.onCreate(s);
        setContentView(R.layout.activity_developer);

        Button btnBack = findViewById(R.id.btnBack);
        btnBack.setOnClickListener(v -> finish());

        Button btnTelegram = findViewById(R.id.btnTelegramChannel);
        btnTelegram.setText(R.string.dev_telegram);
        btnTelegram.setOnClickListener(v -> openUrl(getString(R.string.dev_telegram_url)));

        Button btnEmail = findViewById(R.id.btnTelegramContact);
        btnEmail.setText(R.string.dev_email);
        btnEmail.setOnClickListener(v ->
                openUrl("mailto:" + getString(R.string.dev_email_address)));
    }

    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "لا يمكن فتح الرابط", Toast.LENGTH_SHORT).show();
        }
    }
}
