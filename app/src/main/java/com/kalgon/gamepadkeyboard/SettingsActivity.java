package com.kalgon.gamepadkeyboard;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

//    /**
//     * code to post/handler request for permission
//     */
//    public final static int REQUEST_CODE = 12312;

//    @Override
//    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
//        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
//
//        switch (requestCode) {
//            case REQUEST_CODE:
//                // If request is cancelled, the result arrays are empty.
//                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
//                    Log.i("requestPermissionLauncher", "Permission granted");
//                } else {
//                    // Explain to the user that the feature is unavailable because the
//                    // features requires a permission that the user has denied. At the
//                    // same time, respect the user's decision. Don't link to system
//                    // settings in an effort to convince the user to change their
//                    // decision.
//                    Log.w("requestPermissionLauncher", "Permission denied");
//                }
//        }
//    }

//    public void checkDrawOverlayPermission() {
//        /** check if we already have permission to draw over other apps */
//        if (!Settings.canDrawOverlays(getApplicationContext())) {
//            if (ActivityCompat.shouldShowRequestPermissionRationale(this,
//                    Manifest.permission.SYSTEM_ALERT_WINDOW)) {
//                Log.d("checkDrawOverlayPermission", "Need to show rationale");
//            }
//            Log.d("checkDrawOverlayPermission", "Requesting permissions");
//            ActivityCompat.requestPermissions(this,
//                    new String[]{Manifest.permission.SYSTEM_ALERT_WINDOW},
//                    REQUEST_CODE);
//        }
//    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

//        checkDrawOverlayPermission();

        setContentView(R.layout.settings_activity);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.settings, new SettingsFragment())
                .commit();
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Respond to the action bar's Up/Home button
            case android.R.id.home:
                super.onBackPressed();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public static class SettingsFragment extends PreferenceFragmentCompat
            implements Preference.OnPreferenceChangeListener {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferencesFromResource(R.xml.root_preferences, rootKey);

            getPreferenceManager().findPreference("languages").setOnPreferenceChangeListener(this);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (preference.getKey().equals("languages")) {
                Set<String> languages = (Set<String>)newValue;
                if (languages.size() == 0) {
                    Toast.makeText(getContext(), "Must have at least one language selected!", Toast.LENGTH_SHORT).show();
                    return false;
                }
            }
            return true;
        }
    }
}