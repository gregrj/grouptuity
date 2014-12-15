/*	The MIT License (MIT)
 *	
 *	Copyright (c) 2014 Venmo Inc.
 *	
 *	Permission is hereby granted, free of charge, to any person obtaining a copy of
 *	this software and associated documentation files (the "Software"), to deal in
 *	the Software without restriction, including without limitation the rights to
 *	use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *	the Software, and to permit persons to whom the Software is furnished to do so,
 *	subject to the following conditions:
 *	The above copyright notice and this permission notice shall be included in all
 *	copies or substantial portions of the Software.
 *	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *	FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *	COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *	IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *	CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 *	MODIFIED FOR GROUPTUITY
 */

package com.grouptuity.venmo;

import android.webkit.JavascriptInterface;
import com.grouptuity.R;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View; 
import android.view.Window;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.RelativeLayout;

public class VenmoWebViewActivity extends Activity {
    
	Context mContext;
	private WebView mVenmoWebView;
	String mUrl;
	
	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		requestWindowFeature(Window.FEATURE_CUSTOM_TITLE);
		setContentView(R.layout.venmo_webview);
		
		mUrl = getIntent().getExtras().getString("url");
		
		mContext = this;
		
		mVenmoWebView = (WebView)getLastNonConfigurationInstance();
    	if(mVenmoWebView != null) { //If screen was rotated, don't reload the whole webview
    		
    		WebView myWebView = (WebView)findViewById(R.id.venmo_wv);
    		RelativeLayout parent = (RelativeLayout)myWebView.getParent();
    		parent.removeView(myWebView);
    		
    		RelativeLayout parent2 = (RelativeLayout)mVenmoWebView.getParent();
    		parent2.removeView(mVenmoWebView);
    		
    		parent.addView(mVenmoWebView);
    	}
    	else { //load the webview 
    		mVenmoWebView = (WebView)findViewById(R.id.venmo_wv);
    		mVenmoWebView.addJavascriptInterface(new VenmoJavaScriptInterface(this), "VenmoAndroidSDK");
        	mVenmoWebView.setScrollBarStyle(View.SCROLLBARS_INSIDE_OVERLAY);
        	mVenmoWebView.getSettings().setJavaScriptEnabled(true);
        	mVenmoWebView.getSettings().setUserAgentString("venmo-android-2.0");
    		mVenmoWebView.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
        	mVenmoWebView.loadUrl(mUrl);
    	}
	}

	/*called right before screen rotates. We store the webview object so it can be recovered when the activity re-starts*/ 
    @Override
    public Object onRetainNonConfigurationInstance(){return mVenmoWebView;}
	
    //This class handles what happens when the user has successfully paid OR if there's an error, and it's time to
    //yield control back to your previous activity (the one that opened up this Venmo payment webview).  
    public class VenmoJavaScriptInterface 
    {
    	Context mContext;
    	Activity mActivity;
        
        /** Instantiate the interface and set the context */
        VenmoJavaScriptInterface(Context c) {
            mContext = c;
            mActivity = (Activity)c;
        }

    	@JavascriptInterface
        public void paymentSuccessful(String signed_request) {
        	Intent i = new Intent();
        	i.putExtra("signedrequest", signed_request);
    		mActivity.setResult(mActivity.RESULT_OK, i);
    		mActivity.finish();
        }

    	@JavascriptInterface
        public void error(String error_message) {
        	Intent i = new Intent();
        	i.putExtra("error_message", error_message);
    		mActivity.setResult(mActivity.RESULT_OK, i);
    		mActivity.finish();
        }

    	@JavascriptInterface
        public void cancel() {
        	Intent i = new Intent();
        	mActivity.setResult(mActivity.RESULT_CANCELED);
    		mActivity.finish();
        }
    }
}
