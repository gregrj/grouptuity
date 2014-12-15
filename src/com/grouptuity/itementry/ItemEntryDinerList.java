package com.grouptuity.itementry;

import android.os.Bundle;
import android.view.MotionEvent;
import android.widget.ListView;
import com.grouptuity.*;
import com.grouptuity.model.Contact;
import com.grouptuity.model.Diner;
import com.grouptuity.view.*;
import com.grouptuity.view.ContactListing.ContactListingStyle;
import com.grouptuity.view.ContactListingAdapter.ContactListingAdapterController;

public class ItemEntryDinerList extends ViewTemplate<ItemEntryModel,ItemEntryDinerList.ItemEntryDinerListController>
{
	final private ContactListingAdapter contactListingAdapter;
	final private ListView listView;
	private boolean locked;

	protected static interface ItemEntryDinerListController extends ControllerTemplate{void onDinerSelected(Diner d);}

	public ItemEntryDinerList(ActivityTemplate<ItemEntryModel> context, final ItemEntryDinerListController controller)
	{
		super(context);

		contactListingAdapter = new ContactListingAdapter(context,ContactListingStyle.ITEM_ENTRY,Grouptuity.getBill().diners,new ContactListingAdapterController()
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

	protected void rewind(){listView.setSelection(0);}

	protected void lock(){locked = true;}
	protected void unlock(){locked = false;}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle(){}
	protected void refresh(){contactListingAdapter.notifyDataSetChanged();}
}