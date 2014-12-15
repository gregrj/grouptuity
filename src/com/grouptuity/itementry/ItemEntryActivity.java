package com.grouptuity.itementry;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.LinearLayout.LayoutParams;
import com.grouptuity.*;
import com.grouptuity.Grouptuity.*;
import com.grouptuity.billcalculation.BillCalculationActivity;
import com.grouptuity.itementry.ItemEntryCommandPanel.ItemEntryCommandPanelController;
import com.grouptuity.itemreview.ItemReviewActivity;
import com.grouptuity.model.*;
import com.grouptuity.model.Discount.DiscountMethod;
import com.grouptuity.view.*;

public class ItemEntryActivity extends ActivityTemplate<ItemEntryModel>
{
	private ItemEntryCommandPanel commandPanel;
	private NumberPad numberPad;
	private ItemEntrySlidingBar bottomBar;
	private ItemEntryDinerList itemEntryDinerList;
	private InstructionsBar textInstructionsBar;
	private SlidingBarBillReport billReport;
	private ItemAlertDialogList iaDialogList;
	final private int itemReviewAdvanceCode = 1, billcalculationAdvanceCode = 2;

	@SuppressLint("ShowToast")
	protected void createActivity(Bundle bundle)
	{
		model = new ItemEntryModel(this,bundle);
		menuInt = R.menu.item_entry_menu;

		titleBar = new TitleBar(this,"What Was Ordered?",TitleBar.BACK_AND_MENU);
		mainLinearLayout.addView(titleBar,Grouptuity.fillWrapLP());

		commandPanel = new ItemEntryCommandPanel(this);
		commandPanel.setController(new ItemEntryCommandPanelController()
		{
			public void onBrowse(){model.setNewState(ItemEntryModel.BROWSING_DINERS);refreshActivity();}
			public void onAddItem()
			{
				model.currentBillableType = BillableType.ENTREE;
				model.setNewState(ItemEntryModel.ITEM_ENTRY_ENTER_COST_ITEM);
				refreshActivity();
			}
			public void onAddDebt()
			{
				model.currentBillableType = BillableType.DEBT;
				model.setNewState(ItemEntryModel.ITEM_ENTRY_ENTER_COST_DEBT);
				refreshActivity();
			}
			public void onAddDeal()
			{
				final String[] dealOptions;
				if(model.hasItems())
					dealOptions = new String[]
					{
						BillableType.DEAL_PERCENT_ITEM.name,
						BillableType.DEAL_PERCENT_BILL.name,
						BillableType.DEAL_FIXED.name,
						BillableType.DEAL_GROUP_VOUCHER.name
					};
				else
					dealOptions = new String[]
					{
						BillableType.DEAL_PERCENT_BILL.name,
						BillableType.DEAL_FIXED.name,
						BillableType.DEAL_GROUP_VOUCHER.name
					};

				AlertDialogList discountSelector= new AlertDialogList(activity,dealOptions,null, new AlertDialogList.Controller()
				{
					public void optionSelected(int i)
					{
						if(model.hasItems())
						{
							switch(i)
							{
								case 0:	model.currentBillableType = BillableType.DEAL_PERCENT_ITEM;
										iaDialogList.show();
										break;
								case 1:	model.currentBillableType = BillableType.DEAL_PERCENT_BILL;
										model.setNewState(ItemEntryModel.ITEM_ENTRY_ENTER_VALUE_DEAL);
										refreshActivity();
										break;
								case 2:	model.currentBillableType = BillableType.DEAL_FIXED;
										model.setNewState(ItemEntryModel.ITEM_ENTRY_ENTER_VALUE_DEAL);
										refreshActivity();
										break;
								case 3:	model.currentBillableType = BillableType.DEAL_GROUP_VOUCHER;
										model.setNewState(ItemEntryModel.ITEM_ENTRY_ENTER_VALUE_DEAL);
										refreshActivity();
										break;
							}
						}
						else
						{
							switch(i)
							{
								case 0:	model.currentBillableType = BillableType.DEAL_PERCENT_BILL;
										model.setNewState(ItemEntryModel.ITEM_ENTRY_ENTER_VALUE_DEAL);
										refreshActivity();
										break;
								case 1:	model.currentBillableType = BillableType.DEAL_FIXED;
										model.setNewState(ItemEntryModel.ITEM_ENTRY_ENTER_VALUE_DEAL);
										refreshActivity();
										break;
								case 2:	model.currentBillableType = BillableType.DEAL_GROUP_VOUCHER;
										model.setNewState(ItemEntryModel.ITEM_ENTRY_ENTER_VALUE_DEAL);
										refreshActivity();
										break;
							}
						}
					}
				});
				discountSelector.setTitle("What Kind of Deal?");
				discountSelector.show();
			}
		});

		billReport = new SlidingBarBillReport(this, new SlidingBarBillReport.SlidingBarBillReportController()
		{
			public void onItemValueEditClick(Item itemToEdit){model.currentItem = itemToEdit;model.setNewState(ItemEntryModel.EDIT_BILL_ITEM_VALUE);}
			public void onItemDeleteClick(Item itemToDelete)
			{
				Grouptuity.getBill().removeItem(itemToDelete);
				showToastMessage("Item Removed");
				refreshActivity();
			}
			public void onItemPayerListClick(Item itemToEdit){}
			public void onDebtValueEditClick(Debt debtToEdit){model.currentDebt = debtToEdit;model.setNewState(ItemEntryModel.EDIT_BILL_DEBT_VALUE);}
			public void onDebtDeleteClick(Debt debtToDelete)
			{
				Grouptuity.getBill().removeDebt(debtToDelete);
				showToastMessage("Debt Removed");
				refreshActivity();
			}
			public void onDebtPayerListClick(Debt debtToEdit){}
			public void onDebtPayeeListClick(Debt debtToEdit){}
			public void onDiscountValueEditClick(Discount discountToEdit){model.currentDiscount = discountToEdit;model.setNewState(ItemEntryModel.EDIT_BILL_DEAL_VALUE);}
			public void onDiscountCostEditClick(Discount discountToEdit){model.currentDiscount = discountToEdit;model.setNewState(ItemEntryModel.EDIT_BILL_DEAL_COST);}
			public void onDiscountDeleteClick(Discount discountToDelete)
			{
				Grouptuity.getBill().removeDiscount(discountToDelete);
				showToastMessage("Discount Removed");
				refreshActivity();
			}
			public void onDiscountPayerListClick(Discount discountToEdit){}
			public void onDiscountPayeeListClick(Discount discountToEdit){}
		});

		numberPad = new NumberPad(this);
		numberPad.setController(new NumberPad.NumberPadController()
		{
			public void onConfirm(double returnValue, BillableType billableType, boolean taxM, boolean tipM)
			{
				switch(model.getState())
				{
					case ItemEntryModel.ITEM_ENTRY_ENTER_COST_ITEM:	model.currentItem = new Item(returnValue,billableType);
																	model.currentBillableType = model.currentItem.billableType;
																	model.setNewState(ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_ITEM);
																	break;
					case ItemEntryModel.ITEM_ENTRY_ENTER_COST_DEBT:	model.currentDebt = new Debt(returnValue);model.setNewState(ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYER);break;
					case ItemEntryModel.ITEM_ENTRY_ENTER_COST_DEAL:	DiscountMethod taxMethod, tipMethod;
																	if(taxM)
																		taxMethod = DiscountMethod.ON_COST;
																	else
																	{
																		switch(model.currentDiscount.taxable)
																		{
																			case ON_VALUE:	taxMethod = DiscountMethod.ON_VALUE;break;
																			default:		taxMethod = DiscountMethod.NONE;break;
																		}
																	}
																	if(tipM)
																		tipMethod = DiscountMethod.ON_COST;
																	else
																	{
																		switch(model.currentDiscount.tipable)
																		{
																			case ON_VALUE:	tipMethod = DiscountMethod.ON_VALUE;break;
																			default:		tipMethod = DiscountMethod.NONE;break;
																		}
																	}
																	model.currentDiscount.cost = returnValue;
																	model.currentDiscount.taxable = taxMethod;
																	model.currentDiscount.tipable = tipMethod;
																	model.setNewState(ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYER);
																	break;
					case ItemEntryModel.ITEM_ENTRY_ENTER_VALUE_DEAL:if(taxM)
																		taxMethod = DiscountMethod.ON_VALUE;
																	else
																	{
																		switch(DiscountMethod.values()[Grouptuity.DISCOUNT_TAX_METHOD.getValue()])
																		{
																			case ON_COST:	taxMethod = DiscountMethod.ON_COST;break;
																			default:		taxMethod = DiscountMethod.NONE;break;
																		}
																	}
																	if(tipM)
																		tipMethod = DiscountMethod.ON_VALUE;
																	else
																	{
																		switch(DiscountMethod.values()[Grouptuity.DISCOUNT_TIP_METHOD.getValue()])
																		{
																			case ON_COST:	tipMethod = DiscountMethod.ON_COST;break;
																			default:		tipMethod = DiscountMethod.NONE;break;
																		}
																	}
																	switch(model.currentBillableType)
																	{
																		case DEAL_PERCENT_ITEM:	model.currentDiscount = Discount.createItemPercentDiscount(returnValue, taxMethod, tipMethod, model.currentItem);break;
																		case DEAL_PERCENT_BILL:	model.currentDiscount = Discount.createBillPercentDiscount(returnValue, taxMethod,  tipMethod);break;
																		case DEAL_FIXED:		model.currentDiscount = Discount.createFixedAmountVoucher(returnValue, taxMethod, tipMethod);break;
																		case DEAL_GROUP_VOUCHER:model.currentDiscount = Discount.createGroupDealVoucher(returnValue,  taxMethod, tipMethod);break;
																		default: break;
																	}
																	if(model.currentBillableType==BillableType.DEAL_PERCENT_ITEM)
																	{
																		showToastMessage("Discount Added, "+model.currentDiscount.toString());
																		model.applyCurrentItemPayersToCurrentDiscount();
																		model.commitCurrentDiscount();
																		model.revertToState(ItemEntryModel.ITEM_ENTRY_DEFAULT);
																	}
																	else
																		model.setNewState(ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYEE);
																	break;
					case ItemEntryModel.EDIT_BILL_ITEM_VALUE:		model.editItem(model.currentItem,returnValue,billableType);
																	showToastMessage("Edited Item Value: "+Grouptuity.formatNumber(NumberFormat.CURRENCY,returnValue));
																	model.revertToState(ItemEntryModel.REVIEWING_BILLABLES);
																	break;
					case ItemEntryModel.EDIT_BILL_DEBT_VALUE:		model.editDebt(model.currentDebt,returnValue);
																	showToastMessage("Edited Debt Amount: "+Grouptuity.formatNumber(NumberFormat.CURRENCY,returnValue));
																	model.revertToState(ItemEntryModel.REVIEWING_BILLABLES);
																	break;
					case ItemEntryModel.EDIT_BILL_DEAL_COST:		Discount d = model.currentDiscount;
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
																	model.revertToState(ItemEntryModel.REVIEWING_BILLABLES);
																	break;
					case ItemEntryModel.EDIT_BILL_DEAL_VALUE:		d = model.currentDiscount;
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
																	model.revertToState(ItemEntryModel.REVIEWING_BILLABLES);
																	break;
				}
			}
			public void onCancel()
			{
				switch(model.getState())
				{
					case ItemEntryModel.ITEM_ENTRY_ENTER_COST_ITEM:	showToastMessage("Item Canceled");model.revertToState(ItemEntryModel.ITEM_ENTRY_DEFAULT);break;
					case ItemEntryModel.ITEM_ENTRY_ENTER_COST_DEBT:	showToastMessage("Debt Canceled");model.revertToState(ItemEntryModel.ITEM_ENTRY_DEFAULT);break;
					case ItemEntryModel.ITEM_ENTRY_ENTER_COST_DEAL:	
					case ItemEntryModel.ITEM_ENTRY_ENTER_VALUE_DEAL:showToastMessage("Deal Canceled");model.revertToState(ItemEntryModel.ITEM_ENTRY_DEFAULT);break;
					case ItemEntryModel.EDIT_BILL_ITEM_VALUE:		showToastMessage("Item Edit Canceled");model.revertToState(ItemEntryModel.REVIEWING_BILLABLES);break;
					case ItemEntryModel.EDIT_BILL_DEBT_VALUE:		showToastMessage("Debt Edit Canceled");model.revertToState(ItemEntryModel.REVIEWING_BILLABLES);break;
					case ItemEntryModel.EDIT_BILL_DEAL_COST:
					case ItemEntryModel.EDIT_BILL_DEAL_VALUE:		showToastMessage("Discount Edit Canceled");model.revertToState(ItemEntryModel.REVIEWING_BILLABLES);break;
				}
			}
		});

		itemEntryDinerList = new ItemEntryDinerList(this,new ItemEntryDinerList.ItemEntryDinerListController()
		{
			public void onDinerSelected(Diner d)
			{
				if(model.getState()==ItemEntryModel.BROWSING_DINERS)
				{
					model.currentDiner = d;
					if(d.hasBillables())
						advanceActivity(itemReviewAdvanceCode);
					else
						showToastMessage("This diner has no items to view");
				}
				else
				{
					model.toggleDinerSelections(d);
					updateInstructionBarMessage();
				}
				refreshActivity();
			}
		});

		iaDialogList = new ItemAlertDialogList(this, new ItemAlertDialogList.Controller()
		{
			@Override
			public void optionSelected(int i)
			{
				model.currentItem = Grouptuity.getBill().items.get(i);
				model.setNewState(ItemEntryModel.ITEM_ENTRY_ENTER_VALUE_DEAL);
				refreshActivity();
			}
		});

		textInstructionsBar = new InstructionsBar(this);

		bottomBar = new ItemEntrySlidingBar(this, billReport);
		bottomBar.setController(new SlidingBar.SlidingBarController()
		{
			public void onMotionStarted(){}
			public void onOpened(){model.setNewState(ItemEntryModel.REVIEWING_BILLABLES);}
			public void onClosed(){model.revertPastFirst(ItemEntryModel.REVIEWING_BILLABLES);}
			public void onDisabledButtonClick(String buttonName)
			{
				switch(ItemEntrySlidingBar.Button.getButton(buttonName))
				{
					case FINISH:		if(!model.hasBillables())
											showToastMessage("You must add some items first");
										break;
					case REVIEW_ITEMS:	if(!model.hasBillables())
											showToastMessage("You must add some items first");
										break;
					case NEXT:
					case DONE:			showToastMessage("Select at least one diner");break;
					default:			break;
				}
			}
			public void onButtonClick(String name)
			{
				switch(ItemEntrySlidingBar.Button.getButton(name))
				{
					case REVIEW_ITEMS:			bottomBar.open();model.setNewState(ItemEntryModel.REVIEWING_BILLABLES);break;
					case RETURN_TO_ITEM_ENTRY:	bottomBar.close();model.revertToState(ItemEntryModel.ITEM_ENTRY_DEFAULT);break;
					case DONE:					switch(model.getState())
												{
													case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_ITEM:			showToastMessage("Item Added, "+Grouptuity.formatNumber(NumberFormat.CURRENCY,model.currentItem.value));
																												model.addPayersForCurrentItem();
																												model.commitCurrentItem();
																												break;
													case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYEE:	showToastMessage("Debt Added, "+Grouptuity.formatNumber(NumberFormat.CURRENCY,model.currentDebt.value));
																												model.addPayeesForCurrentDebt();
																												model.commitCurrentDebt();
																												break;
													case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYER:	showToastMessage("Discount Added, "+model.currentDiscount.toString());
																												model.addPayersForCurrentDiscount();
																												model.commitCurrentDiscount();
																												break;
													case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYEE:	showToastMessage("Discount Added, "+model.currentDiscount.toString());
																												model.addPayeesForCurrentDiscount();
																												model.commitCurrentDiscount();
																												break;
												}
												model.revertToState(ItemEntryModel.ITEM_ENTRY_DEFAULT);
												break;
					case CANCEL:				switch(model.getState())
												{
													case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_ITEM:			showToastMessage("Item Canceled");break;
													case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYER:
													case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYEE:	showToastMessage("Debt Canceled");break;
													case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYER:
													case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYEE:	showToastMessage("Discount Canceled");break;
												}
												model.revertToState(ItemEntryModel.ITEM_ENTRY_DEFAULT);
												break;
					case NEXT:					switch(model.getState())
												{
													case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYER:	model.addPayersForCurrentDebt();
																												model.setNewState(ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYEE);
																												break;
													case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYEE:	model.addPayeesForCurrentDebt();
																												model.setNewState(ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYER);
																												break;
													case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYER:	model.addPayersForCurrentDiscount();
																												model.setNewState(ItemEntryModel.ITEM_ENTRY_ENTER_COST_DEAL);
																												break;
													case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYEE:	model.addPayeesForCurrentDiscount();
																												model.setNewState(ItemEntryModel.ITEM_ENTRY_ENTER_COST_DEAL);
																												break;
												}
												model.clearDinerSelections();
												break;
					case FINISH:				advanceActivity(billcalculationAdvanceCode);break;
				}
				refreshActivity();
			}
		});
	}

	protected void createHorizontalLayout(){/*TODO*/}
	protected void createVerticalLayout()
	{
		LinearLayout.LayoutParams tibLP = new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);
		tibLP.bottomMargin = -textInstructionsBar.panel.style.bottomContraction;
		mainLinearLayout.addView(textInstructionsBar,tibLP);
		mainLinearLayout.addView(itemEntryDinerList,Grouptuity.fillWrapLP(1.0f));
		mainLinearLayout.addView(bottomBar.getPlaceholder(),Grouptuity.wrapWrapLP());
		rootLayout.addView(commandPanel,Grouptuity.relWrapWrapLP(RelativeLayout.CENTER_IN_PARENT));
		rootLayout.addView(numberPad);
		rootLayout.addView(bottomBar,Grouptuity.relFillFillLP());
	}
	protected String createHelpString()
	{
		return	"Make sure you have the restaurant check in front of you, then click \"Add Item\" to get started. "+
				"Start with the first item on the check. Enter the item cost, click the names of the diners sharing the item, and then press \"Done\" to add the item."+
				"Go down the check, and repeat this process for each item.\n\n"+
				"When you're finished, click \"Tip & Tax\" at the bottom right to proceed. The "+
				"\"View Bill\" button on the bottom left lets you edit existing items.";
	}

	private void updateInstructionBarMessage()
	{
		int selectionCount = model.getNumDinerSelections();
		switch(model.getState())
		{
			case ItemEntryModel.ITEM_ENTRY_DEFAULT:						textInstructionsBar.setInstructionsText(((Grouptuity.getBill().items.size()==1)?("1 Item, "):(Grouptuity.getBill().items.size()+" Items, "))+model.getDiscountedSubtotal());break;
			case ItemEntryModel.BROWSING_DINERS:						textInstructionsBar.setInstructionsText("Click on a diner to view his or her items");break;
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_ITEM:			if(selectionCount==0)
																			textInstructionsBar.setInstructionsText("Select the diners paying for this item");
																		else if(selectionCount==1)
																			textInstructionsBar.setInstructionsText("Item for 1 diner, "+Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,model.currentItem.value));
																		else
																			textInstructionsBar.setInstructionsText("Item shared by "+selectionCount+" diners, "+Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,model.currentItem.value));
																		break;
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYER:	if(selectionCount==0)
																			textInstructionsBar.setInstructionsText("Select the diners who owe money");
																		else if(selectionCount==1)
																			textInstructionsBar.setInstructionsText("Debt owed by 1 diner, "+Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,model.currentDebt.value));
																		else
																			textInstructionsBar.setInstructionsText("Debt owed by "+selectionCount+" diners, "+Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,model.currentDebt.value));
																		break;
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYEE:	if(selectionCount==0)
																			textInstructionsBar.setInstructionsText("Select the diners getting paid");
																		else if(selectionCount==1)
																			textInstructionsBar.setInstructionsText("1 diner getting paid, "+Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,model.currentDebt.value));
																		else
																			textInstructionsBar.setInstructionsText(selectionCount+" diners getting paid, "+Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,model.currentDebt.value));
																		break;
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYER:	if(selectionCount==0)
																			textInstructionsBar.setInstructionsText("Select the diners who paid for this deal");
																		else if(selectionCount==1)
																			textInstructionsBar.setInstructionsText("Deal purchased by 1 diner, "+Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,model.currentDiscount.value));
																		else
																			textInstructionsBar.setInstructionsText("Deal purchased by "+selectionCount+" diners, "+Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,model.currentDiscount.value));
																		break;
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYEE:	if(selectionCount==0)
																			textInstructionsBar.setInstructionsText("Select the diners sharing the discount");
																		else if(selectionCount==1)
																		{
																			if(model.currentDiscount.billableType==BillableType.DEAL_PERCENT_ITEM || model.currentDiscount.billableType==BillableType.DEAL_PERCENT_BILL)
																				textInstructionsBar.setInstructionsText("Deal for 1 diner, "+Grouptuity.formatNumber(Grouptuity.NumberFormat.TAX_PERCENT,model.currentDiscount.percent));
																			else
																				textInstructionsBar.setInstructionsText("Deal for 1 diner, "+Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,model.currentDiscount.value));
																		}
																		else
																		{
																			if(model.currentDiscount.billableType==BillableType.DEAL_PERCENT_ITEM || model.currentDiscount.billableType==BillableType.DEAL_PERCENT_BILL)
																				textInstructionsBar.setInstructionsText("Deal shared by "+selectionCount+" diners, "+Grouptuity.formatNumber(Grouptuity.NumberFormat.TAX_PERCENT,model.currentDiscount.percent));
																			else
																				textInstructionsBar.setInstructionsText("Deal shared by "+selectionCount+" diners, "+Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY,model.currentDiscount.value));
																		}
																		break;
		}
	}

	protected void processStateChange(int state)
	{
		itemEntryDinerList.unlock();
		model.clearDinerSelections();
		updateInstructionBarMessage();
		switch(state)
		{
			case ItemEntryModel.START:								model.setNewState(ItemEntryModel.ITEM_ENTRY_DEFAULT);break;
			case ItemEntryModel.ITEM_ENTRY_DEFAULT:					commandPanel.show();
																	numberPad.close();
																	itemEntryDinerList.lock();
																	break;
			case ItemEntryModel.BROWSING_DINERS:					commandPanel.hide();
																	numberPad.close();
																	break;
			case ItemEntryModel.ITEM_ENTRY_ENTER_COST_ITEM:			commandPanel.hide();numberPad.open(NumberFormat.CURRENCY_NO_ZERO,"Enter the item cost",model.currentBillableType);break;
			case ItemEntryModel.ITEM_ENTRY_ENTER_COST_DEBT:			commandPanel.hide();numberPad.open(NumberFormat.CURRENCY_NO_ZERO,"Enter the debt amount",BillableType.DEBT);break;
			case ItemEntryModel.ITEM_ENTRY_ENTER_VALUE_DEAL:		commandPanel.hide();
																	boolean taxMethod = Grouptuity.DISCOUNT_TAX_METHOD.getValue()==DiscountMethod.ON_VALUE.ordinal();
																	boolean tipMethod = Grouptuity.DISCOUNT_TIP_METHOD.getValue()==DiscountMethod.ON_VALUE.ordinal();
																	switch(model.currentBillableType)
																	{
																		case DEAL_PERCENT_ITEM:	numberPad.open(NumberFormat.TAX_PERCENT,"Enter the percent discount",model.currentBillableType,taxMethod,tipMethod,true,true);break;
																		case DEAL_PERCENT_BILL:	numberPad.open(NumberFormat.TAX_PERCENT,"Enter the percent discount",model.currentBillableType,taxMethod,tipMethod,true,true);break;
																		case DEAL_FIXED:		numberPad.open(NumberFormat.CURRENCY_NO_ZERO,"Enter the discount value",model.currentBillableType,taxMethod,tipMethod,true,true);break;
																		case DEAL_GROUP_VOUCHER:numberPad.open(NumberFormat.CURRENCY_NO_ZERO,"Enter the discount value",model.currentBillableType,taxMethod,tipMethod,true,true);break;
																		default: break;
																	}
																	break;
			case ItemEntryModel.ITEM_ENTRY_ENTER_COST_DEAL:			commandPanel.hide();
																	boolean showTaxMethod = true;
																	boolean showTipMethod = true;
																	if(model.currentDiscount.taxable==DiscountMethod.ON_VALUE)
																	{
																		showTaxMethod = false;
																		taxMethod = false;
																	}
																	else
																	{
																		showTaxMethod = true;
																		if(Grouptuity.DISCOUNT_TAX_METHOD.getValue()==DiscountMethod.ON_COST.ordinal())
																			taxMethod = true;
																		else
																			taxMethod = false;
																	}
																	if(model.currentDiscount.tipable==DiscountMethod.ON_VALUE)
																	{
																		showTipMethod = false;
																		tipMethod = false;
																	}
																	else
																	{
																		showTipMethod = true;
																		if(Grouptuity.DISCOUNT_TIP_METHOD.getValue()==DiscountMethod.ON_COST.ordinal())
																			tipMethod = true;
																		else
																			tipMethod = false;
																	}
																	numberPad.open(NumberFormat.CURRENCY,"Enter the deal purchase cost",model.currentDiscount.billableType,taxMethod,tipMethod,showTaxMethod,showTipMethod);
																	break;
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_ITEM:		
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYER:
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYEE:
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYER:
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYEE:commandPanel.hide();numberPad.close();break;
			case ItemEntryModel.EDIT_BILL_ITEM_VALUE:				numberPad.open(NumberFormat.CURRENCY_NO_ZERO,"Enter the item cost (was "+Grouptuity.formatNumber(NumberFormat.CURRENCY,model.currentItem.value)+")",model.currentItem.billableType);break;
			case ItemEntryModel.EDIT_BILL_DEBT_VALUE:				numberPad.open(NumberFormat.CURRENCY_NO_ZERO,"Enter the debt amount (was "+Grouptuity.formatNumber(NumberFormat.CURRENCY,model.currentDebt.value)+")",model.currentDebt.billableType);break;
			case ItemEntryModel.EDIT_BILL_DEAL_VALUE:				switch(model.currentDiscount.taxable)
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
			case ItemEntryModel.EDIT_BILL_DEAL_COST:				switch(model.currentDiscount.taxable)
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

	public void onBackPressed() 
	{
		switch(model.getState())
		{
			case ItemEntryModel.ITEM_ENTRY_ENTER_VALUE_DEAL:
			case ItemEntryModel.ITEM_ENTRY_ENTER_COST_DEAL:
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYER:
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEAL_PAYEE:showToastMessage("Deal Canceled");
																	model.revertToState(ItemEntryModel.ITEM_ENTRY_DEFAULT);
																	break;
			case ItemEntryModel.ITEM_ENTRY_ENTER_COST_DEBT:
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYEE:
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_DEBT_PAYER:showToastMessage("Debt Canceled");
																	model.revertToState(ItemEntryModel.ITEM_ENTRY_DEFAULT);
																	break;
			case ItemEntryModel.ITEM_ENTRY_ENTER_COST_ITEM:
			case ItemEntryModel.ITEM_ENTRY_SELECT_DINERS_ITEM:		showToastMessage("Item Canceled");
																	model.revertToState(ItemEntryModel.ITEM_ENTRY_DEFAULT);
																	break;
			case ItemEntryModel.REVIEWING_BILLABLES:				bottomBar.close();model.revertToState(ItemEntryModel.ITEM_ENTRY_DEFAULT);break;
			case ItemEntryModel.BROWSING_DINERS:					model.revertToState(ItemEntryModel.ITEM_ENTRY_DEFAULT);break;
			case ItemEntryModel.EDIT_BILL_ITEM_VALUE:				numberPad.close();showToastMessage("Item Edit Canceled");model.revertToState(ItemEntryModel.REVIEWING_BILLABLES);break;
			case ItemEntryModel.EDIT_BILL_DEBT_VALUE:				numberPad.close();showToastMessage("Debt Edit Canceled");model.revertToState(ItemEntryModel.REVIEWING_BILLABLES);break;
			case ItemEntryModel.EDIT_BILL_DEAL_VALUE:				
			case ItemEntryModel.EDIT_BILL_DEAL_COST:				numberPad.close();showToastMessage("Discount Edit Canceled");model.revertToState(ItemEntryModel.REVIEWING_BILLABLES);break;
			default: 												super.onBackPressed();
		}
	}
	public void onResume(){super.onResume();}

	protected boolean onOptionsMenuSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
			case R.id.reset:				showToastMessage("Removed All Items");
											model.resetBillables();
											bottomBar.close();
											if(model.getState()!=ItemEntryModel.ITEM_ENTRY_DEFAULT)
												model.revertToState(ItemEntryModel.ITEM_ENTRY_DEFAULT);
											else
											{
												commandPanel.show();
												numberPad.close();
												model.clearDinerSelections();
												textInstructionsBar.setInstructionsText("0 Items, "+model.getRawSubtotal());
												itemEntryDinerList.lock();
												refreshActivity();
											}
											return true;
			case R.id.contacts_refresh:		Grouptuity.reloadContacts();return true;
		}
		return false;
	}

	@Override
	protected GrouptuityPreference<Boolean> getTutorialPopupFlag(){return Grouptuity.SHOW_TUTORIAL_ITEMENTRY;}

	@Override
	protected void onAdvanceActivity(int code)
	{
		switch(code)
		{
			case itemReviewAdvanceCode:	if(model.currentDiner!=null)
										{
											Intent intent = new Intent(activity,ItemReviewActivity.class);
											intent.putExtra("com.grouptuity.hasCurrentDinerIndex",true);
											intent.putExtra("com.grouptuity.currentDinerIndex",Grouptuity.getBill().diners.indexOf(model.currentDiner));
											startActivity(intent);
										}
										break;
			case billcalculationAdvanceCode:	if(Grouptuity.getBill().getDiscountedSubtotal()<0)
												{
													AlertDialog.Builder negativeRestaurantAlertBuilder = new AlertDialog.Builder(this);
													negativeRestaurantAlertBuilder.setCancelable(true);
													negativeRestaurantAlertBuilder.setTitle("Negative Restaurant Bill");
													negativeRestaurantAlertBuilder.setMessage("The discounts you added have made the amount owed to the restaurant negative.  If you continue, the amount owed will be set to $0.");
													negativeRestaurantAlertBuilder.setPositiveButton("Continue", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){startActivity(new Intent(activity,BillCalculationActivity.class));}});
													negativeRestaurantAlertBuilder.setNegativeButton("Cancel", new DialogInterface.OnClickListener(){public void onClick(DialogInterface dialog, int id){dialog.cancel();}});
													negativeRestaurantAlertBuilder.show();
												}
												else
													startActivity(new Intent(activity,BillCalculationActivity.class));
		}
	}
}