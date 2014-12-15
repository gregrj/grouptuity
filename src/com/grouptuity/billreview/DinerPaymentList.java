package com.grouptuity.billreview;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.widget.ListView;
import com.grouptuity.*;
import com.grouptuity.Grouptuity.PaymentType;
import com.grouptuity.model.*;
import com.grouptuity.view.*;
import com.grouptuity.view.ContactListingAdapter.ContactListingAdapterController;

public class DinerPaymentList extends ViewTemplate<BillReviewModel,DinerPaymentList.DinerPaymentListController>
{
	final private ContactListingAdapter contactListingAdapter;
	final private ListView listView;

	protected static interface DinerPaymentListController extends ControllerTemplate
	{
		void onDinerSelected(Diner d);
		void onDinerContactPaymentInfoEdit(Diner d);
		void onPaymentMethodSwitch(Diner d);
	}

	public DinerPaymentList(ActivityTemplate<BillReviewModel> context, DinerPaymentListController dplc)
	{
		super(context);
		controller = dplc;

		Bitmap[] paymentImages = new Bitmap[PaymentType.values().length];
		for(int i=0; i<PaymentType.values().length; i++)
			paymentImages[i] = BitmapFactory.decodeResource(context.getResources(),PaymentType.values()[i].imageInt);
		contactListingAdapter = new ContactListingAdapter(context,Grouptuity.getBill().diners,paymentImages,new ContactListingAdapterController()
		{
			public void onDinerClick(Diner d){controller.onDinerSelected(d);}
			public void onDinerLongClick(Diner d){controller.onDinerContactPaymentInfoEdit(d);}
			public void onContactClick(Contact c){}
			public void onPaymentToggle(Diner d){controller.onPaymentMethodSwitch(d);}
		});
		listView = new ListView(activity);
		listView.setAdapter(contactListingAdapter);
		listView.setFastScrollEnabled(true);
		listView.setCacheColorHint(Grouptuity.foregroundColor);
		addView(listView,Grouptuity.fillWrapLP(1.0f));
	}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle(){}
	protected void refresh(){}
}