package net.osmand.plus.dashboard;

import java.util.ArrayList;
import java.util.List;

import net.osmand.data.LatLon;
import net.osmand.plus.OsmAndAppCustomization;
import net.osmand.plus.OsmandApplication;
import net.osmand.plus.R;
import net.osmand.plus.activities.MapActivity;
import net.osmand.plus.activities.search.SearchActivity;
import net.osmand.plus.activities.search.SearchHistoryFragment;
import net.osmand.plus.dialogs.DirectionsDialogs;
import net.osmand.plus.helpers.FontCache;
import net.osmand.plus.helpers.SearchHistoryHelper;
import net.osmand.plus.helpers.SearchHistoryHelper.HistoryEntry;
import net.osmand.util.Algorithms;
import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Created by Denis on 24.11.2014.
 */
public class DashRecentsFragment extends DashLocationFragment {
	public static final String TAG = "DASH_RECENTS_FRAGMENT";

	private List<ImageView> arrows = new ArrayList<ImageView>();
	List<HistoryEntry> points = new ArrayList<HistoryEntry>();

	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
		View view = getActivity().getLayoutInflater().inflate(R.layout.dash_common_fragment, container, false);
		Typeface typeface = FontCache.getRobotoMedium(getActivity());
		((TextView) view.findViewById(R.id.fav_text)).setTypeface(typeface);
		((TextView) view.findViewById(R.id.fav_text)).setText(Algorithms
				.capitalizeFirstLetterAndLowercase(getString(R.string.shared_string_history)));
		((Button) view.findViewById(R.id.show_all)).setTypeface(typeface);

		(view.findViewById(R.id.show_all)).setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View view) {
				Activity activity = getActivity();
				OsmAndAppCustomization appCustomization = getMyApplication().getAppCustomization();

				final Intent search = new Intent(activity, appCustomization.getSearchActivity());
				// search.putExtra(SearchActivity.SHOW_ONLY_ONE_TAB, true);
				search.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
				getMyApplication().getSettings().SEARCH_TAB.set(SearchActivity.HISTORY_TAB_INDEX);
				activity.startActivity(search);
			}
		});
		return view;
	}

	@Override
	public void onOpenDash() {
		setupRecents();
	}

	public void setupRecents() {
		View mainView = getView();
		SearchHistoryHelper helper = SearchHistoryHelper.getInstance((OsmandApplication) getActivity()
				.getApplicationContext());
		points = helper.getHistoryEntries();
		arrows.clear();
		if (points.size() == 0) {
			(mainView.findViewById(R.id.main_fav)).setVisibility(View.GONE);
			return;
		} else {
			(mainView.findViewById(R.id.main_fav)).setVisibility(View.VISIBLE);
		}

		LinearLayout recents = (LinearLayout) mainView.findViewById(R.id.items);
		recents.removeAllViews();
		if (points.size() > 3) {
			points = points.subList(0, 3);
		}
		LatLon loc = getDefaultLocation();
		List<DashLocationView> distances = new ArrayList<DashLocationFragment.DashLocationView>();
		for (final HistoryEntry historyEntry : points) {
			LayoutInflater inflater = getActivity().getLayoutInflater();
			View view = inflater.inflate(R.layout.search_history_list_item, null, false);
			SearchHistoryFragment.udpateHistoryItem(historyEntry, view, loc, getActivity(), getMyApplication());
			view.findViewById(R.id.divider).setVisibility(View.VISIBLE);
			view.findViewById(R.id.navigate_to).setVisibility(View.VISIBLE);
			view.findViewById(R.id.navigate_to).setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					DirectionsDialogs.directionsToDialogAndLaunchMap(getActivity(), historyEntry.getLat(),
							historyEntry.getLon(), historyEntry.getName());
				}
			});
			view.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View view) {
					getMyApplication().getSettings().setMapLocationToShow(historyEntry.getLat(), historyEntry.getLon(),
							15, historyEntry.getName(), true, historyEntry); //$NON-NLS-1$
					MapActivity.launchMapActivityMoveToTop(getActivity());
				}
			});
			DashLocationView dv = new DashLocationView((ImageView) view.findViewById(R.id.direction),
					(TextView) view.findViewById(R.id.distance), new LatLon(historyEntry.getLat(),
							historyEntry.getLon()));
			distances.add(dv);
			recents.addView(view);
		}
		this.distances = distances;
	}


}
