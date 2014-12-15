package com.grouptuity.model;

import com.grouptuity.Grouptuity;
import com.grouptuity.Grouptuity.*;

public class Payment
{
	final public Diner payer, payee;
	public PaymentType type;
	public double amount;

	public Payment(double amount, PaymentType type, Diner payer, Diner payee)
	{
		this.amount = amount;
		this.type = type;
		this.payer = payer;
		this.payee = payee;
	}

	public String toStringPayer()
	{
		switch(type)
		{
			case CASH:		return "Pay "+Grouptuity.formatNumber(NumberFormat.CURRENCY,amount)+" in cash to "+payee.name;
			case CREDIT:	String instruction = "Pay "+Grouptuity.formatNumber(NumberFormat.CURRENCY,amount)+" to "+payee.name+" by credit card";
							if(payee.name.equals(Grouptuity.RESTAURANT_NAME))
							{
								double adjustedTip = (payer.tipPercent*amount)/(100.0 + payer.tipPercent + (Grouptuity.TIP_THE_TAX.getValue()?0:payer.taxPercent));
								instruction += " (" + Grouptuity.formatNumber(NumberFormat.CURRENCY,amount-adjustedTip) + " + " + Grouptuity.formatNumber(NumberFormat.CURRENCY,adjustedTip) + " tip)";
							}
							return instruction;
			case VENMO:		return "Pay "+payee.name+" "+Grouptuity.formatNumber(NumberFormat.CURRENCY,amount)+" using Venmo";
			case IOU_EMAIL:	return "Owes "+payee.name+" "+Grouptuity.formatNumber(NumberFormat.CURRENCY,amount);
		}
		return "";
	}
	public String toStringPayee()
	{
		switch(type)
		{
			case CASH:		return "Receive "+Grouptuity.formatNumber(NumberFormat.CURRENCY,amount)+" in cash from "+payer.name;
			case CREDIT:	return "Receive "+Grouptuity.formatNumber(NumberFormat.CURRENCY,amount)+" from "+payer.name+" by credit card";
			case VENMO:		return "Charge "+payer.name+" "+Grouptuity.formatNumber(NumberFormat.CURRENCY,amount)+" using Venmo";
			case IOU_EMAIL:	return "Is owed "+Grouptuity.formatNumber(NumberFormat.CURRENCY,amount)+" by "+payer.name;
		}
		return "";
	}
}