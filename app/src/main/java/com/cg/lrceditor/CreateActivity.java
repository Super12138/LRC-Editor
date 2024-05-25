package com.cg.lrceditor;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.Toast;
import com.cg.lrceditor.R;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class CreateActivity extends AppCompatActivity {

	private EditText lyricsTextBox;

	private boolean isDarkTheme = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		SharedPreferences preferences = getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE);
		String theme = preferences.getString(Constants.THEME_PREFERENCE, "light");
		if (theme.equals("dark")) {
			isDarkTheme = true;
			setTheme(R.style.AppThemeDark);
		} else if (theme.equals("darker")) {
			isDarkTheme = true;
			setTheme(R.style.AppThemeDarker);
		}

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_create);

		Toolbar toolbar = findViewById(R.id.toolbar);
		lyricsTextBox = findViewById(R.id.lyrics_textbox);
		if (isDarkTheme) {
			/* Dark toolbar popups for dark themes */
			toolbar.setPopupTheme(R.style.AppThemeDark_PopupOverlay);

			/* Dark border when using a dark theme */
			lyricsTextBox.setBackground(getDrawable(R.drawable.rounded_border_light));
		}
		setSupportActionBar(toolbar);

		try {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_create_activity, menu);
		return super.onCreateOptionsMenu(menu);
	}

	public void startEditor() {
		String data = lyricsTextBox.getText().toString().trim();

		if (data.isEmpty()) {
			Toast.makeText(this, getString(R.string.no_lyrics_found_message), Toast.LENGTH_SHORT).show();
			return;
		}

		Intent intent = new Intent(this, EditorActivity.class);
		intent.putExtra(IntentSharedStrings.LYRICS, data.split("\\n"));
		startActivity(intent);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		} else if (item.getItemId() == R.id.action_done) {
			startEditor();
			return true;
		}

		return (super.onOptionsItemSelected(item));
	}
}
