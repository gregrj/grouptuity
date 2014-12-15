package com.grouptuity.dinerentry;

import java.util.ArrayList;
import java.util.HashMap;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.grouptuity.ActivityTemplate;
import com.grouptuity.ControllerTemplate;
import com.grouptuity.Grouptuity;
import com.grouptuity.InputListenerTemplate;
import com.grouptuity.R;
import com.grouptuity.ViewTemplate;
import com.grouptuity.model.*;
import com.grouptuity.style.composites.EmbeddedStyle;
import com.grouptuity.style.composites.EmbeddedStyleDisabled;
import com.grouptuity.style.composites.EmbeddedStylePressed;
import com.grouptuity.view.ContactListing;
import com.grouptuity.view.Panel;
import com.grouptuity.view.ContactListing.ContactListingStyle;

public class DinerEntryStatusBar extends ViewTemplate<DinerEntryModel,DinerEntryStatusBar.DinerEntryStatusBarController>
{
	final private HashMap<Diner,ContactListing> contactListingMapping;
	final private HorizontalScrollView scrollView;
	final private LinearLayout innerLayout;
	final private Panel finishButton;
	final AlertDialog selfDeletionConfirmation;

	protected static interface DinerEntryStatusBarController extends ControllerTemplate{void onDinerSelected(Diner d);void finish();}

	public DinerEntryStatusBar(ActivityTemplate<DinerEntryModel> context)
	{
		super(context);
		setOrientation(LinearLayout.HORIZONTAL);
		setBackgroundColor(0xFF555555);

		contactListingMapping = new HashMap<Diner, ContactListing>();

		scrollView = new HorizontalScrollView(activity);
		scrollView.setFillViewport(true);
		addView(scrollView,Grouptuity.wrapWrapLP(1.0f));

		innerLayout = new LinearLayout(context);
		innerLayout.setOrientation(LinearLayout.HORIZONTAL);
		scrollView.addView(innerLayout);

		finishButton = new Panel(context,new EmbeddedStyle())
		{
			private TextView textView;
			protected void addContents()
			{
				textView = new TextView(activity);
				textView.setText("\u3000Add\u3000\n\u3000Items \u25B6"); //Hack for the hack below to keep the text centered
				textView.setGravity(Gravity.CENTER); 
				textView.append("\u3000");	//Hack for Android 4.0 to trigger remeasuring of textView when changing text size
				addView(textView,Grouptuity.gravWrapWrapLP(Gravity.CENTER));
			}
			protected void applyStyle(){textView.setTextColor(style.textColor);}
			protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
			{
				View v = innerLayout.getChildAt(0);
				if(v==null){setMeasuredDimension(0,0);return;}

				float sizingUnit = 10.0f, tolerance = 0.1f;
				boolean previousWasShrink = false;
				float matchMeasurement = v.getMeasuredHeight();
				do
				{
					float newTextSize;
					if(previousWasShrink)
					{
						if(getMeasuredHeight()>matchMeasurement)
						{
							newTextSize = textView.getTextSize()-sizingUnit;
							if(newTextSize<=0.0f){sizingUnit /= 2;continue;}
						}
						else
						{
							sizingUnit /= 2;
							newTextSize = textView.getTextSize()+sizingUnit;
							previousWasShrink = false;
						}
					}
					else
					{
						if(getMeasuredHeight()<matchMeasurement)
							newTextSize = textView.getTextSize()+sizingUnit;
						else
						{
							sizingUnit /= 2;
							newTextSize = textView.getTextSize()-sizingUnit;
							previousWasShrink = true;
						}
					}
					textView.setTextSize(TypedValue.COMPLEX_UNIT_PX,newTextSize);
					super.onMeasure(widthMeasureSpec, heightMeasureSpec);
				}while(sizingUnit>tolerance && getMeasuredHeight()!=matchMeasurement);
				textView.setTextSize(TypedValue.COMPLEX_UNIT_PX,textView.getTextSize());
				super.onMeasure(widthMeasureSpec, heightMeasureSpec);
				setMeasuredDimension(getMeasuredWidth(), v.getMeasuredHeight());
			};
		};
		finishButton.setGravity(Gravity.CENTER);
		finishButton.setStyle(ViewState.HIGHLIGHTED,new EmbeddedStylePressed());
		finishButton.setStyle(ViewState.DISABLED,new EmbeddedStyleDisabled());
		finishButton.setOnTouchListener(new InputListenerTemplate(false)
		{
			protected boolean handlePress(){finishButton.setViewState(ViewState.HIGHLIGHTED);return true;}
			protected boolean handleHold(long holdTime){return true;}
			protected boolean handleRelease(){finishButton.setViewState(ViewState.DEFAULT);return true;}
			protected boolean handleClick(){controller.finish();return true;}
			protected boolean handleLongClick(){return true;}
		});
		addView(finishButton);

		AlertDialog.Builder selfDeletionConfirmationBuilder = new AlertDialog.Builder(context);
		selfDeletionConfirmationBuilder.setCancelable(true);
		selfDeletionConfirmationBuilder.setTitle("Remove User");
		selfDeletionConfirmationBuilder.setMessage("Do you want to remove yourself from the bill split?");
		selfDeletionConfirmationBuilder.setIcon(R.drawable.mainmenu_quickbill);
		selfDeletionConfirmationBuilder.setPositiveButton("Yes",new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int id){for(Diner d: Grouptuity.getBill().diners)if(d.isSelf){controller.onDinerSelected(d);return;}}
		});
		selfDeletionConfirmationBuilder.setNegativeButton("No",new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){dialog.cancel();}});
		selfDeletionConfirmation = selfDeletionConfirmationBuilder.create();
	}

	protected void fullScroll(){scrollView.post(new Runnable(){public void run(){scrollView.smoothScrollTo(innerLayout.getRight(),0);}});}
	protected void fullScrollFast(){scrollView.post(new Runnable(){public void run(){scrollView.scrollTo(innerLayout.getRight(),0);}});}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle(){}
	protected void refresh()
	{
		final DinerEntryStatusBarController parentController = controller;
		if(Grouptuity.getBill().diners.size()==0)
		{
			innerLayout.removeAllViews();
			finishButton.requestLayout();
			return;
		}

		contactListingMapping.clear();

		ArrayList<Diner> newDiners = Grouptuity.getBill().diners;
		int newDinerCount = newDiners.size();

		int oldDinerCount = innerLayout.getChildCount();
		Diner[] oldDiners = new Diner[oldDinerCount];
		ContactListing[] oldContactListings = new ContactListing[oldDinerCount];
		for(int i=0; i<oldDinerCount; i++)
		{
			oldContactListings[i] = (ContactListing)innerLayout.getChildAt(i);
			oldDiners[i] = oldContactListings[i].diner;
		}

		//Remove views that correspond to diners no longer part of the bill
		int resizingCompensator = 0;
		for(int i=0; i<oldDinerCount; i++)
		{
			if(!newDiners.contains(oldDiners[i]))
			{
				innerLayout.removeViewAt(i-resizingCompensator);
				resizingCompensator++;
			}
		}

		//Take inventory of views remaining in the list
		ArrayList<Diner> currentDiners = new ArrayList<Diner>();
		int currentDinerCount = innerLayout.getChildCount();
		for(int i=0; i<currentDinerCount; i++)
			currentDiners.add(((ContactListing)innerLayout.getChildAt(i)).diner);

		//Reorder existing views and inset new views
		for(int i=0; i<newDinerCount; i++)
		{
			Diner d = newDiners.get(i);
			if(currentDiners.contains(d))
			{
				if(currentDiners.indexOf(d)!=newDiners.indexOf(d))
				{
					ContactListing clToMove = contactListingMapping.get(d);
					innerLayout.removeView(clToMove);
					innerLayout.addView(clToMove,i);
				}
			}
			else
			{
				ContactListing cl = new ContactListing(activity,d,ContactListingStyle.FACE_ICON)
				{
					protected void clickCallback(Diner d)
					{
						if(d.isSelf)
							selfDeletionConfirmation.show();
						else
							parentController.onDinerSelected(d);
						return;
					}
				};
				cl.defaultPhoto = BitmapFactory.decodeResource(activity.getResources(),R.drawable.contact_icon);
				cl.setViewState(cl.viewState);
				innerLayout.addView(cl,i);
				currentDiners.add(i, d);
			}
		}

		finishButton.requestLayout();
	}
}