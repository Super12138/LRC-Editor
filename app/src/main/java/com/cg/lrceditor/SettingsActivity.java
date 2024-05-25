package com.cg.lrceditor;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.ImageButton;
import android.widget.RadioButton;
import com.cg.lrceditor.R;
import android.widget.RadioGroup;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import java.io.File;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

	private TextView saveLocation;
	private TextView readLocation;
	private TextView timestampStep;
	private Switch threeDigitMillisecondsSwitch;

	private RadioButton light, dark, darker;

	private SharedPreferences preferences;

	private boolean isDarkTheme = false;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		preferences = getSharedPreferences("LRC Editor Preferences", MODE_PRIVATE);
		String theme = preferences.getString(Constants.THEME_PREFERENCE, "light");
		if (theme.equals("dark")) {
			isDarkTheme = true;
			setTheme(R.style.AppThemeDark);
		} else if (theme.equals("darker")) {
			isDarkTheme = true;
			setTheme(R.style.AppThemeDarker);
		}

		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_settings);

		Toolbar toolbar = findViewById(R.id.toolbar);
		if (isDarkTheme) {
			toolbar.setPopupTheme(R.style.AppThemeDark_PopupOverlay);
		}
		setSupportActionBar(toolbar);

		try {
			getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		} catch (NullPointerException e) {
			e.printStackTrace();
		}

		saveLocation = findViewById(R.id.save_location);
		readLocation = findViewById(R.id.read_location);

		timestampStep = findViewById(R.id.timestamp_step);
		timestampStep.setText(
				String.format(Locale.getDefault(), "%d ms", preferences.getInt(Constants.TIMESTAMP_STEP_AMOUNT_PREFERENCE, 100)));

		threeDigitMillisecondsSwitch = findViewById(R.id.three_digit_milliseconds_switch);
		threeDigitMillisecondsSwitch.setChecked(preferences.getBoolean(Constants.THREE_DIGIT_MILLISECONDS_PREFERENCE, false));
		threeDigitMillisecondsSwitch.setOnCheckedChangeListener((compoundButton, checked) -> {
			SharedPreferences.Editor editor = preferences.edit();
			editor.putBoolean(Constants.THREE_DIGIT_MILLISECONDS_PREFERENCE, checked);
			editor.apply();
		});

		RadioGroup themeGroup = findViewById(R.id.theme_group);
		light = findViewById(R.id.radioButtonLight);
		dark = findViewById(R.id.radioButtonDark);
		darker = findViewById(R.id.radioButtonDarker);

		switch (theme) {
			case "light":
				light.setChecked(true);
				break;
			case "dark":
				dark.setChecked(true);
				break;
			case "darker":
				darker.setChecked(true);
				break;
		}

		themeGroup.setOnCheckedChangeListener((group, checkedId) -> {
			SharedPreferences.Editor editor = preferences.edit();

			if (checkedId == light.getId()) {
				editor.putString(Constants.THEME_PREFERENCE, "light");
			} else if (checkedId == dark.getId()) {
				editor.putString(Constants.THEME_PREFERENCE, "dark");
			} else if (checkedId == darker.getId()) {
				editor.putString(Constants.THEME_PREFERENCE, "darker");
			}

			Toast.makeText(getApplicationContext(), getString(R.string.restart_for_theme_message), Toast.LENGTH_SHORT).show();
			editor.apply();
			recreate();
		});

		String location = preferences.getString(Constants.SAVE_LOCATION_PREFERENCE, Constants.defaultLocation);
		saveLocation.setText(location);
		location = preferences.getString(Constants.READ_LOCATION_PREFERENCE, Constants.defaultLocation);
		readLocation.setText(location);

		if (preferences.getString(Constants.PURCHASED_PREFERENCE, "").equals("Y")) {
			TextView themeTitle = findViewById(R.id.theme_title);
			themeGroup = findViewById(R.id.theme_group);

			themeTitle.setVisibility(View.VISIBLE);
			themeGroup.setVisibility(View.VISIBLE);
		}
	}

	public void changeReadLocation(View view) {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		intent.addCategory(Intent.CATEGORY_DEFAULT);
		intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
		try {
			startActivityForResult(intent, Constants.READ_LOCATION_REQUEST);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(this, "Whoops! " + getString(R.string.failed_to_open_directory_picker_message), Toast.LENGTH_SHORT).show();
			Toast.makeText(this, getString(R.string.documentsui_enable_message), Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}
	}

	public void changeSaveLocation(View view) {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		intent.addCategory(Intent.CATEGORY_DEFAULT);
		intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
		try {
			startActivityForResult(intent, Constants.SAVE_LOCATION_REQUEST);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(this, "Whoops! " + getString(R.string.failed_to_open_directory_picker_message), Toast.LENGTH_SHORT).show();
			Toast.makeText(this, getString(R.string.documentsui_enable_message), Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}
	}

	public void adjustTimestampStep(View v) {
		LayoutInflater inflater = this.getLayoutInflater();
		final View view = inflater.inflate(R.layout.dialog_adjust, null);
		final TextView title = view.findViewById(R.id.title);
		title.setVisibility(View.GONE);
		final TextView timestamp = view.findViewById(R.id.content);
		timestamp.setText(
				String.format(Locale.getDefault(), "%d ms", preferences.getInt(Constants.TIMESTAMP_STEP_AMOUNT_PREFERENCE, 100)));

		ImageButton increase = view.findViewById(R.id.increase_button);
		if (isDarkTheme) {
			increase.setImageDrawable(getDrawable(R.drawable.ic_add_light));
		}
		increase.setOnClickListener(view1 -> {
			String timestampStepVal = timestamp.getText().toString();
			int value = Integer.parseInt(timestampStepVal.substring(0, timestampStepVal.length() - 3));
			value += 10;
			value = Math.min(value, 200);

			timestamp.setText(
					String.format(Locale.getDefault(), "%d ms", value));
		});

		ImageButton decrease = view.findViewById(R.id.decrease_button);
		if (isDarkTheme) {
			decrease.setImageDrawable(getDrawable(R.drawable.ic_minus_light));
		}
		decrease.setOnClickListener(view1 -> {
			String timestampStepVal = timestamp.getText().toString();
			int value = Integer.parseInt(timestampStepVal.substring(0, timestampStepVal.length() - 3));
			value -= 10;
			value = Math.max(value, 10);

			timestamp.setText(
					String.format(Locale.getDefault(), "%d ms", value));
		});

		new AlertDialog.Builder(this)
				.setView(view)
				.setTitle(R.string.timestamp_step_amount_prompt)
				.setPositiveButton(getString(R.string.adjust), (dialog1, which) -> {
					String timestampStepVal = timestamp.getText().toString();
					int value = Integer.parseInt(timestampStepVal.substring(0, timestampStepVal.length() - 3));

					SharedPreferences.Editor editor = preferences.edit();
					editor.putInt(Constants.TIMESTAMP_STEP_AMOUNT_PREFERENCE, value);
					editor.apply();

					timestampStep.setText(timestampStepVal);
				})
				.setNegativeButton(getString(R.string.cancel), null)
				.setCancelable(false)
				.create()
				.show();
	}

	public void showTimestampStepHelp(View view) {
		new AlertDialog.Builder(this)
				.setMessage(R.string.timestamp_step_help)
				.setNeutralButton(getString(R.string.ok), null)
				.create()
				.show();
	}

	public void toggleThreeDigitMillisecondSwitch(View view) {
		threeDigitMillisecondsSwitch.toggle();
	}

	public void showThreeDigitMillisecondsHelp(View view) {
		new AlertDialog.Builder(this)
				.setMessage(R.string.three_digit_milliseconds_help)
				.setNeutralButton(getString(R.string.ok), null)
				.create()
				.show();
	}

	private void releasePersistence(String uriLookupString) {
		String previousUriString = preferences.getString(uriLookupString, null);
		if (previousUriString != null) {
			// Release previously acquired persistence
			try {
				getContentResolver().releasePersistableUriPermission(Uri.parse(previousUriString),
						Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
			} catch (SecurityException | NullPointerException e) {
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
		super.onActivityResult(requestCode, resultCode, resultData);
		if (requestCode == Constants.SAVE_LOCATION_REQUEST && resultCode == Activity.RESULT_OK) {
			if (resultData != null) {
				Uri uri = resultData.getData();
				if (uri != null) {
					releasePersistence("saveUri");

					String realPath = FileUtil.getFullPathFromTreeUri(uri, this);

					SharedPreferences.Editor editor = preferences.edit();
					editor.putString(Constants.SAVE_LOCATION_PREFERENCE, realPath);
					editor.putString("saveUri", uri.toString());
					editor.apply();

					saveLocation.setText(realPath);

					try {
						getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
					} catch (SecurityException e) {
						e.printStackTrace();
					}

					if (realPath == null || realPath.equals(File.separator)) {
						Toast.makeText(getApplicationContext(), R.string.generic_save_path_error, Toast.LENGTH_LONG).show();
					}
				}
			}
		} else if (requestCode == Constants.READ_LOCATION_REQUEST && resultCode == Activity.RESULT_OK) {
			if (resultData != null) {
				Uri uri = resultData.getData();
				if (uri != null) {
					releasePersistence("readUri");

					String realPath = FileUtil.getFullPathFromTreeUri(uri, this);

					SharedPreferences.Editor editor = preferences.edit();
					editor.putString(Constants.READ_LOCATION_PREFERENCE, realPath);
					editor.putString("readUri", uri.toString());
					editor.apply();

					readLocation.setText(realPath);

					try {
						getContentResolver().takePersistableUriPermission(uri,
								Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
					} catch (SecurityException e) {
						e.printStackTrace();
					}

					if (realPath == null || realPath.equals(File.separator)) {
						Toast.makeText(getApplicationContext(), R.string.generic_read_path_error, Toast.LENGTH_LONG).show();
					}
				}
			}
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}

		return (super.onOptionsItemSelected(item));
	}
}
