package net.osmand.plus.osmedit;

import net.osmand.data.PointDescription;
import net.osmand.plus.OsmAndAppCustomization;
import net.osmand.plus.OsmandPlugin;
import net.osmand.plus.ProgressImplementation;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.dashboard.DashBaseFragment;
import net.osmand.plus.helpers.FontCache;
import net.osmand.plus.myplaces.FavoritesActivity;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by Denis
 * on 20.01.2015.
 */
public class DashOsmEditsFragment extends DashBaseFragment implements OsmEditsUploadListener {
	public static final String TAG = "DASH_OSM_EDITS_FRAGMENT";

	OsmEditingPlugin plugin;

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		plugin = OsmandPlugin.getEnabledPlugin(OsmEditingPlugin.class);

		View view = getActivity().getLayoutInflater().inflate(R.layout.dash_audio_video_notes_plugin, container, false);
		Typeface typeface = FontCache.getRobotoMedium(getActivity());
		TextView header = ((TextView) view.findViewById(R.id.notes_text));
		header.setTypeface(typeface);
		header.setText(R.string.osm_settings);
		Button manage = ((Button) view.findViewById(R.id.show_all));
		manage.setTypeface(typeface);
		manage.setText(R.string.osm_editing_manage);
		(view.findViewById(R.id.show_all)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				startFavoritesActivity(FavoritesActivity.OSM_EDITS_TAB);
			}
		});

		return view;
	}


	@Override
	public void onOpenDash() {
		if (plugin == null) {
			plugin = OsmandPlugin.getEnabledPlugin(OsmEditingPlugin.class);
		}
		setupEditings();		
	}
	
	private void setupEditings() {
		View mainView = getView();
		if (plugin == null){
			mainView.setVisibility(View.GONE);
			return;
		}

		ArrayList<OsmPoint> dataPoints = new ArrayList<>();
		getOsmPoints(dataPoints);
		if (dataPoints.size() == 0){
			mainView.setVisibility(View.GONE);
			return;
		} else {
			mainView.setVisibility(View.VISIBLE);
		}

		LinearLayout osmLayout = (LinearLayout) mainView.findViewById(R.id.notes);
		osmLayout.removeAllViews();

		for (final OsmPoint point : dataPoints){
			LayoutInflater inflater = getActivity().getLayoutInflater();
			View view = inflater.inflate(R.layout.note, null, false);

			OsmEditsFragment.getOsmEditView(view, point, getMyApplication());
			ImageButton send =(ImageButton) view.findViewById(R.id.play);
			send.setImageDrawable(getMyApplication().getIconsCache().getContentIcon(R.drawable.ic_action_gup_dark));
			send.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					OpenstreetmapRemoteUtil remotepoi = new OpenstreetmapRemoteUtil(getActivity());
					OsmPoint[] toUpload = new OsmPoint[]{point};
					OsmBugsRemoteUtil remotebug = new OsmBugsRemoteUtil(getMyApplication());
					ProgressDialog dialog = ProgressImplementation.createProgressDialog(
							getActivity(),
							getString(R.string.uploading),
							getString(R.string.local_openstreetmap_uploading),
							ProgressDialog.STYLE_HORIZONTAL).getDialog();
					UploadOpenstreetmapPointAsyncTask uploadTask = new UploadOpenstreetmapPointAsyncTask(dialog,DashOsmEditsFragment.this, remotepoi,
							remotebug, toUpload.length);
					uploadTask.execute(toUpload);
					dialog.show();
				}
			});
			view.findViewById(R.id.options).setVisibility(View.GONE);
			view.findViewById(R.id.divider).setVisibility(View.VISIBLE);
			view.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					boolean poi = point.getGroup() == OsmPoint.Group.POI;
					String name = poi ?
							((OpenstreetmapPoint) point).getName() : ((OsmNotesPoint) point).getText();
					getMyApplication().getSettings().setMapLocationToShow(point.getLatitude(), point.getLongitude(),
							15, new PointDescription(poi ? PointDescription.POINT_TYPE_POI : PointDescription.POINT_TYPE_OSM_BUG, name), true,
							point); //$NON-NLS-1$
					MapActivity.launchMapActivityMoveToTop(getActivity());
				}
			});
			osmLayout.addView(view);
		}
	}

	private void getOsmPoints(ArrayList<OsmPoint> dataPoints) {
		OpenstreetmapsDbHelper dbpoi = new OpenstreetmapsDbHelper(getActivity());
		OsmBugsDbHelper dbbug = new OsmBugsDbHelper(getActivity());

		List<OpenstreetmapPoint> l1 = dbpoi.getOpenstreetmapPoints();
		List<OsmNotesPoint> l2 = dbbug.getOsmbugsPoints();
		if (l1.isEmpty()){
			int i = 0;
			for(OsmPoint point : l2){
				if (i > 2) {
					break;
				}
				dataPoints.add(point);
				i++;
			}
		} else if (l2.isEmpty()) {
			int i = 0;
			for(OsmPoint point : l1){
				if (i > 2) {
					break;
				}
				dataPoints.add(point);
				i++;
			}
		} else {
			dataPoints.add(l1.get(0));
			dataPoints.add(l2.get(0));
			if (l1.size() > 1){
				dataPoints.add(l1.get(1));
			} else if (l2.size() > 1){
				dataPoints.add(l2.get(1));
			}
		}
	}

	@Override
	public void uploadUpdated(OsmPoint point) {
		if (!this.isDetached()){
			onOpenDash();
		}
	}

	@Override
	public void uploadEnded(Integer result) {
		if (!this.isDetached()){
			onOpenDash();
		}
	}
}
