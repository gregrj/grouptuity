package com.grouptuity;

import java.util.ArrayList;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.PixelFormat;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Toast;

import com.grouptuity.preferences.PreferencesActivity;
import com.grouptuity.view.TitleBar;

public abstract class ActivityTemplate<M extends ModelTemplate> extends Activity
{
	protected ActivityTemplate<M> activity;
	protected M model;
	private int ID;
	protected Integer menuInt;

	//DISPLAY AND LAYOUT VARIABLES
	private ArrayList<ViewTemplate<?,?>> controllableViews, pendingControllableViews, pendingRemovals;
	public DisplayMetrics displayMetrics;
	public RelativeLayout rootLayout;
	protected LinearLayout mainLinearLayout;
	public TitleBar titleBar;
	public Toast toastMessage;
	private boolean refreshingViews, needToRefreshViews;
	public boolean softKeyboardShowing;
	private int defaultToastXOffset, defaultToastYOffset, defaultToastGravity;

	final int PREFERENCES_REQUEST_CODE = -213412;	//TODO need to put this in values.xml to prevent accidental duplication

	@SuppressLint("ShowToast")
	final public void onCreate(Bundle bundle)
	{		
		if(Grouptuity.terminateIfNotMainMenu && !isMainMenu())
			finish();
		else
			Grouptuity.terminateIfNotMainMenu = false;

		if(!Grouptuity.HORIZONTAL_ENABLED.getValue())
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		try
		{
			super.onCreate(bundle);
			requestWindowFeature(Window.FEATURE_NO_TITLE);

			activity = this;
			ID = 0;
			controllableViews = new ArrayList<ViewTemplate<?,?>>();
			pendingControllableViews = new ArrayList<ViewTemplate<?,?>>();
			pendingRemovals = new ArrayList<ViewTemplate<?,?>>();
			displayMetrics = getResources().getDisplayMetrics();
			toastMessage = Toast.makeText(activity,"",Toast.LENGTH_LONG);
			defaultToastGravity = toastMessage.getGravity();
			defaultToastXOffset = toastMessage.getXOffset();
			defaultToastYOffset = toastMessage.getYOffset();

			getWindow().setFormat(PixelFormat.RGBA_8888);
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_DITHER);

			mainLinearLayout = new LinearLayout(this);
			mainLinearLayout.setOrientation(LinearLayout.VERTICAL);

			rootLayout = new RelativeLayout(this)
			{
				private int maxH;

				public void onMeasure(int w, int h)
				{
					/* This is a hack to determine whether the keyboard is open/closed.
					 * It assumes that over the lifespan of this activity the maximum height
					 * will not increase (i.e. status bar presence does not change) and that
					 * any increases/decreases in height from the previous value are
					 * attributable to a software keyboard disappearing/appearing.
					 */
					int newH = MeasureSpec.getSize(h);
					if(newH>=maxH)
					{
						maxH = newH;
						if(softKeyboardShowing)
						{
							softKeyboardShowing = false;
							onSoftKeyboardHide();
						}
					}
					else
					{
						if(!softKeyboardShowing)
						{
							softKeyboardShowing = true;
							onSoftKeyboardShow();
						}
					}
					super.onMeasure(w,h);
				}
			};
			rootLayout.setBackgroundColor(Grouptuity.backgroundColor);
			rootLayout.addView(mainLinearLayout,Grouptuity.fillFillLP());
			setContentView(rootLayout);

			createActivity(bundle);
			if(isHorizontal())
				createHorizontalLayout();
			else
				createVerticalLayout();

			for(ViewTemplate<?,?> vt: controllableViews)
				vt.setViewState(vt.viewState);
			refreshViews();

			GrouptuityPreference<Boolean> tutorialPreference = getTutorialPopupFlag();
			if(tutorialPreference!=null && tutorialPreference.getValue())
				showHelpDialog(true,tutorialPreference);
		}catch(Exception exception){Grouptuity.log(exception);}
	}

	public void onPause()
	{
		super.onPause();
		Grouptuity.unregisterActivityListener(this);
		onActivityPause();
		model.saveToDatabase();
		toastMessage.cancel();
	}
	public void onResume()
	{
		super.onResume();
		Grouptuity.registerActivityListener(this);
		Grouptuity.getBill().calculateTotal();
		refreshActivity();
	}
	final protected void onSaveInstanceState(Bundle bundle){model.superSaveState(bundle);super.onSaveInstanceState(bundle);}
	final protected void onRestoreInstanceState(Bundle savedInstanceState)
	{
		model.superRestoreState(savedInstanceState);
		try{super.onRestoreInstanceState(savedInstanceState);}
		catch(Exception e){Grouptuity.log(e);}
	}

	final public boolean onCreateOptionsMenu(Menu menu)
	{
		if(menuInt!=null)
		{
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(menuInt,menu);
			return true;
		}
		return false;
	}
	final public boolean onOptionsItemSelected(MenuItem item)
	{
		if(onOptionsMenuSelected(item))
			return true;
		switch (item.getItemId())
		{
			case R.id.help:		showHelpDialog(false, null);
//								AlertDialog.Builder helpBuilder = new AlertDialog.Builder(this);
//								helpBuilder.setCancelable(true);
//								helpBuilder.setMessage(createHelpString());
//								helpBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){dialog.cancel();}});
//								helpBuilder.setNegativeButton("Support Email", new DialogInterface.OnClickListener()
//								{
//									public void onClick(DialogInterface dialog, int id){startActivity(Grouptuity.generateSupportEmailIntent(activity));}
//								});
//								AlertDialog help = helpBuilder.create();
//								help.setTitle("Help");
//								help.setIcon(R.drawable.logo_on_red);
//								help.show();
								return true;
			case R.id.settings:	Intent intent = new Intent(activity,PreferencesActivity.class);
								activity.startActivityForResult(intent,PREFERENCES_REQUEST_CODE);
								return true;
			case R.id.about:	Grouptuity.showAboutWindow(this);
								return true;
		}
		return false;
	}

	final public void showToastMessage(String message)
	{
		toastMessage.cancel();
		if(softKeyboardShowing)
			toastMessage.setGravity(Gravity.CENTER, 0, 0);
		else
			toastMessage.setGravity(defaultToastGravity,defaultToastXOffset,defaultToastYOffset);
		toastMessage.setText(message);
		toastMessage.show();
		
	}
	final public void showHelpDialog(final boolean tutorial, final GrouptuityPreference<Boolean> showPreference)
	{
		if(isMainMenu() && tutorial) //Show EULA and ask for acceptance 
		{
			EULA.showEULADialog(this);
		}
		else
		{
			AlertDialog.Builder helpBuilder = new AlertDialog.Builder(this);
			helpBuilder.setIcon(R.drawable.logo_on_red);
			helpBuilder.setCancelable(true);

			helpBuilder.setMessage(createHelpString());
			helpBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int id)
				{
					if(tutorial && showPreference!=null)
						showPreference.setValue(false);
				}
			});
			if(tutorial)
				helpBuilder.setTitle("Instructions");
			else
			{
				helpBuilder.setTitle("Help");
				helpBuilder.setNegativeButton("Support Email", new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int id){startActivity(Grouptuity.generateSupportEmailIntent(activity));}
				});
			}
			helpBuilder.show();
		}
	}

	final public void assignID(View view){view.setId(++ID);}
	final void addControllableView(ViewTemplate<?,?> vt)
	{
		if(controllableViews.contains(vt))
			return;
		if(!refreshingViews)
			controllableViews.add(vt);
		else if(!pendingControllableViews.contains(vt))
			pendingControllableViews.add(vt);
	}
	final void removeControllableView(ViewTemplate<?,?> vt)
	{
		if(refreshingViews)
		{
			if(controllableViews.contains(vt))
				pendingRemovals.add(vt);
			else
				pendingControllableViews.remove(vt);
		}
		else
			controllableViews.remove(vt);
	}
	final public boolean hasControllableView(ViewTemplate<?,?> vt){return controllableViews.contains(vt) || pendingControllableViews.contains(vt);}
	final public void advanceActivity(int code){onAdvanceActivity(code);}
	final public void advanceActivity(){onAdvanceActivity(-238976);} //Arbitrary number that hopefully won't collide with actual number codes so that accidental calls to this method have no action
	final public void terminateActivity(){onTerminateActivity();finish();}
	final public void refreshActivity()
	{
		needToRefreshViews = true;
		if(Grouptuity.getBill()!=null)
			Grouptuity.getBill().calculateTotal();
		if(model.stateChanged)
		{
			model.stateChanged = false;
			processStateChange(model.getState());
			if(needToRefreshViews)
				refreshViews();
		}
		else
			refreshViews();
	}
	final private void refreshViews()
	{
		needToRefreshViews = false;
		refreshingViews = true;
		for(ViewTemplate<?,?> vt: controllableViews)
			vt.refresh();
		while(pendingControllableViews.size()>0)
		{
			ViewTemplate<?,?> mark = pendingControllableViews.get(0);
			controllableViews.addAll(pendingControllableViews);
			pendingControllableViews.clear();
			for(int i=controllableViews.lastIndexOf(mark); i<controllableViews.size(); i++)
				controllableViews.get(i).refresh();
		}
		refreshingViews = false;
		for(ViewTemplate<?,?> vt: pendingRemovals)
			controllableViews.remove(vt);
		pendingRemovals.clear();
	}

	final public boolean isHorizontal(){return displayMetrics.widthPixels>displayMetrics.heightPixels;}
	protected boolean isMainMenu(){return false;}
	protected GrouptuityPreference<Boolean> getTutorialPopupFlag(){return null;}
	final protected void hideKeyboard(){((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(rootLayout.getWindowToken(),0);}
	final protected void showKeyboard(){((InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE)).toggleSoftInput(0,0);}

	protected abstract void createActivity(Bundle bundle);
	protected abstract void createHorizontalLayout();
	protected abstract void createVerticalLayout();
	protected abstract String createHelpString();
	protected abstract void processStateChange(int state);

	public void onGrouptuityCreditBalanceChange(){}
	protected void onSoftKeyboardHide(){}
	protected void onSoftKeyboardShow(){}
	protected void onContactsLoadStart(){}
	protected void onContactsLoadProgressUpdate(){}
	protected void onContactsLoadComplete(){}
	protected void onAdvanceActivity(int code){}
	protected void onTerminateActivity(){}
	protected void onActivityPause(){}
	protected boolean onOptionsMenuSelected(MenuItem item){return false;}
}