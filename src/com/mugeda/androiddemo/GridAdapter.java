package com.mugeda.androiddemo;

import java.util.List;
import java.util.Map;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class GridAdapter extends BaseAdapter{
	
	public static final String DATA_NAME = "name";
	public static final String DATA_DESC = "desc";
	public static final String DATA_URL = "url";
	public static final String DATA_THUMBNAIL = "thumbnail";
	
	Context mContext;
	List<Map<String,Object>> mData;
	LayoutInflater mInflater = null;
	
	GridAdapter(Context context, List<Map<String,Object>> data){
		mContext = context;
		mData = data;
		mInflater = LayoutInflater.from(context);
	}
	@Override
	public int getCount() {
		return mData.size();
	}

	@Override
	public Object getItem(int position) {
		return position;
	}

	@Override
	public long getItemId(int position) {
		return position;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		ViewHolder holder = null;
		
		if(convertView == null){
			holder = new ViewHolder();
			convertView = mInflater.inflate(R.layout.griditem, null);
			holder.title = (TextView)convertView.findViewById(R.id.griditemtitle);
            holder.img = (ImageView)convertView.findViewById(R.id.griditemthumbnail);
            convertView.setTag(holder);
		}else{
			holder = (ViewHolder)convertView.getTag();
		}
		
		holder.img.setImageBitmap((Bitmap)(mData.get(position).get(DATA_THUMBNAIL)));
		holder.title.setText((String)(mData.get(position).get(DATA_NAME)));
		
		return convertView;
	}
	
    //ViewHolder静态类
    static class ViewHolder
    {
        public ImageView img;
        public TextView title;
    }
    
}