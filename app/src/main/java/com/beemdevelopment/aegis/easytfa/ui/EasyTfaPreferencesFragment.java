package com.beemdevelopment.aegis.easytfa.ui;


import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import com.beemdevelopment.aegis.Preferences;
import com.beemdevelopment.aegis.R;
import com.beemdevelopment.aegis.ui.dialogs.Dialogs;
import com.beemdevelopment.aegis.ui.fragments.PreferencesFragment;

public class EasyTfaPreferencesFragment extends PreferencesFragment {

    private Preference _easyTfaEnabledPreference;
    private Preference _easyTfaFirebaseEnabledPreference;
    private Preference _easyTfaLinkBrowserPreference;
    private Preference _easyTfaLinkedBrowsersPreference;
    private Preference _easyTfaServerUrlPreference;
    private Preference _easyTfaTestServerConnectionPreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);
        addPreferencesFromResource(R.xml.preferences_easytfa);
        Preferences prefs = getPreferences();

        _easyTfaEnabledPreference = findPreference("pref_easytfa_enabled");
        _easyTfaFirebaseEnabledPreference = findPreference("pref_easytfa_firebase_enabled");
        _easyTfaLinkBrowserPreference = findPreference("pref_easytfa_browser_link");
        _easyTfaLinkedBrowsersPreference = findPreference("pref_easytfa_browser_linked");
        _easyTfaTestServerConnectionPreference = findPreference("pref_easytfa_server_test");
        _easyTfaServerUrlPreference = findPreference("pref_easytfa_server_url");

        setEnabledStates(getApp().getPreferences().isEasyTfaEnabled());

        _easyTfaEnabledPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            getResult().putExtra("needsRecreate", true);
            setEnabledStates((boolean) newValue);
            return true;
        });


        _easyTfaFirebaseEnabledPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            getResult().putExtra("needsRecreate", true);
            return true;
        });

        _easyTfaLinkBrowserPreference.setOnPreferenceClickListener(preference -> {

            return true;
        });

        _easyTfaLinkedBrowsersPreference.setOnPreferenceClickListener(preference -> {
            Intent intent = new Intent(getApp(), LinkedBrowsersActivity.class);
            startActivity(intent);
            return true;
        });

        String currentServer = prefs.getEasyTfaServerUrl();
        _easyTfaServerUrlPreference.setSummary(String.format("%s: %s", getString(R.string.selected), currentServer));
        _easyTfaServerUrlPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            getResult().putExtra("needsRecreate", true);
            _easyTfaServerUrlPreference.setSummary(String.format("%s: %s", getString(R.string.selected), (String) newValue));
            return true;
        });

        _easyTfaTestServerConnectionPreference.setOnPreferenceClickListener(preference -> {
            Dialogs.showSecureDialog(new AlertDialog.Builder(getActivity())
                    .setTitle(R.string.preference_reset_usage_count)
                    .setMessage(R.string.preference_reset_usage_count_dialog)
                    .setPositiveButton(android.R.string.yes, (dialog, which) -> getPreferences().clearUsageCount())
                    .setNegativeButton(android.R.string.no, null)
                    .create());
            return true;
        });
    }

    private void setEnabledStates(boolean enabled) {
        _easyTfaFirebaseEnabledPreference.setEnabled(enabled);
        _easyTfaServerUrlPreference.setEnabled(enabled);
        _easyTfaTestServerConnectionPreference.setEnabled(enabled);
        _easyTfaLinkBrowserPreference.setEnabled(enabled);
        _easyTfaLinkedBrowsersPreference.setEnabled(enabled);
    }
}
