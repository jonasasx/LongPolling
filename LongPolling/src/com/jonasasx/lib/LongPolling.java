package com.jonasasx.lib;

import java.io.UnsupportedEncodingException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONTokener;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.TextHttpResponseHandler;

// TODO: Auto-generated Javadoc
/**
 * The Class LongPolling.
 *
 * @author jonasasx@gmail.com
 */
public class LongPolling {

	private static final String		LOG_TAG		= "LongPolling";

	/** The context. */
	private Context					context;

	/** The _channel. */
	private String					_channel;

	/** The _response handler. */
	private TextHttpResponseHandler	_responseHandler;

	/** The http client. */
	private AsyncHttpClient			httpClient;

	/** The _url. */
	private String					_url;

	/** The date. */
	private String					date		= null;

	/** The etag. */
	private String					etag		= "0";

	/** The _date last. */
	private int						_dateLast	= 5;

	/** The _time out. */
	private int						_timeOut	= 31000;

	/** The listening. */
	private boolean					mListening	= false;

	/** The hashes. */
	private List<Integer>			hashes		= new ArrayList<Integer>();

	/** The m on reconnect listener. */
	private OnReconnectListener		mOnReconnectListener;

	/**
	 * Instantiates a new long polling.
	 *
	 * @param context the context
	 * @param url example: http://example.com/lp/
	 * @param channel may be generated with LongPolling.channel
	 * @param responseHandler json response handler
	 */
	public LongPolling(Context context, String url, String channel, final OnMessageListener responseHandler) {
		this.context = context;
		httpClient = new AsyncHttpClient();
		httpClient.setTimeout(_timeOut);
		_channel = channel;
		if (_channel == null)
			return;
		_url = url + _channel;
		_responseHandler = new TextHttpResponseHandler() {

			@Override
			public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
				parseHeaders(headers);
				get();
			}

			@Override
			public void onSuccess(int statusCode, Header[] headers, String responseString) {
				String[] responses = responseString.replaceAll("\\}\\{", "}\r\n{").split("\r\n");
				for (String subResponse : responses) {
					int hash = subResponse.hashCode();
					if (hashes.contains(hash))
						continue;
					hashes.add(hash);
					Object parsed;
					try {
						parsed = parseResponse(subResponse.getBytes(getCharset()));
						if (parsed instanceof JSONObject) {
							responseHandler.onMessage((JSONObject) parsed);
						} else if (parsed instanceof JSONArray) {
							responseHandler.onMessage((JSONArray) parsed);
						}
					} catch (UnsupportedEncodingException e) {
					} catch (JSONException e) {
					}
				}
				responseHandler.onMessage(responseString);
				parseHeaders(headers);
				get();
			}

			protected Object parseResponse(byte[] responseBody) throws JSONException {
				if (null == responseBody)
					return null;
				Object result = null;
				String jsonString = getResponseString(responseBody, getCharset());
				if (jsonString != null) {
					jsonString = jsonString.trim();
					if (jsonString.startsWith("{") || jsonString.startsWith("[")) {
						result = new JSONTokener(jsonString).nextValue();
					}
				}
				if (result == null) {
					result = jsonString;
				}
				return result;
			}
		};
	}

	/**
	 * The listener interface for receiving onMessage events. The class that is interested in processing a onMessage event implements this interface, and the object created with that class is
	 * registered with a component using the component's <code>addOnMessageListener<code> method. When
	 * the onMessage event occurs, that object's appropriate
	 * method is invoked.
	 *
	 * @see OnMessageEvent
	 */
	public static abstract class OnMessageListener {

		/**
		 * On message.
		 *
		 * @param response the response
		 */
		public void onMessage(JSONObject response) {
		};

		/**
		 * On message.
		 *
		 * @param response the response
		 */
		public void onMessage(JSONArray response) {
		};

		/**
		 * On message.
		 *
		 * @param response the response
		 */
		public void onMessage(String response) {
		};
	}

	/**
	 * The listener interface for receiving onReconnect events. The class that is interested in processing a onReconnect event implements this interface, and the object created with that class is
	 * registered with a component using the component's <code>addOnReconnectListener<code> method. When
	 * the onReconnect event occurs, that object's appropriate
	 * method is invoked.
	 *
	 * @see OnReconnectEvent
	 */
	public static interface OnReconnectListener {

		/**
		 * On reconnect.
		 *
		 * @return true, if successful
		 */
		public boolean onReconnect();
	}

	/**
	 * Connect.
	 */
	public void connect() {
		if (_channel == null)
			return;
		if (mListening)
			return;
		if (date == null)
			date = makeDate();
		mListening = true;
		Log.v(LOG_TAG, "Long Polling connected on " + _channel);
		get();
	}

	/**
	 * Disconnect.
	 */
	public void disconnect() {
		if (!mListening)
			return;
		mListening = false;
		httpClient.cancelRequests(context, true);
		Log.v(LOG_TAG, "Long Polling disconnected on " + _channel);
	}

	/**
	 * Parses the headers.
	 *
	 * @param headers the headers
	 */
	private void parseHeaders(Header[] headers) {
		if (headers == null)
			return;
		for (Header header : headers) {
			if (header.getName().toString().equals("Etag")) {
				etag = header.getValue().toString();
			}
			if (header.getName().toString().equals("Last-Modified")) {
				date = header.getValue().toString();
			}
		}
	}

	/**
	 * Make date.
	 *
	 * @return the string
	 */
	@SuppressLint("SimpleDateFormat")
	private String makeDate() {
		Locale oldLocale = Locale.getDefault();
		Locale.setDefault(Locale.US);
		SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz");
		format.setTimeZone(TimeZone.getTimeZone("GMT"));
		Date d = new Date();
		Calendar c = Calendar.getInstance();
		c.setTime(d);
		c.add(Calendar.SECOND, -_dateLast);
		String s = format.format(c.getTime()).toString();
		Locale.setDefault(oldLocale);
		return s;
	}

	/**
	 * Making the request.
	 */
	private void get() {
		if (!mListening)
			return;
		if (mOnReconnectListener != null && !mOnReconnectListener.onReconnect())
			return;
		Header[] headers = new Header[] { new BasicHeader("If-Modified-Since", date), new BasicHeader("If-None-Match", etag), };
		RequestParams params = null;
		try {
			httpClient.get(context, _url, headers, params, _responseHandler);
		} catch (Exception e) {
		}
	}

	/**
	 * Channel.
	 *
	 * @param uid User id
	 * @param service Service name
	 * @param sid Service id
	 * @return Channel string
	 */
	public static String channel(String uid, String service, String sid) {
		if (uid == null)
			uid = "0";
		if (sid == null)
			sid = "0";
		return LongPolling.channel(Integer.parseInt(uid), service, Integer.parseInt(sid));
	}

	/**
	 * Channel.
	 *
	 * @param uid User id
	 * @param service Service name
	 * @param sid Service id
	 * @return Channel string
	 */
	public static String channel(String uid, String service, int sid) {
		if (uid == null)
			uid = "0";
		return LongPolling.channel(Integer.parseInt(uid), service, sid);
	}

	/**
	 * Channel.
	 *
	 * @param uid User id
	 * @param service Service name
	 * @param sid Service id
	 * @return Channel string
	 */
	public static String channel(int uid, String service, int sid) {
		return Integer.toString(uid) + "_" + service + "_" + Integer.toString(sid);
	}

	/**
	 * Sets the on reconnect listener.
	 *
	 * @param mOnReconnectListener the new on reconnect listener
	 */
	public void setOnReconnectListener(OnReconnectListener mOnReconnectListener) {
		this.mOnReconnectListener = mOnReconnectListener;
	}

	/**
	 * Checks if is listening.
	 *
	 * @return true, if is listening
	 */
	public boolean isListening() {
		return mListening;
	}

	/**
	 * Sets the listening.
	 *
	 * @param mListening the new listening
	 */
	public void setListening(boolean mListening) {
		this.mListening = mListening;
	}

}
