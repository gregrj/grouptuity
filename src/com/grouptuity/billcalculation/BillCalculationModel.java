package com.grouptuity.billcalculation;

import android.os.Bundle;
import com.grouptuity.ActivityTemplate;
import com.grouptuity.model.BillModelTemplate;

public class BillCalculationModel extends BillModelTemplate
{
	//ACTIVITY STATES
	final static int DEFAULT = 0;
	final static int VIEWING_BILL = 1;
	final static int EDIT_TAX_PERCENT = 2;
	final static int EDIT_TAX_VALUE = 3;
	final static int EDIT_TIP_PERCENT = 4;
	final static int EDIT_TIP_VALUE = 5;
	final static int EDIT_RAW_SUBTOTAL = 6;
	final static int EDIT_AFTER_TAX_SUBTOTAL = 7;
	final static int EDIT_BILL_ITEM_VALUE = 11;
	final static int EDIT_BILL_DEBT_VALUE = 12;
	final static int EDIT_BILL_DEAL_VALUE = 13;
	final static int EDIT_BILL_DEAL_COST = 14;

	public BillCalculationModel(ActivityTemplate<?> activity, Bundle bundle){super(activity,bundle);}

	protected void saveState(Bundle bundle){}
}