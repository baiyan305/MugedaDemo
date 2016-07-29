package com.mugeda.androidsdk.view;

import java.io.InputStream;

import com.mugeda.androidsdk.view.InternalBrowser;
import com.mugeda.androidsdk.controller.OrmmaController;
import com.mugeda.androidsdk.controller.util.CommonUtil;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Base64;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView.ScaleType;

public class AdViewCore extends WebView{

	// layout constants
	public static final int PLACEHOLDER_ID = 100;
	public static final int BACKGROUND_ID = 101;
	public static final int ORMMA_ID = 102;
	public static final int CLOSEBUTTON_ID = 104;

	protected static final int CLOSEBTN_ID = 103;
	protected static final int CLOSEBTN_FRAME = 104;

	/**
	 * enum representing possible view states
	 */
	public enum ViewState {
		DEFAULT, RESIZED, EXPANDED, HIDDEN, LEFT_BEHIND, OPENED;
	}

	//context
	Context mContext;

	//handler
	protected Handler mInternalHandler;

	//signature valid?
	protected boolean mSign = true;

	//OrmmaController
	protected OrmmaController mOrmmaController;	

	//mraid.js  mraid_bridge.js  player JavaScript
	protected String mraid;
	protected String mraidbridge;
	protected String mPlayer;

	// screen info
	protected int mScreenWidth; //screen width
	protected int mScreenHeigth; //screen height
	protected float mDensity; // screen pixel density
	protected int mContentWidth;
	protected int mContentHeight;

	//place in view tree
	protected int mIndex; // index of the view within its ViewGroup

	/**
	 * Internal used view.
	 * mContentView__Top View, android.R.id.content
	 * mPlaceHolder__inflate in the position where AdView stand before move the AdView to contentView when expand or resize.
	 * mExpandBackGround__when expand the AdView, add the AdView to contentView. Add a background FrameLayout between contentView and AdView.
	 * mExpandAdView__when need expand with new piece,  use this ExpandAdView to load.
	 * mExpandParent__when need expand with new piece, use ExpandAdView to load, the ExpandAdView need remember its father view.
	 *  mCover__when expand AdView, use a blank FrameLayout to cover the expand progress because the progress is little ugly.
	 *  mButton__close button show on AdView. Trigger mraid.close() on click.
	 * */
	protected FrameLayout mContentView;
	protected FrameLayout mPlaceHolder;
	protected FrameLayout mExpandBackGround;
	protected AdViewCore mExpandAdView;
	protected AdViewCore mExpandParent;
	protected FrameLayout mCover;
	protected ImageButton mButton;

	//trace state of view, use for close
	protected ViewState mViewState = ViewState.DEFAULT; //current state
	protected int mDefaultWidth; //width of DEFAULT
	protected int mDefaultHeight; //height of DEFAULT
	protected ViewGroup.LayoutParams mDefaultLayoutParams;

	//listener
	protected Listener mListener;

	//The ad URL the AdView is loading and its base URL.
	protected String mUrl;
	protected String mBaseUrl;
	protected String mPlacementType = "unknow";
	protected boolean mUseCustomClose = false;

	
	/*****************************************************/
	/******************Constructor*********************/
	/****************************************************/
	
	public AdViewCore(Context context){
		super(context);
		mContext = context;
		initialize();
	}

	public AdViewCore(Context context, AttributeSet attrs){
		super(context, attrs);
		mContext = context;
		initialize();
	}

	public AdViewCore(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mContext = context;
		initialize();
	}

	public AdViewCore(Context context, boolean isExpanded, AdViewCore expandParent){
		super(context);
		mContext = context;
		if(isExpanded){
			mViewState = ViewState.EXPANDED;
			mSign = true;
			mExpandParent = expandParent;
		}
		initialize();
	}


	/*****************************************************/
	/************PUBLIC METHOD, CALLED BY USER************/
	/****************************************************/


	public void loadAd(String adUrl){
		resolveUrl(adUrl);
		loadUrl(true);
	}

	public void destory(){
		stopLoading();
		resetAllToDefault();
	}

	public void injectJavaScript(String dataToInject){
		if(dataToInject != null){
			loadUrl("javascript:"+dataToInject);
		}
	}


	/****************************************************/
	/******************MRAID PART******************/
	/****************************************************/


	/***EXPAND PART***/
	public void doExpand(String url, int[] dimension, boolean[] properties){

		if(mViewState == ViewState.EXPANDED){
			injectJavaScript("window.mraidview.fireErrorEvent({'expand','The Ad have been expanded'});");
			return;
		}

		mContentWidth = mContentView.getWidth();
		mContentHeight = mContentView.getHeight();

		//isModal
		boolean useCustomClose = properties[0];

		if(mViewState == ViewState.DEFAULT){
			if(url == null){
				expandOnePiece(dimension, useCustomClose);
				mListener.onExpand(false);
			}else{
				expandTwoPieces(url, dimension, useCustomClose);
			}
		}

		if(mViewState == ViewState.RESIZED){
			if(url == null){
				resizeToOnePiece(dimension);
			}else{
				resizeToTwoPieces(url, dimension, useCustomClose);
			}
		}

		//set state to expanded
		mViewState = ViewState.EXPANDED;

		//callback ad
		String injection = "window.mraidview.fireChangeEvent({ state: \'expanded\',"
				+ " size: "
				+ "{ width: "
				+ (int) (dimension[0]/mDensity)
				+ ", "
				+ "height: " + (int) (dimension[1]/mDensity) + "}" 
				+ " });";
		injectJavaScript(injection);

		//System.out.println(injection);
	}

	/**
	 * Expand one-piece advertisement.
	 * */
	protected void expandOnePiece(int[] dimension, boolean useCustomClose){
		ViewGroup parent = (ViewGroup) getParent();

		//remove AdView from parent and use empty FrameLayout to hold the place
		int index = 0;
		int count = parent.getChildCount();
		for (index = 0; index < count; index++) {
			if (parent.getChildAt(index) == this)
				break;
		}
		mIndex = index;
		mPlaceHolder = new FrameLayout(getContext());
		mPlaceHolder.setId(PLACEHOLDER_ID);
		ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(getWidth(),getHeight());
		parent.addView(mPlaceHolder, index, lp);
		parent.removeView(this);

		//set new LayoutParams
		FrameLayout.LayoutParams newLayoutParams = new FrameLayout.LayoutParams(
				dimension[0],dimension[1]);
		if(dimension[1] > mContentView.getHeight())newLayoutParams.gravity = Gravity.TOP;
		else newLayoutParams.gravity = Gravity.CENTER;
		setLayoutParams(newLayoutParams);

		//add black container to contain this AdView. prevent user from application content.
		mExpandBackGround = new FrameLayout(getContext());
		mExpandBackGround.setBackgroundColor(Color.BLACK);
		mExpandBackGround.setId(BACKGROUND_ID);
		mExpandBackGround.addView(this);
		FrameLayout.LayoutParams bgfl = new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT);

		//add a black cover to hide the expand progress as it is little ugly now.
		mCover = new FrameLayout(getContext());
		mCover.setBackgroundColor(Color.BLACK);
		mExpandBackGround.addView(mCover, bgfl);

		mContentView.addView(mExpandBackGround, bgfl);

		//Add close button
		inflateCloseBtn(useCustomClose, "top-right");
		showCloseBtn();
	}

	/**
	 * Expand two-piece advertisements.Instantiate a new AdView to load expand page. 
	 * Place the new AdViewon top view hierarchy, the contentview.
	 **/
	protected void expandTwoPieces(String url, int[] dimension, boolean useCustomClose){

		//new adview
		mExpandAdView = new AdViewCore(getContext(),true, this);
		FrameLayout.LayoutParams adviewlp = new FrameLayout.LayoutParams(dimension[0],dimension[1]);
		if(dimension[0] > mContentView.getHeight())adviewlp.gravity = Gravity.TOP;
		else adviewlp.gravity = Gravity.CENTER;

		//close button
		mExpandAdView.inflateCloseBtn(useCustomClose, "top-right");
		mExpandAdView.showCloseBtn();

		//Add the new adview to container
		mExpandBackGround = new FrameLayout(getContext());
		mExpandBackGround.setBackgroundColor(Color.BLACK);
		mExpandBackGround.setId(BACKGROUND_ID);
		mExpandBackGround.addView(mExpandAdView, adviewlp);

		//Add container to contentview
		FrameLayout.LayoutParams bgfl = new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT);
		mContentView.addView(mExpandBackGround, bgfl);

		//load expand page
		mExpandAdView.loadUrl(false);
		mExpandAdView.requestFocus();
	}

	/**
	 * Expand one-piece advertisement from resize.
	 * */
	protected void resizeToOnePiece(int[] dimension){

		//remove from contentView
		((ViewGroup)this.getParent()).removeView(this);

		//set new LayoutParams for AdView
		FrameLayout.LayoutParams newLayoutParams = new FrameLayout.LayoutParams(
				dimension[0],dimension[1]);
		newLayoutParams.gravity = Gravity.CENTER;
		setLayoutParams(newLayoutParams);

		//add black container to contain this AdView. Prevent user from application content.
		mExpandBackGround = new FrameLayout(getContext());
		mExpandBackGround.setBackgroundColor(Color.BLACK);
		mExpandBackGround.setId(BACKGROUND_ID);
		mExpandBackGround.addView(this);

		//add container to contentView
		FrameLayout.LayoutParams bgfl = new FrameLayout.LayoutParams(
				FrameLayout.LayoutParams.MATCH_PARENT,
				FrameLayout.LayoutParams.MATCH_PARENT);
		mContentView.addView(mExpandBackGround, bgfl);
	}

	/**
	 * Expand two-pieces advertisement from resize.
	 * */
	protected void resizeToTwoPieces(String url, int[] dimension, boolean useCustomClose){

		//Relocate the AdView to default position and back to default size.
		FrameLayout contentView = (FrameLayout) getRootView().findViewById(
				android.R.id.content);
		contentView.removeView(this);
		resetSize();
		ViewGroup parent = (ViewGroup) mPlaceHolder.getParent();
		parent.addView(this, mIndex);
		parent.removeView(mPlaceHolder);
		parent.invalidate();

		mPlaceHolder = null;

		//Open new AdView to load second piece
		expandTwoPieces(url, dimension, useCustomClose);
	}


	/***RESIZE PART***/
	public void doResize(int width,int height, int left, int top, boolean allowOffScreen, String customClosePosition){

		if(mViewState == ViewState.EXPANDED){
			injectJavaScript("window.mraidview.fireErrorEvent({'resize','The expanded Ad can not be resized'});");
			return;
		}

		System.out.println("AdView Resize");
		mContentWidth = mContentView.getWidth();
		mContentHeight = mContentView.getHeight();

		if(mViewState == ViewState.DEFAULT)	resizeSelf(width, height, left, top, allowOffScreen,customClosePosition);
		if(mViewState == ViewState.RESIZED)	resizeAgain(width, height, left, top, allowOffScreen);

		//mListener.onResize();

		mViewState = ViewState.RESIZED;

		String injection = "window.mraidview.fireChangeEvent({ 'state': \'resized\',"
				+ " 'size': { 'width': "
				+ width/mDensity
				+ ", "
				+ "'height': "
				+ height/mDensity + "}});";
		injectJavaScript(injection);

		System.out.println(injection);
	}

	/**
	 *resize AdView on the top view hierarchy 
	 * */
	protected void resizeSelf(int width, int height, int left, int top, boolean allowOffscreen,String customClosePosition){
		ViewGroup parent = (ViewGroup)getParent();

		//remove AdView form current view hierarchy and use a empty FrameLayout to hold the place.
		int index = 0;
		int count = parent.getChildCount();
		for (index = 0; index < count; index++) {
			if (parent.getChildAt(index) == this)
				break;
		}
		mIndex = index;
		mPlaceHolder = new FrameLayout(getContext());
		mPlaceHolder.setId(PLACEHOLDER_ID);
		ViewGroup.LayoutParams lp = new ViewGroup.LayoutParams(getWidth(),getHeight());
		parent.addView(mPlaceHolder, index, lp);
		parent.removeView(this);

		//set new LayoutParams according to input parameters
		FrameLayout.LayoutParams newLayoutParams = new FrameLayout.LayoutParams(
				width, height);

		int defaultX = getLeft();
		int defaultY = getTop();
		int resizedLeft = 0;
		int resizedTop = 0;
		if(!allowOffscreen){
			resizedLeft = defaultX+left;
			resizedTop = defaultY+top;
			if((resizedLeft<0)||(resizedLeft+width>mContentWidth)){
				resizedLeft = (int)((mContentWidth-width)/2);
			}
			if(  (resizedTop<0) || (resizedTop+height>mContentHeight) ){
				resizedTop = (int)((mContentHeight-height)/2);
			}
		}else{
			resizedLeft = defaultX+left;
			resizedTop = defaultY+top;
		}

		newLayoutParams.gravity = Gravity.TOP;
		newLayoutParams.leftMargin= resizedLeft;
		newLayoutParams.topMargin= resizedTop;

		//Add AdView to contentView
		mContentView.addView(this, newLayoutParams);

		//Add close button
		inflateCloseBtn(true, customClosePosition);
		showCloseBtn();
	}

	/**
	 * resize AdView if the AdView have been resized
	 **/
	protected void resizeAgain(int width, int height, int left, int top, boolean allowOffscreen){
		FrameLayout.LayoutParams newLayoutParams = new FrameLayout.LayoutParams(
				width, height);

		int defaultX = getLeft();
		int defaultY = getTop();
		int resizedLeft = 0;
		int resizedTop = 0;
		if(!allowOffscreen){
			resizedLeft = defaultX+left;
			resizedTop = defaultY+top;
			if((resizedLeft<0)||(resizedLeft+width>mContentWidth)){
				resizedLeft = (int)((mContentWidth-width)/2);
			}
			if(  (resizedTop<0) || (resizedTop+height>mContentHeight) ){
				resizedTop = (int)((mContentHeight-height)/2);
			}
		}else{
			resizedLeft = defaultX+left;
			resizedTop = defaultY+top;
		}

		newLayoutParams.leftMargin= resizedLeft;
		newLayoutParams.topMargin= resizedTop;
		setLayoutParams(newLayoutParams);
	}


	/***CLOSE PART***/
	public void doClose(){
		System.out.println("Close AdView, check current state.");

		//Close if AdView is Expanded
		if(mViewState == ViewState.EXPANDED){
			System.out.println("==closeExpand==");

			//Do real close expand action
			closeExpand();

			if(mExpandParent == null){
				mViewState = ViewState.DEFAULT;
				String injection = "window.mraidview.fireChangeEvent({ 'state': \'default\',"
						+ " 'size': "
						+ "{ 'width': "
						+ mDefaultWidth
						+ ", "
						+ "'height': "
						+ mDefaultHeight + "}" + "});";
				injectJavaScript(injection);

				hideCloseBtn();
			}
			return;
		}

		//Close if AdView is Resized
		if(mViewState == ViewState.RESIZED){
			System.out.println("==closeResize==");

			//Do real close resize action
			closeResize();

			hideCloseBtn();

			mViewState = ViewState.DEFAULT;
			String injection = "window.mraidview.fireChangeEvent({ 'state': \'default\',"
					+ " 'size': "
					+ "{ 'width': "
					+ mDefaultWidth
					+ ", "
					+ "'height': "
					+ mDefaultHeight + "}" + "});";
			injectJavaScript(injection);

			return;
		}

		//Close if AdView is Default
		if(mViewState == ViewState.DEFAULT){
			return;
		}
	}

	/**
	 * close expand
	 **/
	protected void closeExpand(){
		//If this is an expanded AdView with new url, just call parent AdView to close
		if(mExpandParent != null){
			mExpandParent.closeExpand();
			return;
		}

		//expand is called with URL
		if(mExpandAdView != null){
			mExpandBackGround.removeAllViews();

			mContentView.removeView(mExpandBackGround);

			mExpandAdView = null;
			return;
		}

		//expand is called without URL
		if(mPlaceHolder != null){
			mExpandBackGround.removeView(this);

			mContentView.removeView(mExpandBackGround);
			resetSize();
			ViewGroup parent = (ViewGroup) mPlaceHolder.getParent();
			parent.addView(this, mIndex);
			parent.removeView(mPlaceHolder);
			parent.invalidate();

			mPlaceHolder = null;

			clearView();
			return;
		}
	}

	/**
	 * close resize
	 **/
	protected void closeResize(){

		//Remove AdView from contentView
		FrameLayout contentView = (FrameLayout) getRootView().findViewById(
				android.R.id.content);
		contentView.removeView(this);

		//Retrieve AdView dimension and position
		resetSize();
		ViewGroup parent = (ViewGroup) mPlaceHolder.getParent();
		parent.addView(this, mIndex);
		parent.removeView(mPlaceHolder);
		parent.invalidate();

		mPlaceHolder = null;
	}

	/**
	 * close default
	 * */
	protected void closeDefault(){
		setVisibility(View.INVISIBLE);
	}


	/***OPEN PART***/
	public void doOpen(String url){
		System.out.println("Will open===>"+url);
		Intent intent = new Intent(this.getContext(), InternalBrowser.class);
		intent.putExtra("extra_url", url);
		intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		this.getContext().startActivity(intent);
	}


	/***SHOW&HIDE***/
	public void doShow(){
		setVisibility(View.VISIBLE);

		String injection = "window.mraidview.fireChangeEvent({ state: \'default\' });";
		injectJavaScript(injection);

		System.out.println(injection);
	}

	public void doHide(){
		setVisibility(View.INVISIBLE);

		String injection = "window.mraidview.fireChangeEvent({state:\'hidden\'});";
		injectJavaScript(injection);

		System.out.println(injection);
	}

	public void setMaxSize(int width, int height){
		mOrmmaController.setMaxSize(width, height);
	}

	public String getPlacementType(){
		return "\'"+mPlacementType+"\'";
	}

	public String getCurrentPosotion() {
		return "{x: "+ (int) (getLeft() / mDensity) +","+"y: "+(int) (getRight() / mDensity)+","+" width: " + (int) (getWidth() / mDensity) + ", " + "height: "
				+ (int) (getHeight() / mDensity) + "}";
	}

	public String getDefaultPosition(){
		int x = (int)(getLeft()/mDensity);
		int y = (int)(getTop()/mDensity);
		int width = (int)(getWidth()/mDensity);
		int height = (int)(getHeight()/mDensity);

		return "{x:"+x+",y:"+y+",width:"+width+",height:"+height+"}";
	}

	public void useCustomClose(boolean use){
		mUseCustomClose = use;
	}


	/****************************************************/
	/**************INTERNAL METHOD**************/
	/****************************************************/


	/**
	 * Check mugeda.license signature
	 * */
	protected void checkLicense(){
		mSign = mOrmmaController.checkValidation();
		if(mSign&&mPlayer == null){
			mPlayer = mOrmmaController.getPlayer();
		}
	}

	/**
	 * Called by all constructor. Do initialization job.
	 * */
	protected void initialize(){

		//OrmmaController
		mOrmmaController = new OrmmaController(this, getContext());

		//check license
		//checkLicense();

		//if sign is not valid ,return directly.
		if(!mSign)return;

		//read mraid js and mraid_bridge js
		if(mraid == null){
			mraid = getMraidJs("js/mraid.js");
		}
		if(mraidbridge == null){
			mraidbridge = getMraidJs("js/mraid_bridge.js");
		}
		
		//obtain screen params
		DisplayMetrics dm = getContext().getResources().getDisplayMetrics();
		mScreenWidth = dm.widthPixels;
		mScreenHeigth = dm.heightPixels;
		mDensity = dm.density;

		//ContentView
		mContentView =  (FrameLayout) ((Activity)mContext).getWindow().getDecorView().findViewById(android.R.id.content);

		//set BackGroundColor
		setBackgroundColor(Color.argb(0, 0, 0, 0));
		if(android.os.Build.VERSION.SDK_INT>=11){
			setLayerType(WebView.LAYER_TYPE_SOFTWARE, null);
		}

		//initialize webview properties
		getSettings().setJavaScriptEnabled(true);
		getSettings().setUseWideViewPort(true);
		getSettings().setLoadWithOverviewMode(true);
		getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
		
		setVerticalScrollBarEnabled(false);
		setHorizontalScrollBarEnabled(false);
		getSettings().setPluginState(WebSettings.PluginState.ON);

		//set webviewclient
		setWebViewClient(new WebViewClient(){

			@Override
			public void onPageStarted(WebView view, String url, Bitmap favicon) {
				super.onPageStarted(view, url, favicon);
				if(url.equals("about:blank")){
					return;
				}
				System.out.println("Start loading Ad==>"+url);
				String initialInfo = mOrmmaController.sendInitToAd();
				injectJavaScript(mraidbridge+mraid+initialInfo);
			}

			@Override
			public void onPageFinished(WebView view, String url) {
				if(url.equals("about:blank")){
					return;
				}

				System.out.println("Finish loading Ad==>"+url);

				String readyStr = "mraidview.fireChangeEvent({'state':'default'});"+"mraidview.fireReadyEvent();";
				injectJavaScript(readyStr);
				
				if(mViewState == ViewState.DEFAULT){
					mDefaultWidth = getWidth();
					mDefaultHeight = getHeight();
					mDefaultLayoutParams = getLayoutParams();
				}

				if(AdViewCore.this.getVisibility() != VISIBLE){
					AdViewCore.this.setVisibility(VISIBLE);
				}

			}

			@Override
			public boolean shouldOverrideUrlLoading(WebView view, String url) {
				System.out.println("Get message from Ad======"+url);

				if(url.startsWith("mraid://")){
					mOrmmaController.obtainMessage(url);
					return true;
				} 

				if (url.startsWith("mailto:") || url.startsWith("sms:") ||url.startsWith("tel:")) {
					Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					mContext.getApplicationContext().startActivity(intent);
					return true;
				}

				if(url.startsWith("http://")||url.startsWith("https://")){
					Uri uri = Uri.parse(url);        
					Intent intent = new Intent(Intent.ACTION_VIEW, uri);  
					intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
					mContext.getApplicationContext().startActivity(intent);
					return true;
				}

				return true;
			}
			
		});

		//add close button
		initCloseBtn();

		//Listener
		mListener = new Listener();

		//setHandler
		mInternalHandler = new Handler();

		//Fixed virtual soft keyboard doesn't popup on 2.3
		setOnTouchListener(new View.OnTouchListener(){
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				switch (event.getAction()) {
				case MotionEvent.ACTION_DOWN:
				case MotionEvent.ACTION_UP:
					if (!v.hasFocus()) {
						v.requestFocus();
					}
					break;
				}
				return false;
			}
		});

		//allow javascript alert
		setWebChromeClient(new WebChromeClient() { 

			@Override 
			public boolean onJsAlert(WebView view, String url, String message, JsResult result) { 
				return super.onJsAlert(view, url, message, result); 
			}

		});

	}

	/**
	 * Real method to load the advertisement. Called by loadInline or loadInterstitial.
	 * */
	protected void loadUrl(boolean reset) {
		if(getVisibility() == 0)setVisibility(View.INVISIBLE);
		if(reset)resetAllToDefault();
		
		//String initialInfo = mOrmmaController.sendInitToAd();
		//loadUrl("javascript:"+mraidbridge+mraid+initialInfo);
		loadUrl(mUrl);
	}
	

	/**
	 * Get mraid.js and mraid_bridge.js.
	 * */
	protected String getMraidJs(String fileName){
		String re;

		InputStream is = this.getClass().getResourceAsStream(fileName);
		re = CommonUtil.streamToString(is);

		return re;
	}

	/**
	 * Reset dimension of AdView.
	 */
	protected void resetSize() {
		setLayoutParams(mDefaultLayoutParams);
		requestLayout();
	}

	/**
	 * revert to default state. called when new Ad loaded.
	 * */
	protected void resetAllToDefault(){

		if(mViewState == ViewState.DEFAULT){
			closeDefault();
			stopAllListeners();
			
			return;
		}

		if (mViewState == ViewState.EXPANDED) {
			closeResize();
			stopAllListeners();

			return;
		}

		if (mViewState == ViewState.EXPANDED) {
			closeExpand();
			stopAllListeners();

			return;
		}

	}

	protected void stopAllListeners(){
		mOrmmaController.stopAllListeners();
	}

	/**
	 * attach a close button to the view.
	 **/
	protected void initCloseBtn(){
		mButton = new ImageButton(getContext());
		mButton.setVisibility(INVISIBLE);
		mButton.setScaleType(ScaleType.CENTER_CROP);
		mButton.setBackgroundColor(0);
		mButton.setPadding(0, 0, 0, 0);
		mButton.setId(CLOSEBUTTON_ID);

		FrameLayout fl = new FrameLayout(getContext());
		fl.setBackgroundColor(0);
		fl.setPadding(0, 0, 0, 0);
		FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams((int)(50*mDensity), (int)(50*mDensity));
		flp.gravity = Gravity.RIGHT;
		flp.topMargin = 5;
		flp.rightMargin = 5;
		fl.addView(mButton, flp);

		ViewGroup.LayoutParams vlp = new ViewGroup.LayoutParams(
				ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT);

		addView(fl,vlp);

		mButton.setOnClickListener(new OnClickListener(){
			@Override
			public void onClick(View v) {
				System.out.println("use click close button");
				injectJavaScript("mraid.close()");
			}
		});

	} 

	protected void inflateCloseBtn(boolean useCustomClose, String customClosePosition){
		mButton.setImageResource(android.R.color.transparent);
		if(!useCustomClose){
			String strBtn = "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"; 
			byte[] bitmapArray = null;
			bitmapArray = Base64.decode(strBtn, Base64.DEFAULT);
			Bitmap bitmap = BitmapFactory.decodeByteArray(bitmapArray, 0, bitmapArray.length);
			mButton.setImageBitmap(bitmap);
		}

		FrameLayout.LayoutParams lp = (android.widget.FrameLayout.LayoutParams) mButton.getLayoutParams();
		if(customClosePosition.equals("top-left")){
			lp.gravity = Gravity.LEFT;
		}
		else 
			if(customClosePosition.equals("top-center")){
				lp.gravity = Gravity.CENTER_HORIZONTAL ;
			}
			else
				if(customClosePosition.equals("top-right")){
					lp.gravity = Gravity.RIGHT;
				}
				else
					if(customClosePosition.equals("center")){
						lp.gravity = Gravity.CENTER;
					}
					else
						if(customClosePosition.equals("bottom-left")){
							lp.gravity = Gravity.BOTTOM;
						}
						else
							if(customClosePosition.equals("bottom-center")){
								lp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
							}
							else
								if(customClosePosition.equals("bottom-right")){
									lp.gravity = Gravity.BOTTOM | Gravity.RIGHT;
								}
	}

	protected void showCloseBtn(){
		if(mButton != null){
			if(mButton.getVisibility() != View.VISIBLE)mButton.setVisibility(View.VISIBLE);
		}
	}

	protected void hideCloseBtn(){
		if(mButton != null){
			if(mButton.getVisibility() == View.VISIBLE)mButton.setVisibility(View.GONE);
		}
	}

	/**
	 * Lock or Unlock orientation
	 * 
	 * @param
	 * boolean lock, true means lock, false means unlock.
	 * */
	public void allowOrientation(boolean allow){
		if(!allow){
			int currentOrientation = getResources().getConfiguration().orientation;
			if (currentOrientation == Configuration.ORIENTATION_LANDSCAPE) {
				((Activity)mContext).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
			}
			else {
				((Activity)mContext).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT);
			}
		}else{
			((Activity)mContext).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
		}
	}

	/**
	 * Force change the orientation of current Activity.
	 * 
	 * @param
	 * int orientation, 0 means Portrait, 1 means Landscape.
	 * */
	public void forceOrientation(int orientation){
		if(orientation ==0 ) {
			((Activity)mContext).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		}else if(orientation == 1) {
			((Activity)mContext).setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
		}
	}

	//resolve advertisement url to get the base url
	public void resolveUrl(String url){
		mUrl = url;
		mBaseUrl = "";

		String[] parts = url.split("/");
		for(int i=0; i<parts.length-1;i++){
			if(parts[i].length() == 0){
				parts[i] = "/";
				mBaseUrl += parts[i];
			}else{
				mBaseUrl += parts[i]+"/";
			}
		}
	}

	public class Listener{

		void onExpand(boolean newUrl){
			//Remove black cover after 400 mileseconds
			if(newUrl){
				mInternalHandler.postDelayed(new ExpandRunnable(mCover), 400);
			}else{
				mInternalHandler.postDelayed(new ExpandRunnable(mCover), 400);
			}
		}

		void onResize(){
		}

		void onCloseSelf(){
		}

		void onCloseResized(){
		}

	}
	
	@Override
	protected void onVisibilityChanged(View changedView, int visibility) {
		if(changedView == this){
			if(visibility == 0){
			}else{
			}
		}
	}

}