package com.grouptuity.itementry;

import android.os.Bundle;
import com.grouptuity.*;
import com.grouptuity.model.*;

public class ItemEntryModel extends BillModelTemplate
{
	//ACTIVITY STATES
	final static int ITEM_ENTRY_DEFAULT = 0;
	final static int ITEM_ENTRY_ENTER_COST_ITEM = 1;
	final static int ITEM_ENTRY_ENTER_COST_DEBT = 2;
	final static int ITEM_ENTRY_ENTER_VALUE_DEAL = 3;
	final static int ITEM_ENTRY_ENTER_COST_DEAL = 4;
	final static int ITEM_ENTRY_SELECT_DINERS_ITEM = 5;
	final static int ITEM_ENTRY_SELECT_DINERS_DEBT_PAYER = 6;
	final static int ITEM_ENTRY_SELECT_DINERS_DEBT_PAYEE = 7;
	final static int ITEM_ENTRY_SELECT_DINERS_DEAL_PAYER = 8;
	final static int ITEM_ENTRY_SELECT_DINERS_DEAL_PAYEE = 9;
	final static int REVIEWING_BILLABLES = 10;
	final static int EDIT_BILL_ITEM_VALUE = 11;
	final static int EDIT_BILL_DEBT_VALUE = 12;
	final static int EDIT_BILL_DEAL_VALUE = 13;
	final static int EDIT_BILL_DEAL_COST = 14;
	final static int BROWSING_DINERS = 15;

	public ItemEntryModel(ActivityTemplate<?> activity, Bundle bundle){super(activity,bundle);}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
}