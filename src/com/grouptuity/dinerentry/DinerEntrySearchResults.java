package com.grouptuity.dinerentry;

import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import com.grouptuity.*;
import com.grouptuity.model.Contact;
import com.grouptuity.model.Diner;
import com.grouptuity.style.composites.*;
import com.grouptuity.view.*;
import com.grouptuity.view.ContactListing.ContactListingStyle;
import com.grouptuity.view.ContactListingAdapter.ContactListingAdapterController;
import com.grouptuity.view.ScalableTextView.ScalingStyle;

public class DinerEntrySearchResults extends ViewTemplate<DinerEntryModel,DinerEntrySearchResults.DinerEntrySearchResultsController>
{
	final private ContactListingAdapter adapterAlphabet, adapterAlgorithm;
	final private ListView listViewAlphabet, listViewAlgorithm;
	final private LoadMessagePanel loadMessagePanel;
	private ListView activeListView;
	private ContactListingAdapter activeAdapter;
	private boolean loadFlag = false, searchAlgorithm;

	protected static interface DinerEntrySearchResultsController extends ControllerTemplate{void onContactSelected(Contact c);}

	public DinerEntrySearchResults(ActivityTemplate<DinerEntryModel> context, DinerEntrySearchResultsController controller)
	{
		super(context);
		searchAlgorithm = Grouptuity.SEARCH_ALGORITHM.getValue();

		final DinerEntrySearchResultsController parentController = controller;
		adapterAlphabet = new ContactListingAdapter(context,true,model.suggestedContacts,ContactListingStyle.DIRECTORY_LISTING,new ContactListingAdapterController()
		{
			public void onDinerClick(Diner d){}
			public void onContactClick(Contact c){parentController.onContactSelected(c);}
			public void onPaymentToggle(Diner d){}
			public void onDinerLongClick(Diner d){}
		});
		listViewAlphabet = new ListView(activity);
		listViewAlphabet.setAdapter(adapterAlphabet);
		listViewAlphabet.setFastScrollEnabled(true);
		listViewAlphabet.setCacheColorHint(Grouptuity.foregroundColor);
		addView(listViewAlphabet,Grouptuity.fillWrapLP(1.0f));

		adapterAlgorithm = new ContactListingAdapter(context,false,model.suggestedContacts,ContactListingStyle.DIRECTORY_LISTING,new ContactListingAdapterController()
		{
			public void onDinerClick(Diner d){}
			public void onContactClick(Contact c){parentController.onContactSelected(c);}
			public void onPaymentToggle(Diner d){}
			public void onDinerLongClick(Diner d){}
		});
		listViewAlgorithm = new ListView(activity);
		listViewAlgorithm.setAdapter(adapterAlgorithm);
		listViewAlgorithm.setFastScrollEnabled(true);
		listViewAlgorithm.setCacheColorHint(Grouptuity.foregroundColor);
		addView(listViewAlgorithm,Grouptuity.fillWrapLP(1.0f));

		if(searchAlgorithm){activeListView = listViewAlgorithm;activeAdapter = adapterAlgorithm;listViewAlphabet.setVisibility(GONE);}
		else{activeListView = listViewAlphabet;activeAdapter = adapterAlphabet;listViewAlgorithm.setVisibility(GONE);}

		loadMessagePanel = new LoadMessagePanel();
		addView(loadMessagePanel,Grouptuity.fillWrapLP(1.0f));
		loadMessagePanel.setVisibility(View.GONE);
	}

	protected void rewind()
	{
		if(searchAlgorithm)
			activeListView.setSelection(0);
		else
		{
			for(int i=0; i<model.suggestedContacts.size(); i++)
			{
				String compareName = model.suggestedContacts.get(i).name;
				if(compareName.compareToIgnoreCase(model.dinerNameSearch)>=0)
				{activeListView.setSelection(i);return;}
			}
			activeListView.setSelection(0);
		}
	}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle(){}
	protected void refresh()
	{
		if(searchAlgorithm!=Grouptuity.SEARCH_ALGORITHM.getValue())
			if(Grouptuity.SEARCH_ALGORITHM.getValue())
			{
				searchAlgorithm = true;
				activeAdapter = adapterAlgorithm;
				activeListView = listViewAlgorithm;
				if(!loadFlag)
				{
					listViewAlphabet.setVisibility(GONE);
					listViewAlgorithm.setVisibility(VISIBLE);
				}
				rewind();
			}
			else
			{
				searchAlgorithm = false;
				activeAdapter = adapterAlphabet;
				activeListView = listViewAlphabet;
				if(!loadFlag)
				{
					listViewAlgorithm.setVisibility(GONE);
					listViewAlphabet.setVisibility(VISIBLE);
				}
				rewind();
			}

		activeAdapter.notifyDataSetChanged();
		if(!Grouptuity.contactsLoaded)
		{
			if(!loadFlag)
			{
				activeListView.setVisibility(View.GONE);
				loadMessagePanel.setVisibility(View.VISIBLE);
				loadFlag = true;
			}
			loadMessagePanel.setProgress(Grouptuity.contactsLoadPercent);
		}
		else if(loadFlag)
		{
			activeListView.setVisibility(View.VISIBLE);
			loadMessagePanel.setVisibility(View.GONE);
			loadFlag = false;
		}
	}

	private class LoadMessagePanel extends LinearLayout
	{
		final private LinearLayout topSpacer, bottomSpacer;
		final private Panel panel;
		final private ScalableTextView textView;
		final private ProgressBar progressBar;

		public LoadMessagePanel()
		{
			super(activity);
			setOrientation(LinearLayout.VERTICAL);
			setGravity(Gravity.CENTER);

			topSpacer = new LinearLayout(activity);
			bottomSpacer = new LinearLayout(activity);
			panel = new Panel(activity,new RoundForegroundPanelStyle());

			progressBar = new ProgressBar(activity,null,android.R.attr.progressBarStyleHorizontal);
			panel.addView(progressBar);

			textView = new ScalableTextView(activity, new RoundForegroundPanelStyle(), progressBar,ScalingStyle.MATCH_HEIGHT);
			textView.setText("Loading Contacts...");
			textView.setScalingMultiplier(2.0f);
			textView.setPaddingOverride(0, 0, 0, 0);
			panel.addView(textView,Grouptuity.fillWrapLP());

			addView(topSpacer,Grouptuity.fillWrapLP(1.0f));
			addView(panel,new LayoutParams((int)(activity.displayMetrics.widthPixels*0.8),LayoutParams.WRAP_CONTENT));
			addView(bottomSpacer,Grouptuity.fillWrapLP(1.0f));
			setLayoutParams(Grouptuity.fillWrapLP(1.0f));
		}

		private void setProgress(int progress){progressBar.setProgress(progress);}
	}
}