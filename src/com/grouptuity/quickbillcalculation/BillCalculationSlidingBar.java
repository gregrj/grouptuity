package com.grouptuity.quickbillcalculation;

import android.widget.LinearLayout;
import com.grouptuity.*;
import com.grouptuity.view.SlidingBar;

public class BillCalculationSlidingBar extends SlidingBar
{
	protected static enum Button
	{
		BACK("Go Back"),
		RESET("Reset");

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

	public BillCalculationSlidingBar(ActivityTemplate<BillCalculationModel> context)
	{
		super(context,new LinearLayout(context),SlidingBar.Orientation.BOTTOM);
		lockMotion();
	}

	protected void refresh()
	{
		super.refresh();
		switch(model.getState())
		{
			case BillCalculationModel.DEFAULT:		setButtons(Button.BACK.name,Button.RESET.name);
													unlockButtons();
													break;
			default:								disableAllButtons();
													lock();
		}
	}
}