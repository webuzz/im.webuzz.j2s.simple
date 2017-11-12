/*******************************************************************************
 * Copyright (c) 2007 java2script.org and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Zhou Renjian - initial API and implementation
 *******************************************************************************/
package net.sf.j2s.ajax;

import java.io.ByteArrayOutputStream;
import java.util.LinkedList;
import java.util.List;

import net.sf.j2s.ajax.HttpRequest;
import net.sf.j2s.ajax.SimpleRPCRequest;
import net.sf.j2s.ajax.SimpleSerializable;
import net.sf.j2s.ajax.XHRCallbackAdapter;
import net.sf.j2s.annotation.J2SIgnore;

/**
 * 
 * @author zhou renjian
 * 
 * @j2sSuffix
 * window["$p1p3p$"] = net.sf.j2s.ajax.SimplePipeRequest.parseReceived;
 * window["$p1p3b$"] = net.sf.j2s.ajax.SimplePipeRequest.pipeNotifyCallBack;
 */
public class SimplePipeRequest extends SimpleRPCRequest {
	
	@J2SIgnore
	public static interface IHttpPipeRequestFactory extends IHttpRequestFactory {

		public HttpRequest createRequestWithMonitor(HttpRequest.IXHRReceiving monitor);
		
	}

	/**
	 * @j2sNative
	 */
	static {
		SimpleSerializable.registerClassShortenName(SimplePipeSequence.class.getName(), "SPS");
	}

	@J2SIgnore
	private static Object notifyingMutex = new Object();

	@J2SIgnore
	private static boolean notifyingThreadStarted = false;
	
	@J2SIgnore
	private static List<SimplePipeRunnable> notifyingPipes = new LinkedList<SimplePipeRunnable>();
	
	/**
	 * Status of pipe: ok.
	 */
	public static final char PIPE_STATUS_OK = 'o'; // "ok";

	/**
	 * Status of pipe: destroyed.
	 */
	public static final char PIPE_STATUS_DESTROYED = 'd'; // "destroyed";

	/**
	 * Status of pipe: continue.
	 */
	public static final char PIPE_STATUS_CONTINUE = 'e'; // "continue";
	
	/**
	 * Status of pipe: lost.
	 */
	public static final char PIPE_STATUS_LOST = 'l'; // "lost";

	
	/**
	 * Type of pipe request: query
	 */
	public static final char PIPE_TYPE_QUERY = 'q';
	
	/**
	 * Type of pipe request: subdomain-query
	 */
	public static final char PIPE_TYPE_SUBDOMAIN_QUERY = 'u';
	
	/**
	 * Type of pipe request: notify
	 */
	public static final char PIPE_TYPE_NOTIFY = 'n';
	
	/**
	 * Type of pipe request: script
	 */
	public static final char PIPE_TYPE_SCRIPT = 's';
	
	/**
	 * Type of pipe request: xss
	 */
	public static final char PIPE_TYPE_XSS = 'x';
	
	/**
	 * Type of pipe request: continuum
	 */
	public static final char PIPE_TYPE_CONTINUUM = 'c';
	
	
	/**
	 * Query key for pipe: pipekey
	 */
	public static final char FORM_PIPE_KEY = 'k'; // "pipekey";
	
	/**
	 * Query key for pipe: pipetype
	 */
	public static final char FORM_PIPE_TYPE = 't'; // "pipetype";
	
	/**
	 * Query key for pipe: pipetype
	 */
	public static final char FORM_PIPE_DOMAIN = 'd'; // "pipedomain";

	/**
	 * Query key for pipe: pipernd
	 */
	public static final char FORM_PIPE_RANDOM = 'r'; // "pipernd";

	/**
	 * Query key for pipe: pipeseq
	 */
	public static final char FORM_PIPE_SEQUENCE = 's'; // "pipeseq";
	
	static final int PIPE_KEY_LENGTH = 6;

	public static final int MODE_PIPE_QUERY = 3;
	
	public static final int MODE_PIPE_CONTINUUM = 4;
	
	private static int pipeMode = MODE_PIPE_CONTINUUM;
	
	private static boolean queueNotifying = false;
	
	private static long pipeQueryInterval = 1000;
	
	static long pipeLiveNotifyInterval = 25000;
	
	private static long reqCount = 0;

	private static String lastPipeRequestURL = null;
	
	static Object pipeScriptMap = new Object();
	
	static Object pipeQueryMap = new Object();
	
	private static boolean escKeyAbortingDisabled = false;
	
	public static int getPipeMode() {
		return pipeMode;
	}
	
	public static long getQueryInterval() {
		return pipeQueryInterval;
	}
	
	public static void switchToQueryMode() {
		pipeMode = MODE_PIPE_QUERY;
		pipeQueryInterval = 1000;
	}
	
	public static void switchToQueryMode(long ms) {
		pipeMode = MODE_PIPE_QUERY;
		if (ms < 0) {
			ms = 1000;
		}
		pipeQueryInterval = ms;
	}
	
	public static void switchToContinuumMode() {
		pipeMode = MODE_PIPE_CONTINUUM;
		queueNotifying = false;
	}
	
	/**
	 * Switch to continuum mode, specifying whether sending notify request back to server in queue or not. 
	 * @param queue If queue is true, all local pipes' notify requests are sent asynchronously. If queue
	 * is false, notify requests are sent one by one in Pipe Live Notifier thread.
	 */
	public static void switchToContinuumMode(boolean queue) {
		pipeMode = MODE_PIPE_CONTINUUM;
		queueNotifying = queue;
	}
	
	/**
	 * Construct request string for pipe.
	 * @param pipeKey
	 * @param pipeRequestType
	 * @return request data for both GET and POST request. 
	 */
	protected static String constructRequest(String pipeKey, char pipeRequestType, long pipeSequence) {
		reqCount++;
		/**
		 * @j2sNative
		 * return"k="+pipeKey+"&t="+pipeRequestType+"&s="+pipeSequence+"&r="+net.sf.j2s.ajax.SimplePipeRequest.reqCount;
		 */ {
			return FORM_PIPE_KEY + "=" + pipeKey + "&" 
				+ FORM_PIPE_TYPE + "=" + pipeRequestType + "&"
				+ FORM_PIPE_SEQUENCE + "=" + pipeSequence + "&"
				+ FORM_PIPE_RANDOM + "=" + reqCount;
		 }
	}
	
	protected static void sendRequest(HttpRequest request, String method, String url, 
			String data, boolean async) {
		if ("GET".equals(method.toUpperCase())) {
			request.open(method, url + (url.indexOf('?') != -1 ? "&" : "?") + data, async);
			request.send(null);
		} else {
			request.open(method, url, async);
			request.send(data);
		}
	}
	
	/**
	 * 
	 * @param runnable
	 * 
	 * @j2sNative
	 * runnable.ajaxIn ();
	 * net.sf.j2s.ajax.SimplePipeRequest.pipeRequest(runnable);
	 */
	public static void pipe(final SimplePipeRunnable runnable) {
		runnable.ajaxIn();
		runnable.lastLiveDetected = System.currentTimeMillis();
		if (getRequstMode() == MODE_LOCAL_JAVA_THREAD) {
			SimpleThreadHelper.runTask(new Runnable() {
				public void run() {
					try {
						runnable.ajaxRun();
					} catch (Throwable e) {
						e.printStackTrace(); // should never fail in Java thread mode!
						runnable.ajaxFail();
						return;
					}
					keepPipeLive(runnable);
					runnable.ajaxOut();
				}
			}, "Simple Pipe Simulator");
		} else {
			pipeRequest(runnable);
		}
	}

	/*
	 * Be used in Java mode to keep the pipe live.
	 */
	@J2SIgnore
	static void keepPipeLive(final SimplePipeRunnable pipe) {
		if (pipe.pipeKey == null) {
			return;
		}
		pipe.updateStatus(true);
		if (getRequstMode() != MODE_LOCAL_JAVA_THREAD && getPipeMode() == MODE_PIPE_QUERY) {
			return;
		}
		//*
		boolean startingThread = false;
		synchronized (notifyingMutex) {
			if (!notifyingPipes.contains(pipe)) {
				pipe.lastPipeNotified = System.currentTimeMillis();
				notifyingPipes.add(pipe);
			}
			if (!notifyingThreadStarted) {
				startingThread = true;
				notifyingThreadStarted = true;
			}
		}
		if (!startingThread) {
			return;
		}
		Thread notifyThread = new Thread(new Runnable() {
			
			public void run() {
				while (true) {
					try {
						Thread.sleep(1000);
					} catch (InterruptedException e1) {
						//e1.printStackTrace();
					}
					
					SimplePipeRunnable[] pipes = null;
					synchronized (notifyingMutex) {
						int size = notifyingPipes.size();
						if (size == 0) {
							notifyingThreadStarted = false;
							return;
						}
						pipes = notifyingPipes.toArray(new SimplePipeRunnable[size]);
					}
					long now = System.currentTimeMillis();
					for (int i = 0; i < pipes.length; i++) {
						final SimplePipeRunnable p = pipes[i];
						if (getRequstMode() == MODE_LOCAL_JAVA_THREAD) {
							if (!p.isPipeLive()) {
								if (now - p.lastLiveDetected > p.pipeWaitClosingInterval()) {
									p.pipeDestroy(); // Pipe's server side destroying
									p.pipeClosed(); // Pipe's client side closing
									synchronized (notifyingMutex) {
										notifyingPipes.remove(p);
									}
								}
							} else if ((now - p.lastPipeNotified) * (2 + p.pipeSequence - p.notifySequence)
										< pipeLiveNotifyInterval + pipeLiveNotifyInterval) {
								// do nothing
							} else {
								p.keepPipeLive();
								p.lastPipeNotified = now;
								p.lastLiveDetected = System.currentTimeMillis();
							}
							continue; // end of MODE_LOCAL_JAVA_THREAD
						}
						
						SimplePipeRunnable r = SimplePipeHelper.getPipe(p.pipeKey);
						if (r == null || !r.isPipeLive()) {
							if (now - p.lastLiveDetected > p.pipeWaitClosingInterval()) {
								p.pipeDestroy(); // Pipe's server side destroying
								p.pipeClosed(); // Pipe's client side closing
								synchronized (notifyingMutex) {
									notifyingPipes.remove(p);
								}
							}
							continue; // to next pipe
						}
						p.lastLiveDetected = System.currentTimeMillis();
						p.updateStatus(true);
						if ((now - p.lastPipeNotified) * (2 + p.pipeSequence - p.notifySequence)
								< pipeLiveNotifyInterval + pipeLiveNotifyInterval) {
							// do nothing
							continue; // to next pipe
						}
						if (r.pipeSequence != r.notifySequence) {
							p.lastPipeNotified = now;
							final HttpRequest request = getRequest();
							final String pipeKey = p.pipeKey;
							final long sequence = p.pipeSequence;
							String pipeRequestData = constructRequest(pipeKey, PIPE_TYPE_NOTIFY, sequence);
							request.registerOnReadyStateChange(new XHRCallbackAdapter() {
								public void onLoaded() {
									String response = request.getResponseText();
									if (response == null || response.length() == 0 || response.indexOf("$p1p3b$") != 0) {
										// Incorrect response
										return;
									}
									if (p.notifySequence < sequence) {
										p.notifySequence = sequence;
									}
									if (response.indexOf("\"" + PIPE_STATUS_LOST + "\"") != -1) {
										p.pipeAlive = false;
										p.pipeLost();
										SimplePipeHelper.removePipe(pipeKey);
										// may need to inform user that connection is already lost!
										synchronized (notifyingMutex) {
											notifyingPipes.remove(p);
										}
									} else {
										p.lastLiveDetected = System.currentTimeMillis();
										p.updateStatus(true);
									}
								}
							});
							sendRequest(request, p.getPipeMethod(), p.getPipeURL(), pipeRequestData, pipes.length == 1 ? false : queueNotifying);
						}
					} // end of pipes for-loop
				} // end of while true
			}
		
		}, "Simple Pipe Live Notifier");
		notifyThread.setDaemon(true);
		notifyThread.start();
		// */
	}

	/**
	 * Press <ESC> key in browser will abort comet connection. Here try to
	 * disable such key being detected by browser.
	 * 
	 * @j2sNative
Clazz.addEvent (document, "keydown", function (e) {
	var ie = false;
	if (e == null) {
		e = window.event;
		ie = true;
	}
	var key = e.keyCode || e.charCode;
	if (key == 27) {
		if (ie) {
			e.returnValue = false;
		} else {
			e.preventDefault();
		}
		return false;
	}	
});
	 */
	private static void disableESCKeyConnectionAbsorting() {
	}
	
	private static void pipeRequest(final SimplePipeRunnable runnable) {
		if (!escKeyAbortingDisabled) {
			escKeyAbortingDisabled = true;
			disableESCKeyConnectionAbsorting();
		}
		String url = runnable.getHttpURL();
		String method = runnable.getHttpMethod();
		String serialize = runnable.serialize();
		if (method == null) {
			method = "POST";
		}
		Object ajaxOut = null;
		/**
		 * Need to call #ajaxPipe inside #checkXSS
		 * 
		 * @j2sNative
		 * ajaxOut = runnable.ajaxOut;
		 * if (ajaxOut.wrapped != true) {
		 * runnable.ajaxOut = (function (aO, r) {
		 * 	return function () {
		 * 		aO.apply (r, []);
		 * 		r.ajaxOut = aO;
		 * 		net.sf.j2s.ajax.SimplePipeRequest.ajaxPipe (r);
		 * 	};
		 * }) (ajaxOut, runnable);
		 * runnable.ajaxOut.wrapped = true;
		 * }
		 */ { if (ajaxOut == null) ajaxOut = null; /* no warning */ }
		if (checkXSS(url, serialize, runnable)) {
			// Already send out pipe request in XSS mode. Just return here.
			return;
		}
		/**
		 * @j2sNative
		 * runnable.ajaxOut = ajaxOut;
		 */ {}
		String url2 = SimpleRPCRequest.adjustRequestURL(method, url, serialize);
		if (url2 != url) {
			serialize = null;
		}
		/**
		 * @j2sNative
		 */
		if (serialize != null) {
			if (serialize.length() > 1024 && runnable.supportsGZipEncoding()) {
				byte[] bytes = serialize.getBytes(SimpleSerializable.ISO_8859_1);
				String zStr = new String(gzipCompress(bytes, 3, bytes.length - 3), SimpleSerializable.ISO_8859_1);
				serialize = "WLZ" + toBase62Length(zStr.length()) + zStr;
			}
		}
		final HttpRequest request = getRequest();
		if (!runnable.supportsKeepAlive()) {
			request.setRequestHeader("Connection", "close");
		}
		if (runnable instanceof ISimpleRequestInfo) {
			ISimpleRequestInfo reqInfo = (ISimpleRequestInfo) runnable;
			String ua = reqInfo.getRemoteUserAgent();
			if (ua != null) {
				request.setRequestHeader("User-Agent", ua);
			}
		}
		request.open(method, url, true);
		request.registerOnReadyStateChange(new XHRCallbackAdapter() {
			public void onLoaded() {
				boolean isJavaScript = false;
				/**
				 * @j2sNative
				 * isJavaScript = true;
				 */ {}
				if (isJavaScript) { // for SCRIPT mode only
					// For JavaScript, there is no #getResponseBytes
					String responseText = request.getResponseText();
					if (responseText == null || responseText.length() == 0
							|| !runnable.deserialize(responseText)) {
						runnable.ajaxFail(); // should seldom fail!
						return;
					}
				} else {
					// For Java, use #getResponseBytes for performance optimization
					byte[] responseBytes = request.getResponseBytes();
					if (responseBytes == null || responseBytes.length == 0
							|| !runnable.deserializeBytes(responseBytes)) {
						runnable.ajaxFail(); // should seldom fail!
						return;
					}
				}
				runnable.ajaxOut();
				if (runnable.isPipeLive()) { // false if #pipeFailed is called in #ajaxOut
					ajaxPipe(runnable);
				}
			}
		});
		request.send(serialize);
	}
	
	/**
	 * 
	 * @param url
	 * @j2sNative
if (url == null || url.length == 0) {
	return;
}
var map = net.sf.j2s.ajax.SimplePipeRequest.pipeScriptMap;
var pipe = map[url];
if (pipe != null && pipeID != null && pipeID.length > 0) {
	var stillExistedRequest = false;
	var idPrefix = pipeID;
	var idx = pipeID.lastIndexOf ("-");
	if (idx != -1) {
		idPrefix = pipeID.substring (0, idx);
	}
	var iframes = document.getElementsByTagName ("IFRAME");
	for (var i = 0; i < iframes.length; i++) {
		var el = iframes[i];
		if (el.id != null && el.id.indexOf (idPrefix) == 0) {
			stillExistedRequest = true;
			break;
		}
	}
	if (!stillExistedRequest) {
		var scripts = document.getElementsByTagName ("SCRIPT");
		for (var i = 0; i < scripts.length; i++) {
			var el = scripts[i];
			if (el.id != null && el.id.indexOf (idPrefix) == 0) {
				stillExistedRequest = true;
				break;
			}
		}
	}
	pipe.queryEnded = !stillExistedRequest;
	delete map[url];
}
	 */
	native static void updatePipeByURL(String pipeID, String url);

	/**
	 * @j2sNative
return function () {
	if (pipeID != null) {
		var pw = window.parent;
		if (pw == null || pw["net"] == null) return;
		if (!pw.net.sf.j2s.ajax.SimpleRPCRequest.cleanUp(this)) {
			return; // IE, not completed yet
		}
		var url = this.url;
		this.url = null;
		document.getElementsByTagName ("HEAD")[0].removeChild (this);
		var iframe = pw.document.getElementById (pipeID);
		if (iframe != null) {
			iframe.parentNode.removeChild (iframe);
		}
		pw.net.sf.j2s.ajax.SimplePipeRequest.updatePipeByURL (pipeID, url);
	} else {
		if (window == null || window["net"] == null) return;
		if (!net.sf.j2s.ajax.SimpleRPCRequest.cleanUp(this)) {
			return; // IE, not completed yet
		}
		var url = this.url;
		this.url = null;
		document.getElementsByTagName ("HEAD")[0].removeChild (this);
		net.sf.j2s.ajax.SimplePipeRequest.updatePipeByURL (pipeID, url);
	}
};
	 */
	native static Object generatePipeScriptCallback(String pipeID);
	
	/**
	 * Load or send data for pipe using SCRIPT tag.
	 * 
	 * @param url
	 * 
	 * @j2sNative
var script = document.createElement ("SCRIPT");
script.type = "text/javascript";
script.src = url;
script.url = url;
var pipeID = arguments[1];
if (pipeID != null && pipeID.length > 0) {
	script.id = pipeID;
}
var userAgent = navigator.userAgent.toLowerCase ();
var isOpera = (userAgent.indexOf ("opera") != -1);
var isIE = (userAgent.indexOf ("msie") != -1) && !isOpera;
var fun = net.sf.j2s.ajax.SimplePipeRequest.generatePipeScriptCallback (pipeID);
script.defer = true;
if (typeof (script.onreadystatechange) == "undefined" || !isIE) { // W3C
	script.onload = script.onerror = fun;
} else { // IE
	script.onreadystatechange = fun;
}
var head = document.getElementsByTagName ("HEAD")[0];
head.appendChild (script);
	 */
	native static void loadPipeScript(String url);

	/**
	 * Load or send data for pipe using SCRIPT tag.
	 * 
	 * @param url
	 * 
	 * @j2sNative
var iframe = document.createElement ("IFRAME");
iframe.style.display = "none";
var pipeID = null;
do {
	pipeID = "pipe-script-" + pipeKey + "-" + Math.round (10000000 * Math.random ());
} while (document.getElementById (pipeID) != null);
iframe.id = pipeID;
if (ClazzLoader.isIE) { // Avoid being warned with "This page contains both secure and nonsecure items."
	iframe.src = "javascript:false;"; // http://gemal.dk/blog/2005/01/27/iframe_without_src_attribute_on_https_in_internet_explorer/
}
document.body.appendChild (iframe);
var html = "<html><head><title></title>";
html += "<script type=\"text/javascript\">\r\n";
html += "window[\"$p1p3p$\"] = function (string) {\r\n";
html += "		with (window.parent) {\r\n";
html += "				net.sf.j2s.ajax.SimplePipeRequest.parseReceived (string);\r\n";
html += "		};\r\n";
html += "};\r\n";
html += "window[\"$p1p3b$\"] = function (key, result, sequence) {\r\n";
html += "		with (window.parent) {\r\n";
html += "				net.sf.j2s.ajax.SimplePipeRequest.pipeNotifyCallBack (key, result, sequence);\r\n";
html += "		};\r\n";
html += "};\r\n";
html += "</scr" + "ipt></head><body><script type=\"text/javascript\">\r\n";
if (ClassLoader.isOpera)
html += "window.setTimeout (function () {\r\n";
html += "net = { sf : { j2s : { ajax : { SimplePipeRequest : { generatePipeScriptCallback : " + net.sf.j2s.ajax.SimplePipeRequest.generatePipeScriptCallback + " } } } } };\r\n";
html += "(" + net.sf.j2s.ajax.SimplePipeRequest.loadPipeScript + ") (";
html += "\"" + url.replace (/"/g, "\\\"") + "\", \"" + pipeID + "\"";
html += ");\r\n";
if (ClassLoader.isOpera)
html += "}, " + (net.sf.j2s.ajax.SimplePipeRequest.pipeQueryInterval >> 2) + ");\r\n";
html += "</scr" + "ipt></body></html>";
net.sf.j2s.ajax.SimplePipeRequest.iframeDocumentWrite (iframe, html);
	 */
	native static void loadPipeIFrameScript(String pipeKey, String url); // for JavaScript only
	
	/**
	 * @j2sNative
return function () {
	try {
		var doc = handle.contentWindow.document;
		doc.open ();
		if (ClazzLoader.isIE && window["xss.domain.enabled"] == true
				&& domain != null && domain.length > 0) {
			try {
				doc.domain = domain;
			} catch (e) {}
		}
		doc.write (html);
		doc.close ();
		// To avoid blank title in title bar
		document.title = document.title;
		handle = null;
	} catch (e) {
		window.setTimeout (arguments.callee, 25);
	}
};
	 */
	native static Object generateLazyIframeWriting(Object handle, String domain, String html);
	
	/**
	 * @param handle
	 * @param html
	 * @j2sNative
	var handle = arguments[0];
	var html = arguments[1];
	var domain = null;
	try {
		domain = document.domain;
	} catch (e) {}
	if (ClazzLoader.isIE && window["xss.domain.enabled"] == true
			&& domain != null && domain.length > 0) {
		document.domain = domain;
	}
	try {
		if (handle.contentWindow != null) {
			if (ClazzLoader.isIE && window["xss.domain.enabled"] == true
					&& domain != null && domain.length > 0) {
				handle.contentWindow.location = "javascript:document.open();document.domain='" + domain + "';document.close();false;"; // void(0);";
			} else if (ClazzLoader.isIE) {
				handle.contentWindow.location = "javascript:false;"; //"about:blank";
			} else {
				handle.contentWindow.location = "about:blank";
			}
		} else { // Opera
			handle.src = "about:blank";
		}
	} catch (e) {
	}
	try {
		var doc = handle.contentWindow.document;
		doc.open ();
		if (ClazzLoader.isIE && window["xss.domain.enabled"] == true
				&& domain != null && domain.length > 0) {
			doc.domain = domain;
		}
		doc.write (html);
		doc.close ();
		// To avoid blank title in title bar
		document.title = document.title;
	} catch (e) {
		window.setTimeout (net.sf.j2s.ajax.SimplePipeRequest.generateLazyIframeWriting (handle, domain, html), 25);
	}
	 */
	native static void iframeDocumentWrite(Object handle, String html);

	static void pipeScript(SimplePipeRunnable runnable) { // xss
		// only for JavaScript
		String url = runnable.getPipeURL();
		String requestURL = url + (url.indexOf('?') != -1 ? "&" : "?")
				+ constructRequest(runnable.pipeKey, PIPE_TYPE_XSS, runnable.pipeSequence);
		/**
		 * @j2sNative
		 * net.sf.j2s.ajax.SimplePipeRequest.pipeScriptMap[requestURL] = runnable;
		 */ {}
		if (isXSSMode(url)) {
			boolean ok4IFrameScript = true;
			/**
			 * @j2sNative
			 * var domain = null;
			 * try {
			 * 	domain = document.domain;
			 * } catch (e) {
			 * }
			 * ok4IFrameScript = (domain != null && domain.length > 0) || navigator.userAgent.toLowerCase ().indexOf ("msie") == -1;
			 */ {}
			if (ok4IFrameScript) {
				// in xss mode, iframe is used to avoid blocking other *.js loading
				loadPipeIFrameScript(runnable.pipeKey, requestURL);
				return;
			}
		}
		/**
		 * @j2sNative
		 * var pipeID = null;
		 * do {
		 * 	pipeID = "pipe-script-" + runnable.pipeKey + "-" + Math.round (10000000 * Math.random ());
		 * } while (document.getElementById (pipeID) != null);
		 * net.sf.j2s.ajax.SimplePipeRequest.loadPipeScript(requestURL, pipeID);
		 */ {
			 loadPipeScript(requestURL); // reach here for about:blank page. April 8, 2010
		 }
	}
	
	/**
	 * @param runnable
	 * @param domain
	 * @j2sNative
var pipeKey = runnable.pipeKey;
var spr = net.sf.j2s.ajax.SimplePipeRequest;
spr.pipeIFrameClean (pipeKey);
var ifr = document.createElement ("IFRAME");
ifr.style.display = "none";
var url = runnable.getPipeURL();
var src = url + (url.indexOf('?') != -1 ? "&" : "?") 
		+ spr.constructRequest(pipeKey, spr.PIPE_TYPE_SUBDOMAIN_QUERY, runnable.pipeSequence)
		+ "&" + spr.FORM_PIPE_DOMAIN + "=" + domain;
ifr.id = "pipe-" + pipeKey;
ifr.src = src;
document.body.appendChild (ifr);
	 */
	native static void pipeSubdomainQuery(SimplePipeRunnable runnable, String domain); // for JavaScript only
	
	static void pipeNotify(SimplePipeRunnable runnable) { // notifier
		String url = runnable.getPipeURL();
		loadPipeScript(url + (url.indexOf('?') != -1 ? "&" : "?")
				+ constructRequest(runnable.pipeKey, PIPE_TYPE_NOTIFY, runnable.pipeSequence));
		// only for JavaScript
	}
	
	static void pipeNotifyCallBack(String key, String result, long sequence) {
		SimplePipeRunnable pipe = SimplePipeHelper.getPipe(key);
		if (pipe != null) {
			if (pipe.notifySequence < sequence) {
				pipe.notifySequence = sequence;
			}
			if (result != null && result.length() == 1 && result.charAt(0) == PIPE_STATUS_LOST) {
				pipe.pipeAlive = false;
				pipe.pipeLost();
				SimplePipeHelper.removePipe(key);
			}
		}
		// only for JavaScript
	}
	
	/**
	 * Each query will tell that the pipe is still alive.
	 * 
	 * @param runnable
	 */
	static void pipeQuery(final SimplePipeRunnable runnable) {
		final HttpRequest pipeRequest = getRequest();
		String pipeKey = runnable.pipeKey;
		String pipeMethod = runnable.getPipeMethod();
		String pipeURL = runnable.getPipeURL();
		pipeRequest.registerOnReadyStateChange(new XHRCallbackAdapter() {
		
			@Override
			public void onLoaded() {
				boolean isJavaScript = false;
				/**
				 * Maybe user click on refresh button!
				 * @j2sNative
				 * isJavaScript = true;
				 * if (window == null || window["net"] == null) return;
				 */ {}
				if (pipeRequest.getStatus() != 200) {
					runnable.queryFailedRetries++;
				} else {
					runnable.queryFailedRetries = 0; // succeeded
					if (isJavaScript) {
						parseReceived(pipeRequest.getResponseText());
					} else {
						byte[] bytes = pipeRequest.getResponseBytes();
						try {
							parseReceivedBytes(bytes);
						} catch (RuntimeException e) { // invalid simple format
							int length = bytes.length;
							if (length < 1024) {
								System.out.println("[ERROR]: " + new String(bytes));
							} else {
								System.out.println("[ERROR]: " + new String(bytes, 0, 1024) + " ...");
							}
							throw e;
						}
					}
				}
				runnable.queryEnded = true; // for JavaScript only
				/**
				 * @j2sNative
				 * var pqMap = net.sf.j2s.ajax.SimplePipeRequest.pipeQueryMap;
				 * for (var key in pqMap) {
				 * 	if (typeof key == "string" && key.indexOf ("xhr." + this.f$.runnable.pipeKey + ".") == 0) {
				 * 		if (pqMap[key] == null || pqMap[key] === this.f$.pipeRequest) {
				 * 			delete pqMap[key];
				 * 		} else {
				 * 			delete pqMap[key];
				 * 			this.f$.runnable.queryEnded = false;
				 * 		}
				 * 	}
				 * }
				 */ { }
			}
		
		});

		String pipeRequestData = constructRequest(pipeKey, PIPE_TYPE_QUERY, runnable.pipeSequence);
		boolean async = false;
		/**
		 * In JavaScript such queries are not wrapped inside Thread, so asynchronous mode is required!
		 * @j2sNative
		 * async = true;
		 * var key = "xhr." + pipeKey + "." + pipeRequestData;
		 * net.sf.j2s.ajax.SimplePipeRequest.pipeQueryMap[key] = pipeRequest;
		 */ {}
		sendRequest(pipeRequest, pipeMethod, pipeURL, pipeRequestData, async);
	}
	
	/*
	 * A hack to make HttpRequest to support receiving and parsing data
	 * in a Comet way.
	 * 
	 * (non-Javadoc)
	 * @see net.sf.j2s.ajax.HttpRequest#initializeReceivingMonitor()
	 */
	public static HttpRequest getRequestWithMonitor(final HttpRequest.IXHRReceiving monitor) {
		/**
		 * @j2sNative
		 */
		{
			if (requestFactory != null && requestFactory instanceof IHttpPipeRequestFactory) {
				try {
					return ((IHttpPipeRequestFactory) requestFactory).createRequestWithMonitor(monitor);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
		return new HttpRequest() {
			@Override
			protected IXHRReceiving initializeReceivingMonitor() {
				return monitor;
			}
		};
	}

	/**
	 * Create pipe connection for the SimplePipeRunnable.
	 * In Java, customized HttpRequest object is used to create Comet connection.
	 * In JavaScript, IFRAME is used to simulate Comet connection.
	 * In both mode, it is necessary to notify the server that pipe's client end is still
	 * alive in periods.
	 * To send out the notification, Java mode just send out request by HttpRequest, JavaScript
	 * mode will try to send notification requests in XMLHttpRequest or SCRIPT mode according
	 * to the scenarios.
	 * 
	 * @param runnable
	 * 
	 * @j2sNative
var pipeKey = runnable.pipeKey;
var spr = net.sf.j2s.ajax.SimplePipeRequest;
spr.pipeIFrameClean (pipeKey);
var subdomain = arguments[1];
var pipeContinued = arguments[2];
(function () { // avoiding element reference in closure
var ifr = document.createElement ("IFRAME");
ifr.style.display = "none";
ifr.id = "pipe-" + pipeKey;
var url = runnable.getPipeURL();
if (subdomain == null) {
	document.domain = document.domain;
	window["xss.domain.enabled"] = true;
}
ifr.src = url + (url.indexOf('?') != -1 ? "&" : "?") 
		+ spr.constructRequest(pipeKey, spr.PIPE_TYPE_SCRIPT, runnable.pipeSequence)
		+ (subdomain == null ? ""
				: "&" + spr.FORM_PIPE_DOMAIN + "=" + subdomain);
document.body.appendChild (ifr);
}) ();
if (pipeContinued == true) { // pipe script continue ...
	return;
}
var fun = (function (key, created) {
	return function () {
		var sph = net.sf.j2s.ajax.SimplePipeHelper;
		var runnable = sph.getPipe(key);
		if (runnable != null) {
			var spr = net.sf.j2s.ajax.SimplePipeRequest;
			if (runnable.pipeSequence == runnable.notifySequence) {
				window.setTimeout (arguments.callee, spr.pipeLiveNotifyInterval);
				return;
			}
			var now = new Date ().getTime ();
			var last = runnable.lastPipeDataReceived;
			if (last <= 0) {
				last = created;
			}
			if (now - last > 3 * spr.pipeLiveNotifyInterval) {
				if (key == runnable.pipeKey) {
					runnable.pipeAlive = false;
					runnable.pipeClosed();
				}
				sph.removePipe(key);
				spr.pipeIFrameClean (key);
			} else {
				spr.pipeNotify (runnable);
				window.setTimeout (arguments.callee, spr.pipeLiveNotifyInterval);
			}
		}
	};
}) (runnable.pipeKey, new Date ().getTime ());
window.setTimeout (fun, spr.pipeLiveNotifyInterval);
	 */
	static void pipeContinuum(final SimplePipeRunnable runnable) {
		final String pipeKey = runnable.pipeKey;
		HttpRequest pipeRequest = getRequestWithMonitor(new HttpRequest.IXHRReceiving() {
			
			public boolean receiving(ByteArrayOutputStream baos, byte b[], int off, int len) {
				runnable.updateStatus(true);
				baos.write(b, off, len);
				byte[] bytes = baos.toByteArray();
				int resetIndex = parseReceivedBytes(bytes);
				if (resetIndex > 0) {
					baos.reset();
					if (resetIndex < bytes.length) {
						baos.write(bytes, resetIndex, bytes.length - resetIndex);
					}
				} else if (resetIndex < 0) { // Error
					String key = runnable.pipeKey;
					if (pipeKey != null && pipeKey.equals(key)) {
						runnable.pipeLost();
						SimplePipeHelper.removePipe(key);
					}
				}
				return true;
			}
		
		});
		
		pipeRequest.registerOnReadyStateChange(new XHRCallbackAdapter() {
		
			@Override
			public void onReceiving() {
				keepPipeLive(runnable);
			}

			@Override
			public void onLoaded() { // on case that no destroy event is sent to client
				String key = runnable.pipeKey;
				if (pipeKey != null && pipeKey.equals(key)) {
					runnable.pipeClosed(); // may set runnable.pipeKey = null;
					SimplePipeHelper.removePipe(key);
				} else { // broken, reconnected and already assigned another pipe key
					// remove old pipe from pipe pool
					SimplePipeHelper.removePipe(pipeKey);
				}
			}
		
		});
		pipeRequest.setCometConnection(true);

		String pipeMethod = runnable.getPipeMethod();
		String pipeURL = runnable.getPipeURL();

		String pipeRequestData = constructRequest(pipeKey, PIPE_TYPE_CONTINUUM, runnable.pipeSequence);
		lastPipeRequestURL = pipeRequestData;
		sendRequest(pipeRequest, pipeMethod, pipeURL, pipeRequestData, true);
	}
	
	/**
	 * Clean pipe's IFRAME elements
	 * @param pipeKey
	 * 
	 * @j2sNative
var urlSignature = net.sf.j2s.ajax.SimplePipeRequest.FORM_PIPE_KEY + "=" + pipeKey + "&";
var iframes = document.getElementsByTagName ("IFRAME");
for (var i = 0; i < iframes.length; i++) {
	var el = iframes[i];
	var url = null;
	try {
		url = el.src;
	} catch (e) {
	}
	if (url == null || url.length == 0) {
		try {
			url = el.contentWindow.location.toString ();
		} catch (e) {
		}
	}
	if (url != null && url.indexOf (urlSignature) == 0) {
		el.parentNode.removeChild (el);
		continue;
	}
	if (el.id == pipeKey || el.id == "pipe-" + pipeKey) {
		el.parentNode.removeChild (el);
		continue;
	}
}
	 */
	native static void pipeIFrameClean(String pipeKey); // for JavaScript only

	/**
	 * Parse a SimpleSerializable object's string from given string.
	 * 
	 * The given string should be in format of "X...WLL101...#...$...". The first
	 * 32-length string is pipe key string. And the following string is the
	 * serialized string of SimpleSerializable object or "pipe-is-destroyed",
	 * which indicates that pipe is destroyed.
	 * 
	 * If given string is not in the above format, it is considered that the
	 * string is not completed yet. And in this scenario, return null to
	 * indicate to keep receiving more data before call this method again.
	 * 
	 * If given string contains above format string fragment, the segment
	 * will be parsed and relative {@link SimplePipeRunnable#deal(SimpleSerializable)}
	 * will be called on the string fragment. And the rest string is returned
	 * so later data may continue to construct a new string. 
	 * 
	 * @param string
	 * @return null if given string is not completed, or rest of string after
	 * being parsed.
	 */
	public static String parseReceived(final String string) {
		if (string == null) {
			return null;
		}
		SimpleSerializable ss = null;
		int start = 0;
		long now = System.currentTimeMillis();
		while (string.length() > start + PIPE_KEY_LENGTH) { // should be bigger than 48 ( 32 + 6 + 1 + 8 + 1)
			int end = start + PIPE_KEY_LENGTH;
			String key = string.substring(start, end);
			SimplePipeRunnable pipe = SimplePipeHelper.getPipe(key);
			if (PIPE_STATUS_DESTROYED == string.charAt(end)) {
				if (pipe != null) {
					if (key.equals(pipe.pipeKey)) {
						pipe.pipeAlive = false;
						pipe.pipeClosed();
					}
					SimplePipeHelper.removePipe(key);
				}
				return string.substring(end + 1);
			}
			if (PIPE_STATUS_OK == string.charAt(end)) {
				if (pipe != null) { // should always satisfy this condition
					pipe.lastPipeDataReceived = now;
				}
				start = end + 1;
				if (start == string.length()) {
					return string.substring(start);
				}
				continue;
			}
			if (pipe != null) { // should always satisfy this condition
				pipe.lastPipeDataReceived = now;
			}
			boolean isJavaScript = false;
			/**
			 * @j2sNative
			 * isJavaScript = true;
			 */ {}
			if (isJavaScript) { // for SCRIPT mode only
				if (PIPE_STATUS_CONTINUE == string.charAt(end) ) {
					if (pipe != null) { // should always satisfy this condition
						pipeIFrameClean(pipe.pipeKey);
						String pipeURL = pipe.getPipeURL();
						boolean isXSS = isXSSMode(pipeURL);
						boolean isSubdomain = false;
						if (isXSS) {
							isSubdomain = isSubdomain(pipeURL);
						}
						String subdomain = adjustSubdomain(isSubdomain);
						/**
						 * @j2sNative
						 * net.sf.j2s.ajax.SimplePipeRequest.pipeContinuum(runnable, subdomain, true);
						 */ { subdomain.toString(); }
					}
					return string.substring(end + 1);
				}
			}
			ss = SimpleSerializable.parseInstance(string, end);
			if (ss == null) {
				break;
			}
			if (ss == SimpleSerializable.ERROR) {
				break;
			}
			if (!ss.deserialize(string, end)) {
				break;
			}
			if (pipe != null) { // should always satisfy this condition
				if (ss != SimpleSerializable.UNKNOWN) {
					if (ss instanceof SimplePipeSequence) {
						long sequence = ((SimplePipeSequence) ss).sequence;
						if (sequence > pipe.pipeSequence) {
							pipe.pipeSequence = sequence;
						}
					} else {
						pipe.deal(ss);
					}
				}
			}
			
			start = restStringIndex(string, start);
		}
		if (start != 0) {
			return string.substring(start);
		}
		return string;
	}


	/**
	 * Parse a SimpleSerializable object's bytes from given bytes.
	 * 
	 * The given bytes should be in format of "X...WLL101...#...$...". The first
	 * 32-length bytes is pipe key bytes. And the following bytes is the
	 * serialized bytes of SimpleSerializable object or "pipe-is-destroyed",
	 * which indicates that pipe is destroyed.
	 * 
	 * If given bytes is not in the above format, it is considered that the
	 * bytes is not completed yet. And in this scenario, return null to
	 * indicate to keep receiving more data before call this method again.
	 * 
	 * If given bytes contains above format bytes fragment, the segment
	 * will be parsed and relative {@link SimplePipeRunnable#deal(SimpleSerializable)}
	 * will be called on the bytes fragment. And the rest bytes is returned
	 * so later data may continue to construct a new bytes. 
	 * 
	 * @param bytes
	 * @return null if given bytes is not completed, or rest of bytes after
	 * being parsed.
	 */
	public static int parseReceivedBytes(final byte[] bytes) {
		if (bytes == null) {
			return -1;
		}
		SimpleSerializable ss = null;
		int start = 0;
		long now = System.currentTimeMillis();
		while (bytes.length > start + PIPE_KEY_LENGTH) { // should be bigger than 48 ( 32 + 6 + 1 + 8 + 1)
			int end = start + PIPE_KEY_LENGTH;
			String key = new String(bytes, start, PIPE_KEY_LENGTH);
			SimplePipeRunnable pipe = SimplePipeHelper.getPipe(key);
			if (PIPE_STATUS_DESTROYED == bytes[end]) {
				if (pipe != null) {
					if (key.equals(pipe.pipeKey)) {
						pipe.pipeAlive = false;
						pipe.pipeClosed();
					}
					SimplePipeHelper.removePipe(key);
				}
				return end + 1;
			}
			if (PIPE_STATUS_OK == bytes[end]) {
				if (pipe != null) { // should always satisfy this condition
					pipe.lastPipeDataReceived = now;
				}
				start = end + 1;
				if (start == bytes.length) {
					return start;
				}
				continue;
			}
			if (pipe != null) { // should always satisfy this condition
				pipe.lastPipeDataReceived = now;
			}
			boolean isJavaScript = false;
			/**
			 * @j2sNative
			 * isJavaScript = true;
			 */ {}
			if (isJavaScript) { // for SCRIPT mode only
				if (PIPE_STATUS_CONTINUE == bytes[end]) {
					if (pipe != null) { // should always satisfy this condition
						pipeIFrameClean(pipe.pipeKey);
						String pipeURL = pipe.getPipeURL();
						boolean isXSS = isXSSMode(pipeURL);
						boolean isSubdomain = false;
						if (isXSS) {
							isSubdomain = isSubdomain(pipeURL);
						}
						String subdomain = adjustSubdomain(isSubdomain);
						/**
						 * @j2sNative
						 * net.sf.j2s.ajax.SimplePipeRequest.pipeContinuum(runnable, subdomain, true);
						 */ { subdomain.toString(); }
					}
					return end + 1;
				}
			}
			ss = SimpleSerializable.parseInstance(bytes, end);
			if (ss == null) {
				break;
			}
			if (ss == SimpleSerializable.ERROR) {
				return -1; // error
			}
			List<SimpleSerializable> ssObjs = new LinkedList<SimpleSerializable>();
			ssObjs.add(ss);
			int result = ss.deserializeBytes(bytes, end, ssObjs);
			if (result == SimpleSerializable.SIMPLE_MISSING_DATA) {
				break;
			}
			if (result < SimpleSerializable.SIMPLE_MISSING_DATA) { // Other errors
				int length = bytes.length;
				if (length < 1024) {
					System.out.println("[ERROR]: " + new String(bytes));
				} else {
					System.out.println("[ERROR]: " + new String(bytes, 0, 1024) + " ...");
				}
				return -1; // error
			}
			if (pipe != null) { // should always satisfy this condition
				if (ss != SimpleSerializable.UNKNOWN) {
					if (ss instanceof SimplePipeSequence) {
						long sequence = ((SimplePipeSequence) ss).sequence;
						if (sequence > pipe.pipeSequence) {
							pipe.pipeSequence = sequence;
						}
					} else {
						pipe.deal(ss);
					}
				} // else ignore unknown event silently?
			}
			
			start = restBytesIndex(bytes, start);
		}
		if (start != 0) {
			return start;
		}
		return 0;
	}

	/*
	 * Return the string index from beginning of next SimpleSerializable
	 * instance.
	 */
	static int restStringIndex(final String string, int start) {
		// Format: WLL101ClassName#NNNNNN$SerializedData...
		int idx1 = string.indexOf('#', start) + 1;
		int idx2 = string.indexOf('$', idx1);
		int size = 0;
		for (int i = idx1; i < idx2; i++) {
			char c = string.charAt(i);
			if (c != '0') {
				size = c - '0';
				i++;
				for (; i < idx2; i++) {
					size = ((size << 3) + (size << 1)) + (string.charAt(i) - '0'); // size * 10
				}
				break;
			}
		}
		int end = idx2 + size + 1;
		if (end <= string.length()) {
			return end;
		} else {
			return start;
		}
	}
	
	public static int restBytesIndex(final byte[] bytes, int start) {
		// Format: WLL101ClassName#NNNNNN$SerializedData...
		int idx1 = SimpleSerializable.bytesIndexOf(bytes, (byte) '#', start) + 1;
		int idx2 = SimpleSerializable.bytesIndexOf(bytes, (byte) '$', idx1);
		int size = 0;
		for (int i = idx1; i < idx2; i++) {
			byte b = bytes[i];
			if (b != '0') {
				size = b - '0';
				i++;
				for (; i < idx2; i++) {
					size = ((size << 3) + (size << 1)) + (bytes[i] - '0'); // size * 10
				}
				break;
			}
		}
		int end = idx2 + size + 1;
		if (end <= bytes.length) {
			return end;
		} else {
			return start;
		}
	}
	
	/**
	 * 
	 * @param isSubdomain
	 * @return
	 * @j2sNative
	 * var subdomain = null;
	 * if (isSubdomain) {
	 * 	try {
	 * 		subdomain = window.location.host;
	 * 	} catch (e) {}
	 * 	if (subdomain != null) {
	 * 		var idx = subdomain.indexOf (":");
	 * 		if (idx != -1) {
	 * 			subdomain = subdomain.substring (0, idx);
	 * 		}
	 * 		document.domain = subdomain; // set owner iframe's domain
	 * 		window["xss.domain.enabled"] = true;
	 * 	}
	 * }
	 * return subdomain;
	 */
	native static String adjustSubdomain(boolean isSubdomain); // for JavaScript only
	
	/**
	 * Start pipe in AJAX mode.
	 * In Java mode, thread is used to receive data from pipe.
	 * In JavaScript mode, IFRAME or XHR/SCRIPT requests are used to receive data 
	 * from server.
	 * 
	 * @param runnable
	 */
	static void ajaxPipe(final SimplePipeRunnable runnable) {
		SimplePipeHelper.registerPipe(runnable.pipeKey, runnable);
		
		/*
		 * Here in JavaScript mode, try to detect whether it's in cross site
		 * script mode or not. In XSS mode, <SCRIPT> is used to make requests. 
		 */
		String pipeURL = runnable.getPipeURL();
		boolean isXSS = isXSSMode(pipeURL);
		boolean isSubdomain = false;
		if (isXSS) {
			isSubdomain = isSubdomain(pipeURL);
		}

		if ((!isXSS || isSubdomain) && pipeMode == MODE_PIPE_CONTINUUM)
			/**
			 * @j2sNative
			 * var spr = net.sf.j2s.ajax.SimplePipeRequest;
			 * var subdomain = spr.adjustSubdomain (isSubdomain);
			 * spr.pipeContinuum (runnable, subdomain, false);
			 */
		{
			//pipeQuery(runnable, "continuum");
			SimpleThreadHelper.runTask(new Runnable(){
				public void run() {
					pipeContinuum(runnable);
				}
			}, "Simple Pipe Continuum Worker");
		} else
			/**
			 * @j2sNative
var spr = net.sf.j2s.ajax.SimplePipeRequest;
if (isXSS && isSubdomain && spr.isSubdomainXSSSupported ()) {
	var subdomain = spr.adjustSubdomain (isSubdomain);
	spr.pipeSubdomainQuery (runnable, subdomain);
	return;
}
runnable.queryEnded = true;
(function (pipeFun, key, created, lastXHR) { // Use function to simulate Thread
	return function () {
		var sph = net.sf.j2s.ajax.SimplePipeHelper;
		var runnable = sph.getPipe(key);
		if (runnable != null) {
			var spr = net.sf.j2s.ajax.SimplePipeRequest;
			var now = new Date ().getTime ();
			var last = runnable.lastPipeDataReceived;
			if (last <= 0) {
				last = created;
			}
			if (runnable.isPipeLive() // may be false after a few queries
			 		&& (runnable.queryEnded || (now - last >= spr.pipeLiveNotifyInterval
					&& (lastXHR == -1 || now - lastXHR >= spr.pipeLiveNotifyInterval)))
					&& runnable.queryFailedRetries < 3) {
				runnable.queryEnded = false;
				if (runnable.received == runnable.lastPipeDataReceived
						&& runnable.retries == runnable.queryFailedRetries) {
					runnable.queryFailedRetries++; // response must not be empty
				}
				pipeFun (runnable);
				lastXHR = new Date ().getTime ();
			}
			runnable.retries = runnable.queryFailedRetries;
			runnable.received = runnable.lastPipeDataReceived;
			if (!runnable.isPipeLive()) { // try to clean up for lost pipes
				runnable.pipeAlive = false;
				runnable.pipeClosed();
				sph.removePipe(key);
				spr.pipeIFrameClean (key);
			} else if (runnable.queryFailedRetries >= 3
					|| now - last > 3 * spr.pipeLiveNotifyInterval) {
				if (key == runnable.pipeKey) {
					runnable.pipeAlive = false;
					runnable.pipeClosed();
				}
				sph.removePipe(key);
				spr.pipeIFrameClean (key);
			} else {
				window.setTimeout (arguments.callee, spr.pipeQueryInterval);
			}
		}
	};
}) ((!isXSS) ? spr.pipeQuery : spr.pipeScript, runnable.pipeKey, new Date ().getTime (), -1) ();
			 */
		{
			final String key = runnable.pipeKey;
			final long created = System.currentTimeMillis();
			Thread queryThread = new Thread(new Runnable() {
				public void run() {
					SimplePipeRunnable runnable = null;
					while ((runnable = SimplePipeHelper.getPipe(key)) != null && runnable.isPipeLive()) {
						pipeQuery(runnable);
						
						long now = System.currentTimeMillis();
						long last = runnable.lastPipeDataReceived;
						if (last <= 0) {
							last = created;
						}
						if (runnable.queryFailedRetries >= 3
								|| now - last > 3 * pipeLiveNotifyInterval) {
							if (key.equals(runnable.pipeKey)) {
								runnable.pipeAlive = false;
								runnable.pipeClosed();
							}
							SimplePipeHelper.removePipe(key);
							return;
						}

						try {
							Thread.sleep(pipeQueryInterval);
						} catch (InterruptedException e) {
							//e.printStackTrace();
						}
					}
					if (runnable != null) { // runnable is still in SimplePipeHelper before #removePipe
						runnable.pipeAlive = false;
						runnable.pipeClosed();
					}
					SimplePipeHelper.removePipe(key);

				}
			}, "Simple Pipe Query Worker");
			queryThread.setDaemon(true);
			queryThread.start();
		}
	}

	/**
	 * For early version of Firefox (<1.5) and Opera (<9.6), subdomain XSS
	 * query may be unsupported.
	 * 
	 * @return whether subdomain XSS is supported or not.
	 * 
	 * @j2sNative
	var ua = navigator.userAgent;
	var name = "Opera";
	var idx = ua.indexOf(name);
	if (idx != -1) {
		return parseFloat (ua.substring(idx + name.length + 1)) >= 9.6;
	}
	name = "Firefox";
	idx = ua.indexOf(name);
	if (idx != -1) {
		return parseFloat (ua.substring(idx + name.length + 1)) >= 1.5;
	}
	name = "MSIE";
	idx = ua.indexOf(name);
	if (idx != -1) {
		return parseFloat (ua.substring(idx + name.length + 1)) >= 6.0;
	}
	return true;
	 */
	native static boolean isSubdomainXSSSupported(); // for JavaScript only
	
	/**
	 * 
	 * @param p
	 * @j2sNative
if (window["NullObject"] == null) {
	window["NullObject"] = function () { };
}
p.initParameters = function () {
	this.parentDomain = document.domain;
	this.pipeQueryInterval = 1000;
	this.pipeLiveNotifyInterval = 25000;
	this.runnable = null;
	this.lastXHR = -1;
	var oThis = this;
	with (window.parent) {
		var sph = net.sf.j2s.ajax.SimplePipeHelper;
		var spr = net.sf.j2s.ajax.SimplePipeRequest;
		this.runnable = sph.getPipe(this.key);
		this.pipeQueryInterval = spr.getQueryInterval ();
		this.pipeLiveNotifyInterval = spr.pipeLiveNotifyInterval;
	}
	if (this.runnable == null) { // refreshing
		eval ("(" + window.parent.net.sf.j2s.ajax.SimplePipeRequest.checkIFrameSrc + ") ();");
	} else {
		this.runnable.queryEnded = true;
	}
};
p.initHttpRequest = function () {
	this.xhrHandle = null;
	if (window.XMLHttpRequest) {
		this.xhrHandle = new XMLHttpRequest();
	} else {
		try {
			this.xhrHandle = new ActiveXObject("Msxml2.XMLHTTP");
		} catch (e) {
			this.xhrHandle = new ActiveXObject("Microsoft.XMLHTTP");
		}
	}
	var oThis = this;
	this.xhrHandle.onreadystatechange = function () {
		if (oThis.xhrHandle == null) {
			oThis = null;
			return;
		}
		var state = oThis.xhrHandle.readyState;
		if (state == 4) {
			var pipeData = oThis.xhrHandle.responseText;
			oThis.xhrHandle.onreadystatechange = NullObject;
			var pipe = oThis.runnable;
			document.domain = oThis.parentDomain;
			if (oThis.xhrHandle.status != 200) {
				pipe.queryFailedRetries++;
			} else {
				pipe.queryFailedRetries = 0; // succeeded
				with (window.parent) {
					net.sf.j2s.ajax.SimplePipeRequest.parseReceived (pipeData);
					oThis.runnable = net.sf.j2s.ajax.SimplePipeHelper.getPipe (oThis.key);
				}
			}
			pipe.queryEnded = true;
			var xhrHandle = oThis.xhrHandle;
			with (window.parent) {
				var pqMap = net.sf.j2s.ajax.SimplePipeRequest.pipeQueryMap;
				for (var key in pqMap) {
					if (typeof key == "string" && key.indexOf ("xhr." + pipe.pipeKey + ".") == 0) {
						if (pqMap[key] == null || pqMap[key] === xhrHandle) {
							delete pqMap[key];
						} else {
							delete pqMap[key];
							pipe.queryEnded = false;
						}
					}
				}
			}
			oThis.xhrHandle = null;
			oThis = null;
		}
	};
};
p.pipeXHRQuery = function (request, method, url, data) {
	if ("GET" == method.toUpperCase ()) {
		request.open (method, url + (data != null ? ((url.indexOf ('?') != -1 ? "&" : "?") + data) : ""), true, null, null);
		data = null;
	} else {
		request.open (method, url, true, null, null);
	}
	if (method != null && method.toLowerCase () == "post") {
		try {
			request.setRequestHeader ("Content-type", 
					"application/x-www-form-urlencoded");
		} catch (e) {
			// log ("Setting 'Content-type' header error : " + e);
		}
		//if (request.overrideMimeType) {
		//	try {
		//		request.setRequestHeader ("Connection", "close");
		//	} catch (e) {
		//		log ("Setting 'Connection' header error : " + e);
		//	}
		//}
	}
	request.send(data);
};
p.initParameters ();
	 */
	native static void subdomainInit(Object p); // for JavaScript only
	
	/**
	 * 
	 * @param p
	 * @j2sNative
var created = new Date ().getTime ();
return function () {
	var runnable = p.runnable;
	if (runnable != null) {
		if (runnable.pipeKey != p.key) {
			var key = p.key;
			with (window.parent) {
				try {
					net.sf.j2s.ajax.SimplePipeHelper.removePipe (key);
					net.sf.j2s.ajax.SimplePipeRequest.pipeIFrameClean (key);
					return;
				} catch (e) {
				}
			}
		}
		var now = new Date ().getTime ();
		var last = runnable.lastPipeDataReceived;
		if (last <= 0) {
			last = created;
		}
		if ((runnable.queryEnded || (now - last >= p.pipeLiveNotifyInterval
				&& (p.lastXHR == -1 || now - p.lastXHR >= p.pipeLiveNotifyInterval)))
				&& runnable.queryFailedRetries < 3) {
			runnable.queryEnded = false;
			var method = null;
			var url = null;
			var data = null;
			var key = p.key;
			with (window.parent) {
				try {
					method = runnable.getPipeMethod ();
					url = runnable.getPipeURL ();
					var spr = net.sf.j2s.ajax.SimplePipeRequest;
					data = spr.constructRequest(key, spr.PIPE_TYPE_QUERY, runnable.pipeSequence);
				} catch (e) {
				}
			}
			try {
				document.domain = p.originalDomain;
			} catch (e) {};
			if (data == null) { // make sure that data is not null
				with (window.parent) {
					try {
						method = runnable.getPipeMethod ();
						url = runnable.getPipeURL ();
						var spr = net.sf.j2s.ajax.SimplePipeRequest;
						data = spr.constructRequest(key, spr.PIPE_TYPE_QUERY, runnable.pipeSequence);
					} catch (e) {
					}
				}
			}
			try {
				p.initHttpRequest ();
			} catch (e) {};
			var xhrHandle = p.xhrHandle;
			try {
				with (window.parent) {
					spr.pipeQueryMap["xhr." + key + "." + data] = xhrHandle;
				}
			} catch (e) {
			}
			try {
				p.pipeXHRQuery (p.xhrHandle, method, url, data);
				p.lastXHR = new Date ().getTime ();
			} catch (e) {
				p.xhrHandle.onreadystatechange = NullObject;
				p.xhrHandle = null;
				document.domain = p.parentDomain;
				runnable.queryEnded = true;
				runnable.queryFailedRetries++; // Failed
			}
		}
		if (runnable.queryFailedRetries >= 3
				|| now - last > 3 * p.pipeLiveNotifyInterval) {
			document.domain = p.parentDomain;
			var key = p.key;
			with (window.parent) {
				if (runnable.pipeKey == key) {
					runnable.pipeAlive = false;
					runnable.pipeClosed ();
				}
				net.sf.j2s.ajax.SimplePipeHelper.removePipe (key);
				net.sf.j2s.ajax.SimplePipeRequest.pipeIFrameClean (key);
			}
		} else {
			window.setTimeout (arguments.callee, p.pipeQueryInterval);
		}
	}
};
	 */
	native static void subdomainLoopQuery(Object p); // for JavaScript only
	
	/**
	 * @j2sNative
try {
	var curLoc = "" + window.location;
	var existed = false;
	with (window.parent) {
		var iframes = document.getElementsByTagName ("IFRAME");
		for (var i = 0; i < iframes.length; i++) {
			if (iframes[i].src == curLoc) {
				existed = true;
				break;
			}
		}
	}
	if (!existed) { // refreshing in Firefox 3.0 will trigger this scenario
		var idx = curLoc.indexOf ("?");
		if (idx != -1) {
			var urlPrefix = curLoc.substring (0, idx);
			var goalURL = null;
			with (window.parent) {
				var iframes = document.getElementsByTagName ("IFRAME");
				for (var i = 0; i < iframes.length; i++) {
					if (iframes[i].src.indexOf (urlPrefix) == 0) {
						goalURL = iframes[i].src;
						break;
					}
				}
			}
			if (goalURL != null) {
				window.location.replace (goalURL);
			}
		}
	}
} catch (e) {}
$$ = $; 
$ = function (s) {
	$$ (s);
	try {
		var length = document.body.childNodes.length;
		for (var i = length - 1; i >= 0; i--) { // remove old SCRIPT elements
			var child = document.body.childNodes[i];
			child.parentNode.removeChild (child);
		}
	} catch (e) {}
}
	 */
	native static void checkIFrameSrc(); // for JavaScript only

}
