package com.mugeda.androiddemo;

import com.mugeda.androidsdk.view.AdView;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;

public class PreviewActivity extends Activity{
	
	public static final String DATA_NAME = "name";
	public static final String DATA_DESC = "desc";
	public static final String DATA_URL = "url";
	
	private String mName;
	private String mDesc;
	private String mURL;
	
	public AdView mAdView;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.preview);
		
		Intent intent = getIntent();
		mName = intent.getStringExtra(DATA_NAME);
		mDesc = intent.getStringExtra(DATA_DESC);
		mURL = intent.getStringExtra(DATA_URL);
		
		mAdView = (AdView)findViewById(R.id.adview);
		
		Button closeBtn = (Button)findViewById(R.id.backbutton);
		closeBtn.setOnClickListener(new BackListener());
		
		setTitleAndDesc();
		loadAd();
	}
	
	private void setTitleAndDesc(){
		TextView title = (TextView)findViewById(R.id.preview_title);
		TextView desc = (TextView)findViewById(R.id.preview_desc);
        title.setText(mName);
        desc.setText(mDesc);
	}
	
	private void loadAd(){
		mAdView.loadAd(mURL);
	}
	
	@Override
	protected void onDestroy(){
		mAdView.destroy();
		super.onDestroy();
	}

	class BackListener implements OnClickListener{
		@Override
		public void onClick(View v) {
			PreviewActivity.this.finish();
		}
	}
	
}
