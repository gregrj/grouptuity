package com.grouptuity.model;

import java.util.ArrayList;
import com.grouptuity.Grouptuity.*;

public abstract class Billable
{
	final public ArrayList<Diner> payers, payees;
	final public ArrayList<Double> payerWeights, payeeWeights;
	public double value;
	private double totalPayerWeight, totalPayeeWeight;
	public BillableType billableType;

	public Billable(double v, BillableType type)
	{
		value = v;
		payers = new ArrayList<Diner>();
		payees = new ArrayList<Diner>();
		payerWeights = new ArrayList<Double>();
		payeeWeights = new ArrayList<Double>();
		billableType = type;
	}

	protected void addPayer(Diner diner){addPayer(diner,1.0);}
	protected void addPayer(Diner diner, double weight)
	{
		if(!payers.contains(diner))
		{
			payers.add(diner);
			payerWeights.add(weight);
			totalPayerWeight+=weight;
		}
	}
	protected void removePayer(Diner diner)
	{
		if(payers.contains(diner))
		{
			totalPayerWeight -= payerWeights.remove(payers.indexOf(diner));
			payers.remove(diner);
		}
	}
	protected void clearPayers(){payers.clear();totalPayerWeight = 0.0;}
	protected void addPayee(Diner diner){addPayee(diner,1.0);}
	protected void addPayee(Diner diner, double weight)
	{
		if(!payees.contains(diner))
		{
			payees.add(diner);
			payeeWeights.add(weight);
			totalPayeeWeight+=weight;
		}
	}
	protected void removePayee(Diner diner)
	{
		if(payees.contains(diner))
		{
			totalPayeeWeight -= payeeWeights.remove(payees.indexOf(diner));
			payees.remove(diner);
		}
	}
	protected void clearPayees(){payees.clear();totalPayeeWeight = 0.0;}

	protected double getWeightForPayer(Diner d)
	{
		int index = payers.indexOf(d);
		if(index == -1)
			return 0;
		return payerWeights.get(payers.indexOf(d));
	}
	protected double getWeightForPayee(Diner d)
	{
		int index = payees.indexOf(d);
		if(index == -1)
			return 0;
		return payeeWeights.get(payees.indexOf(d));
	}
	public double getWeightFractionForPayer(Diner d)
	{
		int index = payers.indexOf(d);
		if(index == -1)
			return 0;
		return payerWeights.get(payers.indexOf(d))/totalPayerWeight;
	}
	public double getWeightFractionForPayee(Diner d)
	{
		int index = payees.indexOf(d);
		if(index == -1)
			return 0;
		return payeeWeights.get(payees.indexOf(d))/totalPayeeWeight;
	}
	protected double getTotalPayerWeight(){return totalPayerWeight;}
	protected double getTotalPayeeWeight(){return totalPayeeWeight;}
}