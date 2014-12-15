package com.grouptuity.billreview;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.LinearLayout.LayoutParams;
import com.grouptuity.*;
import com.grouptuity.Grouptuity.PaymentType;
import com.grouptuity.billreview.DinerPaymentList.DinerPaymentListController;
import com.grouptuity.billreviewindividual.IndividualBillActivity;
import com.grouptuity.model.Diner;
import com.grouptuity.model.Payment;
import com.grouptuity.venmo.*;
import com.grouptuity.view.*;
import com.grouptuity.view.AlertDialogList.Controller;

public class BillReviewActivity extends ActivityTemplate<BillReviewModel>
{
	private DinerPaymentList dinerPaymentList;
	private InstructionsBar instructions;
	private BillReviewSlidingBar bottomBar;
	private BillReviewEmailList billReviewEmailList;
	private AlertDialogList paymentSelector,iouSelector,venmoSelector,venmoContactSelector,emailSelector;
	private AlertDialog venmoContactInput,emailInput;
	private List<PaymentType> paymentTypesCache;
	private Diner tempDeferredPaymentRecipient;
	private Payment venmoTransaction;

	final private int VenmoActivityRequestCode = 125343;
	final private int INDIVIDUAL_BILL = 1;
	final private int SEND_RECIEPT = 2;

	protected void createActivity(Bundle bundle)
	{
		model = new BillReviewModel(this,bundle);
		menuInt = R.menu.bill_review_menu;
		paymentTypesCache = Arrays.asList(PaymentType.values());

		titleBar = new TitleBar(this,"Review Bill",TitleBar.BACK_AND_MENU);
		mainLinearLayout.addView(titleBar,Grouptuity.fillWrapLP());

		billReviewEmailList = new BillReviewEmailList(this, new BillReviewEmailList.BillReviewEmailListController()
		{
			public void onDinerSelected(Diner d)
			{
				model.currentDiner = d;
				if(d.contact.getDefaultEmailAddress()==null && (d.contact.emailAddresses == null || d.contact.emailAddresses.length == 0))
					showEmailInputAlert(d);
				else 
					showEmailChoiceAlert(d);
			}
		});

		dinerPaymentList = new DinerPaymentList(this,new DinerPaymentListController()
		{
			public void onDinerSelected(Diner d){model.currentDiner = d;advanceActivity(INDIVIDUAL_BILL);}
			public void onPaymentMethodSwitch(Diner d){model.currentDiner = d;showPaymentSelectionAlert();}
			public void onDinerContactPaymentInfoEdit(final Diner d)
			{
				model.currentDiner = d;
				String emailOption = (d.contact.venmoUsername==null)?"Enter Email Address":"Change Email Address";
				String venmoOption = (d.contact.venmoUsername==null)?"Enter Venmo Username":"Change Venmo Username";

				AlertDialogList dialogList = new AlertDialogList(activity,new String[]{"View Personalized Bill",emailOption,venmoOption}, null, new Controller()
				{
					public void optionSelected(int i)
					{
						switch(i)
						{
							case 0:	advanceActivity(INDIVIDUAL_BILL);
									break;
							case 1:	if(d.contact.getDefaultEmailAddress()==null && (d.contact.emailAddresses == null || d.contact.emailAddresses.length == 0))
										showEmailInputAlert(d);
									else 
										showEmailChoiceAlert(d);
									break;
							case 2:	showVenmoContactAlert(d,false,true);
									break;
						}
					}
				});
				dialogList.setTitle(d.name);
				dialogList.show();
			}
		});

		instructions = new InstructionsBar(this);
		instructions.setInstructionsText("Click icons to change payment methods");
		instructions.activateVenmoMode();
		instructions.setController(new InstructionsBar.InstructionsBarController()
		{
			public void onClick()
			{
				final Payment[] payments = Grouptuity.getBill().getPendingVenmoPayments();
				if(payments.length==1)
				{
					executeVenmoPayment(payments[0]);
				}
				else
				{
					VenmoPaymentListingAlertDialog vplad = new VenmoPaymentListingAlertDialog(activity,payments,new VenmoPaymentListingAlertDialog.Controller()
					{
						public void paymentSelected(Payment p){executeVenmoPayment(p);}
					});
					vplad.show();
				}
			}
		});

		bottomBar = new BillReviewSlidingBar(this,billReviewEmailList);
		bottomBar.setController(new SlidingBar.SlidingBarController()
		{
			public void onMotionStarted(){}
			public void onOpened(){}
			public void onClosed(){}
			public void onDisabledButtonClick(String buttonName){}
			public void onButtonClick(String name)
			{
				switch(BillReviewSlidingBar.Button.getButton(name))
				{
					case PAY:					break;//TODO advanceCode = RECIEPT_BILL;refreshActivity();advanceActivity();break;
					case RECEIPT_PREPROCESSING:	model.setNewState(BillReviewModel.SET_EMAILS);break;
					case CREATE_EMAIL:			advanceActivity(SEND_RECIEPT);model.revertToState(BillReviewModel.DEFAULT);break;
					case BACK: 					model.revertToState(BillReviewModel.DEFAULT);break;
				}
			}
		});
	}
	public void showPaymentSelectionAlert()
	{
		ArrayList<PaymentType> paymentTypesList = new ArrayList<PaymentType>(paymentTypesCache);
		if(!Grouptuity.VENMO_ENABLED.getValue())// || !model.currentDiner.venmoEnabled) <== uncomment to only add venmo for people who have it enabled
			paymentTypesList.remove(PaymentType.VENMO);
		boolean noMoreDeferredPayments = true;
		for(Diner d: Grouptuity.getBill().diners)
		{
			if((d.defaultPaymentType==PaymentType.CASH || d.defaultPaymentType==PaymentType.CREDIT) && d!=model.currentDiner)
			{
				noMoreDeferredPayments = false;
				break;
			}
		}
		if(noMoreDeferredPayments)
			for(PaymentType type: PaymentType.values())
				if(type.deferred)
					paymentTypesList.remove(type);

		final PaymentType[] paymentTypes = new PaymentType[paymentTypesList.size()];
		paymentTypesList.toArray(paymentTypes);
		String[] paymentMethodNames = new String[paymentTypes.length];
		int[] paymentMethodIcons = new int[paymentTypes.length];
		for(int i=0; i<paymentMethodNames.length; i++)
		{
			paymentMethodNames[i] = paymentTypes[i].description;
			paymentMethodIcons[i] = paymentTypes[i].imageInt;
		}
		paymentSelector = new AlertDialogList(this,paymentMethodNames,paymentMethodIcons, new Controller()
		{
			public void optionSelected(int i)
			{
				switch(paymentTypes[i])
				{
					case IOU_EMAIL:	showIOUSelectionAlert();break;
					case VENMO:		showVenmoSelectionAlert(false);break;
					default:		model.setPaymentType(paymentTypes[i]);
									refreshActivity();
				}
			}
		});
		paymentSelector.setTitle("Select payment method for\n"+model.currentDiner.name);
		paymentSelector.show();
	}
	public void showIOUSelectionAlert()
	{
		//Identify the diners eligible to receive the IOU
		ArrayList<Diner> diners = Grouptuity.getBill().diners;
		final Diner[] tempSelectableDiners = new Diner[diners.size()];
		int skip = 0;
		for(int i=0; i<tempSelectableDiners.length; i++)
		{
			if(diners.get(i).equals(model.currentDiner) || !Grouptuity.getBill().canPayByDeferredMethod(model.currentDiner,diners.get(i)))
				skip++;
			else
				tempSelectableDiners[i] = diners.get(i);
		}
		final Diner[] selectableDiners = new Diner[diners.size()-skip];
		final String[] iouNames = new String[selectableDiners.length];
		int index = 0;
		for(int i=0; i<tempSelectableDiners.length; i++)
		{
			if(tempSelectableDiners[i]!=null)
			{
				selectableDiners[index] = tempSelectableDiners[i];
				iouNames[index] = selectableDiners[index].name;
				index++;
			}
		}

		//Create an alert dialog to select the IOU recipient
		iouSelector = new AlertDialogList(this,iouNames,null, new Controller()
		{
			public void optionSelected(int i)
			{
				model.currentDiner.defaultPayee = selectableDiners[i];
				model.setPaymentType(PaymentType.IOU_EMAIL);
				refreshActivity();
			}
		});
		iouSelector.setTitle("Who does "+model.currentDiner.name+" owe?");
		iouSelector.show();
	}
	public void showVenmoSelectionAlert(final boolean onlyUpdateUserName)
	{
		//Identify the diners eligible to receive the Venmo payment
		ArrayList<Diner> diners = Grouptuity.getBill().diners;
		final Diner[] tempSelectableDiners = new Diner[diners.size()];
		int skip = 0;
		for(int i=0; i<tempSelectableDiners.length; i++)
		{
			if(diners.get(i).equals(model.currentDiner) || !Grouptuity.getBill().canPayByDeferredMethod(model.currentDiner,diners.get(i)))
				skip++;
			else
				tempSelectableDiners[i] = diners.get(i);
		}
		final Diner[] selectableDiners = new Diner[diners.size()-skip];
		final String[] venmoNames = new String[selectableDiners.length];
		final int[] venmoIcons = new int[selectableDiners.length];
		int index = 0;
		for(int i=0; i<tempSelectableDiners.length; i++)
		{
			if(tempSelectableDiners[i]!=null)
			{
				selectableDiners[index] = tempSelectableDiners[i];
				venmoNames[index] = selectableDiners[index].name;
				venmoIcons[index] = (selectableDiners[index].contact.venmoUsername!=null)? R.drawable.payment_venmo : R.drawable.payment_venmo_invite;
				index++;
			}
		}

		//Create an alert dialog to select the Venmo payment recipient
		venmoSelector = new AlertDialogList(this,venmoNames,venmoIcons, new Controller()
		{
			public void optionSelected(int i)
			{
				tempDeferredPaymentRecipient = selectableDiners[i];
				if(model.currentDiner.contact.venmoUsername==null)
					showVenmoContactAlert(model.currentDiner,true,onlyUpdateUserName);
				else if(tempDeferredPaymentRecipient.contact.venmoUsername==null)
					showVenmoContactAlert(tempDeferredPaymentRecipient,false,onlyUpdateUserName);
				else
				{
					model.currentDiner.defaultPayee = tempDeferredPaymentRecipient;
					model.setPaymentType(PaymentType.VENMO);
					refreshActivity();
				}
			}
		});
		venmoSelector.setTitle("Who will "+model.currentDiner.name+" pay on Venmo?");
		venmoSelector.show();
	}
	public void showVenmoContactAlert(final Diner d, final boolean isPromptForPayer, final boolean onlyUpdateUserName)
	{
		String[] emails = d.contact.emailAddresses;
		int emailsCount = emails==null?0:emails.length;
//		String[] phones = d.contact.phoneNumbers;
//		int phonesCount = phones==null?0:phones.length;
		final String[] contactOptions = new String[emailsCount + /*phonesCount +*/ 1];
		contactOptions[0] = " (enter other email)";
		for(int i = 1; i<contactOptions.length; i++)
		{
			if(i<emailsCount+1)
				contactOptions[i] = emails[i-1];
//			else
//				contactOptions[i] = phones[i-emailsCount-1];
		}

		//if no contact info there's no point in showing this list, take straight to entry
		if(contactOptions.length == 1){showVenmoContactInput(d,isPromptForPayer,onlyUpdateUserName);return;}

		//Show an alert dialog to select the contact method to use for Venmo
		venmoContactSelector= new AlertDialogList(this,contactOptions,null, new Controller()
		{
			public void optionSelected(int i)
			{
				if(i>0)
				{
					d.contact.venmoUsername = contactOptions[i];
					if(isPromptForPayer && tempDeferredPaymentRecipient.contact.venmoUsername==null)
						showVenmoContactAlert(tempDeferredPaymentRecipient,false,false);
					else if(!onlyUpdateUserName)
					{
						model.currentDiner.defaultPayee = tempDeferredPaymentRecipient;
						model.setPaymentType(PaymentType.VENMO);
						refreshActivity();
					}
					else
					{
						Grouptuity.getBill().invalidatePayments();
						refreshActivity();
					}
				}
				else
					showVenmoContactInput(d,isPromptForPayer,onlyUpdateUserName);
			}
		});
		venmoContactSelector.setTitle("Which email will "+d.name+" use on Venmo?");
		venmoContactSelector.show();
	}
	public void showVenmoContactInput(final Diner d,final boolean isPayerPrompt, final boolean onlyUpdateUserName) 
	{
		AlertDialog.Builder venmoContactInputBuilder = new AlertDialog.Builder(this);
		venmoContactInputBuilder.setCancelable(true);
		venmoContactInputBuilder.setTitle("Venmo Contact");
		venmoContactInputBuilder.setMessage("Enter the email "+d.name+" will use on Venmo.");
		venmoContactInputBuilder.setIcon(R.drawable.payment_venmo);
		final EditText input = new EditText(this);
		venmoContactInputBuilder.setView(input);
		venmoContactInputBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int id)
			{
				//TODO need to prevent bad inputs
				d.contact.venmoUsername = input.getText().toString();
				d.contact.setDefaultEmailAddress(d.contact.venmoUsername);
				if(isPayerPrompt && tempDeferredPaymentRecipient.contact.venmoUsername==null)
					showVenmoContactAlert(tempDeferredPaymentRecipient,false,false);
				else if(!onlyUpdateUserName)
				{
					model.currentDiner.defaultPayee = tempDeferredPaymentRecipient;
					model.setPaymentType(PaymentType.VENMO);
					refreshActivity();
				}
				else
				{
					Grouptuity.getBill().invalidatePayments();
					refreshActivity();
				}
			}
		});
		venmoContactInput = venmoContactInputBuilder.create();
		venmoContactInput.show();
	}
	
	public void showEmailChoiceAlert(final Diner d)
	{
		final String[] dinerEmails = d.contact.emailAddresses;
		int emailsCount = dinerEmails==null?0:dinerEmails.length;
		final String[] contactOptions = new String[emailsCount + 2];
		final int last = emailsCount + 1;
		contactOptions[0] = " (enter other email)";
		contactOptions[last] = " (don't email "+d.name+")";
		for (int i = 1; i < contactOptions.length-1; i++)
			contactOptions[i] = dinerEmails[i-1];
		emailSelector= new AlertDialogList(this,contactOptions,null, new Controller()
		{
			public void optionSelected(int i)
			{
				if(i>0)
				{
					if(i==last)
						d.contact.setDefaultEmailAddress(null);
					else
						d.contact.setDefaultEmailAddress(contactOptions[i]);
					refreshActivity();
				}
				else
					showEmailInputAlert(d);
			}
		});
		emailSelector.setTitle("Choose an email address for "+d.name+":");
		emailSelector.show();
	}
	public void showEmailInputAlert(final Diner d) 
	{
		AlertDialog.Builder emailInputBuilder = new AlertDialog.Builder(this);
		emailInputBuilder.setCancelable(true);
		emailInputBuilder.setTitle("Enter Email");
		emailInputBuilder.setMessage("Enter the email address to send "+d.name+"'s reciept to.");
		emailInputBuilder.setIcon(R.drawable.payment_iou_email);
		final EditText input = new EditText(this);
		emailInputBuilder.setView(input);
		emailInputBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int id)
			{
				//TODO need to prevent bad inputs
				d.contact.setDefaultEmailAddress(input.getText().toString());
				refreshActivity();
			}
		}).setNegativeButton("Skip Diner", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				dialog.cancel();
				d.contact.setDefaultEmailAddress(null);
				refreshActivity();
			}
		});
		emailInput = emailInputBuilder.create();
		emailInput.show();
	}
	
/*	public void showEmailChoiceAlert(final Diner d, final String[] emails, final int index)
	{

		final String[] dinerEmails = d.contact.emailAddresses;
		int emailsCount = dinerEmails==null?0:dinerEmails.length;
		final String[] contactOptions = new String[emailsCount + 2];
		final int last = emailsCount + 1;
		contactOptions[0] = "Enter new email address";
		contactOptions[last] = "Skip";
		for (int i = 1; i < contactOptions.length-1; i++)
			contactOptions[i] = dinerEmails[i-1];
		emailSelector= new AlertDialogList(this,contactOptions,null, new Controller()
		{
			public void optionSelected(int i)
			{
				if(i>0)
				{
					if(i==last)
						emails[index] = null;
					else
						emails[index] = contactOptions[i];
					refreshActivity();
					recursiveSetUserEmails(emails,index+1);
				}
				else
					showEmailInputAlert(d, emails, index);
			}
		});
		emailSelector.setTitle("Choose an email address for "+d.name+":");
		emailSelector.show();
	}
	public void showEmailInputAlert(final Diner d, final String[] emails, final int index) 
	{
		AlertDialog.Builder emailInputBuilder = new AlertDialog.Builder(this);
		emailInputBuilder.setCancelable(true);
		emailInputBuilder.setTitle("Select Email");
		emailInputBuilder.setMessage("Enter the email address to send "+d.name+"'s reciept to.");
		emailInputBuilder.setIcon(R.drawable.mainmenu_quickbill);
		final EditText input = new EditText(this);
		emailInputBuilder.setView(input);
		emailInputBuilder.setPositiveButton("OK", new DialogInterface.OnClickListener()
		{
			@Override
			public void onClick(DialogInterface dialog, int id)
			{
				emails[index] = input.getText().toString();
				refreshActivity();
				recursiveSetUserEmails(emails,index+1);
			}
		}).setNegativeButton("Skip Diner", new DialogInterface.OnClickListener()
		{
			public void onClick(DialogInterface dialog, int id)
			{
				dialog.cancel();
				emails[index] = null;
				refreshActivity();
				recursiveSetUserEmails(emails,index+1);
			}
		});
		emailInput = emailInputBuilder.create();
		emailInput.show();
	}*/

	protected void createHorizontalLayout(){/*TODO*/}
	protected void createVerticalLayout()
	{
		LinearLayout.LayoutParams ibLP = new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);
		ibLP.bottomMargin = -instructions.panel.style.bottomContraction;
		mainLinearLayout.addView(instructions,ibLP);
		mainLinearLayout.addView(dinerPaymentList,Grouptuity.fillWrapLP(1.0f));
		mainLinearLayout.addView(bottomBar.getPlaceholder(),Grouptuity.wrapWrapLP());
		rootLayout.addView(bottomBar,Grouptuity.fillFillLP());
	}
	protected String createHelpString()
	{
		return	"Click the payment icons along the left side of the screen to change each diner's payment instructions.\n\n"+
				"Click a diner name to view personalized bills for each diner.\n\n"+
				"Send a receipt email by clicking \"Send Receipt to Diners.\".  If any diners are paying by Venmo, the email will contain a payment link.";
	}

	@Override
	protected GrouptuityPreference<Boolean> getTutorialPopupFlag(){return Grouptuity.SHOW_TUTORIAL_BILLREVIEW;}

	protected void processStateChange(int state)
	{
		switch(state)
		{
			case BillReviewModel.START:			model.setNewState(BillReviewModel.DEFAULT);
			case BillReviewModel.DEFAULT:		bottomBar.close();break;
			case BillReviewModel.SET_EMAILS:	for(Diner d : Grouptuity.getBill().diners)
												{
													if(!d.contact.isDefaultEmailAddressSet() && d.contact.emailAddresses != null && d.contact.emailAddresses.length > 0)
														d.contact.setDefaultEmailAddress(d.contact.emailAddresses[0]);
												}
												model.setNewState(BillReviewModel.SET_EMAILS);
												/*UserTracking.reportBill(model.bill, mixpanelMetrics);*/
												bottomBar.open();
												break;
			case BillReviewModel.HELP:			break;
		}
	}

	public void onResume()
	{
		if(!Grouptuity.VENMO_ENABLED.getValue())
			model.clearVenmoPayments();
		super.onResume();
	}
	public boolean onPrepareOptionsMenu (Menu menu)
	{
		if(model.isPaymentComplete())
			menu.getItem(0).setEnabled(false);
		return true;
	}
	public void onBackPressed()
	{
		switch(model.getState())
		{
			case BillReviewModel.SET_EMAILS: model.revertToState(BillReviewModel.DEFAULT);break;
			default: super.onBackPressed();
		}
	}
	protected boolean onOptionsMenuSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
			case R.id.reset:	for(Diner d: Grouptuity.getBill().diners)
								{
									d.defaultPayee = null;
									if(d.contact!=null && d.contact.defaultPaymentType!=null)
										d.defaultPaymentType = d.contact.defaultPaymentType;
									else
										d.defaultPaymentType = Grouptuity.PaymentType.values()[Grouptuity.DEFAULT_PAYMENT_TYPE.getValue()];
								}
								Grouptuity.getBill().invalidatePayments();
								refreshActivity();
								break;
		}
		return false;
	}
	protected void onAdvanceActivity(int code)
	{
		switch(code)
		{
			case INDIVIDUAL_BILL:	if(model.currentDiner!=null)
									{
										Intent intent = new Intent(activity,IndividualBillActivity.class);
										intent.putExtra("com.grouptuity.hasCurrentDinerIndex",true);
										intent.putExtra("com.grouptuity.currentDinerIndex",Grouptuity.getBill().diners.indexOf(model.currentDiner));
										startActivity(intent);
									}
									break;
			case SEND_RECIEPT:		String[] emails = new String[Grouptuity.getBill().diners.size()];
									for(int i=0;i<emails.length;i++)
										emails[i] = Grouptuity.getBill().diners.get(i).contact.getDefaultEmailAddress();
									sendRecieptEmail(emails);
									break;
		}
	}
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		if(Grouptuity.FAKE_VENMO_PAYMENTS)
		{
			Grouptuity.getBill().recordVenmoTransaction(venmoTransaction.amount, venmoTransaction.payer, venmoTransaction.payee);
			return;
		}
		
		try
		{
			if(data.getExtras()==null)
			{
				Grouptuity.log("venmo app result error: data is null");
				return;
			}
			else
			{
				String postPaymentMessage = data.getStringExtra("post_payment_message");
				if(postPaymentMessage!=null && postPaymentMessage.contains("success"))
				{
					Grouptuity.getBill().recordVenmoTransaction(venmoTransaction.amount, venmoTransaction.payer, venmoTransaction.payee);
				}
			}
		}
		catch(Exception e){Grouptuity.log(e);}
		refreshActivity();
	}

	private void executeVenmoPayment(Payment p)
	{
		String action, contact, note;
		if(p.payee==Grouptuity.getSelf())
		{
			action = "charge";
			note = "Dining out using Grouptuity";
			contact = p.payer.contact.venmoUsername;
		}
		else
		{
			action = "pay";
			note = "Dining out using Grouptuity";
			contact = p.payee.contact.venmoUsername;
		}
		java.text.NumberFormat numberFormat = java.text.NumberFormat.getNumberInstance();
		numberFormat.setMinimumFractionDigits(2);
		numberFormat.setMaximumFractionDigits(2);
		numberFormat.setGroupingUsed(false);
		String amount = numberFormat.format(p.amount);

		venmoTransaction = p;

		try
		{
			Intent venmoIntent = VenmoSDK.openVenmoPayment(Grouptuity.VENMO_APP_ID, "Grouptuity", contact, amount, note, action);
			startActivityForResult(venmoIntent, VenmoActivityRequestCode);
		}
		catch(android.content.ActivityNotFoundException e)
		{
			Intent venmoIntent = new Intent(this, VenmoWebViewActivity.class);
			String venmo_uri = VenmoSDK.openVenmoPaymentInWebView(Grouptuity.VENMO_APP_ID, "Grouptuity", contact, amount, note, action);
			venmoIntent.putExtra("url", venmo_uri);
			startActivityForResult(venmoIntent, VenmoActivityRequestCode);
		}
	}

	private void sendRecieptEmail(String[] emails)
	{
		String emailString = "";//emails[0];
		for (int i = 0; i < emails.length; i++)
			emailString += (emailString.length()>0 ? "," : "") + (emails[i]==null ? "" : emails[i]);	
		
		Calendar cal = Calendar.getInstance();
		SimpleDateFormat dateFormat = new SimpleDateFormat("EEEE, MMMM dd");
		String date = dateFormat.format(cal.getTime());
		
		String emailTitle = "Grouptuity reciept from "+date;
		String emailBody = generateRecieptEmailBody();
		
		Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:"+ Uri.encode(emailString) + "?subject=" + Uri.encode(emailTitle) + "&body=" + Uri.encode(emailBody))); startActivity(intent);
	}
	private String generateRecieptEmailBody()
	{
		String underline = "";for(int n=0;n<25;n++) underline+="\u00AF";
		String body = "SPLIT BILL RECEIPT\nSent from Grouptuity for Android\nhttps://play.google.com/store/apps/details?id=com.grouptuity\n\n";
		String venmoPayments = "";
		String iouPayments = "";
		String billString = "";
		for(Diner d : Grouptuity.getBill().diners) {
			for(Payment p : d.paymentsOut) {
				switch(p.type)
				{
					case VENMO:	venmoPayments += "\u2022 " + p.payer.name+" owes "+ Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY, p.amount)+" to "+ p.payee.name+" on Venmo. Pay here: "+ getVenmoPaymentLink(p.amount,p.payee.contact.venmoUsername)+"\n";break;
					case IOU_EMAIL:	venmoPayments += "\u2022 " + p.payer.name+" owes "+ Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY, p.amount)+" to "+ p.payee.name+"\n"; break;
					default:	break;
				}
			}
			billString+= d.name+"\n"+d.getReceiptNotes()+"\n";
		}
		body+="OUTSTANDING PAYMENTS:\n"+underline+"\n"+venmoPayments+"\n"+iouPayments+"\nFULL BILL:\n"+underline+"\n"+billString;
		
		return body;
	}
	private String getVenmoPaymentLink(double amount, String payee){return "https://venmo.com/?txn=pay&amount="+Grouptuity.formatNumber(Grouptuity.NumberFormat.NUMBER,amount)+"&recipients="+payee;}
}