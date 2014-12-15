package com.grouptuity.itementry;

import com.grouptuity.*;
import com.grouptuity.Grouptuity.*;
import com.grouptuity.view.SlidingBar;
import com.grouptuity.view.SlidingBarBillReport;

public class ItemEntrySlidingBar extends SlidingBar
{
	protected static enum Button
	{
		REVIEW_ITEMS("View Bill"),
		DONE("Done"),
		CANCEL("Cancel"),
		NEXT("Next"),
		FINISH("Tip & Tax \u25B6"),
		RETURN_TO_ITEM_ENTRY("Return to Item Entry");

		protected String name;
		private Button(String str){name = str;}
		protected static Button getButton(String str){for(Button b: values())if(b.name.equals(str))return b;return null;}
	}

	public ItemEntrySlidingBar(ActivityTemplate<ItemEntryModel> context, SlidingBarBillReport report){super(context,report,SlidingBar.Orientation.BOTTOM);}

	protected void refresh()
	{
		super.refresh();
		unlockMotion();
		ItemEntryModel ieModel = (ItemEntryModel) model;
		switch(model.getState())
		{
			case ItemEntryModel.REVIEWING_BILLABLES:				setButtons(Button.RETURN_TO_ITEM_ENTRY.name);break;
			case ItemEntryModel.BROWSING_DINERS:					setButtons(Button.RETURN_TO_ITEM_ENTRY.name);
																	lockMotion();
																	break;
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_ITEM:		setButtons(Button.CANCEL.name,Button.DONE.name);if(!ieModel.hasDinerSelections())disableButtons(Button.DONE.name);break;
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYER:setButtons(Button.CANCEL.name,Button.NEXT.name);if(!ieModel.hasDinerSelections())disableButtons(Button.NEXT.name);break;
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYEE:setButtons(Button.CANCEL.name,Button.DONE.name);if(!ieModel.hasDinerSelections())disableButtons(Button.DONE.name);break;
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYER:setButtons(Button.CANCEL.name,Button.DONE.name);if(!ieModel.hasDinerSelections())disableButtons(Button.NEXT.name);break;
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYEE:if(ieModel.currentBillableType==BillableType.DEAL_GROUP_VOUCHER)
																	{
																		setButtons(Button.CANCEL.name,Button.NEXT.name);
																		if(!ieModel.hasDinerSelections())
																			disableButtons(Button.NEXT.name);
																	}
																	else
																	{
																		setButtons(Button.CANCEL.name,Button.DONE.name);
																		if(!ieModel.hasDinerSelections())
																			disableButtons(Button.DONE.name);
																	}
																	break;
			default:												setButtons(Button.REVIEW_ITEMS.name,Button.FINISH.name);
																	if(!ieModel.hasBillables())
																	{
																		disableAllButtons();
																		lockMotion();
																	}
		}
	}

	
}