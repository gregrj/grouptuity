package com.grouptuity.view;

import java.io.IOException;
import java.io.InputStream;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.grouptuity.*;
import com.grouptuity.Grouptuity.*;
import com.grouptuity.model.*;
import com.grouptuity.replacements.AsyncTaskReplacement;
import com.grouptuity.style.composites.*;

public class ContactListing extends Panel
{
	public ContactListingStyle style;
	public Diner diner;
	public Contact contact;
	private TextView contactName, subname, total, venmoName;
	private LinearLayout venmoLayout;
	final public ImageView imageView;
	final private Paint linePaint;
	private boolean holdHighlight;
	public boolean lastListing;
	public Bitmap defaultPhoto;
	Bitmap[] paymentImages;

	public static enum ContactListingStyle{FACE_ICON, DIRECTORY_LISTING, ITEM_ENTRY, PAYMENT_MODE_SELECTOR, EMAIL_LISTING, VENMO_PAYMENTS;}

	public ContactListing(ActivityTemplate<?> context, ContactListingStyle cls){this(context, null, null, cls);}
	public ContactListing(ActivityTemplate<?> context, Diner d, ContactListingStyle cls){this(context, d, d.contact, cls);}
	public ContactListing(ActivityTemplate<?> context, Contact c, ContactListingStyle cls){this(context, null, c, cls);}
	@SuppressWarnings("unchecked")
	private ContactListing(ActivityTemplate<?> context, Diner d, Contact c, ContactListingStyle cls)
	{
		super((ActivityTemplate<ModelTemplate>) context,new StyleTemplate());
		diner = d;
		if(diner==null)
			contact = c;
		else
			contact = diner.contact;
		style = cls;

		linePaint = new Paint();
		linePaint.setColor(Color.LTGRAY);
		setGravity(Gravity.CENTER_VERTICAL);

		imageView = new ImageView(context);
		switch(style)
		{
			case FACE_ICON:				setOrientation(LinearLayout.VERTICAL);
										setStyle(ViewState.DEFAULT,new ListingStyleFaceIcon());
										setStyle(ViewState.HIGHLIGHTED,new ListingStyleFaceIconHighlighted());
										addView(imageView,Grouptuity.wrapWrapLP());

										contactName = new TextView(context)
										{
											protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
											{
												super.onMeasure(widthMeasureSpec, heightMeasureSpec);
												setMeasuredDimension(imageView.getMeasuredWidth(),getMeasuredHeight());
											}
										};
										contactName.setTextSize(TypedValue.COMPLEX_UNIT_DIP,10.0f);
										contactName.setGravity(Gravity.CENTER_VERTICAL);
										addView(contactName,Grouptuity.wrapWrapLP());

										setOnTouchListener(new InputListenerTemplate(false)
										{
											protected boolean handleRelease(){setViewState(ViewState.DEFAULT);return true;}
											protected boolean handlePress(){setViewState(ViewState.HIGHLIGHTED);return true;}
											protected boolean handleLongClick(){return true;}
											protected boolean handleHold(long holdTime){return true;}
											protected boolean handleClick(){clickCallback(diner);return true;}
										});
										break;
			case DIRECTORY_LISTING:		setStyle(ViewState.DEFAULT,new ListingStyle());
										setStyle(ViewState.HIGHLIGHTED,new ListingStyleHighlighted());
										setOrientation(LinearLayout.HORIZONTAL);

										contactName = new TextView(context);
										contactName.setTextSize(TypedValue.COMPLEX_UNIT_DIP,20.0f);
										contactName.setPadding(10,0,0,0);
										contactName.setTextColor(Grouptuity.softTextColor);
										contactName.setHorizontallyScrolling(true);
										contactName.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);

										addView(imageView,Grouptuity.wrapWrapLP());
										addView(contactName,Grouptuity.wrapWrapLP(1.0f));

										setOnTouchListener(new InputListenerTemplate(false)
										{
											protected boolean handleRelease(){setViewState(ViewState.DEFAULT);return true;}
											protected boolean handlePress(){setViewState(ViewState.HIGHLIGHTED);return true;}
											protected boolean handleLongClick(){return true;}
											protected boolean handleHold(long holdTime){return true;}
											protected boolean handleClick(){clickCallback(contact);return true;}
										});
										break;
			case ITEM_ENTRY:			setStyle(ViewState.DEFAULT,new ListingStyle());
										setStyle(ViewState.HIGHLIGHTED,new ListingStyleHighlighted());
										setOrientation(LinearLayout.HORIZONTAL);

										contactName = new TextView(context);
										contactName.setTextSize(TypedValue.COMPLEX_UNIT_DIP,20.0f);
										contactName.setTextColor(Grouptuity.softTextColor);
										contactName.setPadding(10,0,0,0);
										contactName.setHorizontallyScrolling(true);
										contactName.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);

										total = new TextView(context);
										total.setPadding(10,0,0,0);
										total.setTextSize(TypedValue.COMPLEX_UNIT_DIP,20.0f);
										total.setTextColor(Grouptuity.softTextColor);
										total.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);

										addView(imageView,Grouptuity.wrapWrapLP());
										addView(contactName,Grouptuity.wrapWrapLP(1.0f));
										addView(total,Grouptuity.wrapWrapLP());

										setOnTouchListener(new InputListenerTemplate(false)
										{
											protected boolean handleRelease(){if(!holdHighlight)setViewState(ViewState.DEFAULT);releaseCallback(contact);return true;}
											protected boolean handlePress(){setViewState(ViewState.HIGHLIGHTED);pressCallback(contact);return true;}
											protected boolean handleLongClick(){return true;}
											protected boolean handleHold(long holdTime){return true;}
											protected boolean handleClick(){clickCallback(diner);return true;}
										});
										break;
			case PAYMENT_MODE_SELECTOR:	setOrientation(LinearLayout.HORIZONTAL);

										final Panel left = new Panel(context,new ListingStyle());
										left.setStyle(ViewState.HIGHLIGHTED,new ListingStyleHighlighted());
										left.setOrientation(LinearLayout.HORIZONTAL);
										left.setGravity(Gravity.CENTER_VERTICAL);
										left.setOnTouchListener(new InputListenerTemplate(false)
										{
											protected boolean handleRelease(){left.setViewState(ViewState.DEFAULT);return true;}
											protected boolean handlePress(){left.setViewState(ViewState.HIGHLIGHTED);return true;}
											protected boolean handleLongClick(){return true;}
											protected boolean handleHold(long holdTime){return true;}
											protected boolean handleClick(){paymentToggle(diner);return true;}
										});

										final Panel right = new Panel(context,new ListingStyle())
										{
											protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
											{
												super.onMeasure(widthMeasureSpec, heightMeasureSpec);
												if(getMeasuredHeight()<left.getMeasuredHeight())
													setMeasuredDimension(getMeasuredWidth(), left.getMeasuredHeight());
												else
													setMeasuredDimension(getMeasuredWidth(),getMeasuredHeight());
											}
										};
										right.setStyle(ViewState.HIGHLIGHTED,new ListingStyleHighlighted());
										right.setOrientation(LinearLayout.HORIZONTAL);
										right.setGravity(Gravity.CENTER_VERTICAL);
										right.setOnTouchListener(new InputListenerTemplate(true)
										{
											protected boolean handleRelease(){right.setViewState(ViewState.DEFAULT);return true;}
											protected boolean handlePress(){right.setViewState(ViewState.HIGHLIGHTED);return true;}
											protected boolean handleLongClick(){longClickCallback(diner);return true;}
											protected boolean handleHold(long holdTime){return true;}
											protected boolean handleClick(){clickCallback(diner);return true;}
										});

										LinearLayout dualRow = new LinearLayout(context);
										dualRow.setOrientation(LinearLayout.VERTICAL);
										dualRow.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);

										contactName = new TextView(context);
										contactName.setTextSize(TypedValue.COMPLEX_UNIT_DIP,20.0f);
										contactName.setTextColor(Grouptuity.softTextColor);
										contactName.setPadding(5,0,0,0);
										contactName.setHorizontallyScrolling(true);
										contactName.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);

										venmoLayout = new LinearLayout(context);
										venmoLayout.setOrientation(LinearLayout.HORIZONTAL);
										venmoLayout.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);

										ImageView venmoIcon = new ImageView(context);
										venmoIcon.setImageResource(R.drawable.payment_mini_venmo);
										venmoIcon.setPadding(5,0,0,0);

										venmoName = new TextView(context);
										venmoName.setTextSize(TypedValue.COMPLEX_UNIT_DIP,14.0f);
										venmoName.setTextColor(Grouptuity.softTextColor);
										venmoName.setPadding(10,0,0,0);
										venmoName.setHorizontallyScrolling(false);
										venmoName.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);

										subname = new TextView(context);
										subname.setTextSize(TypedValue.COMPLEX_UNIT_DIP,14.0f);
										subname.setTextColor(Grouptuity.softTextColor);
										subname.setPadding(5,0,0,0);
										subname.setHorizontallyScrolling(false);
										subname.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);

										total = new TextView(context);
										total.setPadding(10,0,10,0);
										total.setTextSize(TypedValue.COMPLEX_UNIT_DIP,20.0f);
										total.setTextColor(Grouptuity.softTextColor);
										total.setGravity(Gravity.CENTER_VERTICAL);

										addView(left,Grouptuity.wrapWrapLP());
										addView(right,Grouptuity.wrapWrapLP(1.0f));
										left.addView(imageView,Grouptuity.wrapWrapLP());
										right.addView(dualRow,Grouptuity.wrapWrapLP(1.0f));
										dualRow.addView(contactName,Grouptuity.wrapWrapLP());
										dualRow.addView(venmoLayout,Grouptuity.wrapWrapLP());
										venmoLayout.addView(venmoIcon,Grouptuity.wrapWrapLP());
										venmoLayout.addView(venmoName,Grouptuity.wrapWrapLP());
										dualRow.addView(subname,Grouptuity.wrapWrapLP());
										right.addView(total,Grouptuity.wrapWrapLP());
										break;
			case EMAIL_LISTING:			setStyle(ViewState.DEFAULT,new ListingStyle());
										setStyle(ViewState.HIGHLIGHTED,new ListingStyleHighlighted());
										setOrientation(LinearLayout.HORIZONTAL);

										LinearLayout dual = new LinearLayout(context);
										dual.setOrientation(LinearLayout.VERTICAL);
										dual.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);

										contactName = new TextView(context);
										contactName.setTextSize(TypedValue.COMPLEX_UNIT_DIP,20.0f);
										contactName.setTextColor(Grouptuity.softTextColor);
										contactName.setPadding(10,0,0,0);
										contactName.setHorizontallyScrolling(true);
										contactName.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);

										subname = new TextView(context);
										subname.setTextSize(TypedValue.COMPLEX_UNIT_DIP,14.0f);
										subname.setTextColor(Grouptuity.softTextColor);
										subname.setPadding(10,0,0,0);
										subname.setHorizontallyScrolling(false);
										subname.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);

										addView(imageView,Grouptuity.wrapWrapLP());
										addView(dual,Grouptuity.wrapWrapLP(1.0f));
										dual.addView(contactName,Grouptuity.wrapWrapLP());
										dual.addView(subname,Grouptuity.wrapWrapLP());

										setOnTouchListener(new InputListenerTemplate(false)
										{
											protected boolean handleRelease(){setViewState(ViewState.DEFAULT);return true;}
											protected boolean handlePress(){setViewState(ViewState.HIGHLIGHTED);return true;}
											protected boolean handleLongClick(){return true;}
											protected boolean handleHold(long holdTime){return true;}
											protected boolean handleClick(){clickCallback(diner);return true;}
										});										
										break;
			case VENMO_PAYMENTS:		setStyle(ViewState.DEFAULT,new ListingStyle());
										setStyle(ViewState.HIGHLIGHTED,new ListingStyleHighlighted());
										setOrientation(LinearLayout.HORIZONTAL);
							
										contactName = new TextView(context);
										contactName.setTextSize(TypedValue.COMPLEX_UNIT_DIP,20.0f);
										contactName.setTextColor(Grouptuity.softTextColor);
										contactName.setPadding(10,0,0,0);
										contactName.setHorizontallyScrolling(true);
										contactName.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);
							
										total = new TextView(context);
										total.setPadding(10,0,0,0);
										total.setTextSize(TypedValue.COMPLEX_UNIT_DIP,20.0f);
										total.setTextColor(Grouptuity.softTextColor);
										total.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);
							
										addView(imageView,Grouptuity.wrapWrapLP());
										addView(contactName,Grouptuity.wrapWrapLP(1.0f));
										addView(total,Grouptuity.wrapWrapLP());
							
										setOnTouchListener(new InputListenerTemplate(false)
										{
											protected boolean handleRelease(){if(!holdHighlight)setViewState(ViewState.DEFAULT);releaseCallback(contact);return true;}
											protected boolean handlePress(){setViewState(ViewState.HIGHLIGHTED);pressCallback(contact);return true;}
											protected boolean handleLongClick(){return true;}
											protected boolean handleHold(long holdTime){return true;}
											protected boolean handleClick(){clickCallback(diner);return true;}
										});
										break;
		}
	}

	protected void dispatchDraw(Canvas canvas)
	{
		super.dispatchDraw(canvas);
		if(style!=ContactListingStyle.FACE_ICON || viewState==ViewState.HIGHLIGHTED)
			canvas.drawLine(0, getHeight()-1, getWidth(), getHeight()-1, linePaint);
		//line at the bottom, lastlisting not needed
//		canvas.drawLine(0, 0, getWidth(), 0, linePaint);
//		if(lastListing)
//			canvas.drawLine(0, getHeight()-1, getWidth(), getHeight()-1, linePaint);
		if(style==ContactListingStyle.PAYMENT_MODE_SELECTOR)
			canvas.drawLine(imageView.getLeft()+imageView.getWidth()+11,0.10f*getHeight(),imageView.getLeft()+imageView.getWidth()+11,0.90f*getHeight(), linePaint);
	}

	private void applyContactPhoto()
	{
		if(contact.photo==null)
		{
			if(contact.loadingPhotoTask==null)
			{
				final Contact c = contact;
				contact.loadingPhotoTask = new AsyncTaskReplacement<Void, Void, Bitmap>()
				{
					protected Bitmap doInBackground(Void... params)
					{
						InputStream input = null;
						try
						{
							if(c.lookupKey!=null && !c.lookupKey.trim().equals(""))
							{
								//TODO error occurs here when there's a non-contacts diner entered!
								Uri contentUri = Contacts.lookupContact(activity.getContentResolver(),Contacts.CONTENT_LOOKUP_URI.buildUpon().appendPath(c.lookupKey).build());
								input = ContactsContract.Contacts.openContactPhotoInputStream(activity.getContentResolver(),contentUri);
								if(input!=null)
									return Bitmap.createScaledBitmap(BitmapFactory.decodeStream(input),Grouptuity.contactIconSize,Grouptuity.contactIconSize,true);
							}
						}catch(Exception e){Grouptuity.log(e);}
						finally{try{if(input!=null)input.close();}catch(IOException e){Grouptuity.log(e);}}
						if(defaultPhoto!=null)
							return defaultPhoto;
						return BitmapFactory.decodeResource(activity.getResources(),R.drawable.contact_icon);
					}

					protected void onPostExecute(Bitmap result)
					{
						super.onPostExecute(result);
						c.photo = result;
						c.loadingPhotoTask = null;
						if(contact==c && result!=null) //TODO need to make sure the view is still valid
							imageView.setImageBitmap(result);
					}
				}.execute();
			}
			if(defaultPhoto!=null)
				imageView.setImageBitmap(defaultPhoto);
		}
		else
			imageView.setImageBitmap(contact.photo);
	}

	protected void releaseCallback(Contact c){}
	protected void pressCallback(Contact c){}
	protected void clickCallback(Contact c){}
	protected void clickCallback(Diner d){}
	protected void longClickCallback(Diner d){}
	protected void paymentToggle(Diner d){}

	protected void applyStyle(){setBackgroundColor(Grouptuity.foregroundColor);}
	public void refresh()
	{
		switch(style)
		{
			case FACE_ICON:				if(contact!=null){applyContactPhoto();contactName.setText(contact.name);}break;
			case DIRECTORY_LISTING:		if(contact!=null){applyContactPhoto();contactName.setText(contact.name);}break;
			case ITEM_ENTRY:			applyContactPhoto();
										contactName.setText(diner.name);
										if(diner.selected){holdHighlight = true;setViewState(ViewState.HIGHLIGHTED);}
										else{holdHighlight = false;setViewState(ViewState.DEFAULT);}
										total.setText(Grouptuity.formatNumber(NumberFormat.CURRENCY,diner.fullSubTotal));
										break;
			case PAYMENT_MODE_SELECTOR:	if(diner.defaultPaymentType!=null)
											imageView.setImageBitmap(paymentImages[diner.defaultPaymentType.ordinal()]);
										else if(contact!=null && contact.defaultPaymentType!=null)
											imageView.setImageBitmap(paymentImages[contact.defaultPaymentType.ordinal()]);
										else
											imageView.setImageBitmap(paymentImages[Grouptuity.PaymentType.values()[Grouptuity.DEFAULT_PAYMENT_TYPE.getValue()].ordinal()]);
										contactName.setText(diner.name);

										boolean showVenmo = (diner.defaultPaymentType==PaymentType.VENMO); //set true if paying with venmo
										for(Payment p: diner.paymentsIn){showVenmo = showVenmo || p.type==PaymentType.VENMO;} //use OR to make true if being paid venmo, and keep true
										if(diner.contact.venmoUsername!=null && showVenmo)
										{
											venmoLayout.setVisibility(VISIBLE);
											venmoName.setText(diner.contact.venmoUsername);
										}
										else
											venmoLayout.setVisibility(GONE);
										subname.setText(diner.getPaymentInstructions());

										total.setText(diner.total==0?"--":Grouptuity.formatNumber(NumberFormat.CURRENCY,diner.total));
										break;
			case EMAIL_LISTING:			if(diner.contact!=null && diner.contact.getDefaultEmailAddress()!=null)
										{
											imageView.setImageResource(R.drawable.payment_iou_email);
											subname.setText(diner.contact.getDefaultEmailAddress());
										}
										else
										{
											imageView.setImageResource(R.drawable.ic_menu_help);
											subname.setText("Touch to enter email");
										}
										contactName.setText(diner.name);
										break;
			case VENMO_PAYMENTS:		applyContactPhoto();
										contactName.setText(diner.name);
										if(diner.selected){holdHighlight = true;setViewState(ViewState.HIGHLIGHTED);}
										else{holdHighlight = false;setViewState(ViewState.DEFAULT);}
										total.setText(Grouptuity.formatNumber(NumberFormat.CURRENCY,diner.fullSubTotal));
										break;
		}
	}
}