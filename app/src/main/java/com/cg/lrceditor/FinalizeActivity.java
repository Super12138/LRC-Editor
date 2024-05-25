package com.cg.lrceditor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.text.method.ScrollingMovementMethod;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import com.cg.lrceditor.R;

public class FinalizeActivity extends AppCompatActivity {

	private ArrayList<LyricItem> lyricData;

	private EditText songName;
	private EditText artistName;
	private EditText albumName;
	private EditText composerName;
	private EditText creatorName;

	private TextView statusTextView;

	private Uri saveUri;
	private String saveLocation = null;

	private String lrcFileName = null;
	private String lrcFilePath = null;
	private String songFileName = null;

	private View dialogView;

	private SharedPreferences preferences;

	private boolean isDarkTheme = false;
	private boolean overwriteFailed = false;
	private boolean threadIsExecuting = false;
	private boolean useThreeDigitMilliseconds = false;

	@SuppressLint("ClickableViewAccessibility")
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
		setContentView(R.layout.activity_finalize);

		Intent intent = getIntent();
		lyricData = (ArrayList<LyricItem>) intent.getSerializableExtra(IntentSharedStrings.LYRIC_DATA);
		Metadata metadata = (Metadata) intent.getSerializableExtra(IntentSharedStrings.METADATA);
		Uri songUri = intent.getParcelableExtra(IntentSharedStrings.SONG_URI);
		lrcFileName = intent.getStringExtra(IntentSharedStrings.LRC_FILE_NAME);
		lrcFilePath = intent.getStringExtra(IntentSharedStrings.LRC_FILE_PATH);
		songFileName = intent.getStringExtra(IntentSharedStrings.SONG_FILE_NAME);

		songName = findViewById(R.id.songName_edittext);
		artistName = findViewById(R.id.artistName_edittext);
		albumName = findViewById(R.id.albumName_edittext);
		composerName = findViewById(R.id.composer_edittext);
		creatorName = findViewById(R.id.creatorName_edittext);

		statusTextView = findViewById(R.id.status_textview);
		statusTextView.setMovementMethod(new ScrollingMovementMethod());
		statusTextView.setOnTouchListener((textView, event) -> {
			// Prevents the parent scrollview from scrolling around when the user touches and scrolls the statusTextView
			textView.getParent().requestDisallowInterceptTouchEvent(true);
			return false;
		});

		useThreeDigitMilliseconds = preferences.getBoolean(Constants.THREE_DIGIT_MILLISECONDS_PREFERENCE, false);

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

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) /* Marshmallow onwards require runtime permissions */
			grantPermission();

		if (metadata != null) {
			if (!metadata.getSongName().isEmpty())
				songName.setText(metadata.getSongName());
			if (!metadata.getArtistName().isEmpty())
				artistName.setText(metadata.getArtistName());
			if (!metadata.getAlbumName().isEmpty())
				albumName.setText(metadata.getAlbumName());
			if (!metadata.getComposerName().isEmpty())
				composerName.setText(metadata.getComposerName());
			if (!metadata.getCreatorName().isEmpty()) {
				creatorName.setText(metadata.getCreatorName());
			}
		}

		if (songUri != null) {
			try {
				MediaMetadataRetriever mmr = new MediaMetadataRetriever();
				mmr.setDataSource(this, songUri);

				if (songName.getText().toString().isEmpty())
					songName.setText(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE));
				if (albumName.getText().toString().isEmpty())
					albumName.setText(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM));
				if (artistName.getText().toString().isEmpty())
					artistName.setText(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST));
				if (composerName.getText().toString().isEmpty())
					composerName.setText(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_COMPOSER));
			} catch (RuntimeException e) {
				Toast.makeText(this, getString(R.string.failed_to_extract_metadata_message), Toast.LENGTH_LONG).show();
			}
		}

		Collections.sort(this.lyricData, new LyricReader.LyricTimestampComparator());
	}

	public void displaySaveDialog(View view) {
		if (!isExternalStorageWritable()) {
			Toast.makeText(this, getString(R.string.storage_unavailable_message), Toast.LENGTH_LONG).show();
			return;
		} else if (threadIsExecuting) {
			Toast.makeText(this, getString(R.string.another_operation_wait_message), Toast.LENGTH_SHORT).show();
			return;
		}

		statusTextView.setText(getString(R.string.processing));
		statusTextView.scrollTo(0, 0);
		statusTextView.setVisibility(View.VISIBLE);

		Button copyError = findViewById(R.id.copy_error_button);
		copyError.setVisibility(View.GONE);

		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !grantPermission()) { /* Marshmallow onwards require runtime permissions */
			statusTextView.setTextColor(ContextCompat.getColor(this, R.color.errorColor));
			statusTextView.setText(getString(R.string.no_permission_to_write_text));
			copyError.setVisibility(View.VISIBLE);
			return;
		} else if (!isDarkTheme) {
			statusTextView.setTextColor(Color.BLACK);
		} else {
			statusTextView.setTextColor(Color.WHITE);
		}

		overwriteFailed = false;

		dialogView = this.getLayoutInflater().inflate(R.layout.dialog_save_lrc, null);
		final EditText editText = dialogView.findViewById(R.id.dialog_edittext);
		final TextView saveLocationDisplayer = dialogView.findViewById(R.id.save_location_display);
		TextView textView = dialogView.findViewById(R.id.dialog_prompt);

		editText.setHint(getString(R.string.file_name_hint));
		if (lrcFileName != null) {
			editText.setText(lrcFileName);
		} else {
			editText.setText(String.format("%s.lrc", songName.getText().toString()));
		}
		textView.setText(getString(R.string.file_name_prompt));

		if (saveLocation == null) {
			String uriString;
			if (lrcFilePath != null) {
				saveLocation = lrcFilePath;
				uriString = preferences.getString("readUri", null);
			} else {
				saveLocation = preferences.getString(Constants.SAVE_LOCATION_PREFERENCE, Constants.defaultLocation);
				uriString = preferences.getString("saveUri", null);
			}

			if (uriString != null) {
				saveUri = Uri.parse(uriString);
			}
		}

		saveLocationDisplayer.setText(getString(R.string.save_location_displayer, saveLocation));

		if (songFileName == null) {
			dialogView.findViewById(R.id.same_name_as_song).setEnabled(false);
		}
		if (lrcFileName == null) {
			dialogView.findViewById(R.id.same_name_as_lrc).setEnabled(false);
		}

		new AlertDialog.Builder(this)
				.setView(dialogView)
				.setPositiveButton(getString(R.string.save), (dialog, which) -> {
					dialog.dismiss();
					hideKeyboard();
					saveLyricsFile(saveLocation, editText.getText().toString());
				})
				.setNegativeButton(getString(R.string.cancel), (dialog, which) -> statusTextView.setVisibility(View.GONE))
				.setCancelable(false)
				.create()
				.show();
	}

	private void setStatusOnUiThread(final String msg) {
		runOnUiThread(() -> statusTextView.setText(msg));
	}

	private void saveLyricsFile(String filePath, String fileName) {
		final String finalFileName = fileName;
		final String finalFilePath = filePath;

		// A bit buggy: See comments in renameAsync() in HomePage.java
		if (new File(finalFilePath, finalFileName).exists()) {
			new AlertDialog.Builder(this)
					.setTitle(getString(R.string.warning))
					.setMessage(getString(R.string.overwrite_prompt, finalFileName, finalFilePath))
					.setCancelable(false)
					.setPositiveButton(getString(R.string.yes), (dialog, which) -> new Thread(() -> {
						threadIsExecuting = true;

						setStatusOnUiThread(getString(R.string.attempting_to_overwrite_message));
						if (!deleteFile(finalFilePath, finalFileName)) {
							overwriteFailed = true;
							runOnUiThread(() -> Toast.makeText(getApplicationContext(),
									getString(R.string.failed_to_overwrite_message), Toast.LENGTH_LONG).show());
						}

						setStatusOnUiThread(getString(R.string.writing_lyrics_message));
						writeLyrics(finalFilePath, finalFileName);

						threadIsExecuting = false;
					}).start())
					.setNegativeButton(getString(R.string.no), (dialog, which) -> statusTextView.setVisibility(View.GONE))
					.show();
		} else {
			new Thread(() -> {
				threadIsExecuting = true;

				setStatusOnUiThread(getString(R.string.writing_lyrics_message));
				writeLyrics(finalFilePath, finalFileName);

				threadIsExecuting = false;
			}).start();
		}
	}

	private boolean deleteFile(String filePath, String fileName) {
		DocumentFile file = FileUtil.getDocumentFileFromPath(saveUri, filePath + "/" + fileName, getApplicationContext());
		return file != null && file.delete();
	}

	private void writeLyrics(final String filePath, final String fileName) {
		DocumentFile file = FileUtil.getDocumentFileFromPath(saveUri, filePath, getApplicationContext());

		try {
			file = file.createFile("application/*", fileName);

			OutputStream out = getContentResolver().openOutputStream(file.getUri());
			InputStream in = new ByteArrayInputStream(lyricsToString(useThreeDigitMilliseconds).getBytes(StandardCharsets.UTF_8));

			byte[] buffer = new byte[1024];
			int read;
			while ((read = in.read(buffer)) != -1) {
				out.write(buffer, 0, read);
			}
			in.close();

			out.flush();
			out.close();

			runOnUiThread(() -> saveSuccessful(filePath + "/" + fileName));

		} catch (IOException | NullPointerException | IllegalArgumentException | SecurityException | UnsupportedOperationException e) {
			e.printStackTrace();
			runOnUiThread(() -> {
				statusTextView.setTextColor(ContextCompat.getColor(getApplicationContext(), R.color.errorColor));
				statusTextView.setText(String.format(Locale.getDefault(),
						getString(R.string.whoops_error) + "\n%s\n%s", e.getMessage(), getString(R.string.save_suggestion)));

				Button copy_error = findViewById(R.id.copy_error_button);
				copy_error.setVisibility(View.VISIBLE);
			});
		}
	}

	public void setAsLRCFileName(View view) {
		((EditText) dialogView.findViewById(R.id.dialog_edittext)).setText(lrcFileName);
	}

	private String lyricsToString(boolean useThreeDigitMilliseconds) {
		StringBuilder sb = new StringBuilder();

		String str;
		str = artistName.getText().toString().trim();
		if (!str.isEmpty())
			sb.append("[ar: ").append(str).append("]\n");
		str = albumName.getText().toString().trim();
		if (!str.isEmpty())
			sb.append("[al: ").append(str).append("]\n");
		str = songName.getText().toString().trim();
		if (!str.isEmpty())
			sb.append("[ti: ").append(str).append("]\n");
		str = composerName.getText().toString().trim();
		if (!str.isEmpty())
			sb.append("[au: ").append(str).append("]\n");
		str = creatorName.getText().toString().trim();
		if (!str.isEmpty())
			sb.append("[by: ").append(str).append("]\n");

		sb.append("\n")
				.append("[re: ").append(getString(R.string.app_name)).append(" - Android app").append("]\n")
				.append("[ve: ").append("Version ").append(BuildConfig.VERSION_NAME).append("]\n")
				.append("\n");

		for (int i = 0, len = lyricData.size(); i < len; i++) {
			Timestamp timestamp = lyricData.get(i).getTimestamp();
			if (timestamp != null) {
				String lyric = lyricData.get(i).getLyric();
				if (lyric == null || lyric.equals("")) {
					// Some players might skip empty lyric lines (I'm looking at you Huawei music player)
					// Hence we replace empty lyric lines with a space
					lyric = " ";
				}
				if (useThreeDigitMilliseconds) {
					sb.append("[").append(timestamp.toStringWithThreeDigitMilliseconds(Locale.ENGLISH)).append("]").append(lyric).append("\n");
				} else {
					sb.append("[").append(timestamp.toString(Locale.ENGLISH)).append("]").append(lyric).append("\n");
				}
			}
		}

		return sb.toString();
	}

	public void setAsSongFileName(View view) {
		EditText editText = dialogView.findViewById(R.id.dialog_edittext);
		try {
			int dotPosition = songFileName.lastIndexOf('.');
			int songNameLength = songFileName.length();

			if (dotPosition != -1 && (dotPosition == songNameLength - 4 || dotPosition == songNameLength - 5)) {
				editText.setText(String.format("%s.lrc", songFileName.substring(0, dotPosition)));
				return;
			}
		} catch (IndexOutOfBoundsException ignored) {
		}

		editText.setText(String.format("%s.lrc", songFileName));
	}

	private boolean grantPermission() {
		if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
				!= PackageManager.PERMISSION_GRANTED) {
			displayDialog();
			return false;
		}
		return true;
	}

	private void saveSuccessful(String fileName) {
		statusTextView.setTextColor(ContextCompat.getColor(this, R.color.successColor));
		String strippedExtensionFileName;
		if (fileName.endsWith(".lrc")) {
			strippedExtensionFileName = fileName.substring(0, fileName.lastIndexOf('.'));
		} else {
			strippedExtensionFileName = fileName;
		}

		// These filenames may not be accurate in certain cases mainly because Android (vfat) handles files case insensitively
		if (overwriteFailed) {
			statusTextView.setText(getString(R.string.save_successful, strippedExtensionFileName + "<suffix> .lrc"));
		} else {
			statusTextView.setText(getString(R.string.save_successful, strippedExtensionFileName + ".lrc"));
		}
	}

	private void displayDialog() {
		new AlertDialog.Builder(this)
				.setMessage(getString(R.string.storage_permission_prompt))
				.setTitle(getString(R.string.need_permissions))
				.setCancelable(false)
				.setPositiveButton(getString(R.string.ok), (dialog, which) -> ActivityCompat.requestPermissions(FinalizeActivity.this,
						new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, Constants.WRITE_EXTERNAL_REQUEST))
				.show();
	}

	public void hideKeyboard() {
		InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
		// Find the currently focused view, so we can grab the correct window token from it.
		View view = this.getCurrentFocus();
		// If no view currently has focus, create a new one, just so we can grab a window token from it
		if (view == null) {
			view = new View(this);
		}
		if (imm != null) {
			imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
		}
	}

	@TargetApi(Build.VERSION_CODES.M)
	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
		super.onRequestPermissionsResult(requestCode, permissions, grantResults);

		if (requestCode == Constants.WRITE_EXTERNAL_REQUEST) {
			for (int i = 0; i < permissions.length; i++) {
				String permission = permissions[i];
				int grantResult = grantResults[i];

				if (permission.equals(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
					if (grantResult == PackageManager.PERMISSION_GRANTED) {
						Button button = findViewById(R.id.save_button);
						button.performClick();
						return;
					} else {
						Toast.makeText(this, getString(R.string.cannot_save_without_permission_message), Toast.LENGTH_LONG).show();
					}
				}
			}
		}
	}

	/* Checks if external storage is available for read and write */
	public boolean isExternalStorageWritable() {
		String state = Environment.getExternalStorageState();
		return Environment.MEDIA_MOUNTED.equals(state);
	}

	public void copyLrc(View view) {
		ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData clip = ClipData.newPlainText("Generated LRC data", lyricsToString(useThreeDigitMilliseconds));
		if (clipboard != null) {
			clipboard.setPrimaryClip(clip);
		} else {
			Toast.makeText(this, getString(R.string.failed_to_fetch_clipboard_message), Toast.LENGTH_LONG).show();
			return;
		}

		Toast.makeText(this, getString(R.string.copy_lrc_file_data_successful_message), Toast.LENGTH_LONG).show();
	}

	public void copyError(View view) {
		ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
		ClipData clip = ClipData.newPlainText("Save Error Info", statusTextView.getText().toString());
		if (clipboard != null) {
			clipboard.setPrimaryClip(clip);
		} else {
			Toast.makeText(this, getString(R.string.failed_to_fetch_clipboard_message), Toast.LENGTH_LONG).show();
			return;
		}

		Toast.makeText(this, getString(R.string.copy_error_successful_message), Toast.LENGTH_LONG).show();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		if (item.getItemId() == android.R.id.home) {
			onBackPressed();
			return true;
		}

		return (super.onOptionsItemSelected(item));
	}

	// Enables the user to select a save path for the file without changing the global settings.
	public void changeSaveLocation(View view) {
		Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
		intent.addCategory(Intent.CATEGORY_DEFAULT);
		intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
		try {
			startActivityForResult(intent, Constants.SAVE_LOCATION_REQUEST);
		} catch (ActivityNotFoundException e) {
			Toast.makeText(this, "Whoops! " + getString(R.string.failed_to_open_directory_picker_message), Toast.LENGTH_SHORT).show();
			Toast.makeText(this, getString(R.string.documentsui_enable_message), Toast.LENGTH_LONG).show();
			e.printStackTrace();
		}
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
		super.onActivityResult(requestCode, resultCode, resultData);
		if (requestCode == Constants.SAVE_LOCATION_REQUEST && resultCode == Activity.RESULT_OK) {
			if (resultData != null) {
				Uri uri = resultData.getData();
				if (uri != null) {
					saveUri = uri;
					saveLocation = FileUtil.getFullPathFromTreeUri(uri, this);
					if (dialogView != null) {
						// Shouldn't really happen, but got a crash report for this
						TextView saveLocationDisplayer = dialogView.findViewById(R.id.save_location_display);
						saveLocationDisplayer.setText(getString(R.string.save_location_displayer, saveLocation));
					}
				}
			}
		}
	}
}
