package com.grouptuity.mainmenu;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.*;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import com.grouptuity.*;
import com.grouptuity.dinerentry.DinerEntryActivity;
import com.grouptuity.preferences.PreferencesActivity;
import com.grouptuity.quickbillcalculation.QuickBillCalculationActivity;
import com.grouptuity.style.composites.TitleBarStyle;
import com.grouptuity.view.Panel;
import com.grouptuity.view.Spacer;

public class MainMenuActivity extends ActivityTemplate<MainMenuModel>
{
	public static enum LaunchActivity
	{
		//N.B. the requestCodes are hard coded at the bottom of this class
		SINGLE_PAYER("Simple Calc",111,R.drawable.mainmenu_quickbill,QuickBillCalculationActivity.class),
		GROUP_SPLIT("Group Split",211,R.drawable.mainmenu_fullbill,DinerEntryActivity.class),
		SETTINGS("Settings",311,R.drawable.mainmenu_settings,PreferencesActivity.class);

		final public String activityTitle;
		final public int requestCode, imageInt;
		final public Class<?> launchActivityClass;

		private LaunchActivity(String at, int r, int i, Class<?> laClass)
		{
			activityTitle = at;
			requestCode = r;
			imageInt = i;
			launchActivityClass = laClass;
		}
	}

	private LinearLayout title;
	private LinearLayout scrollLinearLayout;
	private LaunchButton singlePayer, groupSplit, settings;
	private AlertDialog newBillAlert, userNameSelection;

	protected void createActivity(Bundle bundle)
	{
		model = new MainMenuModel(this,bundle);
		menuInt = R.menu.home_menu;

		AlertDialog.Builder newBillAlertBuilder = new AlertDialog.Builder(this);
		newBillAlertBuilder.setCancelable(true);
		newBillAlertBuilder.setTitle("Group Bill Split");
		newBillAlertBuilder.setMessage("Create new bill or continue with current bill?");
		newBillAlertBuilder.setIcon(R.drawable.mainmenu_savedbills);
		newBillAlertBuilder.setPositiveButton("New Bill", new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int id)
			{
				if(Grouptuity.INCLUDE_SELF.getValue() && !Grouptuity.USER_NAME_CHOSEN.getValue())
					userNameSelection.show();
				else
					startGroupBillSplitIntent(true);
			}
		});
		newBillAlertBuilder.setNegativeButton("Continue", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){startGroupBillSplitIntent(false);}});
		newBillAlert = newBillAlertBuilder.create();

		AlertDialog.Builder userNameSelectionBuilder = new AlertDialog.Builder(this);
		userNameSelectionBuilder.setCancelable(true);
		userNameSelectionBuilder.setTitle("Enter Name");
		userNameSelectionBuilder.setMessage("Please enter a name for yourself (you can change it later in the app settings).");
		userNameSelectionBuilder.setIcon(R.drawable.mainmenu_quickbill);
		final EditText input = new EditText(this);
		input.setText(Grouptuity.USER_NAME.getValue());
		input.setInputType(InputType.TYPE_TEXT_VARIATION_PERSON_NAME);
		userNameSelectionBuilder.setView(input);
		userNameSelectionBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int id)
			{
				Grouptuity.USER_NAME.setValue(input.getText().toString());
				Grouptuity.USER_NAME_CHOSEN.setValue(true);
				startGroupBillSplitIntent(true);
			}
		});
		userNameSelection = userNameSelectionBuilder.create();

		rootLayout.setBackgroundResource(R.drawable.mainbackground0);

		Typeface face=Typeface.createFromAsset(getAssets(),"fonts/segoeui.ttf");
		final Paint whiteShadowPaint = new Paint();
		whiteShadowPaint.setColor(Color.WHITE);
		whiteShadowPaint.setAntiAlias(true);
		whiteShadowPaint.setTextAlign(Paint.Align.CENTER);
		whiteShadowPaint.setTextSize(Grouptuity.toActualPixels(25));
		whiteShadowPaint.setTypeface(Typeface.DEFAULT_BOLD);
		whiteShadowPaint.setShadowLayer(3,-2,-2,0xAA000000);
		whiteShadowPaint.setTypeface(face);

		final Paint blackShadowPaint = new Paint();
		blackShadowPaint.setColor(Color.WHITE);
		blackShadowPaint.setAntiAlias(true);
		blackShadowPaint.setTextAlign(Paint.Align.CENTER);
		blackShadowPaint.setTextSize(Grouptuity.toActualPixels(25));
		blackShadowPaint.setTypeface(Typeface.DEFAULT_BOLD);
		blackShadowPaint.setShadowLayer(3,2,2,0xAAFFFFFF);
		blackShadowPaint.setTypeface(face);
		final Bitmap logo = BitmapFactory.decodeResource(getResources(), R.drawable.logo_on_blank);
		title = new Panel(this, new TitleBarStyle())
		{
			Rect bounds = new Rect();
			int padding = Grouptuity.toActualPixels(25);
			int logooffset = Grouptuity.toActualPixels(15);
			String titletext = "G R O U P T U I T Y";

			public void dispatchDraw(Canvas canvas)
			{
				super.dispatchDraw(canvas);
				canvas.drawText( titletext, logooffset+logo.getWidth()+(getWidth()-logooffset-logo.getWidth())/2, -bounds.top+padding, whiteShadowPaint);
				canvas.drawText( titletext, logooffset+logo.getWidth()+(getWidth()-logooffset-logo.getWidth())/2, -bounds.top+padding, blackShadowPaint);
				canvas.drawBitmap(logo, logooffset, (-bounds.top+2*padding-logo.getHeight())/2, new Paint());
			}
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
			{
				super.onMeasure(widthMeasureSpec, heightMeasureSpec);
				whiteShadowPaint.getTextBounds( titletext, 0,  titletext.length(), bounds);
				setMeasuredDimension(getMeasuredWidth(),bounds.bottom-bounds.top+2*padding);
			}
		};

		singlePayer = new LaunchButton(this,LaunchActivity.SINGLE_PAYER);
		singlePayer.controller = new LaunchButton.LaunchButtonController()
		{
			public void onClick(){activity.startActivityForResult(new Intent(activity,QuickBillCalculationActivity.class),LaunchActivity.SINGLE_PAYER.requestCode);}
		};
		groupSplit = new LaunchButton(this,LaunchActivity.GROUP_SPLIT);
		groupSplit.controller = new LaunchButton.LaunchButtonController()
		{
			public void onClick()
			{
				if(Grouptuity.getBill()==null || (Grouptuity.getBill().diners.isEmpty() && !Grouptuity.INCLUDE_SELF.getValue()) || (Grouptuity.INCLUDE_SELF.getValue() && Grouptuity.getBill().diners.size()==1 && Grouptuity.getBill().diners.get(0).isSelf && Grouptuity.getBill().items.size()==0 && Grouptuity.getBill().debts.size()==0 && Grouptuity.getBill().discounts.size()==0))
					if(Grouptuity.INCLUDE_SELF.getValue() && !Grouptuity.USER_NAME_CHOSEN.getValue())
						userNameSelection.show();
					else
						startGroupBillSplitIntent(true);
				else
					newBillAlert.show();
			}
		};
		settings = new LaunchButton(this,LaunchActivity.SETTINGS);
		settings.controller = new LaunchButton.LaunchButtonController()
		{
			public void onClick()
			{
				Intent intent = new Intent(activity,PreferencesActivity.class);
				activity.startActivityForResult(intent,LaunchActivity.SETTINGS.requestCode);
			}
		};
	}

	protected void createHorizontalLayout()
	{
		HorizontalScrollView scrollView = new HorizontalScrollView(this);
		scrollView.setFillViewport(true);
		scrollView.setLayoutParams(Grouptuity.fillWrapLP(1.0f));
		scrollLinearLayout = new LinearLayout(this);
		scrollView.addView(scrollLinearLayout);

		mainLinearLayout.addView(new Spacer(this),Grouptuity.fillWrapLP(1.0f));
		mainLinearLayout.addView(title);
		mainLinearLayout.addView(new Spacer(this),Grouptuity.fillWrapLP(1.0f));
		mainLinearLayout.addView(scrollView);
		mainLinearLayout.addView(new Spacer(this),Grouptuity.fillWrapLP(1.0f));

		scrollLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
		scrollLinearLayout.setOrientation(LinearLayout.HORIZONTAL);
		scrollLinearLayout.addView(new Spacer(this),Grouptuity.fillWrapLP(1.0f));
		scrollLinearLayout.addView(singlePayer);
		scrollLinearLayout.addView(new Spacer(this),Grouptuity.fillWrapLP(1.0f));
		scrollLinearLayout.addView(groupSplit);
		scrollLinearLayout.addView(new Spacer(this),Grouptuity.fillWrapLP(1.0f));
		scrollLinearLayout.addView(settings);
		scrollLinearLayout.addView(new Spacer(this),Grouptuity.fillWrapLP(1.0f));
	}
	protected void createVerticalLayout()
	{
		ScrollView scrollView = new ScrollView(this);
		scrollView.setFillViewport(true);
		scrollView.setLayoutParams(Grouptuity.fillWrapLP(1.0f));
		scrollLinearLayout = new LinearLayout(this);
		scrollView.addView(scrollLinearLayout);
		scrollLinearLayout.setOrientation(LinearLayout.VERTICAL);
		scrollLinearLayout.setGravity(Gravity.CENTER_HORIZONTAL);

		final Spacer spacer2 = new Spacer(this);
		mainLinearLayout.addView(title,Grouptuity.fillWrapLP());
		mainLinearLayout.addView(scrollView);

		scrollLinearLayout.addView(spacer2,Grouptuity.fillWrapLP(1.0f));
		scrollLinearLayout.addView(groupSplit,Grouptuity.wrapWrapLP());
		scrollLinearLayout.addView(new Spacer(this),Grouptuity.fillWrapLP(1.0f));
		scrollLinearLayout.addView(singlePayer,Grouptuity.wrapWrapLP());
		scrollLinearLayout.addView(new Spacer(this),Grouptuity.fillWrapLP(1.0f));
		scrollLinearLayout.addView(settings,Grouptuity.wrapWrapLP());
		scrollLinearLayout.addView(new Spacer(this),Grouptuity.fillWrapLP(1.0f));
	}
	protected String createHelpString()
	{
		return	"Click one of the three icons:"+
				"\n\n\u2022 Group Split - full calculation for generating personalized bills for groups of diners."+
				"\n\n\u2022 Quick Calc - simple calculator for tax and tip"+
				"\n\n\u2022 Settings - configure app options";
	}
	protected void processStateChange(int state){}

	private void startGroupBillSplitIntent(boolean fresh)
	{
		if(fresh)
			Grouptuity.resetBill();
		startActivityForResult(new Intent(this,DinerEntryActivity.class),LaunchActivity.GROUP_SPLIT.requestCode);
	}

	@Override
	protected boolean isMainMenu(){return true;}

	@Override
	protected GrouptuityPreference<Boolean> getTutorialPopupFlag(){return Grouptuity.SHOW_EULA;}
}