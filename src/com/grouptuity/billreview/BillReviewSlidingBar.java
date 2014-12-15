package com.grouptuity.billreview;

import com.grouptuity.*;
import com.grouptuity.view.SlidingBar;
import com.grouptuity.model.BillModelTemplate;

public class BillReviewSlidingBar extends SlidingBar
{
	protected static enum Button
	{
		PAY("Process Online Payments"),
		RECEIPT_PREPROCESSING("Send Receipt to Diners"),
		CREATE_EMAIL("Create Email"),
		BACK("Cancel");

		protected String name;

		private Button(String str){name = str;}

		protected static Button getButton(String str)
		{
			for(Button b: values())
				if(b.name.equals(str))
					return b;
			return null;
		}
	}

	public BillReviewSlidingBar(ActivityTemplate<BillReviewModel> context, BillReviewEmailList billReviewEmailList)
	{
		super(context,billReviewEmailList,SlidingBar.Orientation.BOTTOM);
		lockMotion();
	}

	protected void refresh()
	{
		super.refresh();
		BillModelTemplate billModel = (BillModelTemplate)model;
		switch(model.getState())
		{
			case BillReviewModel.DEFAULT:		if(billModel.isPaymentComplete())
													setButtons(Button.RECEIPT_PREPROCESSING.name);
												else if(billModel.requiresElectronicPayment())
													setButtons(Button.RECEIPT_PREPROCESSING.name); //TODO setButtons(Button.PAY.name); once venmo processing is active
												else
													setButtons(Button.RECEIPT_PREPROCESSING.name);
												break;
			case BillReviewModel.SET_EMAILS: 	setButtons(Button.BACK.name,Button.CREATE_EMAIL.name);break;
		}
	}
}