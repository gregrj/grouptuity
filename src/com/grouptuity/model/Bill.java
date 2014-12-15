package com.grouptuity.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import android.os.Parcel;
import android.os.Parcelable;
import com.grouptuity.Grouptuity;
import com.grouptuity.Grouptuity.*;

public class Bill implements Parcelable
{
	final public ArrayList<Diner> diners;
	final public ArrayList<Item> items;
	final public ArrayList<Debt> debts;
	final public ArrayList<Discount> discounts;
	final public ArrayList<Payment> staleVenmoTransactions; //venmo transactions that no longer apply to the calculated payments set
	final public ArrayList<Payment> venmoTransactions; //venmo transactions that came from the current calculated set of payments
	final public Diner restaurant;
	final public long date;	//The current time is saved in writeToParcel to reflect latest time the bill was in use
	private double subtotal, boundedDiscountedSubtotal, discountedSubTotal, taxableSubTotal, tipableSubTotal, taxPercent, taxValue, afterTaxSubtotal, tipPercent, tipValue, total;
	private boolean paymentComplete, dirtyCalculation, dirtyPayments;
	private Override override;
	public Diner hostDiner;

	public boolean debug = false;
	
	public static enum Override{NONE,SUB,AFTER,TOTAL;}

	public Bill(){this(new Date().getTime(),0.0,false);}
	public Bill(long d, double t){this(d,t,true);}
	private Bill(long d, double t, boolean over)
	{
		diners = new ArrayList<Diner>();
		restaurant = Grouptuity.getRestaurant();
		items = new ArrayList<Item>();
		debts = new ArrayList<Debt>();
		staleVenmoTransactions = new ArrayList<Payment>();
		venmoTransactions = new ArrayList<Payment>();
		discounts = new ArrayList<Discount>();
		taxPercent = Grouptuity.DEFAULT_TAX.getValue();
		tipPercent = Grouptuity.DEFAULT_TIP.getValue();
		date = d;
		total = t;
		override = over?Override.SUB:Override.NONE;
		dirtyCalculation = true;
		dirtyPayments = true;
	}

	public void invalidate(){dirtyCalculation = true;}
	public void invalidatePayments(){dirtyPayments = true;}

	public void calculateTotal()
	{
		if(dirtyCalculation)
		{
			dirtyCalculation = false;
			dirtyPayments = false;
			
			//all venmo transactions are now stale
			staleVenmoTransactions.addAll(venmoTransactions);
			venmoTransactions.clear();

			//TODO properly calculate taking into account rounding, may need to switch to ints
			switch(override)
			{
				case NONE:	subtotal = 0.0;
							for(Item i: items)
								subtotal += i.value;
							discountedSubTotal = subtotal;
							taxableSubTotal = subtotal;
							tipableSubTotal = subtotal;

							for(Discount discount: discounts)
							{
								discount.update();
								discountedSubTotal -= discount.value;
								switch(discount.taxable)
								{
									case ON_VALUE:	break;
									case ON_COST:	taxableSubTotal += discount.cost;
									case NONE:		taxableSubTotal -= discount.value;break;
								}
								switch(discount.tipable)
								{
									case ON_VALUE:	break;
									case ON_COST:	tipableSubTotal += discount.cost;
									case NONE:		tipableSubTotal -= discount.value;break;
								}
							}

							boundedDiscountedSubtotal = Math.max(0, discountedSubTotal);
							taxValue = 0.01*taxPercent*Math.max(0, taxableSubTotal);
							afterTaxSubtotal = boundedDiscountedSubtotal+taxValue;
							if(Grouptuity.TIP_THE_TAX.getValue())
								tipableSubTotal +=  taxValue;
							tipValue = 0.01*tipPercent*Math.max(0, tipableSubTotal);
							total = afterTaxSubtotal+tipValue;
							break;
				case SUB:	taxValue = subtotal*taxPercent/100.0;
							afterTaxSubtotal = subtotal+taxValue;
							tipValue = (Grouptuity.TIP_THE_TAX.getValue())?((subtotal+taxValue)*tipPercent/100.0):(subtotal*tipPercent/100.0);
							total = afterTaxSubtotal+tipValue;
							break;
				case AFTER:	subtotal = afterTaxSubtotal/(1+taxPercent/100.0);
							taxValue = afterTaxSubtotal-subtotal;
							tipValue = (Grouptuity.TIP_THE_TAX.getValue())?((subtotal+taxValue)*tipPercent/100.0):(subtotal*tipPercent/100.0);
							total = afterTaxSubtotal+tipValue;
							break;
				case TOTAL:	if(Grouptuity.TIP_THE_TAX.getValue())
							{
								afterTaxSubtotal = total/(1+tipPercent/100.0);
								tipValue = total - afterTaxSubtotal;
								subtotal = afterTaxSubtotal/(1+taxPercent/100.0);
								taxValue = afterTaxSubtotal-subtotal;
							}
							else
							{
								subtotal = total/(1+taxPercent/100.0+tipPercent/100.0);
								taxValue = subtotal*taxPercent/100.0;
								afterTaxSubtotal = subtotal+taxValue;
								tipValue = subtotal*tipPercent/100.0;
							}
							break;
			}
			if(override!=Override.NONE)
				override = Override.SUB;

			computePayments();
			return;
		}

		if(dirtyPayments)
		{
			dirtyPayments = false;
			computePayments();
		}
	}

	private void computePayments()
	{		
		//Clear existing payments
		for(Diner d : diners)
			d.clearPayments();
		restaurant.clearPayments();

		for(Discount discount: discounts)
			discount.clearRedistributions();

		//Build the full set of payments based on the billables, without discounts
		for(Item item : items)
		{
			for(Diner d: item.payers)
				addPayment(item.value * item.getWeightFractionForPayer(d),getBestPaymentType(d,restaurant), d, restaurant);
		}
		for(Debt debt : debts)
		{
			for(Diner payer: debt.payers)
			{
				for(Diner payee: debt.payees)
				{
					double amount = debt.value * debt.getWeightFractionForPayer(payer) * debt.getWeightFractionForPayee(payee);
					addPayment(amount, getBestPaymentType(payer,payee), payer, payee);
				}
			}
		}
		for(Payment p: staleVenmoTransactions)
			addPayment(p.amount, Grouptuity.PaymentType.VENMO, p.payee, p.payer); //flip payee and payer since this payment already occurred
		printAll("CANONICAL NO DISCOUNTS");

		//simplify the payments by consolidating duplicates from/to the same pair of diners
		for(Diner d: diners)
			d.consolidatePayments();
		
		//next, reallocate discounts so no diner receives more discount than their total owed to restaurant

		//build a hashmap of amounts owed to restaurant by each diner
		final HashMap<Diner,Double> dinerAmtsOwedToRestaurant = new HashMap<Diner, Double>();
		for(Diner d : diners) {
			double amtPayingRestaurant = 0;
			//we've simplified, so only one payment to the restaurant
			for(Payment p : d.paymentsOut) {
				if(p.payee==restaurant) {
					amtPayingRestaurant=p.amount;
					break;
				}
			}
			dinerAmtsOwedToRestaurant.put(d,amtPayingRestaurant);
		}
		//build a hashmap of overall diner totals, to know to what extent each diner is negative
		final HashMap<Diner,Double> dinerOverallTotals = new HashMap<Diner, Double>();
		dinerOverallTotals.putAll(dinerAmtsOwedToRestaurant);
		for(Diner d : diners) {
			double totalDiscount = 0;
			for(Discount discount : d.discounts)
				totalDiscount += discount.value * discount.getWeightFractionForRedistributionPayee(d);
			dinerOverallTotals.put(d,dinerOverallTotals.get(d) - totalDiscount);
		}

		//excess discount unable to be given out to diners on the discount
		HashMap<Discount,Double> excessByDiscount = new HashMap<Discount, Double>();
		
		//count of non-zero diners on discount
		HashMap<Discount,Double> remainingWtFractionOnDiscount = new HashMap<Discount, Double>();
		for(Discount disc : discounts) {
			excessByDiscount.put(disc, 0.0);
			remainingWtFractionOnDiscount.put(disc, 1.0);
		}
		
		//list of diners that have non-zero totals
		final ArrayList<Diner> remainingDiners = new ArrayList<Diner>();
		remainingDiners.addAll(diners);
		
		//NEW SOLUTION. PUSH UP EXCESS TO DISCOUNTS, PULL DOWN SHARE OF EXCESS TO DINERS. CONTINUE UNTIL RESOLVED.
		//boolean done = false;
		HashMap<Discount,Double> pushedUpExcessByDiscount = new HashMap<Discount, Double>();
		HashMap<Discount,Double> remainingExcessByDiscount = new HashMap<Discount, Double>();
		ArrayList<Diner> dinersToRemove = new ArrayList<Diner>();
		int numNegativeDiners = 1;
		while(numNegativeDiners > 0 && remainingDiners.size() > 0) {
			remainingExcessByDiscount.putAll(excessByDiscount);
			numNegativeDiners = 0;
			for(Discount disc : discounts)
				pushedUpExcessByDiscount.put(disc, 0.0);
			for(Diner d : remainingDiners) {
				if(debug)Grouptuity.log(d.name+":");				
				//pull down diners share of any excess that had been pushed up to the discounts
				double pulledDownExcess = 0, totalDiscount = 0;
				for(Discount disc : d.discounts) {
					pulledDownExcess += excessByDiscount.get(disc) * disc.getWeightFractionForRedistributionPayee(d) / remainingWtFractionOnDiscount.get(disc);
					remainingExcessByDiscount.put(disc,0.0);
					totalDiscount += disc.getWeightFractionForRedistributionPayee(d) * disc.value;
				}
				if(debug)Grouptuity.log("\tPulled down: "+pulledDownExcess);
				dinerOverallTotals.put(d, dinerOverallTotals.get(d) - pulledDownExcess);
				if(debug)Grouptuity.log("\tOverall total:"+dinerOverallTotals.get(d));

				//determine weighted split of excess, and push it up to each discount
				//also mark this diner for removal, subtract off its wt frac from the remaining amt for the discount, and set their redistro amt in the discount
				double dinerExcess = 0;
				if(dinerOverallTotals.get(d) <= 0) {
					numNegativeDiners += 1;
					dinersToRemove.add(d);
					dinerExcess -= dinerOverallTotals.get(d);
					dinerOverallTotals.put(d,0.0);
					for(Discount disc : d.discounts) {
						double discountExcessShare = excessByDiscount.get(disc) * disc.getWeightFractionForRedistributionPayee(d) / remainingWtFractionOnDiscount.get(disc);
						double thisDiscountAmount = discountExcessShare + disc.value * disc.getWeightFractionForRedistributionPayee(d);
						
						pushedUpExcessByDiscount.put(disc, pushedUpExcessByDiscount.get(disc) + dinerExcess * thisDiscountAmount / totalDiscount);
						remainingWtFractionOnDiscount.put(disc, remainingWtFractionOnDiscount.get(disc) - disc.getWeightFractionForRedistributionPayee(d));
						
						disc.setDinerRedistributedAmount(d,thisDiscountAmount - dinerExcess * thisDiscountAmount / totalDiscount);
					}
				}
				else {
					for(Discount disc : d.discounts) {
						double discountExcessShare = excessByDiscount.get(disc) * disc.getWeightFractionForRedistributionPayee(d) / remainingWtFractionOnDiscount.get(disc);
						double thisDiscountAmount = discountExcessShare + disc.value * disc.getWeightFractionForRedistributionPayee(d);
						disc.setDinerRedistributedAmount(d,thisDiscountAmount);
					}
				}
					
			}
			
			HashMap<Discount,Double> temp = excessByDiscount;
			excessByDiscount = pushedUpExcessByDiscount;
			pushedUpExcessByDiscount = temp;
			pushedUpExcessByDiscount.clear();
			
			remainingDiners.removeAll(dinersToRemove);
		}
		
		//comparator for sorting diners
		Comparator<Diner> dinerComparator = new Comparator<Diner>() {
		    public int compare(Diner a, Diner b) {
		        return Double.compare(dinerOverallTotals.get(a), dinerOverallTotals.get(b));
		    }
		};
		
		//sort the diners by the amount still owed to the restaurant, ascending
		ArrayList<Diner> dinersByOverallTotal = new ArrayList<Diner>();
		dinersByOverallTotal.addAll(diners);
		Collections.sort(dinersByOverallTotal, dinerComparator);

		//Use the sorted list to allocate the excess discount evenly/fairly to the diners. Don't
		//give any diner more discount than their total owed to the restaurant. Any leftover discount
		//after going through all the diners is just discarded.
		//The sorted list allows us to incrementally split the remaining pot and achieve this solution in one pass
		for(Discount discount : remainingExcessByDiscount.keySet()) {
			double excessDiscount = remainingExcessByDiscount.get(discount);
			for(int i=0;i<dinersByOverallTotal.size();i++) {
				Diner d = dinersByOverallTotal.get(i);
				double allocation = excessDiscount/(diners.size() - i); //allocation splits remaining pot evenly
				double amtPayingRestaurant = dinerOverallTotals.get(d);
				double currentDiscountAmount = discount.value * discount.getWeightFractionForRedistributionPayee(d);

				if(debug)Grouptuity.log("Diner "+d.name+" has discount of "+currentDiscountAmount+" and is allocated "+allocation);
				if(amtPayingRestaurant == 0)
					continue;
				else if(allocation > amtPayingRestaurant) {
					//if we've allocated too much, only discount what they owe the restaurant
					excessDiscount -= amtPayingRestaurant; //remove their decreased allocation from the pot
					discount.setDinerRedistributedAmount(d,currentDiscountAmount+amtPayingRestaurant);
				}
				else {
					//otherwise, give the full allocated discount
					excessDiscount -= allocation; //remove their allocation from the pot
					discount.setDinerRedistributedAmount(d,currentDiscountAmount+allocation);
				}
			}
		}

		//discounts now have the correct redistributed values, so we can add their associated payments
		for(Discount discount : discounts)
		{
			for(Diner d : discount.redistributionPayees)
			{
				double amount = discount.value * discount.getWeightFractionForRedistributionPayee(d);
				addPayment(amount, PaymentType.CASH, restaurant, d);
			}
		}
		
		//calculate subtotals and totals for diners
		for(Diner d: diners)
			d.calculateSubtotal();
		for(Diner d: diners)
			d.calculateTotal(taxPercent,tipPercent);
		
		//Add the tax and tip as supplemental single payments from the diners to the restaurant
		for(Diner d: diners)
			addPayment(d.taxValue + d.tipValue, getBestPaymentType(d,restaurant), d, restaurant);

		//simplify the payments again by consolidating duplicates from/to the same pair of diners
		for(Diner d: diners)
			d.consolidatePayments();

		printAll("SIMPLIFIED");

//		ArrayList<Diner> paidByRestaurant = new ArrayList<Diner>();
//		for(Payment p : restaurant.paymentsOut) paidByRestaurant.add(p.payee);
		
		//If a diner has a directed payment type, ensure that a payment exists for it, and that their payee is paying the restaurant
		//If a diner has cash, ensure that a payment exists from them to every other diner.
		//If any of these payments don't exist, create a zero value payment in the correct direction 
		for(Diner d : diners)
		{
			switch(d.defaultPaymentType)
			{
			case CREDIT:	if(!paymentExists(d,restaurant))
								addPayment(0, PaymentType.CREDIT, d, restaurant);
							break;
			case IOU_EMAIL:	if(!paymentExists(d,d.defaultPayee))
								addPayment(0, PaymentType.IOU_EMAIL, d, d.defaultPayee);
							if(!paymentExists(d.defaultPayee,restaurant))
								addPayment(0, getBestPaymentType(d.defaultPayee, restaurant), d.defaultPayee, restaurant);
							break;
			case VENMO:		if(!paymentExists(d,d.defaultPayee))
								addPayment(0, PaymentType.VENMO, d, d.defaultPayee);
							if(!paymentExists(d.defaultPayee,restaurant))
								addPayment(0, getBestPaymentType(d.defaultPayee, restaurant), d.defaultPayee, restaurant);
							break;
			case CASH:		break;
//			case CASH:		for(Diner d2 : diners) {
//								if(d2!=d && !paymentExists(d,d2))
//									addPayment(0, PaymentType.IOU_EMAIL, d, d2);
//							}
//							break;
			}
//			for(Diner d2 : diners) {
//				if(d2!=d && !paymentExists(d,d2) && paymentExists(d,restaurant)) {
//						addPayment(0, getBestPaymentType(d,d2), d, d2);
//				}
//			}
		}

		//Search for and eliminate cycles in the payment graph
		//Start with all the restuarant's payees, since the restaurant can't pay people
		//Next try to remove non-preferred payees for diners with directed payments
		removePayeesInPaymentGraph(restaurant,null);
		for(Diner d : diners)
		{
			switch(d.defaultPaymentType)
			{
				case CREDIT:	removePayeesInPaymentGraph(d,restaurant);
								break;
				case IOU_EMAIL:	removePayeesInPaymentGraph(d,d.defaultPayee);
								break;
				case VENMO:		removePayeesInPaymentGraph(d,d.defaultPayee);
								break;
				default:		break;
			}
		}

		//Next, eliminate any other cycles
		for(Diner d : diners) {removePayeesInPaymentGraph(d,null);}

		//Eliminate any pass-through payments, where A==>B and B==>C, and the amount of
		//A-B is less than the amount of B-C. These should resolve to A==>C and B==>C.
		//Only do these for diners paying cash, since it adds an extra payee.
		for(Diner d : diners) {
			//loop through all diners paying with cash
			if(d.defaultPaymentType == PaymentType.CASH) {
				//look through payments out not to the restaurant
				ArrayList<Payment> paymentsQueue = new ArrayList<Payment>();
				paymentsQueue.addAll(d.paymentsOut);
				while(paymentsQueue.size() > 0) {
					Payment p1 = paymentsQueue.remove(0);
					if(p1.payee != restaurant) {
						//for outgoing payments to a diner B, look at B's payments out
						for(Payment p2 : p1.payee.paymentsOut) {
							if(p2.amount >= p1.amount) {
								//if the drop condition is met, reroute payments
								p2.amount -= p1.amount;
								p1.payee.paymentsIn.remove(p1);
								d.paymentsOut.remove(p1);
								Payment p = new Payment(p1.amount, getBestPaymentType(d,p2.payee), d, p2.payee);
								addPayment(p);
								paymentsQueue.add(p); //make sure we check the new payment as well
								break; //break to outer loop
							}
						}
					}
					//break to here, continue through payments out queue
				}
			}
		}

		//Finally, remove any zero payments still present in the bill, and recalc diner totals
		ArrayList<Payment> removePayments = new ArrayList<Payment>();
		for(Diner d : diners) {
			for(Payment p : d.paymentsOut) {
				if(Math.abs(p.amount) < Grouptuity.PRECISION_ERROR) {
					removePayments.add(p);
					p.payee.paymentsIn.remove(p);
				}
			}
			d.paymentsOut.removeAll(removePayments);
		}
		
		printAll("FINAL");
		
		//Using the completed payment set, remove payments corresponding to any fresh Venmo payments that have gone through
		for(Payment vp : venmoTransactions) {
			Payment removePayment = null;
			for(Payment p : vp.payee.paymentsIn) {
				if(p.type == PaymentType.VENMO && p.payer == vp.payer && p.payee == vp.payee && Math.abs(p.amount - vp.amount) < Grouptuity.PRECISION_ERROR) {
					removePayment = p;
					break;
				}
			}
			if(removePayment != null) {
				removePayment.payer.paymentsOut.remove(removePayment);
				removePayment.payee.paymentsIn.remove(removePayment);
			}
		}
	
	}
	private PaymentType getBestPaymentType(Diner payer, Diner payee) {
		if(payer.hasVenmo() && payee.hasVenmo()) {
			if(payer.defaultPaymentType == PaymentType.IOU_EMAIL && payer.defaultPayee==payee)
				return PaymentType.IOU_EMAIL;
			else
				return PaymentType.VENMO;
		}
		else if(payee==restaurant) {
			if(payer.defaultPaymentType==PaymentType.CREDIT)
				return PaymentType.CREDIT;
			else
				return PaymentType.CASH;
		}
		else {
			return PaymentType.IOU_EMAIL;
		}
	}

	private void printAll() {printAll("");}
	private void printAll(String title)
	{
		if(!debug)return;
			
		Grouptuity.log("---------"+title+"--------------------");
		for(Diner d : diners) {
			Grouptuity.log(d.name+":");
			for(Payment p : d.paymentsIn){Grouptuity.log("\t"+getPaymentString(p));}
			for(Payment p : d.paymentsOut){Grouptuity.log("\t"+getPaymentString(p));}
		}
		Grouptuity.log(restaurant.name+":");
		for(Payment p : restaurant.paymentsIn){Grouptuity.log("\t"+getPaymentString(p));}
		for(Payment p : restaurant.paymentsOut){Grouptuity.log("\t"+getPaymentString(p));}
		Grouptuity.log("-----------------------------");
	}
	public static String getPaymentString(Payment p){return p.payer.name+" ["+p.payer.hashCode()+"] ==>"+p.payee.name+" ("+p.amount+") ["+p.payee.hashCode()+"]";}
	private boolean BfsGetPathToDiner(Diner targetDiner, Diner startDiner, HashMap<Diner,Boolean> seenDinersFromAbove, HashMap<Payment,Boolean> cycle, Payment targetPayment, int level)
	{
		if(debug)
		Grouptuity.log("Searching for cycles from "+targetDiner.name+", currently looking at: "+startDiner.name+" (level "+level+")");
		
		//create a new object storing diners we've seen so far in this branch, to be added to and passed if we recurse deeper
		HashMap<Diner, Boolean> seenDiners = new HashMap<Diner, Boolean>();
		seenDiners.putAll(seenDinersFromAbove);
		
		seenDiners.put(startDiner,true);
		if(level==0) {
			seenDiners.put(targetDiner,true);
			cycle.put(targetPayment, true);
		}
		//on the first recursion level we don't do this check, because A=>B=>A payments may exist and are not of concern yet
		if(level>0)
		{
			//iterate through the people the start diner is paying
			//first look for the target diner in the start diner's payments in and out
			for(Payment p : startDiner.paymentsOut) 
			{
				if(debug) Grouptuity.log(level+"\tlooking through payments out");
				if(p.payee == targetDiner)
				{
					cycle.put(p,true); //forward direction
					if(debug) Grouptuity.log("Found a cycle!");
					for(Payment pp : cycle.keySet()){if(debug)Grouptuity.log(pp.payer.name+"==>"+pp.payee.name+" ("+pp.amount+")");}
					boolean result = removeCycle(cycle, targetPayment); //returns whether resolved
					cycle.remove(p);
					printAll();
					return result;
				}
			}
			for(Payment p : startDiner.paymentsIn)
			{
				if(debug) Grouptuity.log(level+"\tlooking through payments in");
				if(p.payer == targetDiner)
				{
					cycle.put(p,false); //reverse direction
					if(debug) Grouptuity.log("Found a cycle!");
					for(Payment pp : cycle.keySet()){if(debug)Grouptuity.log(pp.payer.name+"==>"+pp.payee.name+" ("+pp.amount+")");}
					boolean result = removeCycle(cycle, targetPayment); //returns whether resolved
					cycle.remove(p);
					printAll();
					return result;
				}
			}
		}
		if(debug) Grouptuity.log(level+"\tDiner not found in payments. Going deeper.");
		//then continue the search using the payers and payees as the start diners,
		//checking only the ones we haven't seen before
		ArrayList<Payment> payments = new ArrayList<Payment>();
		payments.addAll(startDiner.paymentsOut);
		for(Payment p : payments)
		{
			if(!seenDiners.containsKey(p.payee))
			{
				if(debug) Grouptuity.log(level+"\titerating into payments out ("+getPaymentString(p)+")");
				cycle.put(p,true); //forward direction
				if(BfsGetPathToDiner(targetDiner, p.payee, seenDiners, cycle, targetPayment, level+1)) {
					if(debug) Grouptuity.log(level+"\tResolved.");
					return true; //resolved
				}
				else
					cycle.remove(p);
			}
		}
		payments.clear();
		payments.addAll(startDiner.paymentsIn);
		for(Payment p : payments)
		{
			if(!seenDiners.containsKey(p.payer))
			{
				if(debug) Grouptuity.log(level+"\titerating into payments in ("+getPaymentString(p)+")");
				cycle.put(p,false); //reverse direction
				if(BfsGetPathToDiner(targetDiner, p.payer, seenDiners, cycle, targetPayment, level+1)) {
					if(debug) Grouptuity.log(level+"\tResolved.");
					return true; //resolved
				}
				else
					cycle.remove(p);
			}
		}
		if(debug)
		Grouptuity.log(level+"\tDiner not found in any branch. Going back up.");
		return false; //not resolved
	}
	private boolean removeCycle(HashMap<Payment,Boolean> cycle, Payment targetPayment) {
		boolean resolved = false;
		
		//find the minimum payment amount in the cycle (fwd direction only)
		double minPaymentAmount = Double.MAX_VALUE;
		for(Payment p : cycle.keySet())
		{
			//only count payments in the forward direction
			if(p.amount < minPaymentAmount && cycle.get(p))
				minPaymentAmount = p.amount;
		}

		if(debug) Grouptuity.log("min payment amt: "+minPaymentAmount);
		//decrease the amount of the payments in the cycle by that amount
		for(Payment p : cycle.keySet())
		{
			//decrement when in forward direction, increment when in reverse
			p.amount -= minPaymentAmount * (cycle.get(p) ? 1 : -1);
			//if we've removed the target payment, set resolved to true
			if(Math.abs(targetPayment.amount) < Grouptuity.PRECISION_ERROR)
				resolved = true;
			//if the payment is now zero and it's not preferred, remove it
			if(Math.abs(p.amount) < Grouptuity.PRECISION_ERROR && !paymentIsPreferred(p))
			{
				if(debug) Grouptuity.log("Dropped: "+p.payer.name+"==>"+p.payee.name+" ($"+p.amount+")");
				p.payee.paymentsIn.remove(p);
				p.payer.paymentsOut.remove(p);
			}
		}
		return resolved;
	}
	private boolean paymentIsPreferred(Payment p)
	{
		//returns whether the payment is the payer's preferred payment, as selected by the user
		switch(p.payer.defaultPaymentType)
		{
			case CREDIT: 	return (p.payee == restaurant);
			case IOU_EMAIL:	return (p.payee == p.payer.defaultPayee);
			case VENMO:		return (p.payee == p.payer.defaultPayee);
			default:		return false;
		}
	}
	private void removePayeesInPaymentGraph(Diner startDiner, Diner ignoreDiner)
	{
		if(debug) Grouptuity.log("Removing payees for "+startDiner.name+(ignoreDiner==null?"":", ignoring "+ignoreDiner.name));
		ArrayList<Payment> tempPayments = new ArrayList<Payment>();
		for(Payment p : startDiner.paymentsOut) tempPayments.add(p);
		for(Payment targetPayment : tempPayments)
		{
			//only attempt to remove the payment if it's not going to the ignored diner and
			//it's not a preferred payment
			if(debug) Grouptuity.log("Considering removal of: "+getPaymentString(targetPayment));
			if(targetPayment.payee != ignoreDiner && !paymentIsPreferred(targetPayment))
			{
					printAll();
					if(debug) Grouptuity.log("Attempting to remove: "+getPaymentString(targetPayment));
					BfsGetPathToDiner(startDiner,targetPayment.payee,new HashMap<Diner, Boolean>(),new HashMap<Payment, Boolean>(),targetPayment,0);
			}
		}
	}

	private static boolean paymentExists(Diner payer, Diner payee)
	{
		for(Payment p: payer.paymentsOut)
		{
			if(p.payee==payee)
				return true;
		}
		return false;
	}
	private static void addPayment(double amount, PaymentType type, Diner payer, Diner payee)
	{
		Payment p = new Payment(amount, type, payer, payee);
		payer.addPaymentOut(p);
		payee.addPaymentIn(p);
	}
	private static void addPayment(Payment p)
	{
		p.payer.addPaymentOut(p);
		p.payee.addPaymentIn(p);
	}
	protected void completePayment(){paymentComplete = true;}
	protected boolean isPaymentComplete(){return paymentComplete;}
	public boolean canPayByDeferredMethod(Diner payer, Diner payee)
	{
		if(!payee.defaultPaymentType.deferred)
			return true;
		boolean[] used = new boolean[diners.size()];
		used[diners.indexOf(payer)] = true;
		used[diners.indexOf(payee)] = true;

		if(payee.defaultPayee!=null)
			payee = payee.defaultPayee;
		else if(hostDiner!=null && payee!=hostDiner)
			payee = hostDiner;
		else if(diners.indexOf(payee)!=0)
			payee = diners.get(0);
		else
			return false;

		while(!used[diners.indexOf(payee)])
		{
			if(!payee.defaultPaymentType.deferred)
				return true;
			else
			{
				used[diners.indexOf(payee)] = true;
				if(payee.defaultPayee!=null)
					payee = payee.defaultPayee;
				else if(hostDiner!=null && payee!=hostDiner)
					payee = hostDiner;
				else if(diners.indexOf(payee)!=0)
					payee = diners.get(0);
				else
					return false;
			}
		}
		return false;
	}

	public void resetBillables()
	{
		for(Diner diner: diners)
			diner.reset();
		items.clear();
		debts.clear();
		discounts.clear();
		dirtyCalculation = true;
	}

	public void addDiner(Diner diner){diners.add(diner);}
	public void removeDiner(Diner diner)
	{
		diners.remove(diner);
		for(Payment p : diner.paymentsIn)
			p.payer.paymentsOut.remove(p);
		for(Payment p : diner.paymentsOut)
			p.payee.paymentsIn.remove(p);
		for(Diner d : diners)
		{
			if(d.defaultPayee == diner)
			{
				//TODO reset the payment type to cash, since the payee is now gone, maybe do user or global default value?  
				d.defaultPayee = null;
				d.defaultPaymentType = PaymentType.CASH;
			}
		}
		for(Item item: diner.items)
		{
			item.removePayer(diner);
			if(item.payers.size() == 0)
				removeItem(item);
		}
		for(Debt debt: diner.debts)
		{
			debt.removePayee(diner);
			debt.removePayer(diner);
			if(debt.payees.size() == 0 || debt.payers.size() == 0)
				removeDebt(debt);
		}
		for(Discount discount: diner.discounts)
		{
			discount.removePayee(diner);
			discount.removePayer(diner);
			if(discount.payees.size() == 0)
				removeDiscount(discount);
		}
		ArrayList<Payment> removals = new ArrayList<Payment>();
		for(Payment p: venmoTransactions)
		{
			if(p.payer==diner || p.payee==diner)
				removals.add(p);
		}
		venmoTransactions.removeAll(removals);
		removals.clear();
		for(Payment p: staleVenmoTransactions)
		{
			if(p.payer==diner || p.payee==diner)
				removals.add(p);
		}
		staleVenmoTransactions.removeAll(removals);
		dirtyCalculation = true;
	}

	public void addItem(Item item)
	{
		items.add(item);
		for(Diner d: item.payers)
			d.addItem(item);
		dirtyCalculation = true;
	}
	public void removeItem(Item item)
	{
		items.remove(item);
		for(Diner d: item.payers)
			d.removeItem(item);
		dirtyCalculation = true;
	}
	public void editItem(Item item, double d, BillableType type)
	{
		item.value = d;
		item.billableType = type;
		dirtyCalculation = true;
	}
	public void addDebt(Debt debt)
	{
		debts.add(debt);
		for(Diner d: debt.payers)
			d.addDebt(debt);
		for(Diner d: debt.payees)
			d.addDebt(debt);
		dirtyCalculation = true;
	}
	public void removeDebt(Debt debt)
	{
		debts.remove(debt);
		for(Diner d: debt.payers)
			d.removeDebt(debt);
		for(Diner d: debt.payees)
			d.removeDebt(debt);
		Discount discount = debt.linkedDiscount;
		if(discount!=null)
		{
			discounts.remove(discount);
			for(Diner d: discount.payers)
				d.removeDiscount(discount);
			for(Diner d: discount.payees)
				d.removeDiscount(discount);
		}
		dirtyCalculation = true;
	}
	public void editDebt(Debt debt, double d)
	{
		debt.value = d;
		dirtyCalculation = true;
	}
	public void addDiscount(Discount discount)
	{
		discounts.add(discount);
		for(Diner d: discount.payers)
			d.addDiscount(discount);
		for(Diner d: discount.payees)
			d.addDiscount(discount);
		dirtyCalculation = true;
	}
	public void removeDiscount(Discount discount)
	{
		discounts.remove(discount);
		for(Diner d: discount.payers)
			d.removeDiscount(discount);
		for(Diner d: discount.payees)
			d.removeDiscount(discount);
		Debt debt = discount.linkedDebt;
		if(debt!=null)
		{
			debts.remove(debt);
			for(Diner d: debt.payers)
				d.removeDebt(debt);
			for(Diner d: debt.payees)
				d.removeDebt(debt);
		}
		dirtyCalculation = true;
	}
	public void editDiscount(Discount discount, double v, double c, double p, BillableType type, Discount.DiscountMethod tax, Discount.DiscountMethod tip)
	{
		discount.value = v;
		discount.cost = c;
		discount.percent = p;
		discount.billableType = type;
		discount.taxable = tax;
		discount.tipable = tip;
		dirtyCalculation = true;
	}

	public void addItemAssignment(Item item, Diner diner){addItemAssignment(item, diner, 1.0);}
	public void addItemAssignment(Item item, Diner diner, double weight)
	{
		item.addPayer(diner,weight);
		diner.addItem(item);
		dirtyCalculation = true;
	}
	public boolean removeItemAssignment(Item item, Diner diner)
	{
		item.removePayer(diner);
		diner.removeItem(item);
		dirtyCalculation = true;
		if(item.payers.isEmpty())
			return true;
		else
			return false;
	}
	public void addDebtCreditAssignment(Debt debt, Diner diner){addDebtCreditAssignment(debt, diner, 1.0);}
	public void addDebtDebitAssignment(Debt debt, Diner diner){addDebtDebitAssignment(debt, diner, 1.0);}
	public void addDebtCreditAssignment(Debt debt, Diner diner, double weight){debt.addPayee(diner,weight);diner.addDebt(debt);dirtyCalculation = true;}
	public void addDebtDebitAssignment(Debt debt, Diner diner, double weight){debt.addPayer(diner,weight);diner.addDebt(debt);dirtyCalculation = true;}
	public boolean removeDebtCreditAssignment(Debt debt, Diner diner){debt.removePayee(diner);diner.removeDebt(debt);dirtyCalculation = true;if(debt.payees.isEmpty())return true;else return false;}
	public boolean removeDebtDebitAssignment(Debt debt, Diner diner){debt.removePayer(diner);diner.removeDebt(debt);dirtyCalculation = true;if(debt.payers.isEmpty())return true;else return false;}
	public void addDiscountCreditAssignment(Discount discount, Diner diner){addDiscountCreditAssignment(discount, diner, 1.0);}
	public void addDiscountDebitAssignment(Discount discount, Diner diner){addDiscountDebitAssignment(discount, diner, 1.0);}
	public void addDiscountCreditAssignment(Discount discount, Diner diner, double weight){discount.addPayee(diner,weight);diner.addDiscount(discount);dirtyCalculation = true;}
	public void addDiscountDebitAssignment(Discount discount, Diner diner, double weight){discount.addPayer(diner,weight);diner.addDiscount(discount);dirtyCalculation = true;}
	public boolean removeDiscountCreditAssignment(Discount discount, Diner diner){discount.removePayee(diner);diner.removeDiscount(discount);dirtyCalculation = true;if(discount.payees.isEmpty())return true;else return false;}
	public boolean removeDiscountDebitAssignment(Discount discount, Diner diner){discount.removePayer(diner);diner.removeDiscount(discount);dirtyCalculation = true;if(discount.payers.isEmpty())return true;else return false;}
	public void recordVenmoTransaction(double amount, Diner payer, Diner payee){venmoTransactions.add(new Payment(amount, PaymentType.VENMO, payer, payee));dirtyPayments=true;}

	public int getOverride(){return override.ordinal();}
	public int getNumPendingVenmoPayments()
	{
		int numPayments = 0;
		Diner self = Grouptuity.getSelf();
		if(self==null)
			return 0;
		for(Payment p: self.paymentsIn)
		{
			if(p.type == PaymentType.VENMO)
				numPayments++;
		}
		for(Payment p: self.paymentsOut)
		{
			if(p.type == PaymentType.VENMO)
				numPayments++;
		}
		return numPayments;
	}
	public Payment[] getPendingVenmoPayments()
	{
		int numPayments = getNumPendingVenmoPayments();
		if(numPayments==0)
			return null;
		Payment[] pendingPayments = new Payment[numPayments];
		int i=0;
		Diner self = Grouptuity.getSelf();
		for(Payment p: self.paymentsIn)
		{
			if(p.type == PaymentType.VENMO)
				pendingPayments[i++] = p;
		}
		for(Payment p: self.paymentsOut)
		{
			if(p.type == PaymentType.VENMO)
				pendingPayments[i++] = p;
		}
		return pendingPayments;
	}
	public ArrayList<Payment> getAllVenmoTransactions() {
		ArrayList<Payment> all = new ArrayList<Payment>();
		all.addAll(venmoTransactions);
		all.addAll(staleVenmoTransactions);
		return all;
	}
	public double getRawSubtotal(){return subtotal;}
	public double getBoundedDiscountedSubtotal(){return boundedDiscountedSubtotal;}
	public double getDiscountedSubtotal(){return discountedSubTotal;}
	public double getTaxableSubtotal(){return taxableSubTotal;}
	public double getTipableSubtotal(){return tipableSubTotal;}
	public double getAfterTaxSubtotal(){return afterTaxSubtotal;}
	public double getTotal(){return total;}
	public double getTaxPercent(){return taxPercent;}
	public double getTaxValue(){return taxValue;}
	public double getTipPercent(){return tipPercent;}
	public double getTipValue(){return tipValue;}
	public void setTaxPercent(double percent){taxPercent = percent;dirtyCalculation = true;}
	public void setTaxValue(double value)
	{
		switch(override)
		{
			case NONE:	setTaxPercent(value/taxableSubTotal*100.0);break;
			case SUB:	setTaxPercent(value/subtotal*100.0);break;
			case AFTER:	setTaxPercent(value/(afterTaxSubtotal-value)*100.0);break;
			case TOTAL:	if(Grouptuity.TIP_THE_TAX.getValue())
							setTaxPercent(100.0*value*(1+tipPercent/100.0)/(total-(1+tipPercent/100.0)*value));
						else
							setTaxPercent(100.0*value*(1+tipPercent/100.0)/(total-value));
						break;
		}
	}
	public void setTipPercent(double percent){tipPercent = percent;dirtyCalculation = true;}
	public void setTipValue(double value)
	{
		switch(override)
		{
			case NONE:	setTipPercent(100.0*value/((Grouptuity.TIP_THE_TAX.getValue())?(tipableSubTotal+taxValue):(tipableSubTotal)));break;
			case SUB:	
			case AFTER:	setTipPercent(100.0*value/((Grouptuity.TIP_THE_TAX.getValue())?(afterTaxSubtotal):(subtotal)));break;
			case TOTAL:	if(Grouptuity.TIP_THE_TAX.getValue())
							setTipPercent(100.0*value/(total-value));
						else
							setTipPercent(100.0*value*(1+taxPercent/100.0)/(total-value));
						break;
		}
	}
	public void overrideRawSubTotal(double value){override = Override.SUB;subtotal = value;dirtyCalculation = true;}
	public void overrideAfterTaxSubTotal(double value){override = Override.AFTER;afterTaxSubtotal = value;dirtyCalculation = true;}
	public void overrideTotal(double newTotal)
	{
		if(afterTaxSubtotal>Grouptuity.PRECISION_ERROR)
			setTipValue(newTotal-afterTaxSubtotal);
		else
		{
			override = Override.TOTAL;
			total = newTotal;
			dirtyCalculation = true;
		}
	}
	public void overrideReset()
	{
		subtotal = 0.0;
		override = Override.SUB;
		taxPercent = Grouptuity.DEFAULT_TAX.getValue();
		tipPercent = Grouptuity.DEFAULT_TIP.getValue();
		dirtyCalculation = true;
	}
	public void overrideOverride(int index){override = Override.values()[index];}

	public void writeToParcel(Parcel out, int flags)
	{
		out.writeLong(new Date().getTime());
		out.writeDouble(taxPercent);
		out.writeDouble(tipPercent);
		out.writeInt(paymentComplete?1:0);
		out.writeList(diners);
		out.writeList(items);
		out.writeList(debts);
		out.writeList(discounts);

		int counter = 0;
		for(Discount d: discounts)
			if(d.billableType==BillableType.DEAL_PERCENT_ITEM)
				counter++;
		int[] itemDiscountMappings = new int[counter];
		out.writeInt(counter);
		counter = 0;
		for(Discount d: discounts)
			if(d.billableType==BillableType.DEAL_PERCENT_ITEM)
			{
				itemDiscountMappings[counter] = items.indexOf(d.discountedItem);
				counter++;
			}
		out.writeIntArray(itemDiscountMappings);

		counter = 0;
		for(Discount d: discounts)
			if(d.billableType==BillableType.DEAL_GROUP_VOUCHER)
				counter++;
		int[] debtDiscountMappings = new int[counter];
		out.writeInt(counter);
		counter = 0;
		for(Discount d: discounts)
			if(d.billableType==BillableType.DEAL_GROUP_VOUCHER)
			{
				debtDiscountMappings[counter] = debts.indexOf(d.linkedDebt);
				counter++;
			}
		out.writeIntArray(debtDiscountMappings);

		for(Diner d: diners)
		{
			int[] dinerItemIndices = new int[d.items.size()];
			int[] dinerDebtIndices = new int[d.debts.size()];
			int[] dinerDiscountIndices = new int[d.discounts.size()];
			double[] dinerItemWeights = new double[dinerItemIndices.length];
			double[] dinerCreditDebtWeights = new double[dinerDebtIndices.length];
			double[] dinerDebitDebtWeights = new double[dinerDebtIndices.length];
			double[] dinerCreditDiscountWeights = new double[dinerDiscountIndices.length];
			double[] dinerDebitDiscountWeights = new double[dinerDiscountIndices.length];

			for(int i=0; i<dinerItemIndices.length; i++)
			{
				Item item = d.items.get(i);
				dinerItemIndices[i] = items.indexOf(item);
				dinerItemWeights[i] = item.getWeightForPayer(d);
			}
			for(int i=0; i<dinerDebtIndices.length; i++)
			{
				Debt debt = d.debts.get(i);
				dinerDebtIndices[i] = debts.indexOf(debt);
				dinerCreditDebtWeights[i] = debt.getWeightForPayee(d);
				dinerDebitDebtWeights[i] = debt.getWeightForPayer(d);
			}
			for(int i=0; i<dinerDiscountIndices.length; i++)
			{
				Discount discount = d.discounts.get(i);
				dinerDiscountIndices[i] = discounts.indexOf(discount);
				dinerCreditDiscountWeights[i] = discount.getWeightForPayee(d);
				dinerDebitDiscountWeights[i] = discount.getWeightForPayer(d);
			}
			out.writeInt(dinerItemIndices.length);
			out.writeInt(dinerDebtIndices.length);
			out.writeInt(dinerDiscountIndices.length);
			out.writeIntArray(dinerItemIndices);
			out.writeIntArray(dinerDebtIndices);
			out.writeIntArray(dinerDiscountIndices);
			out.writeDoubleArray(dinerItemWeights);
			out.writeDoubleArray(dinerCreditDebtWeights);
			out.writeDoubleArray(dinerDebitDebtWeights);
			out.writeDoubleArray(dinerCreditDiscountWeights);
			out.writeDoubleArray(dinerDebitDiscountWeights);

			out.writeInt((d.defaultPayee==null)?-1:diners.indexOf(d.defaultPayee));
		}

		out.writeInt((hostDiner==null)?-1:diners.indexOf(hostDiner));

		out.writeInt(override.ordinal());
		out.writeDouble(subtotal);
		out.writeDouble(afterTaxSubtotal);
		out.writeDouble(total);
		
		double[] venmoAmounts = new double[venmoTransactions.size()];
		int[] venmoPayeeIndex = new int[venmoAmounts.length];
		int[] venmoPayerIndex = new int[venmoAmounts.length];
		for(int i=0; i<venmoAmounts.length; i++)
		{
			venmoAmounts[i] = venmoTransactions.get(i).amount;
			venmoPayeeIndex[i] = diners.indexOf(venmoTransactions.get(i).payee);
			venmoPayerIndex[i] = diners.indexOf(venmoTransactions.get(i).payer);
		}
		out.writeInt(venmoTransactions.size());
		out.writeDoubleArray(venmoAmounts);
		out.writeIntArray(venmoPayeeIndex);
		out.writeIntArray(venmoPayerIndex);
		
		venmoAmounts = new double[staleVenmoTransactions.size()];
		venmoPayeeIndex = new int[venmoAmounts.length];
		venmoPayerIndex = new int[venmoAmounts.length];
		for(int i=0; i<venmoAmounts.length; i++)
		{
			venmoAmounts[i] = staleVenmoTransactions.get(i).amount;
			venmoPayeeIndex[i] = diners.indexOf(staleVenmoTransactions.get(i).payee);
			venmoPayerIndex[i] = diners.indexOf(staleVenmoTransactions.get(i).payer);
		}
		out.writeInt(staleVenmoTransactions.size());
		out.writeDoubleArray(venmoAmounts);
		out.writeIntArray(venmoPayeeIndex);
		out.writeIntArray(venmoPayerIndex);
	}
	public Bill(Parcel in)
	{
		dirtyCalculation = true;
		dirtyPayments = true;
		restaurant = Grouptuity.getRestaurant();
		date = in.readLong();
		taxPercent = in.readDouble();
		tipPercent = in.readDouble();
		paymentComplete = in.readInt()==1;
		in.readList((diners = new ArrayList<Diner>()),Diner.class.getClassLoader());
		in.readList((items = new ArrayList<Item>()),Item.class.getClassLoader());
		in.readList((debts = new ArrayList<Debt>()),Debt.class.getClassLoader());
		in.readList((discounts = new ArrayList<Discount>()),Discount.class.getClassLoader());
		venmoTransactions = new ArrayList<Payment>();
		staleVenmoTransactions = new ArrayList<Payment>();
		
		try
		{
			int[] itemDiscountMappings = new int[in.readInt()];
			in.readIntArray(itemDiscountMappings);
			int counter = 0;
			for(Discount d: discounts)
			{
				if(d.billableType==BillableType.DEAL_PERCENT_ITEM)
				{
					d.discountedItem = items.get(itemDiscountMappings[counter]);
					counter++;
				}
			}

			int[] debtDiscountMappings = new int[in.readInt()];
			in.readIntArray(debtDiscountMappings);
			counter = 0;
			for(Discount discount: discounts)
			{
				if(discount.billableType==BillableType.DEAL_GROUP_VOUCHER)
				{
					Debt debt = debts.get(debtDiscountMappings[counter]);
					discount.linkedDebt = debt;
					debt.linkedDiscount = discount;
					counter++;
				}
			}

		for(Diner d: diners)
		{
			int[] dinerItemIndices = new int[in.readInt()];
			int[] dinerDebtIndices = new int[in.readInt()];
			int[] dinerDiscountIndices = new int[in.readInt()];
			double[] dinerItemWeights = new double[dinerItemIndices.length];
			double[] dinerCreditDebtWeights = new double[dinerDebtIndices.length];
			double[] dinerDebitDebtWeights = new double[dinerDebtIndices.length];
			double[] dinerCreditDiscountWeights = new double[dinerDiscountIndices.length];
			double[] dinerDebitDiscountWeights = new double[dinerDiscountIndices.length];
			in.readIntArray(dinerItemIndices);
			in.readIntArray(dinerDebtIndices);
			in.readIntArray(dinerDiscountIndices);
			in.readDoubleArray(dinerItemWeights);
			in.readDoubleArray(dinerCreditDebtWeights);
			in.readDoubleArray(dinerDebitDebtWeights);
			in.readDoubleArray(dinerCreditDiscountWeights);
			in.readDoubleArray(dinerDebitDiscountWeights);
			for(int i=0; i<dinerItemIndices.length; i++)
				addItemAssignment(items.get(dinerItemIndices[i]),d,dinerItemWeights[i]);
			for(int i=0; i<dinerDebtIndices.length; i++)
			{
				if(dinerCreditDebtWeights[i]>0)
					addDebtCreditAssignment(debts.get(dinerDebtIndices[i]),d,dinerCreditDebtWeights[i]);
				if(dinerDebitDebtWeights[i]>0)
					addDebtDebitAssignment(debts.get(dinerDebtIndices[i]),d,dinerDebitDebtWeights[i]);
			}
			for(int i=0; i<dinerDiscountIndices.length; i++)
			{
				if(dinerCreditDiscountWeights[i]>0)
					addDiscountCreditAssignment(discounts.get(dinerDiscountIndices[i]),d,dinerCreditDiscountWeights[i]);
				if(dinerDebitDiscountWeights[i]>0)
					addDiscountDebitAssignment(discounts.get(dinerDiscountIndices[i]),d,dinerDebitDiscountWeights[i]);
			}

			int index = in.readInt();
			if(index!=-1)
				d.defaultPayee = diners.get(index);
		}

		int hostIndex = in.readInt();
		if(hostIndex!=-1)
			hostDiner = diners.get(hostIndex);

		override = Override.values()[in.readInt()];
		subtotal = in.readDouble();
		afterTaxSubtotal = in.readDouble();
		total = in.readDouble();
		
		double[] venmoAmounts = new double[in.readInt()];
		int[] venmoPayeeIndex = new int[venmoAmounts.length];
		int[] venmoPayerIndex = new int[venmoAmounts.length];
		in.readDoubleArray(venmoAmounts);
		in.readIntArray(venmoPayeeIndex);
		in.readIntArray(venmoPayerIndex);
		for(int i=0; i<venmoAmounts.length; i++)
			venmoTransactions.add(new Payment(venmoAmounts[i],Grouptuity.PaymentType.VENMO,diners.get(venmoPayerIndex[i]),diners.get(venmoPayeeIndex[i])));
		
		venmoAmounts = new double[in.readInt()];
		venmoPayeeIndex = new int[venmoAmounts.length];
		venmoPayerIndex = new int[venmoAmounts.length];
		in.readDoubleArray(venmoAmounts);
		in.readIntArray(venmoPayeeIndex);
		in.readIntArray(venmoPayerIndex);
		for(int i=0; i<venmoAmounts.length; i++)
			staleVenmoTransactions.add(new Payment(venmoAmounts[i],Grouptuity.PaymentType.VENMO,diners.get(venmoPayerIndex[i]),diners.get(venmoPayeeIndex[i])));
		
		}catch(Exception e){Grouptuity.log(e);}
	}
	public int describeContents(){return 0;}
	final public static Parcelable.Creator<Bill> CREATOR = new Parcelable.Creator<Bill>(){public Bill createFromParcel(Parcel in){return new Bill(in);}public Bill[] newArray(int size){return new Bill[size];}};
	
}