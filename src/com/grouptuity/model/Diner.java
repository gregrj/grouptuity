package com.grouptuity.model;

import java.util.ArrayList;
import android.os.Parcel;
import android.os.Parcelable;
import com.grouptuity.Grouptuity;
import com.grouptuity.Grouptuity.*;

public class Diner implements Parcelable
{
	final public Contact contact;
	final public String name;
	final public ArrayList<Item> items;
	final public ArrayList<Debt> debts;
	final public ArrayList<Discount> discounts, redistributedDiscounts;
	public boolean selected, isSelf;
	public double itemSubtotal, debtSum, discountSum, fullSubTotal, taxableSubTotal, tipableSubTotal, taxValue, taxPercent, tipValue, tipPercent, totalLessVenmo, total, redistributedDiscountSum, completedVenmoTransactionsBalance;
	public PaymentType defaultPaymentType;
	public ArrayList<Payment> paymentsOut, paymentsIn;
	public Diner defaultPayee;
	protected double creditCardCashTake; // positive means absolute quantity and negative means percents

	public Diner(Contact c)
	{
		contact = c;
		name = contact.name;
		isSelf = contact.isSelf;
		if(contact!=null && contact.defaultPaymentType!=null)
			defaultPaymentType = contact.defaultPaymentType;
		else
			defaultPaymentType = Grouptuity.PaymentType.values()[Grouptuity.DEFAULT_PAYMENT_TYPE.getValue()];

		items = new ArrayList<Item>();
		debts = new ArrayList<Debt>();
		discounts = new ArrayList<Discount>();
		redistributedDiscounts = new ArrayList<Discount>(); 
		paymentsOut = new ArrayList<Payment>();
		paymentsIn = new ArrayList<Payment>();
		if(contact.venmoUsername==null)
			contact.refreshVenmoUsername();
	}

	protected void addPaymentOut(Payment payment){paymentsOut.add(payment);}
	protected void addPaymentIn(Payment payment){paymentsIn.add(payment);}
	protected void clearPayments(){paymentsOut.clear();paymentsIn.clear();redistributedDiscounts.clear();}
	public String getPaymentInstructions()
	{
		String instructions = "";
		for(Payment p: paymentsOut)
			instructions += "\u2022 "+p.toStringPayer() + "\n";
		for(Payment p: paymentsIn)
			instructions += "\u2022 "+p.toStringPayee()+"\n";
		return instructions;
	}
	public String getReceiptNotes()
	{
		String notes = "";
		if(Math.abs(itemSubtotal) > Grouptuity.PRECISION_ERROR)
			notes += "    Items: "+Grouptuity.formatNumber(NumberFormat.CURRENCY, itemSubtotal)+"\n";
		if(Math.abs(debtSum) > Grouptuity.PRECISION_ERROR)
			notes += "    Debts: "+Grouptuity.formatNumber(NumberFormat.CURRENCY, debtSum)+"\n";
		if(Math.abs(discountSum) > Grouptuity.PRECISION_ERROR)
			notes += "    Discounts: "+Grouptuity.formatNumber(NumberFormat.CURRENCY, discountSum)+"\n";
		notes += "    Tax: "+Grouptuity.formatNumber(NumberFormat.CURRENCY, taxValue)+"\n";
		notes += "    Tip: "+Grouptuity.formatNumber(NumberFormat.CURRENCY, tipValue)+"\n";
		notes += "    Total: "+Grouptuity.formatNumber(NumberFormat.CURRENCY, totalLessVenmo)+"\n";
		return notes;
	}

	public boolean hasVenmo(){return contact.venmoUsername != null;}
	public boolean hasBillables(){return !(items.isEmpty() && debts.isEmpty() && discounts.isEmpty());}
	protected void addItem(Item item){if(!items.contains(item))items.add(item);}
	protected void addDebt(Debt debt){if(!debts.contains(debt))debts.add(debt);}
	protected void addDiscount(Discount discount){if(!discounts.contains(discount))discounts.add(discount);}
	protected void removeItem(Item item){items.remove(item);}
	protected void removeDebt(Debt debt){debts.remove(debt);}
	protected void removeDiscount(Discount discount){discounts.remove(discount);}

	public static double getNetValueToDiner(Diner diner, Debt debt)
	{
		double value = 0.0;
		if(debt.payers.contains(diner))
			value += debt.value*debt.getWeightFractionForPayer(diner);
		if(debt.payees.contains(diner))
			value -= debt.value*debt.getWeightFractionForPayee(diner);
		return value;
	}
	public static double getNetValueToDiner(Diner diner, Discount discount)
	{
		if(discount.payees.contains(diner))
			return -discount.value*discount.getWeightFractionForPayee(diner);
		else
			return 0.0;
	}
	public static double getNetRedistributedValueToDiner(Diner diner, Discount discount)
	{
		if(discount.redistributionPayees.contains(diner))
			return -discount.value*discount.getWeightFractionForRedistributionPayee(diner);
		else
			return 0.0;
	}
	protected void calculateSubtotal()
	{
		itemSubtotal = 0.0;
		discountSum = 0.0;
		redistributedDiscountSum = 0.0;
		debtSum = 0.0;
		for(Item item: items)
		{
			itemSubtotal += item.value*item.getWeightFractionForPayer(this);
		}
		taxableSubTotal = itemSubtotal;
		tipableSubTotal = itemSubtotal;
		fullSubTotal = itemSubtotal;

		for(Debt debt: debts)
			debtSum += getNetValueToDiner(this,debt);

		ArrayList<Discount> combinedDiscounts = new ArrayList<Discount>();
		combinedDiscounts.addAll(discounts);
		combinedDiscounts.addAll(redistributedDiscounts);
		for(Discount discount: combinedDiscounts)
		{
			discountSum += getNetValueToDiner(this,discount);
			redistributedDiscountSum += getNetRedistributedValueToDiner(this,discount);
			if(discount.redistributionPayees.contains(this))
			{
				switch(discount.taxable)
				{
					case ON_VALUE:	break;
					case ON_COST:	taxableSubTotal += discount.cost*discount.getWeightFractionForRedistributionPayee(this);
					case NONE:		taxableSubTotal -= discount.value*discount.getWeightFractionForRedistributionPayee(this);break;
				}
				switch(discount.tipable)
				{
					case ON_VALUE:	break;
					case ON_COST:	tipableSubTotal += discount.cost*discount.getWeightFractionForRedistributionPayee(this);
					case NONE:		tipableSubTotal -= discount.value*discount.getWeightFractionForRedistributionPayee(this);break;
				}
			}
		}
		fullSubTotal += debtSum;
		fullSubTotal += discountSum;
	}
	protected void calculateTotal(double taxPct, double tipPct)
	{
		taxPercent = taxPct;
		tipPercent = tipPct;
		taxValue = 0.01*taxPercent*Math.max(taxableSubTotal,0);
		if(Grouptuity.TIP_THE_TAX.getValue())
			tipableSubTotal +=  taxValue;
		tipValue = 0.01*tipPercent*Math.max(tipableSubTotal,0);

		completedVenmoTransactionsBalance = 0;
		for(Payment venmoTransaction: Grouptuity.getBill().getAllVenmoTransactions())
		{
			if(venmoTransaction.payer==this)
				completedVenmoTransactionsBalance -= venmoTransaction.amount;
			else if(venmoTransaction.payee==this)
				completedVenmoTransactionsBalance += venmoTransaction.amount;
		}
		totalLessVenmo = debtSum+Math.max(itemSubtotal+redistributedDiscountSum,0)+taxValue+tipValue;
		total = totalLessVenmo + completedVenmoTransactionsBalance;
	}

	protected void consolidatePayments()
	{
		//SIMPLIFICATION 1A - combine outgoing payments to the same diner
		boolean[] removePaymentOut = new boolean[paymentsOut.size()];
		for(int i=0; i<paymentsOut.size()-1; i++)
		{
			if(!removePaymentOut[i])
			{
				Payment paymentOut1 = paymentsOut.get(i);
				for(int j=i+1; j<paymentsOut.size(); j++)
				{
					Payment paymentOut2 = paymentsOut.get(j);
					if(paymentOut1.payee.equals(paymentOut2.payee))
					{
						removePaymentOut[j] = true;
						paymentOut1.amount += paymentOut2.amount;
						if(paymentOut1.type==PaymentType.VENMO || paymentOut2.type==PaymentType.VENMO)
							paymentOut1.type = PaymentType.VENMO;
						else if(paymentOut1.type==PaymentType.IOU_EMAIL || paymentOut2.type==PaymentType.IOU_EMAIL)
							paymentOut1.type = PaymentType.IOU_EMAIL;
						else if(paymentOut1.type==PaymentType.CREDIT || paymentOut2.type==PaymentType.CREDIT)
							paymentOut1.type = PaymentType.CREDIT;
					}
				}
			}
		}
		int indexCompensator = 0;
		for(int i=0; i<removePaymentOut.length; i++)
		{
			if(removePaymentOut[i])
			{
				Payment removal = paymentsOut.remove(i-indexCompensator);
				removal.payee.paymentsIn.remove(removal);
				indexCompensator++;
			}
		}

		//SIMPLIFICATION 1B - combine incoming payments from the same diner
		boolean[] removePaymentIn = new boolean[paymentsIn.size()];
		for(int i=0; i<paymentsIn.size()-1; i++)
		{
			if(!removePaymentIn[i])
			{
				Payment paymentIn1 = paymentsIn.get(i);
				for(int j=i+1; j<paymentsIn.size(); j++)
				{
					Payment paymentIn2 = paymentsIn.get(j);
					if(paymentIn1.payer.equals(paymentIn2.payer))
					{
						removePaymentIn[j] = true;
						paymentsIn.get(i).amount += paymentsIn.get(j).amount;
						if(paymentIn1.type==PaymentType.VENMO || paymentIn2.type==PaymentType.VENMO)
							paymentIn1.type = PaymentType.VENMO;
						else if(paymentIn1.type==PaymentType.IOU_EMAIL || paymentIn2.type==PaymentType.IOU_EMAIL)
							paymentIn1.type = PaymentType.IOU_EMAIL;
						else if(paymentIn1.type==PaymentType.CREDIT || paymentIn2.type==PaymentType.CREDIT)
							paymentIn1.type = PaymentType.CREDIT;
					}
				}
			}
		}
		indexCompensator = 0;
		for(int i=0; i<removePaymentIn.length; i++)
		{
			if(removePaymentIn[i])
			{
				Payment removal = paymentsIn.remove(i-indexCompensator);
				removal.payer.paymentsOut.remove(removal);
				indexCompensator++;
			}
		}

		//SIMPLIFICATION 4, arraylist based removal 
		ArrayList<Payment> outRemovalList = new ArrayList<Payment>();
		ArrayList<Payment> inRemovalList = new ArrayList<Payment>();
		for(Payment out: paymentsOut)
		{
			for(Payment in: paymentsIn)
			{
				if(in==out)
				{
					outRemovalList.add(out);
					inRemovalList.add(out);
					break;
				}
				else if(out.payee==in.payer)
				{
					if(Math.abs(out.amount-in.amount) < Grouptuity.PRECISION_ERROR)
					{
						outRemovalList.add(out);
						inRemovalList.add(in);
					}
					else if(out.amount > in.amount)
					{
						out.amount -= in.amount;
						inRemovalList.add(in);
					}
					else
					{
						in.amount -= out.amount;
						outRemovalList.add(out);
					}
					break;
				}
			}
		}
		paymentsIn.removeAll(inRemovalList);
		for(Payment removal: inRemovalList)
			removal.payer.paymentsOut.remove(removal);
		paymentsOut.removeAll(outRemovalList);
		for(Payment removal: outRemovalList)
			removal.payee.paymentsIn.remove(removal);
	}

	protected void reset()
	{
		items.clear();
		debts.clear();
		discounts.clear();
		paymentsIn.clear();
		paymentsOut.clear();
	}

	public void writeToParcel(Parcel out, int flags)
	{
		out.writeParcelable(contact,0);
		out.writeInt((selected)?1:0);
		out.writeInt((isSelf)?1:0);
		out.writeInt(defaultPaymentType.ordinal());
	}
	private Diner(Parcel in)
	{
		this((Contact)in.readParcelable(Contact.class.getClassLoader()));
		selected = in.readInt()==1;
		isSelf = in.readInt()==1;
		defaultPaymentType = PaymentType.values()[in.readInt()];
	}
	public int describeContents(){return 0;}
	public static final Parcelable.Creator<Diner> CREATOR = new Parcelable.Creator<Diner>(){public Diner createFromParcel(Parcel in){return new Diner(in);}public Diner[] newArray(int size){return new Diner[size];}};
}