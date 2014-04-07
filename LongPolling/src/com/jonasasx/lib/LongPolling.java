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
import com.loopj.android.http.JsonHttpResponseHandler;
import com.loopj.android.http.RequestParams;
import com.loopj.android.http.TextHttpResponseHandler;

/**
 * @author jonasasx@gmail.com
 * 
 */
public class LongPolling {
	private final static String LOG_TAG = "LongPolling";
	private Context context;
	private String _channel;
	private TextHttpResponseHandler _responseHandler;
	private AsyncHttpClient httpClient;
	private String _url;
	private String date = null;
	private String etag = "0";
	private int _dateLast = 5;
	private int _timeOut = 31000;
	private Boolean listening = false;
	private List<Integer> hashes = new ArrayList<Integer>();

	/**
	 * @param context
	 * @param url
	 *            example: http://example.com/lp/
	 * @param channel
	 *            may be generated with LongPolling.channel
	 * @param responseHandler
	 *            json response handler
	 */
	public LongPolling(Context context, String url, String channel, final JsonHttpResponseHandler responseHandler) {
		this.context = context;
		httpClient = new AsyncHttpClient();
		httpClient.setTimeout(_timeOut);
		_channel = channel;
		_url = url + _channel;
		_responseHandler = new TextHttpResponseHandler() {

			@Override
			public void onFailure(int statusCode, Header[] headers, String responseString, Throwable throwable) {
				responseHandler.onFailure(statusCode, headers, responseString, throwable);
				parseHeaders(headers);
				get();
			}

			@Override
			public void onSuccess(int statusCode, Header[] headers, String responseString) {
				String[] responses = responseString.replaceAll("\\}\\{", "}\r\n{").split("\r\n");
				for (String subResponse : responses) {
					int hash = subResponse.hashCode();
					if (hashes.contains(hash))
						return;
					hashes.add(hash);
					Object parsed;
					try {
						parsed = parseResponse(subResponse.getBytes(getCharset()));
						if (parsed instanceof JSONObject) {
							responseHandler.onSuccess(statusCode, headers, (JSONObject) parsed);
							Log.v(LOG_TAG, ((JSONObject) parsed).toString());
						} else if (parsed instanceof JSONArray) {
							responseHandler.onSuccess(statusCode, headers, (JSONArray) parsed);
						}
					} catch (UnsupportedEncodingException e) {
						e.printStackTrace();
					} catch (JSONException e) {
						e.printStackTrace();
					}
				}
				responseHandler.onSuccess(statusCode, headers, responseString);
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

	public void connect() {
		if (listening)
			return;
		if (date == null)
			date = makeDate();
		listening = true;
		get();
	}

	public void disconnect() {
		if (!listening)
			return;
		listening = false;
		httpClient.cancelRequests(context, true);
	}

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

	private void get() {
		if (!listening)
			return;
		Header[] headers = new Header[] { new BasicHeader("If-Modified-Since", date), new BasicHeader("If-None-Match", etag), };
		RequestParams params = null;
		try {
			httpClient.get(context, _url, headers, params, _responseHandler);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * @param uid
	 *            User id
	 * @param service
	 *            Service name
	 * @param sid
	 *            Service id
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
	 * @param uid
	 *            User id
	 * @param service
	 *            Service name
	 * @param sid
	 *            Service id
	 * @return Channel string
	 */
	public static String channel(String uid, String service, int sid) {
		if (uid == null)
			uid = "0";
		return LongPolling.channel(Integer.parseInt(uid), service, sid);
	}

	/**
	 * @param uid
	 *            User id
	 * @param service
	 *            Service name
	 * @param sid
	 *            Service id
	 * @return Channel string
	 */
	public static String channel(int uid, String service, int sid) {
		return Integer.toString(uid) + "_" + service + "_" + Integer.toString(sid);
	}

}
