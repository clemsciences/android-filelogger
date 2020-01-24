package fr.clementbesnier.filelogger;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Build;
import android.support.v4.content.FileProvider;
import android.util.Log;
import android.widget.Toast;

import java.io.File;

/**
 * Received for the collected logs.
 * @see #sendMail(FileLogger, File)
 */
public class LogCollectorEmail implements LogCollecting {

	public static final int RESULT_SHARE = 0x3155;

	private final String[] mRecipients;
	private final String mTitle;
	private final String mDialogTitle;
	private final String mText;

	private final Context mContext;

	/**
	 * Constructor for the e-mail collector. Use {@link #sendMail(FileLogger, File)} to send the logs from a file logger.
	 *
	 * @param context
	 * @param recipients  list of email addresses to send the emails to, may be {@code null}
	 * @param emailTitle  title of the email, may be {@code null}
	 * @param dialogTitle title of the dialog shown to the user, may be {@code null}
	 * @param text        body text of the email, may be {@code null}
	 */
	public LogCollectorEmail(Context context, String[] recipients, String emailTitle, String dialogTitle, String text) {
		if (context == null) throw new NullPointerException("null context");
		mRecipients = recipients;
		mTitle = emailTitle;
		mDialogTitle = dialogTitle;
		mText = text;
		mContext = context;
	}

	/**
	 * Send the content of the FileLogger via email using {@link tempLogFile} as a temporary storage file.
	 *
	 * @param logger      the logger containing the data to send
	 * @param tempLogFile the temporary file used to store the content of the email (call {@link File#deleteOnExit()} when done).
	 *                    The file needs to be readable by another process
	 * @param authority   the authority used to get Uri from tempLogFile
	 */
	public void sendMail(FileLogger logger, File tempLogFile, String authority) {
		logger.setFinalPath(tempLogFile);
		logger.collectlogs(this);
		logger.setAuthority(authority);
	}

	public Uri getFileUriBySDKVersion(File file, String authority) {
		Log.d(LogCollectorEmail.class.getSimpleName(), "getFileUriBySDKVersion file: " + file + ", authority: " + authority);
		if (file == null){
			Log.e(LogCollectorEmail.class.getSimpleName(), "Error in getting Uri by file, file is null.");
			return null;
		}

		Uri result;
		if (Build.VERSION.SDK_INT >= 24) {
			if(authority == null) {
				Log.e(LogCollectorEmail.class.getSimpleName(), "Error in getting Uri by file, authority is null.");
				return null;
			}
			result = FileProvider.getUriForFile(mContext, authority, file);
		} else {
			result = Uri.fromFile(file);
		}
		return result;
	}

	@Override
	public void onLogCollected(File path, String mimeType, String authority) {
		Intent emailIntent = new Intent(Intent.ACTION_SEND);
		emailIntent.setType("message/rfc822"/* "text/plain" */);
		//emailIntent.putExtra(android.content.Intent.EXTRA_STREAM, Uri.fromFile(path));
		emailIntent.putExtra(Intent.EXTRA_STREAM, getFileUriBySDKVersion(path, authority)); //fixed crash on API 24 and above
		emailIntent.setType(mimeType);
		if (mRecipients != null) emailIntent.putExtra(Intent.EXTRA_EMAIL, mRecipients);
		if (mTitle != null) emailIntent.putExtra(Intent.EXTRA_SUBJECT, mTitle);
		if (mText != null) emailIntent.putExtra(Intent.EXTRA_TEXT, mText);
		emailIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

		ResolveInfo handler = mContext.getPackageManager().resolveActivity(emailIntent, PackageManager.MATCH_DEFAULT_ONLY);
		if (null == handler) {
			if (mContext instanceof Activity) {
				((Activity) mContext).runOnUiThread(new Runnable() {
					@Override
					public void run() {
						Toast.makeText(mContext, R.string.error_no_email, Toast.LENGTH_LONG).show();
					}
				});
			} else {
				Toast.makeText(mContext, R.string.error_no_email, Toast.LENGTH_LONG).show();
			}
		} else {
			Intent intent = Intent.createChooser(emailIntent, mDialogTitle != null ? mDialogTitle : mTitle);
			if (mContext instanceof Activity)
				((Activity) mContext).startActivityForResult(intent, RESULT_SHARE);
			else {
				intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				mContext.startActivity(intent);
			}
		}
	}

	@Override
	public void onLogCollectingError(String error) {
		FLog.e("FileLogger", error);
		if (mContext instanceof Activity) {
			((Activity) mContext).runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(mContext, R.string.error_collecting_logs, Toast.LENGTH_LONG).show();
				}
			});
		} else {
			Toast.makeText(mContext, R.string.error_collecting_logs, Toast.LENGTH_LONG).show();
		}
	}

	@Override
	public void onEmptyLogCollected() {
		if (mContext instanceof Activity) {
			((Activity) mContext).runOnUiThread(new Runnable() {
				@Override
				public void run() {
					Toast.makeText(mContext, R.string.log_empty, Toast.LENGTH_LONG).show();
				}
			});
		} else {
			Toast.makeText(mContext, R.string.log_empty, Toast.LENGTH_LONG).show();
		}
	}
}