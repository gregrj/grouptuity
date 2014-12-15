package com.grouptuity.billreview;

import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.ListView;
import com.grouptuity.ActivityTemplate;
import com.grouptuity.ControllerTemplate;
import com.grouptuity.Grouptuity;
import com.grouptuity.ViewTemplate;
import com.grouptuity.model.Contact;
import com.grouptuity.model.Diner;
import com.grouptuity.view.ContactListingAdapter;
import com.grouptuity.view.ContactListing.ContactListingStyle;
import com.grouptuity.view.ContactListingAdapter.ContactListingAdapterController;
import com.grouptuity.view.InstructionsBar;

public class BillReviewEmailList extends ViewTemplate<BillReviewModel,BillReviewEmailList.BillReviewEmailListController>
{
	final private ContactListingAdapter contactListingAdapter;
	final private ListView listView;
	private InstructionsBar instructions;
	private boolean locked;

	protected static interface BillReviewEmailListController extends ControllerTemplate{void onDinerSelected(Diner d);}

	public BillReviewEmailList(ActivityTemplate<BillReviewModel> context, final BillReviewEmailListController controller)
	{
		super(context);
		setBackgroundColor(Grouptuity.backgroundColor);

		instructions = new InstructionsBar(activity);
		instructions.setInstructionsText("To whom should the receipt be sent?");
		LinearLayout.LayoutParams ibLP = new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);
		ibLP.bottomMargin = -instructions.panel.style.bottomContraction;
		addView(instructions,ibLP);

		contactListingAdapter = new ContactListingAdapter(context,ContactListingStyle.EMAIL_LISTING,Grouptuity.getBill().diners,new ContactListingAdapterController()
		{
			public void onDinerClick(Diner d){controller.onDinerSelected(d);}
			public void onContactClick(Contact c){}
			public void onPaymentToggle(Diner d){}
			public void onDinerLongClick(Diner d){}
		});
		listView = new ListView(activity);
		listView.setAdapter(contactListingAdapter);
		listView.setFastScrollEnabled(true);
		listView.setCacheColorHint(Grouptuity.foregroundColor);
		addView(listView,Grouptuity.fillWrapLP(1.0f));
	}

	public boolean onInterceptTouchEvent(MotionEvent event){return locked;}

	protected void lock(){locked = true;}
	protected void unlock(){locked = false;}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle(){}
	protected void refresh(){}
}