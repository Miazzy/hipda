package net.jejer.hipda.async;

import android.content.AsyncTaskLoader;
import android.content.Context;
import android.util.Log;

import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.StringRequest;

import net.jejer.hipda.bean.SimpleListBean;
import net.jejer.hipda.utils.HiParser;
import net.jejer.hipda.utils.HiUtils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

public class SimpleListLoader extends AsyncTaskLoader<SimpleListBean> {
	public static final int TYPE_MYREPLY = 0;
	public static final int TYPE_MYPOST = 1;
	public static final int TYPE_SEARCH = 2;
	public static final int TYPE_SMS = 3;
	public static final int TYPE_THREADNOTIFY = 4;
	public static final int TYPE_SMSDETAIL = 5;
	public static final int TYPE_FAVORITES = 6;

	private final String LOG_TAG = getClass().getSimpleName();
	private Context mCtx;
	private int mType;
	private int mPage = 1;
	private String mExtra = "";
	private Object mLocker;
	private String mRsp;

	public SimpleListLoader(Context context, int type, int page, String extra) {
		super(context);
		mCtx = context;
		mType = type;
		mPage = page;
		mExtra = extra;
		mLocker = this;
	}

	@Override
	public SimpleListBean loadInBackground() {
		//Log.v(LOG_TAG, "loadInBackground Enter");

		Document doc = null;
		int count = 0;
		boolean getOk = false;
		do {
			fetchSimpleList(mType);

			synchronized (mLocker) {
				try {
					mLocker.wait();
				} catch (InterruptedException e) {
				}
			}
			//Log.v(LOG_TAG, "loadInBackground got notified");
			if (mRsp != null) {
				doc = Jsoup.parse(mRsp);
				if (!LoginAsyncTask.checkLoggedin(null, doc)) {
					new LoginAsyncTask(mCtx, null).doInBackground();
				} else {
					getOk = true;
				}
			}
			count++;
			//Log.v(LOG_TAG, "try count = " + String.valueOf(count));
		} while (!getOk && count < 3);

		if (!getOk) {
			return null;
		}

		return HiParser.parseSimpleList(mCtx, mType, doc);
	}

	private void fetchSimpleList(int type) {
		String url = null;
		switch (type) {
			case TYPE_MYREPLY:
				url = HiUtils.MyReplyUrl + "&page=" + mPage;
				break;
			case TYPE_MYPOST:
				url = HiUtils.MyPostUrl + "&page=" + mPage;
				break;
			case TYPE_SMS:
				url = HiUtils.SMSUrl;
				break;
			case TYPE_THREADNOTIFY:
				url = HiUtils.ThreadNotifyUrl;
				break;
			case TYPE_SMSDETAIL:
				url = HiUtils.SMSDetailUrl + mExtra;
				break;
			case TYPE_SEARCH:
				try {
					url = HiUtils.SearchTitle + URLEncoder.encode(mExtra, "GBK");
				} catch (UnsupportedEncodingException e) {
					Log.e(LOG_TAG, "Encoding error", e);
				}
				break;
			case TYPE_FAVORITES:
				url = HiUtils.FavoritesUrl;
				break;
			default:
				break;
		}

		StringRequest sReq = new HiStringRequest(mCtx, url,
				new ThreadListListener(), new ThreadListErrorListener());
		VolleyHelper.getInstance().add(sReq);
	}

	private class ThreadListListener implements Response.Listener<String> {
		@Override
		public void onResponse(String response) {
			//Log.v(LOG_TAG, "onResponse");
			mRsp = response;
			synchronized (mLocker) {
				mLocker.notify();
			}
		}
	}

	private class ThreadListErrorListener implements Response.ErrorListener {
		@Override
		public void onErrorResponse(VolleyError error) {
			Log.e(LOG_TAG, error.toString());
			synchronized (mLocker) {
				mRsp = null;
				mLocker.notify();
			}
		}
	}

}