package com.grouptuity.billcalculation;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.*;

import com.grouptuity.*;
import com.grouptuity.Grouptuity.*;
import com.grouptuity.billreview.BillReviewActivity;
import com.grouptuity.model.*;
import com.grouptuity.model.Discount.*;
import com.grouptuity.view.*;

public class BillCalculationActivity extends ActivityTemplate<BillCalculationModel>
{
	private SlidingBarBillReport billReport;
	private BillCalculationSlidingBar bottomBar;
	private BillCalculationPanel calculationPanel;
	private NumberPad numberPad;

	protected void createActivity(Bundle bundle)
	{
		model = new BillCalculationModel(this,bundle);
		menuInt = R.menu.bill_calculation_menu;
		rootLayout.setBackgroundColor(Grouptuity.backgroundColor);

		titleBar = new TitleBar(this,"Tax and Tip",TitleBar.BACK_AND_MENU);
		mainLinearLayout.addView(titleBar,Grouptuity.fillWrapLP());

		billReport = new SlidingBarBillReport(this, new SlidingBarBillReport.SlidingBarBillReportController()
		{
			public void onItemValueEditClick(Item itemToEdit){model.currentItem = itemToEdit;model.setNewState(BillCalculationModel.EDIT_BILL_ITEM_VALUE);}
			public void onItemDeleteClick(Item itemToDelete)
			{
				Grouptuity.getBill().removeItem(itemToDelete);
				showToastMessage("Item Removed");
				refreshActivity();
			}
			public void onItemPayerListClick(Item itemToEdit){}
			public void onDebtValueEditClick(Debt debtToEdit){model.currentDebt = debtToEdit;model.setNewState(BillCalculationModel.EDIT_BILL_DEBT_VALUE);}
			public void onDebtDeleteClick(Debt debtToDelete)
			{
				Grouptuity.getBill().removeDebt(debtToDelete);
				showToastMessage("Debt Removed");
				refreshActivity();
			}
			public void onDebtPayerListClick(Debt debtToEdit){}
			public void onDebtPayeeListClick(Debt debtToEdit){}
			public void onDiscountValueEditClick(Discount discountToEdit){model.currentDiscount = discountToEdit;model.setNewState(BillCalculationModel.EDIT_BILL_DEAL_VALUE);}
			public void onDiscountCostEditClick(Discount discountToEdit){model.currentDiscount = discountToEdit;model.setNewState(BillCalculationModel.EDIT_BILL_DEAL_COST);}
			public void onDiscountDeleteClick(Discount discountToDelete)
			{
				Grouptuity.getBill().removeDiscount(discountToDelete);
				showToastMessage("Discount Removed");
				refreshActivity();
			}
			public void onDiscountPayerListClick(Discount discountToEdit){}
			public void onDiscountPayeeListClick(Discount discountToEdit){}
		});

		bottomBar = new BillCalculationSlidingBar(this,billReport);
		bottomBar.setController(new SlidingBar.SlidingBarController()
		{
			public void onMotionStarted(){}
			public void onOpened(){model.setNewState(BillCalculationModel.VIEWING_BILL);}
			public void onClosed(){model.revertPastFirst(BillCalculationModel.VIEWING_BILL);}
			public void onDisabledButtonClick(String buttonName){}
			public void onButtonClick(String name)
			{
				switch(BillCalculationSlidingBar.Button.getButton(name))
				{
					case VIEW_BILL:				bottomBar.open();break;
					case RETURN_TO_CALCULATION:	bottomBar.close();break;
					case FINISH:				advanceActivity();break;
				}
				refreshActivity();
			}
		});

		numberPad = new NumberPad(this);
		numberPad.setController(new NumberPad.NumberPadController()
		{
			public void onConfirm(double returnValue, BillableType billableType, boolean taxM, boolean tipM)
			{
				switch(model.getState())
				{
					case BillCalculationModel.EDIT_TAX_PERCENT:			model.setTaxPercent(returnValue);model.revertToState(BillCalculationModel.DEFAULT);break;
					case BillCalculationModel.EDIT_TIP_PERCENT:			model.setTipPercent(returnValue);model.revertToState(BillCalculationModel.DEFAULT);break;
					case BillCalculationModel.EDIT_TAX_VALUE:			model.setTaxValue(returnValue);model.revertToState(BillCalculationModel.DEFAULT);break;
					case BillCalculationModel.EDIT_TIP_VALUE:			model.setTipValue(returnValue);model.revertToState(BillCalculationModel.DEFAULT);break;
					case BillCalculationModel.EDIT_RAW_SUBTOTAL:		model.overrideRawSubtotal(returnValue);model.revertToState(BillCalculationModel.DEFAULT);break;
					case BillCalculationModel.EDIT_AFTER_TAX_SUBTOTAL:	model.overrideAfterTaxSubtotal(returnValue);model.revertToState(BillCalculationModel.DEFAULT);break;
					case BillCalculationModel.EDIT_BILL_ITEM_VALUE:		model.editItem(model.currentItem,returnValue,billableType);
																		showToastMessage("Edited Item Value: "+Grouptuity.formatNumber(NumberFormat.CURRENCY,returnValue));
																		model.revertToState(BillCalculationModel.VIEWING_BILL);
																		break;
					case BillCalculationModel.EDIT_BILL_DEBT_VALUE:		model.editDebt(model.currentDebt,returnValue);
																		showToastMessage("Edited Debt Amount: "+Grouptuity.formatNumber(NumberFormat.CURRENCY,returnValue));
																		model.revertToState(BillCalculationModel.VIEWING_BILL);
																		break;
					case BillCalculationModel.EDIT_BILL_DEAL_COST:		Discount d = model.currentDiscount;
																		DiscountMethod taxMethod, tipMethod;
																		if(taxM)
																		taxMethod = DiscountMethod.ON_COST;
																		else if(d.taxable == DiscountMethod.ON_COST)
																			taxMethod = DiscountMethod.NONE;
																		else
																			taxMethod = d.taxable;
																		if(tipM)
																			tipMethod = DiscountMethod.ON_COST;
																		else if(d.tipable == DiscountMethod.ON_COST)
																			tipMethod = DiscountMethod.NONE;
																		else
																			tipMethod = d.tipable;
																		model.editDiscount(d, d.value, returnValue, d.percent, d.billableType, taxMethod, tipMethod);
																		showToastMessage("Edited Discount Cost: "+Grouptuity.formatNumber(NumberFormat.CURRENCY,returnValue));
																		model.revertToState(BillCalculationModel.VIEWING_BILL);
																		break;
					case BillCalculationModel.EDIT_BILL_DEAL_VALUE:		d = model.currentDiscount;
																		if(taxM)
																			taxMethod = DiscountMethod.ON_VALUE;
																		else if(d.taxable == DiscountMethod.ON_VALUE)
																			taxMethod = DiscountMethod.NONE;
																		else
																			taxMethod = d.taxable;
																		if(tipM)
																			tipMethod = DiscountMethod.ON_VALUE;
																		else if(d.tipable == DiscountMethod.ON_VALUE)
																			tipMethod = DiscountMethod.NONE;
																		else
																			tipMethod = d.tipable;
																		switch(d.billableType)
																		{
																			case DEAL_PERCENT_ITEM:	model.editDiscount(d, d.value, d.cost, returnValue, d.billableType, taxMethod, taxMethod);
																									showToastMessage("Edited Discount Percent: "+Grouptuity.formatNumber(NumberFormat.TAX_PERCENT,returnValue));
																									break;
																			case DEAL_PERCENT_BILL:	model.editDiscount(d, d.value, d.cost, returnValue, d.billableType, taxMethod, tipMethod);
																									showToastMessage("Edited Discount Percent: "+Grouptuity.formatNumber(NumberFormat.TAX_PERCENT,returnValue));
																									break;
																			case DEAL_FIXED:		model.editDiscount(d, returnValue, d.cost, d.percent, d.billableType, taxMethod, tipMethod);
																									showToastMessage("Edited Discount Value: "+Grouptuity.formatNumber(NumberFormat.CURRENCY,returnValue));
																									break;
																			case DEAL_GROUP_VOUCHER:model.editDiscount(d, returnValue, d.cost, d.percent, d.billableType, taxMethod, tipMethod);
																									showToastMessage("Edited Discount Value: "+Grouptuity.formatNumber(NumberFormat.CURRENCY,returnValue));
																									break;
																			default: break;
																		}
																		model.revertToState(BillCalculationModel.VIEWING_BILL);
																		break;
				}
			}
			public void onCancel()
			{
				switch(model.getState())
				{
					case BillCalculationModel.EDIT_BILL_ITEM_VALUE:		showToastMessage("Item Edit Canceled");model.revertToState(BillCalculationModel.VIEWING_BILL);break;
					case BillCalculationModel.EDIT_BILL_DEBT_VALUE:		showToastMessage("Debt Edit Canceled");model.revertToState(BillCalculationModel.VIEWING_BILL);break;
					case BillCalculationModel.EDIT_BILL_DEAL_COST:
					case BillCalculationModel.EDIT_BILL_DEAL_VALUE:		showToastMessage("Discount Edit Canceled");model.revertToState(BillCalculationModel.VIEWING_BILL);break;
					default:											model.revertToState(BillCalculationModel.DEFAULT);
				}
			}
		});

		calculationPanel = new BillCalculationPanel(this);
		calculationPanel.setController(new BillCalculationPanel.BillCalculationPanelController()
		{
			public void onRawSubtotalClick(){model.setNewState(BillCalculationModel.EDIT_RAW_SUBTOTAL);}
			public void onTaxPercentClick(){model.setNewState(BillCalculationModel.EDIT_TAX_PERCENT);}
			public void onAfterTaxSubtotalClick(){model.setNewState(BillCalculationModel.EDIT_AFTER_TAX_SUBTOTAL);}
			public void onTaxValueClick(){model.setNewState(BillCalculationModel.EDIT_TAX_VALUE);}
			public void onTipPercentClick(){model.setNewState(BillCalculationModel.EDIT_TIP_PERCENT);}
			public void onTipValueClick(){model.setNewState(BillCalculationModel.EDIT_TIP_VALUE);}
		});
	}
	protected void createHorizontalLayout(){/*TODO*/mainLinearLayout.addView(bottomBar,Grouptuity.fillFillLP());}
	protected void createVerticalLayout()
	{
		final double dataEntryMargin = 0.9, buttonWtoH = 1.6;
		final int dataEntryRows = 6, dataEntryColumns = 3;
		RelativeLayout.LayoutParams dataEntryPanelLP;
		if((displayMetrics.widthPixels*dataEntryMargin*dataEntryRows/dataEntryColumns/buttonWtoH)>(displayMetrics.heightPixels*dataEntryMargin))
			dataEntryPanelLP = new RelativeLayout.LayoutParams((int)(displayMetrics.heightPixels*dataEntryMargin*dataEntryColumns/dataEntryRows*buttonWtoH),(int)(displayMetrics.heightPixels*dataEntryMargin));
		else
			dataEntryPanelLP = new RelativeLayout.LayoutParams((int)(displayMetrics.widthPixels*dataEntryMargin),(int)(displayMetrics.widthPixels*dataEntryMargin*dataEntryRows/dataEntryColumns/buttonWtoH));
		dataEntryPanelLP.addRule(RelativeLayout.CENTER_VERTICAL);
		dataEntryPanelLP.addRule(RelativeLayout.CENTER_HORIZONTAL);

		ScrollView scrollView = new ScrollView(this);
		scrollView.setFillViewport(true);
		scrollView.setLayoutParams(Grouptuity.fillWrapLP(1.0f));
		LinearLayout scrollLinearLayout = new LinearLayout(this);
		scrollLinearLayout.setOrientation(LinearLayout.VERTICAL);
		scrollView.addView(scrollLinearLayout);
		mainLinearLayout.addView(scrollView);

		scrollLinearLayout.addView(new Spacer(this),Grouptuity.fillWrapLP(1.0f));
		RelativeLayout relativeLayout = new RelativeLayout(this);
		scrollLinearLayout.addView(relativeLayout);
		scrollLinearLayout.addView(new Spacer(this),Grouptuity.fillWrapLP(1.0f));
		relativeLayout.addView(calculationPanel,dataEntryPanelLP);

		rootLayout.addView(numberPad,Grouptuity.fillFillLP());
		rootLayout.addView(bottomBar,Grouptuity.fillFillLP());

		mainLinearLayout.addView(bottomBar.getPlaceholder());
	}
	protected String createHelpString()
	{
		return	"Set the tax and tip.  Click the tax and tip values to adjust.  When you're done, "+
				"click \"Set Payments\" at the bottom right to proceed."+
				"\n\nThe \"View Bill\" button on the bottom left lets you edit the bill items.";
	}

	@Override
	protected GrouptuityPreference<Boolean> getTutorialPopupFlag(){return Grouptuity.SHOW_TUTORIAL_BILLCALC;}

	protected void processStateChange(int state)
	{
		switch(state)
		{
			case BillCalculationModel.START:					model.setNewState(BillCalculationModel.DEFAULT);break;
			case BillCalculationModel.HELP:						break;
			case BillCalculationModel.DEFAULT:					numberPad.close();break;
			case BillCalculationModel.VIEWING_BILL:				break;
			case BillCalculationModel.EDIT_TAX_PERCENT:			numberPad.open(NumberFormat.TAX_PERCENT,"Tax Percentage (was "+Grouptuity.formatNumber(NumberFormat.TAX_PERCENT,Grouptuity.getBill().getTaxPercent())+")",null);break;
			case BillCalculationModel.EDIT_TIP_PERCENT:			numberPad.open(NumberFormat.TIP_PERCENT,"Tip Percentage (was "+Grouptuity.formatNumber(NumberFormat.TIP_PERCENT,Grouptuity.getBill().getTipPercent())+")",null);break;
			case BillCalculationModel.EDIT_TAX_VALUE:			numberPad.open(NumberFormat.CURRENCY,"Tax Amount (was "+Grouptuity.formatNumber(NumberFormat.CURRENCY,Grouptuity.getBill().getTaxValue())+")",null);break;
			case BillCalculationModel.EDIT_TIP_VALUE:			numberPad.open(NumberFormat.CURRENCY,"Tip Amount (was "+Grouptuity.formatNumber(NumberFormat.CURRENCY,Grouptuity.getBill().getTipValue())+")",null);break;
			case BillCalculationModel.EDIT_BILL_ITEM_VALUE:		numberPad.open(NumberFormat.CURRENCY_NO_ZERO,"Enter the item cost (was "+Grouptuity.formatNumber(NumberFormat.CURRENCY,model.currentItem.value)+")",model.currentItem.billableType);break;
			case BillCalculationModel.EDIT_BILL_DEBT_VALUE:		numberPad.open(NumberFormat.CURRENCY_NO_ZERO,"Enter the debt amount (was "+Grouptuity.formatNumber(NumberFormat.CURRENCY,model.currentDebt.value)+")",model.currentDebt.billableType);break;
			case BillCalculationModel.EDIT_BILL_DEAL_VALUE:		boolean taxMethod, tipMethod;
																switch(model.currentDiscount.taxable)
																{
																	case ON_VALUE:	taxMethod = true;break;
																	case ON_COST:	taxMethod = false;break;
																	case NONE:		taxMethod = false;break;
																	default:		taxMethod = Grouptuity.DISCOUNT_TAX_METHOD.getValue()==DiscountMethod.ON_VALUE.ordinal();
																}
																switch(model.currentDiscount.tipable)
																{
																	case ON_VALUE:	tipMethod = true;break;
																	case ON_COST:	tipMethod = false;break;
																	case NONE:		tipMethod = false;break;
																	default:		tipMethod = Grouptuity.DISCOUNT_TIP_METHOD.getValue()==DiscountMethod.ON_VALUE.ordinal();
																}
																switch(model.currentDiscount.billableType)
																{
																	case DEAL_PERCENT_ITEM:	numberPad.open(NumberFormat.TAX_PERCENT,"Enter the percent discount",model.currentDiscount.billableType,taxMethod,tipMethod,true,true);break;
																	case DEAL_PERCENT_BILL:	numberPad.open(NumberFormat.TAX_PERCENT,"Enter the percent discount",model.currentDiscount.billableType,taxMethod,tipMethod,true,true);break;
																	case DEAL_FIXED:		numberPad.open(NumberFormat.CURRENCY_NO_ZERO,"Enter the discount value",model.currentDiscount.billableType,taxMethod,tipMethod,true,true);break;
																	case DEAL_GROUP_VOUCHER:numberPad.open(NumberFormat.CURRENCY_NO_ZERO,"Enter the discount value",model.currentDiscount.billableType,taxMethod,tipMethod,true,true);break;
																	default: break;
																}
																break;
			case BillCalculationModel.EDIT_BILL_DEAL_COST:		boolean showTaxMethod, showTipMethod;
																switch(model.currentDiscount.taxable)
																{
																	case ON_VALUE:	showTaxMethod = false;taxMethod = false;break;
																	case ON_COST:	showTaxMethod = true;taxMethod = true;break;
																	case NONE:		showTaxMethod = true;taxMethod = false;break;
																	default:		showTaxMethod = true;taxMethod = Grouptuity.DISCOUNT_TAX_METHOD.getValue()==DiscountMethod.ON_COST.ordinal();
																}
																switch(model.currentDiscount.tipable)
																{
																	case ON_VALUE:	showTipMethod = false;tipMethod = false;break;
																	case ON_COST:	showTipMethod = true;tipMethod = true;break;
																	case NONE:		showTipMethod = true;tipMethod = false;break;
																	default:		showTipMethod = true;tipMethod = Grouptuity.DISCOUNT_TIP_METHOD.getValue()==DiscountMethod.ON_COST.ordinal();
																}
																numberPad.open(NumberFormat.CURRENCY,"Enter the deal purchase cost (was "+Grouptuity.formatNumber(NumberFormat.CURRENCY,model.currentDiscount.cost)+")",model.currentDiscount.billableType,taxMethod,tipMethod,showTaxMethod,showTipMethod);
																break;
		}
	}

	protected boolean onOptionsMenuSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
			case R.id.reset:	showToastMessage("Tax and Tip Reset");
								model.setTaxPercent(Grouptuity.DEFAULT_TAX.getValue());
								model.setTipPercent(Grouptuity.DEFAULT_TIP.getValue());
								bottomBar.close();
								if(model.getState()!=BillCalculationModel.DEFAULT)
									model.revertToState(BillCalculationModel.DEFAULT);
								refreshActivity();
								return true;
		}
		return false;
	}

	@Override
	public void onBackPressed()
	{
		switch(model.getState())
		{
			case BillCalculationModel.VIEWING_BILL:				bottomBar.close();
			case BillCalculationModel.EDIT_TAX_PERCENT:
			case BillCalculationModel.EDIT_TIP_PERCENT:
			case BillCalculationModel.EDIT_TAX_VALUE:
			case BillCalculationModel.EDIT_TIP_VALUE:
			case BillCalculationModel.EDIT_RAW_SUBTOTAL:
			case BillCalculationModel.EDIT_AFTER_TAX_SUBTOTAL:	model.revertToState(BillCalculationModel.DEFAULT);break;
			case BillCalculationModel.EDIT_BILL_ITEM_VALUE:		numberPad.close();showToastMessage("Item Edit Canceled");model.revertToState(BillCalculationModel.VIEWING_BILL);break;
			case BillCalculationModel.EDIT_BILL_DEBT_VALUE:		numberPad.close();showToastMessage("Debt Edit Canceled");model.revertToState(BillCalculationModel.VIEWING_BILL);break;
			case BillCalculationModel.EDIT_BILL_DEAL_VALUE:
			case BillCalculationModel.EDIT_BILL_DEAL_COST:		numberPad.close();showToastMessage("Discount Edit Canceled");model.revertToState(BillCalculationModel.VIEWING_BILL);break;
			default:											super.onBackPressed();
		}
	}

	@Override
	protected void onAdvanceActivity(int code){startActivity(new Intent(activity,BillReviewActivity.class));}
}