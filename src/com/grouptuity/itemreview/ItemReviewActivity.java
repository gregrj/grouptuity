package com.grouptuity.itemreview;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.LinearLayout.LayoutParams;
import com.grouptuity.*;
import com.grouptuity.model.Billable;
import com.grouptuity.model.Debt;
import com.grouptuity.model.Discount;
import com.grouptuity.model.Item;
import com.grouptuity.model.Discount.DiscountMethod;
import com.grouptuity.Grouptuity.*;
import com.grouptuity.view.DinerBillableListingAdapter;
import com.grouptuity.view.InstructionsBar;
import com.grouptuity.view.NumberPad;
import com.grouptuity.view.TitleBar;
import com.grouptuity.view.BillableListingAdapter.BillableListingAdapterController;

public class ItemReviewActivity extends ActivityTemplate<ItemReviewModel>
{
	private InstructionsBar instructions;
	private NumberPad numberPad;
	private DinerBillableListingAdapter billableListingAdapter;
	private ListView listView;
	private AlertDialog deleteBillableAlert;
	private Billable deleteBillable;

	protected void createActivity(Bundle bundle)
	{
		model = new ItemReviewModel(this,bundle);
		
		menuInt = R.menu.individual_bill_review_menu;

		titleBar = new TitleBar(this,model.currentDiner.name,TitleBar.BACK_AND_MENU);
		mainLinearLayout.addView(titleBar,Grouptuity.fillWrapLP());

		instructions = new InstructionsBar(this);

		billableListingAdapter = new DinerBillableListingAdapter(this, model.currentDiner, new BillableListingAdapterController()
		{
			public void onEditValueClick(Billable billable)
			{
				if(billable.billableType.isItem)
				{
					model.currentItem = (Item)billable;
					model.setNewState(ItemReviewModel.EDIT_BILL_ITEM_VALUE);
				}
				else if(billable.billableType.isDebt)
				{
					model.currentDebt = (Debt)billable;
					model.setNewState(ItemReviewModel.EDIT_BILL_DEBT_VALUE);
				}
				else
				{
					model.currentDiscount = (Discount)billable;
					model.setNewState(ItemReviewModel.EDIT_BILL_DEAL_VALUE);
				}
			}
			public void onEditCostClick(Billable billable){model.currentDiscount = (Discount)billable;model.setNewState(ItemReviewModel.EDIT_BILL_DEAL_COST);}
			public void onDeleteItemClick(Billable billable)
			{
				deleteBillable = billable;
				if(billable.billableType.isItem)
				{
					Item item = (Item) billable;
					if(item.payers.size()==1 && item.payers.contains(model.currentDiner))
					{
						deleteBillableAlert.setTitle("Delete Item?");
						deleteBillableAlert.setMessage(model.currentDiner.name+" is the only remaining payer for this item. Should the item be deleted?");
						deleteBillableAlert.show();
					}
					else
						Grouptuity.getBill().removeItemAssignment(item, model.currentDiner);
				}
				else if(billable.billableType.isDebt)
				{
					Debt debt = (Debt) billable;
					if(debt.payees.size()==1 && debt.payees.contains(model.currentDiner))
					{
						deleteBillableAlert.setTitle("Delete Debt?");
						deleteBillableAlert.setMessage(model.currentDiner.name+" is the only person receiving money from this debt. Should the debt be deleted?");
						deleteBillableAlert.show();
					}
					else if(debt.payers.size()==1 && debt.payers.contains(model.currentDiner))
					{
						deleteBillableAlert.setTitle("Delete Debt?");
						deleteBillableAlert.setMessage(model.currentDiner.name+" is the only person owing this debt. Should the debt be deleted?");
						deleteBillableAlert.show();
					}
					else
					{
						Grouptuity.getBill().removeDebtDebitAssignment(debt, model.currentDiner);
						Grouptuity.getBill().removeDebtCreditAssignment(debt, model.currentDiner);
					}
				}
				else
				{
					Discount discount = (Discount) billable;
					if(discount.payees.size()==1 && discount.payees.contains(model.currentDiner))
					{
						deleteBillableAlert.setTitle("Delete Discount?");
						deleteBillableAlert.setMessage(model.currentDiner.name+" is the only person using this discount. Should the discount be deleted?");
						deleteBillableAlert.show();
					}
					else if(discount.payers.size()==1 && discount.payers.contains(model.currentDiner))
					{
						deleteBillableAlert.setTitle("Delete Discount?");
						deleteBillableAlert.setMessage(model.currentDiner.name+" is the only person designated as paying for this discount. Should the discount be deleted?");
						deleteBillableAlert.show();
					}
					else
					{
						Grouptuity.getBill().removeDiscountCreditAssignment((Discount)billable, model.currentDiner);
						Grouptuity.getBill().removeDiscountDebitAssignment((Discount)billable, model.currentDiner);
					}
				}
				update();
			}
			public void onPayerListClick(Billable billable){}
			public void onPayeeListClick(Billable billable){}
		});
		listView = new ListView(activity);
		listView.setAdapter(billableListingAdapter);
		listView.setFastScrollEnabled(true);
		listView.setCacheColorHint(Grouptuity.foregroundColor);

		AlertDialog.Builder deleteBillableAlertBuilder = new AlertDialog.Builder(this);
		deleteBillableAlertBuilder.setCancelable(true);
		deleteBillableAlertBuilder.setIcon(R.drawable.delete);
		deleteBillableAlertBuilder.setPositiveButton("Delete", new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int id)
			{
				if(deleteBillable.billableType.isItem)
					Grouptuity.getBill().removeItem((Item)deleteBillable);
				else if(deleteBillable.billableType.isDebt)
					Grouptuity.getBill().removeDebt((Debt)deleteBillable);
				else
					Grouptuity.getBill().removeDiscount((Discount)deleteBillable);
				update();
			}
		});
		deleteBillableAlertBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){dialog.cancel();}});
		deleteBillableAlert = deleteBillableAlertBuilder.create();

		numberPad = new NumberPad(this);
		numberPad.setController(new NumberPad.NumberPadController()
		{
			public void onConfirm(double returnValue, BillableType billableType, boolean taxM, boolean tipM)
			{
				DiscountMethod taxMethod, tipMethod;
				switch(model.getState())
				{
					case ItemReviewModel.EDIT_BILL_ITEM_VALUE:		model.editItem(model.currentItem,returnValue,billableType);model.revertToState(ItemReviewModel.DEFAULT);break;
					case ItemReviewModel.EDIT_BILL_DEBT_VALUE:		model.editDebt(model.currentDebt,returnValue);model.revertToState(ItemReviewModel.DEFAULT);break;
					case ItemReviewModel.EDIT_BILL_DEAL_COST:		Discount d = model.currentDiscount;
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
																	model.revertToState(ItemReviewModel.DEFAULT);
																	break;
					case ItemReviewModel.EDIT_BILL_DEAL_VALUE:		d = model.currentDiscount;
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
																		case DEAL_PERCENT_ITEM:	model.editDiscount(d, d.value, d.cost, returnValue, d.billableType, taxMethod, taxMethod);break;
																		case DEAL_PERCENT_BILL:	model.editDiscount(d, d.value, d.cost, returnValue, d.billableType, taxMethod, tipMethod);break;
																		case DEAL_FIXED:		model.editDiscount(d, returnValue, d.cost, d.percent, d.billableType, taxMethod, tipMethod);break;
																		case DEAL_GROUP_VOUCHER:model.editDiscount(d, returnValue, d.cost, d.percent, d.billableType, taxMethod, tipMethod);break;
																		default: break;
																	}
																	model.revertToState(ItemReviewModel.DEFAULT);
																	break;
				}
			}
			public void onCancel()
			{
				switch(model.getState())
				{
					case ItemReviewModel.EDIT_BILL_ITEM_VALUE:	showToastMessage("Item Edit Canceled");break;
					case ItemReviewModel.EDIT_BILL_DEBT_VALUE:	showToastMessage("Debt Edit Canceled");break;
					case ItemReviewModel.EDIT_BILL_DEAL_COST:
					case ItemReviewModel.EDIT_BILL_DEAL_VALUE:	showToastMessage("Discount Edit Canceled");break;
				}
				model.revertToState(ItemReviewModel.DEFAULT);
			}
		});
	}

	private void update()
	{
		refreshActivity();
		instructions.setInstructionsText("Subtotal: "+Grouptuity.formatNumber(NumberFormat.CURRENCY, model.currentDiner.fullSubTotal));
		billableListingAdapter.refresh();
		if(!model.currentDiner.hasBillables())
			finish();
	}

	protected void createHorizontalLayout(){}
	protected void createVerticalLayout()
	{
		LinearLayout.LayoutParams ibLP = new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);
		ibLP.bottomMargin = -instructions.panel.style.bottomContraction;
		mainLinearLayout.addView(instructions,ibLP);
		mainLinearLayout.addView(listView,Grouptuity.fillWrapLP(1.0f));
		rootLayout.addView(numberPad);
	}
	protected String createHelpString(){return	"Click the red \"X\" to remove this diner from the given item. Hit the back button to return to item entry.";}

	protected void processStateChange(int state)
	{
		instructions.setInstructionsText("Subtotal: "+Grouptuity.formatNumber(NumberFormat.CURRENCY, model.currentDiner.fullSubTotal));
		billableListingAdapter.refresh();
		boolean taxMethod, tipMethod, showTaxMethod, showTipMethod;
		switch(model.getState())
		{
			case ItemReviewModel.START:					model.setNewState(ItemReviewModel.DEFAULT);break;
			case ItemReviewModel.DEFAULT:				numberPad.close();break;
			case ItemReviewModel.EDIT_BILL_ITEM_VALUE:	numberPad.open(NumberFormat.CURRENCY_NO_ZERO,"Enter the item cost (was "+Grouptuity.formatNumber(NumberFormat.CURRENCY,model.currentItem.value)+")",model.currentItem.billableType);break;
			case ItemReviewModel.EDIT_BILL_DEBT_VALUE:	numberPad.open(NumberFormat.CURRENCY_NO_ZERO,"Enter the debt amount (was "+Grouptuity.formatNumber(NumberFormat.CURRENCY,model.currentDebt.value)+")",model.currentDebt.billableType);break;
			case ItemReviewModel.EDIT_BILL_DEAL_VALUE:	switch(model.currentDiscount.taxable)
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
			case ItemReviewModel.EDIT_BILL_DEAL_COST:	switch(model.currentDiscount.taxable)
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
}