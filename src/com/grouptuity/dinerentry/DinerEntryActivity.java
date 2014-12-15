package com.grouptuity.dinerentry;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import com.grouptuity.*;
import com.grouptuity.itementry.ItemEntryActivity;
import com.grouptuity.model.Contact;
import com.grouptuity.model.Diner;
import com.grouptuity.dinerentry.DinerEntryBar.DinerEntryBarController;
import com.grouptuity.dinerentry.DinerEntrySearchResults.DinerEntrySearchResultsController;
import com.grouptuity.dinerentry.DinerEntryStatusBar.DinerEntryStatusBarController;
import com.grouptuity.view.*;

public class DinerEntryActivity extends ActivityTemplate<DinerEntryModel>
{
	private DinerEntryBar dinerEntryBar;
	private DinerEntrySearchResults dinerEntrySearchResults;
	private DinerEntryStatusBar dinerEntryStatusBar;
	private LinearLayout focusGrabber;
	private boolean searchAlgorithm;
	private boolean statusBarScrollScheduled;

	@SuppressLint("ShowToast")
	protected void createActivity(Bundle bundle)
	{
		model = new DinerEntryModel(this,bundle);
		menuInt = R.menu.diner_entry_menu;
		searchAlgorithm = Grouptuity.SEARCH_ALGORITHM.getValue();

		titleBar = new TitleBar(this,"Who's Dining?",TitleBar.BACK_AND_MENU);

		focusGrabber = new LinearLayout(this);
		focusGrabber.setFocusableInTouchMode(true);

		dinerEntryBar = new DinerEntryBar(this,focusGrabber);
		dinerEntryBar.setController(new DinerEntryBarController()
		{
			public void onAddButtonClick(String text)
			{
				model.currentDiner = model.createDinerFromName(text);
				dinerEntryBar.blankWithRewind();
				model.processNewSearch("");
				model.setNewState(DinerEntryModel.DINER_ENTRY_TABLE_PLACEMENT);
			}
			public void onTextContactTyping(String text){model.processNewSearch(text);model.setAddedContactBasedOnCurrentSearch(false);refreshActivity();dinerEntrySearchResults.rewind();}
			public void onTextContactEntry(String text)
			{
				hideKeyboard(); //this should close the keyboard
				focusGrabber.requestFocus();
				focusGrabber.requestFocusFromTouch();
				//model.currentDiner = model.createDinerFromName(text);	model.setNewState(DinerEntryModel.DINER_ENTRY_TABLE_PLACEMENT); //adds the diner based on current text 
			}
		});

		dinerEntrySearchResults = new DinerEntrySearchResults(this,new DinerEntrySearchResultsController()
		{
			public void onContactSelected(Contact c)
			{
				model.currentDiner = new Diner(c);
				model.setNewState(DinerEntryModel.DINER_ENTRY_TABLE_PLACEMENT);
			}
		});

		dinerEntryStatusBar = new DinerEntryStatusBar(this);
		dinerEntryStatusBar.setController(new DinerEntryStatusBarController()
		{
			public void onDinerSelected(Diner d)
			{
				model.removeDiner(d);
				showToastMessage("("+Grouptuity.getBill().diners.size()+") Removed "+d.name);
				if(Grouptuity.getBill().diners.size()==0)
					dinerEntryStatusBar.hide();
				model.processNewSearch(model.dinerNameSearch);
				refreshActivity();
			}
			public void finish(){advanceActivity();}
		});
	}

	protected void createHorizontalLayout()
	{
		mainLinearLayout.addView(titleBar,Grouptuity.fillWrapLP());
		//TODO
	}
	protected void createVerticalLayout()
	{
		mainLinearLayout.addView(titleBar,Grouptuity.fillWrapLP());
		mainLinearLayout.addView(focusGrabber,Grouptuity.fillWrapLP());
		LinearLayout.LayoutParams debLP = new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);
		debLP.bottomMargin = -dinerEntryBar.panel.style.bottomContraction;
		mainLinearLayout.addView(dinerEntryBar,debLP);
		mainLinearLayout.addView(dinerEntrySearchResults,Grouptuity.fillWrapLP(1.0f));
		mainLinearLayout.addView(dinerEntryStatusBar,Grouptuity.fillWrapLP());
	}
	protected String createHelpString()
	{
		return	"Choose the people dining with you. Click a person's name to add them to the bill, or search for someone "+
				"by typing their name into the search bar. You can remove a diner by clicking their "+
				"corresponding icon from the bar at the bottom. When finished, click \"Add Items.\"";
	}

	@Override
	protected GrouptuityPreference<Boolean> getTutorialPopupFlag(){return Grouptuity.SHOW_TUTORIAL_DINERENTRY;}

	protected void processStateChange(int state)
	{
		switch(state)
		{
			case DinerEntryModel.START:							dinerEntryStatusBar.fullScrollFast();model.setNewState(DinerEntryModel.DINER_ENTRY_DEFAULT);focusGrabber.requestFocus();break;
			case DinerEntryModel.DINER_ENTRY_DEFAULT:			break;
			case DinerEntryModel.DINER_ENTRY_TABLE_PLACEMENT:	model.commitCurrentDiner();
																model.setAddedContactBasedOnCurrentSearch(true);
																model.processNewSearch(model.dinerNameSearch);
																if(!softKeyboardShowing)
																{
																	dinerEntryStatusBar.show();
																	dinerEntryStatusBar.fullScroll();
																}
																else
																	statusBarScrollScheduled = true;

																showToastMessage("("+Grouptuity.getBill().diners.size()+") Added "+model.currentDiner.name);
																model.revertToState(DinerEntryModel.DINER_ENTRY_DEFAULT);
																break;
		}
	}

	public void onResume()
	{
		super.onResume();
		if(searchAlgorithm!=Grouptuity.SEARCH_ALGORITHM.getValue())
		{
			searchAlgorithm = Grouptuity.SEARCH_ALGORITHM.getValue();
			model.processNewSearch(model.dinerNameSearch);
		}
		refreshActivity();
	}

	protected void onSoftKeyboardHide(){dinerEntryStatusBar.show();if(statusBarScrollScheduled){dinerEntryStatusBar.fullScrollFast();statusBarScrollScheduled = false;}}
	protected void onSoftKeyboardShow(){dinerEntryStatusBar.hide();if(model.getAddedContactBasedOnCurrentSearch())dinerEntryBar.blank();}
	protected void onContactsLoadStart(){model.clearSuggestedContacts();dinerEntrySearchResults.refresh();rootLayout.setBackgroundColor(Grouptuity.backgroundColor);}
	protected void onContactsLoadProgressUpdate(){rootLayout.setBackgroundColor(Grouptuity.backgroundColor);dinerEntrySearchResults.refresh();}
	protected void onContactsLoadComplete(){rootLayout.setBackgroundColor(Grouptuity.foregroundColor);model.processNewSearch(model.dinerNameSearch);refreshActivity();}

	protected boolean onOptionsMenuSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
			case R.id.reset:				showToastMessage("Removed All Diners");
											Grouptuity.resetBill();
											model.processNewSearch(model.dinerNameSearch);
											if(!softKeyboardShowing)
												dinerEntryStatusBar.show();
											model.revertToState(DinerEntryModel.DINER_ENTRY_DEFAULT);
											return true;
			case R.id.contacts_refresh:		Grouptuity.reloadContacts();return true;
		}
		return false;
	}

	@Override
	protected void onAdvanceActivity(int code){startActivity(new Intent(activity,ItemEntryActivity.class));}
}