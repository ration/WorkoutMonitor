package fi.iki.lahtela;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.Menu;

public class MonitorPreferencesActivity extends PreferenceActivity {
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.xml.preferences);
	}


}
