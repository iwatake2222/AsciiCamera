/**
 * ActivitySettings
 * User setting Activity
 * related files: settings.xml
 * caller: ActivityMain when tap 'settings' on actionbar
 * @author take.iwiw
 * @version 1.0.0
 */

package jp.take_iwiw.asciicamera;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.ListPreference;
import android.preference.PreferenceFragment;


public class ActivitySettings extends Activity {

    static public final String PREF_KEY_FONT_SIZE = "key_fontSize";
    static public final String PREF_KEY_CONVERT = "key_convert";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new PrefFragment()).commit();

    }

    public static class PrefFragment extends PreferenceFragment {
        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            addPreferencesFromResource(R.xml.settings);

            setSummaryFraction();
        }

        @Override
        public void onResume() {
            super.onResume();
            SharedPreferences sp = getPreferenceScreen().getSharedPreferences();
            sp.registerOnSharedPreferenceChangeListener(listener);
        }

        @Override
        public void onPause() {
            super.onPause();
            SharedPreferences sp = getPreferenceScreen().getSharedPreferences();
            sp.unregisterOnSharedPreferenceChangeListener(listener);
        }

        private OnSharedPreferenceChangeListener listener = new OnSharedPreferenceChangeListener() {
            @Override
            public void onSharedPreferenceChanged(
                                SharedPreferences sharedPreferences, String key) {
                if (key.equals(PREF_KEY_CONVERT)){
                    setSummaryFraction();
                }
            }
        };


        private void setSummaryFraction() {
            ListPreference prefFraction = (ListPreference)findPreference(PREF_KEY_CONVERT);
            prefFraction.setSummary(prefFraction.getEntry());
        }



    }


}
