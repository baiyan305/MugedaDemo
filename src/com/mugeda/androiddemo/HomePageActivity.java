package com.mugeda.androiddemo;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.mugeda.androiddemo.R;

import android.os.Bundle;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.Menu;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.GridView;

public class HomePageActivity extends Activity {
	
	private List<Map<String,Object>> mData;
	
	public static final String DATA_NAME = "name";
	public static final String DATA_DESC = "desc";
	public static final String DATA_URL = "url";
	public static final String DATA_THUMBNAIL = "thumbnail";

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		requestWindowFeature(Window.FEATURE_NO_TITLE);
		setContentView(R.layout.homepage);
		
		//Set data to mData
		getData(getAssetJson());
		
		GridView adGrid = (GridView)findViewById(R.id.adgrid);
		GridAdapter gridAdapter = new GridAdapter(this.getApplicationContext(), mData);
		adGrid.setAdapter(gridAdapter);
		
		adGrid.setOnItemClickListener(new GridClickListener());
	}
	
	/**
	 * Set mData
	 * */
	private void getData(JSONArray jsonarray){
		mData = new ArrayList<Map<String,Object>>();
		Map<String,Object> map;
		for(int num=0;num<jsonarray.length();num++){
			map = new HashMap<String,Object>();
			try {
				JSONObject data =jsonarray.getJSONObject(num);
				if(data.has(DATA_NAME)){
					map.put(DATA_NAME, data.getString(DATA_NAME));
				}
				if(data.has(DATA_DESC)){
					map.put(DATA_DESC, data.getString(DATA_DESC));
				}
				if(data.has(DATA_URL)){
					map.put(DATA_URL, data.getString(DATA_URL));
				}
				if(data.has(DATA_THUMBNAIL)){
					Bitmap thumbnail = getBitMap(data.getString(DATA_THUMBNAIL).substring(22));
					map.put(DATA_THUMBNAIL, thumbnail);
				}
			} catch (JSONException e) {
				e.printStackTrace();
			}
			mData.add(map);
		}
	}
	
	/**
	 * Read data json from asset  
	 * */
	private JSONArray getAssetJson(){
		JSONArray jsonarray = null;
		try {
			//get data in String format
			InputStream is = this.getAssets().open("demos/demos.json");
			
			int i = -1;
			byte[] b = new byte[1024];
			StringBuffer sb = new StringBuffer();
			while ((i = is.read(b)) != -1) {
			    sb.append(new String(b, 0, i));
			}
			String content = sb.toString();
			
			//Save data to mData
			JSONObject animations = new JSONObject(content);
			if(animations.has("animations")){
				String animationSet = animations.getString("animations");
				jsonarray = new JSONArray(animationSet);
				for(int num=0;num<jsonarray.length();num++){
					//Map<String,Object> data = new HashMap<String,Object>();
				}
			}
			
		} catch (IOException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		
		return jsonarray;
	}
	
	/**
	 * Read image from assets and generate Bitmap object
	 * */
	private Bitmap getBitMap(String url){
		Bitmap thumbnail = null;
		
		try {
			InputStream bitMapIs = getAssets().open(url);
			thumbnail = BitmapFactory.decodeStream(bitMapIs);
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		return thumbnail;
	}
	
	class GridClickListener implements OnItemClickListener{

		@Override
		public void onItemClick(AdapterView<?> arg0, View arg1, int position, long arg3) {
			Intent intent = new Intent(HomePageActivity.this, PreviewActivity.class);
			intent.putExtra(DATA_NAME, 	((String)(mData.get(position).get(DATA_NAME))));
			intent.putExtra(DATA_DESC, 	((String)(mData.get(position).get(DATA_DESC))));
			intent.putExtra(DATA_URL, 	((String)(mData.get(position).get(DATA_URL))));

			startActivity(intent);
		}
		
	}
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.activity_home_page, menu);
		return true;
	}
	
}
