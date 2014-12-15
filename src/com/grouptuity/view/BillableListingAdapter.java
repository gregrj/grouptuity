package com.grouptuity.view;

import com.grouptuity.*;
import com.grouptuity.Grouptuity.BillableType;
import com.grouptuity.model.*;

import java.util.ArrayList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

public class BillableListingAdapter extends BaseAdapter
{
	final private ActivityTemplate<?> context;
	final private BillableListingAdapterController controllerRef;
	final protected ArrayList<Billable> billables;
	final private Bitmap[] deleteImages;
	final protected BillableSeparator itemSeparator, debtSeparator, discountSeparator;
	protected Diner diner;

	public static interface BillableListingAdapterController extends ControllerTemplate
	{
		void onEditValueClick(Billable billable);
		void onEditCostClick(Billable billable);
		void onDeleteItemClick(Billable billable);
		void onPayerListClick(Billable billable);
		void onPayeeListClick(Billable billable);
	}

	public BillableListingAdapter(ActivityTemplate<?> c, BillableListingAdapterController control)
	{
		context = c;
		controllerRef = control;
		billables = new ArrayList<Billable>();
		deleteImages = new Bitmap[2];
		deleteImages[0] = BitmapFactory.decodeResource(context.getResources(),R.drawable.delete);
		deleteImages[1] = BitmapFactory.decodeResource(context.getResources(),R.drawable.deletepressed);
		itemSeparator = new BillableSeparator(BillableType.ENTREE);
		debtSeparator = new BillableSeparator(BillableType.DEBT);
		discountSeparator = new BillableSeparator(BillableType.DEAL_GROUP_VOUCHER);
	}

	public int getCount(){return billables.size();}
	public Object getItem(int position){return billables.get(position);}
	public long getItemId(int position){return position;}

	public View getView(int position, View convertView, ViewGroup parent)
	{
		BillableListing billableListing;
		if(convertView == null)
		{
			billableListing = new BillableListing(context, diner, deleteImages)
			{
				protected void onEditValueClick(Billable billable){controllerRef.onEditValueClick(billable);}
				protected void onEditCostClick(Billable billable){controllerRef.onEditCostClick(billable);}
				protected void onDeleteItemClick(Billable billable){controllerRef.onDeleteItemClick(billable);}
				protected void onPayerListClick(Billable billable){controllerRef.onPayerListClick(billable);}
				protected void onPayeeListClick(Billable billable){controllerRef.onPayeeListClick(billable);}
			};
		}
		else
			billableListing = (BillableListing) convertView;

		billableListing.billable = billables.get(position);

		billableListing.setViewState(billableListing.viewState);
		billableListing.refresh();
		return billableListing;
	}

	public void refresh()
	{
		billables.clear();
		Bill bill = Grouptuity.getBill();
		if(!bill.items.isEmpty())
		{
			billables.add(itemSeparator);
			billables.addAll(bill.items);
		}
		if(!bill.debts.isEmpty())
		{
			billables.add(debtSeparator);
			billables.addAll(bill.debts);
		}
		if(!bill.discounts.isEmpty())
		{
			billables.add(discountSeparator);
			billables.addAll(bill.discounts);
		}
		notifyDataSetChanged();
	}

	static class BillableSeparator extends Billable
	{
		BillableType type;
		private BillableSeparator(BillableType t){super(0.0, null);type = t;}
	}
}