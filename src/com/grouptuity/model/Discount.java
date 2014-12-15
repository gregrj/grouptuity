package com.grouptuity.model;

import java.util.ArrayList;

import com.grouptuity.Grouptuity;
import com.grouptuity.Grouptuity.BillableType;
import com.grouptuity.Grouptuity.NumberFormat;

import android.os.Parcel;
import android.os.Parcelable;

public class Discount extends Billable implements Parcelable
{
	final public ArrayList<Diner> redistributionPayees;
	final public ArrayList<Double> redistributionPayeeWeights;
	public double cost, percent;
	public DiscountMethod taxable, tipable;
	public Item discountedItem;
	public Debt linkedDebt;
	private double totalRedistributionPayeeWeight;
	final private Diner restaurant;

	public static enum DiscountMethod{NONE,ON_COST,ON_VALUE;}

	private Discount(double v, double c, double p, BillableType type, DiscountMethod tax, DiscountMethod tip)
	{
		super(v,type);
		redistributionPayees = new ArrayList<Diner>();
		redistributionPayeeWeights = new ArrayList<Double>();
		cost = c;
		percent = p;
		taxable = tax;
		tipable = tip;
		restaurant = Grouptuity.getRestaurant();
	}

	public static Discount createItemPercentDiscount(double p, DiscountMethod tax, DiscountMethod tip, Item item){Discount discount = new Discount(0.0,0.0,p,BillableType.DEAL_PERCENT_ITEM,tax,tip);discount.discountedItem = item;return discount;}
	public static Discount createBillPercentDiscount(double p, DiscountMethod tax, DiscountMethod tip){return new Discount(0.0,0.0,p,BillableType.DEAL_PERCENT_BILL,tax,tip);}
	public static Discount createFixedAmountVoucher(double v, DiscountMethod tax, DiscountMethod tip){return new Discount(v,0.0,0.0,BillableType.DEAL_FIXED,tax,tip);}
	public static Discount createGroupDealVoucher(double v, DiscountMethod tax, DiscountMethod tip){return new Discount(v,0.0,0.0,BillableType.DEAL_GROUP_VOUCHER,tax,tip);} //cost is entered later in the UI so no need to get it now

	protected void setDinerRedistributedAmount(Diner diner, double amount)
	{
		int index = redistributionPayees.indexOf(diner);
		if(index==-1)
		{
			redistributionPayees.add(diner);
			diner.redistributedDiscounts.add(this);
			double weight = amount/value * totalRedistributionPayeeWeight;
			redistributionPayeeWeights.add(weight);
			redistributionPayeeWeights.set(0, redistributionPayeeWeights.get(0)-weight);
		}
		else
		{
			double previousWeight = redistributionPayeeWeights.get(index);
			double weightChange = (amount/value*totalRedistributionPayeeWeight) - previousWeight;
			redistributionPayeeWeights.set(index, previousWeight+weightChange);
			redistributionPayeeWeights.set(0, redistributionPayeeWeights.get(0)-weightChange);
		}
	}

	protected double getWeightForRedistributionPayee(Diner d)
	{
		int index = redistributionPayees.indexOf(d);
		if(index == -1)
			return 0;
		return redistributionPayeeWeights.get(redistributionPayees.indexOf(d));
	}
	protected double getWeightFractionForRedistributionPayee(Diner d)
	{
		int index = redistributionPayees.indexOf(d);
		if(index == -1)
			return 0;
		return redistributionPayeeWeights.get(redistributionPayees.indexOf(d))/totalRedistributionPayeeWeight;
	}
	protected double getTotalRedistributionPayeeWeight(){return totalRedistributionPayeeWeight;}

	protected void update()
	{
		switch(billableType)
		{
			case DEAL_PERCENT_ITEM:	value = 0.01*percent*discountedItem.value;
									for(Diner d: payees)
										d.removeDiscount(this);
									payees.clear();
									for(Diner d: discountedItem.payers)
									{
										addPayee(d, discountedItem.getWeightForPayer(d));
										d.addDiscount(this);
									}
									break;
			case DEAL_PERCENT_BILL:	if(Grouptuity.getBill()!=null)
										value = 0.01*percent*Grouptuity.getBill().getDiscountedSubtotal();break;
			case DEAL_GROUP_VOUCHER:linkedDebt.update();break;
			default:	break;
		}
		clearRedistributions();
	}
	protected void clearRedistributions()
	{
		redistributionPayees.clear();
		redistributionPayeeWeights.clear();
		totalRedistributionPayeeWeight = 0.0;
		redistributionPayees.add(restaurant);
		redistributionPayeeWeights.add(0.0);
		for(int i=0; i<payees.size(); i++)
		{
			redistributionPayees.add(payees.get(i));
			redistributionPayeeWeights.add(payeeWeights.get(i));
			totalRedistributionPayeeWeight += payeeWeights.get(i);
		}
	}
	

	public String toString()
	{
		switch(billableType)
		{
			case DEAL_PERCENT_ITEM:		return Grouptuity.formatNumber(NumberFormat.TAX_PERCENT,percent)+" off item";
			case DEAL_PERCENT_BILL:		return Grouptuity.formatNumber(NumberFormat.TAX_PERCENT,percent)+" off bill";
			case DEAL_FIXED:			return Grouptuity.formatNumber(NumberFormat.CURRENCY,value);
			case DEAL_GROUP_VOUCHER:	return Grouptuity.formatNumber(NumberFormat.CURRENCY,value)+" (cost "+Grouptuity.formatNumber(NumberFormat.CURRENCY,cost)+")";
			default: return "";
		}
	}

	public void writeToParcel(Parcel out, int flags)
	{
		out.writeDouble(value);
		out.writeDouble(cost);
		out.writeDouble(percent);
		out.writeInt(billableType.ordinal());
		out.writeInt(taxable.ordinal());
		out.writeInt(tipable.ordinal());
	}
	private Discount(Parcel in){this(in.readDouble(),in.readDouble(),in.readDouble(),BillableType.values()[in.readInt()],DiscountMethod.values()[in.readInt()],DiscountMethod.values()[in.readInt()]);}
	public int describeContents(){return 0;}
	public static final Parcelable.Creator<Discount> CREATOR = new Parcelable.Creator<Discount>(){public Discount createFromParcel(Parcel in){return new Discount(in);}public Discount[] newArray(int size){return new Discount[size];}};
}