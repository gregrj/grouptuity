package com.grouptuity.view;

import com.grouptuity.*;
import com.grouptuity.model.*;

public class DinerBillableListingAdapter extends BillableListingAdapter
{
	public DinerBillableListingAdapter(ActivityTemplate<?> c, Diner d, BillableListingAdapterController control){super(c,control);diner = d;}

	public void refresh()
	{
		billables.clear();
		if(!diner.items.isEmpty())
		{
			billables.add(itemSeparator);
			billables.addAll(diner.items);
		}
		if(!diner.debts.isEmpty())
		{
			billables.add(debtSeparator);
			billables.addAll(diner.debts);
		}
		if(!diner.discounts.isEmpty())
		{
			billables.add(discountSeparator);
			billables.addAll(diner.discounts);
		}
		notifyDataSetChanged();
	}
}