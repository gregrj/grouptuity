package com.grouptuity.model;

import android.os.Bundle;
import com.grouptuity.*;
import com.grouptuity.Grouptuity.*;

public abstract class BillModelTemplate extends ModelTemplate
{
	public Diner currentDiner;
	public Item currentItem;
	public Debt currentDebt;
	public Discount currentDiscount;
	public BillableType currentBillableType;

	public BillModelTemplate(ActivityTemplate<?> activity, Bundle bundle)
	{
		super(activity, bundle);

		if(bundle==null)
			bundle = activity.getIntent().getExtras();
		if(bundle!=null)
		{
			try
			{
				currentDiner = bundle.getParcelable("com.grouptuity.currentDiner");
				if(currentDiner==null && bundle.getBoolean("com.grouptuity.hasCurrentDinerIndex"))
					currentDiner = Grouptuity.getBill().diners.get(bundle.getInt("com.grouptuity.currentDinerIndex"));
				currentItem = bundle.getParcelable("com.grouptuity.currentItem");
				if(currentItem==null)
					if(bundle.getBoolean("com.grouptuity.hasItemIndex"))
						currentItem = Grouptuity.getBill().items.get(bundle.getInt("com.grouptuity.currentItemIndex"));
				currentDebt = bundle.getParcelable("com.grouptuity.currentDebt");
				if(currentDebt==null)
					if(bundle.getBoolean("com.grouptuity.hasDebtIndex"))
						currentDebt = Grouptuity.getBill().debts.get(bundle.getInt("com.grouptuity.currentDebtIndex"));
				currentDiscount = bundle.getParcelable("com.grouptuity.currentDiscount");
				if(currentDiscount==null)
					if(bundle.getBoolean("com.grouptuity.hasDiscountIndex"))
						currentDiscount = Grouptuity.getBill().discounts.get(bundle.getInt("com.grouptuity.currentDiscountIndex"));
				if(currentDiscount!=null && currentDiscount.billableType==BillableType.DEAL_PERCENT_ITEM)
				{
					if(bundle.getBoolean("hasCurrentDiscountDiscountedItemIndex"))
						currentDiscount.discountedItem = Grouptuity.getBill().items.get(bundle.getInt("com.grouptuity.currentDiscountDiscountedItemIndex"));
					else if(!bundle.getBoolean("com.grouptuity.noCurrentDiscountDiscountedItem"))
						currentDiscount.discountedItem = currentItem;
				}
				int currentBillableTypeIndex = bundle.getInt("com.grouptuity.currentBillableType");
				if(currentBillableTypeIndex>=0 && currentBillableTypeIndex<BillableType.values().length)
					currentBillableType = BillableType.values()[currentBillableTypeIndex];
			}
			catch(Exception e)
			{
				Grouptuity.log(e);
				currentDiner = null;
				currentItem = null;
			}
		}
	}

	@Override
	protected void saveToDatabase(){Grouptuity.saveBill();}
	protected void superSaveState(Bundle bundle)
	{
		super.superSaveState(bundle);
		if(currentDiner!=null)
			if(Grouptuity.getBill().diners.contains(currentDiner))
			{
				bundle.putBoolean("com.grouptuity.hasCurrentDinerIndex",true);
				bundle.putInt("com.grouptuity.currentDinerIndex",Grouptuity.getBill().diners.indexOf(currentDiner));
			}
			else
				bundle.putParcelable("com.grouptuity.currentDiner",currentDiner);
		if(currentItem!=null)
		{
			if(Grouptuity.getBill().items.contains(currentItem))
			{
				bundle.putBoolean("com.grouptuity.hasItemIndex",true);
				bundle.putInt("com.grouptuity.currentItemIndex",Grouptuity.getBill().items.indexOf(currentItem));
			}
			else
				bundle.putParcelable("com.grouptuity.currentItem",currentItem);
		}
		if(currentDebt!=null)
		{
			if(Grouptuity.getBill().debts.contains(currentDebt))
			{
				bundle.putBoolean("com.grouptuity.hasDebtIndex",true);
				bundle.putInt("com.grouptuity.currentDebtIndex",Grouptuity.getBill().debts.indexOf(currentDebt));
			}
			else
				bundle.putParcelable("com.grouptuity.currentDebt",currentDebt);
		}
		if(currentDiscount!=null)
		{
			if(Grouptuity.getBill().discounts.contains(currentDiscount))
			{
				bundle.putBoolean("com.grouptuity.hasDiscountIndex",true);
				bundle.putInt("com.grouptuity.currentDiscountIndex",Grouptuity.getBill().discounts.indexOf(currentDiscount));
			}
			else
				bundle.putParcelable("com.grouptuity.currentDiscount",currentDiscount);
			if(currentDiscount.discountedItem!=null)
			{
				if(Grouptuity.getBill().items.contains(currentDiscount.discountedItem))
				{
					bundle.putBoolean("com.grouptuity.hasCurrentDiscountDiscountedItemIndex",true);
					bundle.putInt("com.grouptuity.currentDiscountDiscountedItemIndex",Grouptuity.getBill().items.indexOf(currentDiscount.discountedItem));
				}
				//if not included in existing items, then it must be the current item
			}
			else
				bundle.putBoolean("com.grouptuity.noCurrentDiscountDiscountedItem",true);
		}
		bundle.putInt("com.grouptuity.currentBillableType", currentBillableType==null? -1: currentBillableType.ordinal());
	}

	public Diner createDinerFromName(String name){return new Diner(new Contact(true, null, name, null, null,null));}
	public void removeDiner(Diner diner){Grouptuity.getBill().removeDiner(diner);}
	public void commitCurrentDiner(){Grouptuity.getBill().addDiner(currentDiner);}
	public void setPaymentType(PaymentType type){currentDiner.defaultPaymentType = type; Grouptuity.getBill().invalidatePayments();}
	public boolean hasDinerSelections(){for(Diner d: Grouptuity.getBill().diners)if(d.selected)return true;return false;}
	public void clearDinerSelections(){for(Diner d: Grouptuity.getBill().diners)d.selected = false;}
	public void selectDiners(Diner... selectedDiners){for(Diner d: selectedDiners)d.selected = true;}
	public void deselectDiners(Diner... deselectedDiners){for(Diner d: deselectedDiners)d.selected = false;}
	public void toggleDinerSelections(Diner... toggledDiners){for(Diner d: toggledDiners)d.selected = !d.selected;}
	public int getNumDinerSelections()
	{
		int count = 0;
		for(Diner d: Grouptuity.getBill().diners)
			if(d.selected)
				count++;
		return count;
	}

	public boolean hasItems(){return (Grouptuity.getBill().items.size()>0);}
	public boolean hasDebts(){return (Grouptuity.getBill().debts.size()>0);}
	public boolean hasDiscounts(){return (Grouptuity.getBill().discounts.size()>0);}
	public boolean hasBillables(){return hasItems() || hasDebts() || hasDiscounts();}
	public void editItem(Item item, double value, BillableType type){Grouptuity.getBill().editItem(item,value,type);}
	public void editDebt(Debt debt, double value){Grouptuity.getBill().editDebt(debt,value);}
	public void editDiscount(Discount discount, double value, double cost, double percent, BillableType type, Discount.DiscountMethod tax, Discount.DiscountMethod tip){Grouptuity.getBill().editDiscount(discount,value,cost,percent,type,tax,tip);}
	public void addPayersForCurrentItem(){for(Diner d: Grouptuity.getBill().diners)if(d.selected)currentItem.addPayer(d);}
	public void addPayeesForCurrentItem(){for(Diner d: Grouptuity.getBill().diners)if(d.selected)currentItem.addPayee(d);}
	public void addPayersForCurrentDebt(){for(Diner d: Grouptuity.getBill().diners)if(d.selected)currentDebt.addPayer(d);}
	public void addPayeesForCurrentDebt(){for(Diner d: Grouptuity.getBill().diners)if(d.selected)currentDebt.addPayee(d);}
	public void addPayersForCurrentDiscount(){for(Diner d: Grouptuity.getBill().diners)if(d.selected)currentDiscount.addPayer(d);}
	public void addPayeesForCurrentDiscount(){for(Diner d: Grouptuity.getBill().diners)if(d.selected)currentDiscount.addPayee(d);}
	public void applyCurrentItemPayersToCurrentDiscount(){for(int i=0; i<currentItem.payers.size(); i++){currentDiscount.addPayee(currentItem.payers.get(i),currentItem.payerWeights.get(i));}}
	public void commitCurrentItem(){Grouptuity.getBill().addItem(currentItem);clearDinerSelections();}
	public void commitCurrentDebt(){Grouptuity.getBill().addDebt(currentDebt);clearDinerSelections();}
	public void commitCurrentDiscount()
	{
		if(currentDiscount.billableType==BillableType.DEAL_GROUP_VOUCHER)
		{
			Debt debt = new Debt(currentDiscount.cost);
			debt.linkedDiscount = currentDiscount;
			currentDiscount.linkedDebt = debt;
			Grouptuity.getBill().addDebt(debt);
		}
		Grouptuity.getBill().addDiscount(currentDiscount);
		clearDinerSelections();
	}
	public void resetBillables(){Grouptuity.getBill().resetBillables();}

	public String getTaxPercent(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.TAX_PERCENT,Grouptuity.getBill().getTaxPercent());}
	public String getTaxValue(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getBill().getTaxValue());}
	public String getTipPercent(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.TIP_PERCENT,Grouptuity.getBill().getTipPercent());}
	public String getTipValue(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getBill().getTipValue());}
	public String getRawSubtotal(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getBill().getRawSubtotal());}
	public String getBoundedDiscountedSubtotal(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getBill().getBoundedDiscountedSubtotal());}
	public String getDiscountedSubtotal(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getBill().getDiscountedSubtotal());}
	public String getTaxableSubtotal(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getBill().getTaxableSubtotal());}
	public String getTipableSubtotal(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getBill().getTipableSubtotal());}
	public String getAfterTaxSubtotal(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getBill().getAfterTaxSubtotal());}
	public String getTotal(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getBill().getTotal());}
	public void setTaxPercent(double percent){Grouptuity.getBill().setTaxPercent(percent);}
	public void setTaxValue(double value){Grouptuity.getBill().setTaxValue(value);}
	public void setTipPercent(double percent){Grouptuity.getBill().setTipPercent(percent);}
	public void setTipValue(double value){Grouptuity.getBill().setTipValue(value);}
	public void overrideRawSubtotal(double value){Grouptuity.getBill().overrideRawSubTotal(value);}
	public void overrideAfterTaxSubtotal(double value){Grouptuity.getBill().overrideAfterTaxSubTotal(value);}
	//public void overrideTotal(double value){bill.overrideTotal(value);} not available in the group bill split

	public void clearVenmoPayments(){for(Diner d: Grouptuity.getBill().diners){if(d.defaultPaymentType==PaymentType.VENMO)d.defaultPaymentType = PaymentType.values()[Grouptuity.VENMO_ALTERNATIVE.getValue()];}Grouptuity.getBill().invalidatePayments();}
	public boolean requiresElectronicPayment(){for(Diner d: Grouptuity.getBill().diners){if(d.defaultPaymentType==PaymentType.VENMO)return true;}return false;}
	public boolean isPaymentComplete(){return Grouptuity.getBill().isPaymentComplete();}
}