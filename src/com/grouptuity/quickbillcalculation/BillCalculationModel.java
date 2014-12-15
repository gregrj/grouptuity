package com.grouptuity.quickbillcalculation;

import android.os.Bundle;
import com.grouptuity.*;

public class BillCalculationModel extends ModelTemplate
{
	//ACTIVITY STATES
	final static int DEFAULT = 0;
	final static int EDIT_TAX_PERCENT = 2;
	final static int EDIT_TAX_VALUE = 3;
	final static int EDIT_TIP_PERCENT = 4;
	final static int EDIT_TIP_VALUE = 5;
	final static int EDIT_RAW_SUBTOTAL = 6;
	final static int EDIT_AFTER_TAX_SUBTOTAL = 7;
	final static int EDIT_TOTAL = 8;
	final static int ENTER_DINER_SPLIT = 9;

	public BillCalculationModel(ActivityTemplate<?> activity, Bundle bundle){super(activity,bundle);}

	protected void saveState(Bundle bundle){}
	protected void saveToDatabase(){}

	private void saveValues()
	{
		Grouptuity.QUICK_CALC_SUBTOTAL.setValue((float)Grouptuity.getQuickCalcBill().getRawSubtotal());
		Grouptuity.QUICK_CALC_TAX.setValue((float)Grouptuity.getQuickCalcBill().getTaxPercent());
		Grouptuity.QUICK_CALC_TIP.setValue((float)Grouptuity.getQuickCalcBill().getTipPercent());
		Grouptuity.QUICK_CALC_OVERRIDE.setValue(Grouptuity.getQuickCalcBill().getOverride());
	}

	public String getTaxPercent(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.TAX_PERCENT,Grouptuity.getQuickCalcBill().getTaxPercent());}
	public String getTaxValue(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getQuickCalcBill().getTaxValue());}
	public String getTipPercent(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.TIP_PERCENT,Grouptuity.getQuickCalcBill().getTipPercent());}
	public String getTipValue(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getQuickCalcBill().getTipValue());}
	public String getRawSubtotal(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getQuickCalcBill().getRawSubtotal());}
	public String getAfterTaxSubtotal(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getQuickCalcBill().getAfterTaxSubtotal());}
	public String getTotal(){return Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getQuickCalcBill().getTotal());}
	public void setTaxPercent(double percent){Grouptuity.getQuickCalcBill().setTaxPercent(percent);saveValues();}
	public void setTaxValue(double value){Grouptuity.getQuickCalcBill().setTaxValue(value);saveValues();}
	public void setTipPercent(double percent){Grouptuity.getQuickCalcBill().setTipPercent(percent);saveValues();}
	public void setTipValue(double value){Grouptuity.getQuickCalcBill().setTipValue(value);saveValues();}
	public void overrideRawSubtotal(double value){Grouptuity.getQuickCalcBill().overrideRawSubTotal(value);saveValues();}
	public void overrideAfterTaxSubtotal(double value){Grouptuity.getQuickCalcBill().overrideAfterTaxSubTotal(value);saveValues();}
	public void overrideTotal(double value){Grouptuity.getQuickCalcBill().overrideTotal(value);saveValues();}
}