package com.grouptuity.quickbillcalculation;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.*;
import com.grouptuity.*;
import com.grouptuity.Grouptuity.*;
import com.grouptuity.view.*;

public class QuickBillCalculationActivity extends ActivityTemplate<BillCalculationModel>
{
	private RelativeLayout relativeLayout;
	private LinearLayout scrollLinearLayout;
	private BillCalculationSlidingBar bottomBar;
	private NumberPad numberPad;
	private BillCalculationPanel calculationPanel;

	protected void createActivity(Bundle bundle)
	{
		model = new BillCalculationModel(this,bundle);
		menuInt = R.menu.quick_calculation_menu;

		titleBar = new TitleBar(this,"Tax and Tip",TitleBar.BACK_AND_MENU);
		mainLinearLayout.addView(titleBar,Grouptuity.fillWrapLP());

		bottomBar = new BillCalculationSlidingBar(this);
		bottomBar.setController(new SlidingBar.SlidingBarController()
		{
			public void onMotionStarted(){}
			public void onOpened(){}
			public void onClosed(){}
			public void onDisabledButtonClick(String buttonName){}
			public void onButtonClick(String name)
			{
				switch(BillCalculationSlidingBar.Button.getButton(name))
				{
					case BACK:					terminateActivity();break;
					case RESET:					Grouptuity.getQuickCalcBill().overrideReset();
					Grouptuity.getQuickCalcBill().calculateTotal();
												break;
				}
				refreshActivity();
			}
		});

		numberPad = new NumberPad(this);
		numberPad.showItemTypeButtons(false);
		numberPad.setController(new NumberPad.NumberPadController()
		{
			public void onConfirm(double returnValue, BillableType itemType, boolean taxMethod, boolean tipMethod)
			{
				switch(model.getState())
				{
					case BillCalculationModel.EDIT_TAX_PERCENT:			model.setTaxPercent(returnValue);break;
					case BillCalculationModel.EDIT_TIP_PERCENT:			model.setTipPercent(returnValue);break;
					case BillCalculationModel.EDIT_TAX_VALUE:			model.setTaxValue(returnValue);break;
					case BillCalculationModel.EDIT_TIP_VALUE:			model.setTipValue(returnValue);break;
					case BillCalculationModel.EDIT_RAW_SUBTOTAL:		model.overrideRawSubtotal(returnValue);break;
					case BillCalculationModel.EDIT_AFTER_TAX_SUBTOTAL:	model.overrideAfterTaxSubtotal(returnValue);break;
					case BillCalculationModel.EDIT_TOTAL:				model.overrideTotal(returnValue);break;
					case BillCalculationModel.ENTER_DINER_SPLIT:		Grouptuity.getQuickCalcBill().overrideRawSubTotal(Grouptuity.getQuickCalcBill().getRawSubtotal()/returnValue);break;
				}
				model.revertToState(BillCalculationModel.DEFAULT);
			}
			public void onCancel(){model.revertToState(BillCalculationModel.DEFAULT);}
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
			public void onTotalClick()
			{
				if(Grouptuity.getQuickCalcBill().getTotal() <= Grouptuity.PRECISION_ERROR)
					model.setNewState(BillCalculationModel.EDIT_TOTAL);
				else
				{
					long roundedTotal = Math.round(Grouptuity.getQuickCalcBill().getTotal());
					String rounded = Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,roundedTotal);
					String unadusted = Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,Grouptuity.getQuickCalcBill().getTotal());

					AlertDialog.Builder newBillAlertBuilder = new AlertDialog.Builder(activity);
					newBillAlertBuilder.setCancelable(true);
					newBillAlertBuilder.setIcon(R.drawable.mainmenu_savedbills);
					newBillAlertBuilder.setPositiveButton("Edit\nTotal", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){model.setNewState(BillCalculationModel.EDIT_TOTAL);}});
					newBillAlertBuilder.setNeutralButton("Split\nTotal", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){model.setNewState(BillCalculationModel.ENTER_DINER_SPLIT);}});
					if(!rounded.equals(unadusted) && roundedTotal > Grouptuity.getQuickCalcBill().getAfterTaxSubtotal())
					{
						newBillAlertBuilder.setTitle("Edit, split, or round the total?");
						newBillAlertBuilder.setNegativeButton("Round\n("+rounded+")", new DialogInterface.OnClickListener()
						{
							public void onClick(DialogInterface dialog, int id)
							{
								Grouptuity.getQuickCalcBill().overrideTotal(Math.floor(Grouptuity.getQuickCalcBill().getTotal()));
								Grouptuity.getQuickCalcBill().calculateTotal();
								refreshActivity();
							}
						});
					}
					else
						newBillAlertBuilder.setTitle("Edit or split the total?");
					newBillAlertBuilder.show();
				}
			}
		});
	}
	protected void createHorizontalLayout()
	{
		ScrollView scrollView = new ScrollView(this);
		scrollView.setFillViewport(true);
		scrollView.setLayoutParams(Grouptuity.fillWrapLP(1.0f));
		scrollLinearLayout = new LinearLayout(this);
		scrollLinearLayout.setOrientation(LinearLayout.VERTICAL);
		mainLinearLayout.addView(scrollView);
		scrollView.addView(scrollLinearLayout);

		final double dataEntryMargin = 0.9, buttonWtoH = 1.6;
		final int dataEntryRows = 6, dataEntryColumns = 3;
		RelativeLayout.LayoutParams dataEntryPanelLP;
		if((displayMetrics.widthPixels*dataEntryMargin*dataEntryRows/dataEntryColumns/buttonWtoH)>(displayMetrics.heightPixels*dataEntryMargin))
			dataEntryPanelLP = new RelativeLayout.LayoutParams((int)(displayMetrics.heightPixels*dataEntryMargin*dataEntryColumns/dataEntryRows*buttonWtoH),(int)(displayMetrics.heightPixels*dataEntryMargin));
		else
			dataEntryPanelLP = new RelativeLayout.LayoutParams((int)(displayMetrics.widthPixels*dataEntryMargin),(int)(displayMetrics.widthPixels*dataEntryMargin*dataEntryRows/dataEntryColumns/buttonWtoH));
		dataEntryPanelLP.addRule(RelativeLayout.CENTER_VERTICAL);
		dataEntryPanelLP.addRule(RelativeLayout.CENTER_HORIZONTAL);
		rootLayout.addView(calculationPanel,dataEntryPanelLP);
		rootLayout.addView(numberPad,Grouptuity.fillFillLP());
		rootLayout.addView(bottomBar,Grouptuity.fillFillLP());
	}
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
		scrollLinearLayout = new LinearLayout(this);
		scrollLinearLayout.setOrientation(LinearLayout.VERTICAL);
		scrollView.addView(scrollLinearLayout);
		mainLinearLayout.addView(scrollView);

		scrollLinearLayout.addView(new Spacer(this),Grouptuity.fillWrapLP(1.0f));
		relativeLayout = new RelativeLayout(this);
		scrollLinearLayout.addView(relativeLayout);
		scrollLinearLayout.addView(new Spacer(this),Grouptuity.fillWrapLP(1.0f));
		relativeLayout.addView(calculationPanel,dataEntryPanelLP);

		rootLayout.addView(numberPad,Grouptuity.fillFillLP());
		rootLayout.addView(bottomBar,Grouptuity.fillFillLP());

		mainLinearLayout.addView(bottomBar.getPlaceholder());
	}
	protected String createHelpString(){return "Set the tax and tip along with the subtotal, after-tax subtotal, or total.  Click the displayed values to adjust.";}
	
	@Override
	protected GrouptuityPreference<Boolean> getTutorialPopupFlag(){return Grouptuity.SHOW_TUTORIAL_QUICKCALC;}

	protected void processStateChange(int state)
	{
		Grouptuity.getQuickCalcBill().calculateTotal();
		calculationPanel.refresh();
		switch(state)
		{
			case BillCalculationModel.START:					model.setNewState(BillCalculationModel.DEFAULT);break;
			case BillCalculationModel.HELP:						break;
			case BillCalculationModel.DEFAULT:					numberPad.close();break;
			case BillCalculationModel.EDIT_TAX_PERCENT:			numberPad.open(NumberFormat.TAX_PERCENT,"Tax Percentage (was "+Grouptuity.formatNumber(NumberFormat.TAX_PERCENT,Grouptuity.getQuickCalcBill().getTaxPercent())+")",null);break;
			case BillCalculationModel.EDIT_TIP_PERCENT:			numberPad.open(NumberFormat.TIP_PERCENT,"Tip Percentage (was "+Grouptuity.formatNumber(NumberFormat.TIP_PERCENT,Grouptuity.getQuickCalcBill().getTipPercent())+")",null);break;
			case BillCalculationModel.EDIT_TAX_VALUE:			numberPad.open(NumberFormat.CURRENCY,"Tax Amount (was "+Grouptuity.formatNumber(NumberFormat.CURRENCY,Grouptuity.getQuickCalcBill().getTaxValue())+")",null);break;
			case BillCalculationModel.EDIT_TIP_VALUE:			numberPad.open(NumberFormat.CURRENCY,"Tip Amount (was "+Grouptuity.formatNumber(NumberFormat.CURRENCY,Grouptuity.getQuickCalcBill().getTipValue())+")",null);break;
			case BillCalculationModel.EDIT_RAW_SUBTOTAL:		numberPad.open(NumberFormat.CURRENCY_NO_ZERO,"Subtotal (was "+Grouptuity.formatNumber(NumberFormat.CURRENCY,Grouptuity.getQuickCalcBill().getRawSubtotal())+")",null);break;
			case BillCalculationModel.EDIT_AFTER_TAX_SUBTOTAL:	numberPad.open(NumberFormat.CURRENCY_NO_ZERO,"After-Tax Subtotal  (was "+Grouptuity.formatNumber(NumberFormat.CURRENCY,Grouptuity.getQuickCalcBill().getAfterTaxSubtotal())+")",null);break;
			case BillCalculationModel.EDIT_TOTAL:				numberPad.setMinValue(Grouptuity.getQuickCalcBill().getAfterTaxSubtotal());
																numberPad.open(NumberFormat.CURRENCY_NO_ZERO,"Total (must exceed "+Grouptuity.formatNumber(NumberFormat.CURRENCY,Grouptuity.getQuickCalcBill().getAfterTaxSubtotal())+")",null);
																break;
			case BillCalculationModel.ENTER_DINER_SPLIT:		numberPad.setMinValue(1);
																numberPad.open(NumberFormat.INTEGER,"Number of Diners",null);break;
		}
	}

	public void onResume(){Grouptuity.getQuickCalcBill().calculateTotal();super.onResume();}

	@Override
	public void onBackPressed()
	{
		switch(model.getState())
		{
			case BillCalculationModel.START:
			case BillCalculationModel.DEFAULT:					terminateActivity();super.onBackPressed();break;
			case BillCalculationModel.EDIT_TAX_PERCENT:
			case BillCalculationModel.EDIT_TIP_PERCENT:
			case BillCalculationModel.EDIT_TAX_VALUE:
			case BillCalculationModel.EDIT_TIP_VALUE:
			case BillCalculationModel.EDIT_RAW_SUBTOTAL:
			case BillCalculationModel.EDIT_AFTER_TAX_SUBTOTAL:
			case BillCalculationModel.EDIT_TOTAL:				model.revertToState(BillCalculationModel.DEFAULT);break;
		}
	}
}