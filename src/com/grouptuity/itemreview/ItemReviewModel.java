package com.grouptuity.itemreview;

import android.os.Bundle;
import com.grouptuity.*;
import com.grouptuity.model.BillModelTemplate;

public class ItemReviewModel extends BillModelTemplate
{
	//ACTIVITY STATES
	final static int DEFAULT = 0;
	final static int EDIT_BILL_ITEM_VALUE = 11;
	final static int EDIT_BILL_DEBT_VALUE = 12;
	final static int EDIT_BILL_DEAL_VALUE = 13;
	final static int EDIT_BILL_DEAL_COST = 14;

	public ItemReviewModel(ActivityTemplate<?> activity, Bundle bundle){super(activity,bundle);}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
}