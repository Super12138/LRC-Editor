package com.cg.lrceditor;

import android.content.Context;
import android.net.Uri;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import com.cg.lrceditor.R;
import java.util.Collections;
import java.util.Comparator;

public class LyricReader {
	private ArrayList<LyricItem> lyricData = new ArrayList<>();
	private Metadata metadata = new Metadata();

	private File file = null;
	private InputStream in = null;
	private String errorMsg;

	private Context ctx;

	LyricReader(String path, String fileName, Context c) {
		this.file = new File(path, fileName);
		this.ctx = c;
	}

	LyricReader(Uri uri, Context c) {
		this.ctx = c;
		try {
			this.in = c.getContentResolver().openInputStream(uri);
		} catch (FileNotFoundException e) {
			e.printStackTrace();
			errorMsg = "Oops! " + ctx.getString(R.string.file_not_found_message) + "\n" + e.getMessage();
		} catch (SecurityException e) {
			e.printStackTrace();
			errorMsg = "Oops! " + ctx.getString(R.string.no_permission_to_read_text) + "\n" + e.getMessage();
		}
	}

	boolean readLyrics() {
		try {
			DataInputStream in;
			if (file != null) {
				FileInputStream fis = new FileInputStream(file);
				in = new DataInputStream(fis);
			} else if (this.in != null) {
				in = new DataInputStream(this.in);
			} else {
				errorMsg = "Oops! " + ctx.getString(R.string.failed_to_open_input_stream_message);
				return false;
			}

			this.lyricData.clear();

			BufferedReader br = new BufferedReader(new InputStreamReader(in));

			StringBuilder contents = new StringBuilder();
			String temp;
			/* Read the file's contents into `contents` */
			while ((temp = br.readLine()) != null) {
				contents.append(temp);
				contents.append('\n');
			}

			in.close();

			int count, extras;
			int offset = 0;
			int invalidTimestamps;

			/* Loop over each line of `contents` */
			for (String line : contents.toString().split("\\n")) {
				count = 0;
				extras = 0;
				invalidTimestamps = 0;
				/* Loop to find multiple timestamps in a single line (Condensed LRC format) */
				while (true) {
					/* `count` keeps track of the number of timestamps and `extras` keeps track of three digit milliseconds in a timestamp */
					temp = line.substring(count * 10 + extras);

					if (temp.matches("^(\\[\\d\\d[:.]\\d\\d[:.]\\d\\d\\d?]).*$")) {
						count++;
						if (temp.charAt(4) - '0' >= 6) { // Invalid timestamp; seconds is >= 60; ignore it
							invalidTimestamps++;
							continue;
						}
						if (temp.charAt(9) != ']') {
							// 3 digit millisecond
							extras++;
							lyricData.add(new LyricItem(null, new Timestamp(temp.substring(1, 10))));
						} else {
							// 2 digit millisecond
							lyricData.add(new LyricItem(null, new Timestamp(temp.substring(1, 9))));
						}
					} else {
						if (temp.length() > 4) {
							String str = temp.substring(4, temp.length() - 1).trim();
							if (metadata.getSongName().isEmpty() && temp.matches("^\\[ti:.*]$")) {
								metadata.setSongName(str);
							} else if (metadata.getArtistName().isEmpty() && temp.matches("^\\[ar:.*]$")) {
								metadata.setArtistName(str);
							} else if (metadata.getAlbumName().isEmpty() && temp.matches("^\\[al:.*]$")) {
								metadata.setAlbumName(str);
							} else if (metadata.getComposerName().isEmpty() && temp.matches("^\\[au:.*]$")) {
								metadata.setComposerName(str);
							} else if (metadata.getCreatorName().isEmpty() && temp.matches("^\\[by:.*]$")) {
								metadata.setCreatorName(str);
							} else if (offset == 0 && temp.matches("^\\[offset:.*]$")) {
								try {
									offset = Integer.parseInt(temp.substring(8, temp.length() - 1).trim());
								} catch (NumberFormatException e) { // Ignore the offset if we couldn't scan it
									e.printStackTrace();
								}
							}
						}

						break;
					}
				}

				if (temp.trim().isEmpty())
					temp = " ";

				count -= invalidTimestamps;

				for (int i = 0, size = lyricData.size(); i < count; i++) {
					lyricData.get(size - i - 1).setLyric(temp.trim());
				}
			}

			if (lyricData.size() == 0) {
				errorMsg = ctx.getString(R.string.could_not_parse_any_lyrics_message);
				return false;
			}

			if (offset != 0) {
				int size = lyricData.size();
				for (int i = 0; i < size; i++) {
					lyricData.get(i).getTimestamp().alterTimestamp(offset);
				}
			}

			Collections.sort(this.lyricData, new LyricTimestampComparator());

		} catch (FileNotFoundException e) {
			e.printStackTrace();
			errorMsg = "Oops! " + ctx.getString(R.string.file_not_found_message) + "\n" + e.getMessage();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			errorMsg = "Oops! " + ctx.getString(R.string.error_occurred_when_reading_message) + "\n" + e.getMessage();
			return false;
		}

		return true;
	}

	ArrayList<LyricItem> getLyricData() {
		if (this.lyricData.size() == 0) {
			return null;
		}

		return lyricData;
	}

	String getErrorMsg() {
		return errorMsg;
	}

	Metadata getMetadata() {
		return metadata;
	}

	static class LyricTimestampComparator implements Comparator<LyricItem> {
		@Override
		public int compare(LyricItem l1, LyricItem l2) {
			Timestamp t1 = l1.getTimestamp();
			Timestamp t2 = l2.getTimestamp();

			if (t1 == null && t2 == null) {
				return 0;
			} else if (t1 == null) {
				return -1;
			} else if (t2 == null) {
				return 1;
			}

			return Long.compare(t1.toMilliseconds(), t2.toMilliseconds());
		}
	}
}
