package org.frasermccrossan.ltc;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class PredictionAdapter extends ArrayAdapter<Prediction> {

	Context context;
	int layoutResourceId;
	ArrayList<Prediction> predictions = null;
	
	public PredictionAdapter(Context c, int layoutResId, ArrayList<Prediction> p) {
		super(c, layoutResId, p);
		context = c;
		layoutResourceId = layoutResId;
		predictions = p;
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		View row = convertView;
		
		//if (row == null) {
			LayoutInflater inflater = ((Activity)context).getLayoutInflater();
			row = inflater.inflate(layoutResourceId, parent, false);
		//}
		
		Prediction p = predictions.get(position);
		TextView routeNumber = (TextView)row.findViewById(R.id.route_number);
		ImageView routeDirImg = (ImageView)row.findViewById(R.id.route_direction_img);
		TextView routeLongName = (TextView)row.findViewById(R.id.route_long_name);
		TextView destination = (TextView)row.findViewById(R.id.destination);
		TextView crossingTime = (TextView)row.findViewById(R.id.crossing_time);
		TextView rawCrossingTime = (TextView)row.findViewById(R.id.raw_crossing_time);
		
		routeNumber.setText(p.routeNumber());
		routeDirImg.setImageResource(p.routeDirectionImgRes());
		routeLongName.setText(p.routeLongName());
		crossingTime.setText(p.crossInMinutes());
		destination.setText(p.destination());
		rawCrossingTime.setText(p.crossAt());
		
		if (p.isValid()) {
			destination.setTextAppearance(context, R.style.destination);
			if (p.isQuerying()) {
				LinearLayout predictionTimes = (LinearLayout)row.findViewById(R.id.prediction_times);
				predictionTimes.setBackgroundResource(R.drawable.time_border_querying);
			}
		}
		else {
			if (p.isSerious()) {
				destination.setTextAppearance(context, R.style.seriously_invalid_destination);
			}
			else {
				destination.setTextAppearance(context, R.style.invalid_destination);
			}
		}
		
		return row;
	}
	
}
