package com.grouptuity.billcalculation;

import com.grouptuity.*;
import com.grouptuity.view.SlidingBar;
import com.grouptuity.view.SlidingBarBillReport;

public class BillCalculationSlidingBar extends SlidingBar
{

	protected static enum Button
	{
		VIEW_BILL("View Bill"),
		FINISH("Set Payments \u25B6"),
		RETURN_TO_CALCULATION("Return to Calculation");

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

	public BillCalculationSlidingBar(ActivityTemplate<BillCalculationModel> context, SlidingBarBillReport report)
	{
		super(context,report,SlidingBar.Orientation.BOTTOM);
	}

	protected void refresh()
	{
		super.refresh();
		switch(model.getState())
		{
			case BillCalculationModel.DEFAULT:		setButtons(Button.VIEW_BILL.name,Button.FINISH.name);
													unlock();
													break;
			case BillCalculationModel.VIEWING_BILL:	setButtons(Button.RETURN_TO_CALCULATION.name);
													unlock();
													break;
			default:								disableAllButtons();
													lock();
		}
	}
}