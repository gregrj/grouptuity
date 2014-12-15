package com.grouptuity.billreviewindividual;

import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import com.grouptuity.*;
import com.grouptuity.Grouptuity.NumberFormat;
import com.grouptuity.model.*;
import com.grouptuity.style.composites.RoundForegroundPanelStyle;
import com.grouptuity.view.Panel;

public class IndividualBillPanel extends ViewTemplate<IndividualBillModel, ControllerTemplate>
{
	final private Panel panel;
	final private Diner diner;
	final private TextView dinerName,dinerInstructions;

	public IndividualBillPanel(ActivityTemplate<IndividualBillModel> context, Diner d)
	{
		super(context);
		diner = d;

		ScrollView scrollView = new ScrollView(context);
		scrollView.setFillViewport(true);
		panel = new Panel(context,new RoundForegroundPanelStyle());
		scrollView.addView(panel,Grouptuity.fillFillLP());
		addView(scrollView,Grouptuity.fillWrapLP(1.0f));

		dinerName = formatTextView(diner.name,Typeface.DEFAULT_BOLD,28.0f);
		dinerName.setHorizontallyScrolling(false);
		panel.addView(dinerName,Grouptuity.gravWrapWrapLP(Gravity.CENTER_HORIZONTAL));

		panel.addView(new ItemRow(" "," "),Grouptuity.fillWrapLP());

		dinerInstructions = formatTextView(diner.getPaymentInstructions(),Typeface.DEFAULT,18.0f);
		dinerInstructions.setHorizontallyScrolling(false);
		panel.addView(dinerInstructions,Grouptuity.gravWrapWrapLP(Gravity.CENTER_HORIZONTAL));

		if(diner.items.size()>0)
		{
			for(Item item: diner.items)
			{
				int numPayers = item.payers.size();
				ItemRow itemLL = new ItemRow((numPayers>1)?"Shared "+item.billableType.name.toLowerCase()+" ("+numPayers+")":"Individual "+item.billableType.name.toLowerCase(),Grouptuity.formatNumber(NumberFormat.CURRENCY,item.value*item.getWeightFractionForPayer(diner)));
				panel.addView(itemLL,Grouptuity.fillWrapLP());
			}
			panel.addView(new ItemRow("Item Total",Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,diner.itemSubtotal),true,false),Grouptuity.fillWrapLP());
			panel.addView(new ItemRow(" "," "),Grouptuity.fillWrapLP());
		}

		if(diner.debts.size()>0)
		{
			for(Debt debt: diner.debts)
			{
				double netDebt = Diner.getNetValueToDiner(diner, debt);

				ItemRow itemLL;
				if(debt.linkedDiscount!=null)
					itemLL = new ItemRow("Group Deal Payback",Grouptuity.formatNumber(NumberFormat.CURRENCY, netDebt));
				else if(netDebt<0)
					itemLL = new ItemRow("Debt (owed to you)",Grouptuity.formatNumber(NumberFormat.CURRENCY, netDebt));
				else
					itemLL = new ItemRow("Debt",Grouptuity.formatNumber(NumberFormat.CURRENCY, netDebt));
				panel.addView(itemLL,Grouptuity.fillWrapLP());
			}
			panel.addView(new ItemRow("Debt Total",Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,diner.debtSum),true,false),Grouptuity.fillWrapLP());
			panel.addView(new ItemRow(" "," "),Grouptuity.fillWrapLP());
		}

		double deltaRedistribution = diner.redistributedDiscountSum - diner.discountSum;
		if(diner.discounts.size()>0 || Math.abs(deltaRedistribution) > Grouptuity.PRECISION_ERROR)
		{
			for(Discount discount: diner.discounts)
			{
				String description;
				switch(discount.billableType)
				{
					case DEAL_PERCENT_ITEM:		description = "Item ("+Grouptuity.formatNumber(NumberFormat.TAX_PERCENT, discount.percent)+" off)";break;
					case DEAL_PERCENT_BILL:		description = "Bill ("+Grouptuity.formatNumber(NumberFormat.TAX_PERCENT, discount.percent)+" off)";break;
					case DEAL_FIXED:			description = "Fixed Discount";break;
					case DEAL_GROUP_VOUCHER:	description = "Group Deal";break;
					default:					description = "";break;
				}
				ItemRow itemLL = new ItemRow(description,Grouptuity.formatNumber(NumberFormat.CURRENCY, Diner.getNetValueToDiner(diner, discount)));
				panel.addView(itemLL,Grouptuity.fillWrapLP());
			}

			if(deltaRedistribution < Grouptuity.PRECISION_ERROR)
			{
				panel.addView(new ItemRow("Unused By Others: ",Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,deltaRedistribution),false,false),Grouptuity.fillWrapLP());
			}
			else if(deltaRedistribution > Grouptuity.PRECISION_ERROR)
			{
				panel.addView(new ItemRow("Unused By You: ",Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,deltaRedistribution),false,false),Grouptuity.fillWrapLP());
			}
			panel.addView(new ItemRow("Discount Total",Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,diner.redistributedDiscountSum),true,false),Grouptuity.fillWrapLP());
			panel.addView(new ItemRow(" "," "),Grouptuity.fillWrapLP());
		}

		String description = "Tax ("+Grouptuity.formatNumber(Grouptuity.NumberFormat.TAX_PERCENT,Grouptuity.getBill().getTaxPercent())+")";
		panel.addView(new ItemRow(description,Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,diner.taxValue)),Grouptuity.fillWrapLP());
		description = "Tip ("+Grouptuity.formatNumber(Grouptuity.NumberFormat.TIP_PERCENT,Grouptuity.getBill().getTipPercent())+")";
		panel.addView(new ItemRow(description,Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,diner.tipValue)),Grouptuity.fillWrapLP());

		double venmoTotal = 0;
		for(Payment venmoTransaction: Grouptuity.getBill().getAllVenmoTransactions())
		{
			if(venmoTransaction.payer==diner)
				venmoTotal -= venmoTransaction.amount;
			else if(venmoTransaction.payee==diner)
				venmoTotal += venmoTransaction.amount;
		}
		if(Math.abs(venmoTotal)>Grouptuity.PRECISION_ERROR)
			panel.addView(new ItemRow("Venmo Payments",Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,venmoTotal)),Grouptuity.fillWrapLP());
		panel.addView(new ItemRow("Total",Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,diner.total),true,true),Grouptuity.fillWrapLP());
	}
	private TextView formatTextView(CharSequence text, Typeface typeface, float fontSize)
	{
		TextView tv = new TextView(activity);
		tv.setText(text);
		tv.setTypeface(typeface);
		tv.setTextColor(Color.BLACK);
		tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP,fontSize);
		return tv;
	}

	private class ItemRow extends LinearLayout
	{
		final private TextView itemName;
		final private TextView itemAmt;

		private ItemRow(String description, final String amount){this(description, amount, false, false);}
		private ItemRow(String description, final String amount, boolean isBold, boolean isLarge)
		{
			super(activity);
			setOrientation(LinearLayout.HORIZONTAL);
			Typeface tf = isBold? Typeface.DEFAULT_BOLD: Typeface.DEFAULT;
			if(isLarge)
			{
				itemName = formatTextView(description,tf,24.0f);
				itemAmt = formatTextView(amount,tf,24.0f);
			}
			else
			{
				itemName = formatTextView(description,tf,18.0f);
				itemAmt = formatTextView(amount,tf,20.0f);
			}

			addView(itemName,Grouptuity.wrapWrapLP(1.0f));
			addView(itemAmt,Grouptuity.wrapWrapLP());
		}
	}

	protected void saveState(Bundle bundle){}
	protected void restoreState(Bundle bundle){}
	protected void applyStyle(){}
	protected void refresh(){}
}