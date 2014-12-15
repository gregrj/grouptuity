package com.grouptuity.view;

import android.graphics.*;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.*;
import android.widget.ImageView.ScaleType;
import com.grouptuity.*;
import com.grouptuity.model.*;
import com.grouptuity.style.composites.*;
import com.grouptuity.view.BillableListingAdapter.BillableSeparator;

public class BillableListing extends Panel
{
	final private LinearLayout linearLayout;
	final private EditText editableValue, editableCost;
	final private TextView separatorTitle, billableType, payerList, payeeList, valueTitle, costTitle;
	final private LinearLayout dinerListLayout, editTextLayout;
	final private ImageView deleteButton;
	final private Paint linePaint;
	final private Bitmap deleteImage, deleteImagePressed;
	protected Billable billable;
	private LayoutType layoutType;
	private Diner diner;

	private enum LayoutType{ITEM, DEBT, DEAL, SEPARATOR;}

	@SuppressWarnings("unchecked")
	public BillableListing(ActivityTemplate<?> context, Diner d, Bitmap[] delete)
	{
		super((ActivityTemplate<ModelTemplate>)context,new ListingStyle());
		setStyle(ViewState.HIGHLIGHTED,new ListingStyleHighlighted());
		setStyle(ViewState.AUXILIARY,new ListingStyleFaceIcon());
		diner = d;
		deleteImage = delete[0];
		deleteImagePressed = delete[1];
		linePaint = new Paint();
		linePaint.setColor(Color.LTGRAY);

		setOnTouchListener(new OnTouchListener(){public boolean onTouch(View v, MotionEvent event){return true;}}); //unless we consume these events, they show up in editableCost for some reason

		linearLayout = new LinearLayout(context);
		linearLayout.setOrientation(LinearLayout.HORIZONTAL);
		addView(linearLayout,Grouptuity.fillWrapLP());

		separatorTitle = new TextView(context);
		separatorTitle.setSingleLine();
		separatorTitle.setTextColor(Color.WHITE);
		separatorTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP,20.0f);
		addView(separatorTitle,Grouptuity.fillWrapLP());

		deleteButton = new ImageView(context);
		deleteButton.setScaleType(ScaleType.FIT_CENTER);
		deleteButton.setImageBitmap(deleteImage);
		deleteButton.setPadding(Grouptuity.toActualPixels(6), Grouptuity.toActualPixels(13), Grouptuity.toActualPixels(17), Grouptuity.toActualPixels(3));
		deleteButton.setOnTouchListener(new InputListenerTemplate(false)
		{
			protected boolean handlePress(){deleteButton.setImageBitmap(deleteImagePressed);setViewState(ViewState.HIGHLIGHTED);return true;}
			protected boolean handleRelease(){deleteButton.setImageBitmap(deleteImage);setViewState(ViewState.DEFAULT);return true;}
			protected boolean handleHold(long holdTime){return false;}
			protected boolean handleClick(){onDeleteItemClick(billable);return false;}
			protected boolean handleLongClick(){return false;}
		});
		linearLayout.addView(deleteButton,Grouptuity.gravWrapWrapLP(Gravity.CENTER_VERTICAL));

		billableType = new TextView(context);
		billableType.setTypeface(Typeface.DEFAULT_BOLD);
		billableType.setTextColor(Grouptuity.softTextColor);
		billableType.setTextSize(TypedValue.COMPLEX_UNIT_SP,18.0f);
		billableType.setGravity(Gravity.CENTER_VERTICAL);

		payerList = new TextView(context);
		payerList.setTypeface(Typeface.DEFAULT);
		payerList.setTextColor(Grouptuity.softTextColor);
		payerList.setTextSize(TypedValue.COMPLEX_UNIT_SP,14.0f);
		payerList.setGravity(Gravity.CENTER_VERTICAL);
		payerList.setOnTouchListener(new OnTouchListener()
		{
			public boolean onTouch(View v, MotionEvent e)
			{
				if(e.getAction()==MotionEvent.ACTION_UP && (e.getX()>=0 && e.getX()<v.getWidth() && e.getY()>=0 && e.getY()<v.getHeight()))
					onPayerListClick(billable);
				return true;
			}
		});

		payeeList = new TextView(context);
		payeeList.setTypeface(Typeface.DEFAULT);
		payeeList.setTextColor(Grouptuity.softTextColor);
		payeeList.setTextSize(TypedValue.COMPLEX_UNIT_SP,14.0f);
		payeeList.setGravity(Gravity.CENTER_VERTICAL);
		payeeList.setOnTouchListener(new OnTouchListener()
		{
			public boolean onTouch(View v, MotionEvent e)
			{
				if(e.getAction()==MotionEvent.ACTION_UP && (e.getX()>=0 && e.getX()<v.getWidth() && e.getY()>=0 && e.getY()<v.getHeight()))
					onPayeeListClick(billable);
				return true;
			}
		});

		dinerListLayout = new LinearLayout(context);
		dinerListLayout.setOrientation(LinearLayout.VERTICAL);
		dinerListLayout.addView(billableType,Grouptuity.gravWrapWrapLP(Gravity.CENTER_VERTICAL));
		dinerListLayout.addView(payerList,Grouptuity.gravWrapWrapLP(Gravity.CENTER_VERTICAL));
		dinerListLayout.addView(payeeList,Grouptuity.gravWrapWrapLP(Gravity.CENTER_VERTICAL));
		linearLayout.addView(dinerListLayout,Grouptuity.gravWrapWrapLP(1.0f,Gravity.CENTER_VERTICAL));

		valueTitle = new TextView(context);
		valueTitle.setTypeface(Typeface.DEFAULT_BOLD);
		valueTitle.setTextColor(Grouptuity.softTextColor);
		valueTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP,18.0f);
		valueTitle.setGravity(Gravity.CENTER_VERTICAL);

		editableValue = new EditText(context);
		activity.assignID(editableValue);
		editableValue.setTypeface(Typeface.DEFAULT_BOLD);
		editableValue.setTextColor(style.textColor);
		editableValue.setFocusable(false);
		editableValue.setCursorVisible(false);
		editableValue.setOnLongClickListener(new OnLongClickListener(){public boolean onLongClick(View v){return true;}});
		editableValue.setOnTouchListener(new InputListenerTemplate(false)
		{
			protected boolean handlePress(){return false;}
			protected boolean handleHold(long holdTime){return false;}
			protected boolean handleRelease(){return false;}
			protected boolean handleClick(){onEditValueClick(billable);return false;}
			protected boolean handleLongClick(){return false;}
		});

		costTitle = new TextView(context);
		costTitle.setTypeface(Typeface.DEFAULT_BOLD);
		costTitle.setTextColor(Grouptuity.softTextColor);
		costTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP,18.0f);
		costTitle.setText("Cost");
		costTitle.setGravity(Gravity.CENTER_VERTICAL);

		editableCost = new EditText(context);
		editableCost.setTypeface(Typeface.DEFAULT_BOLD);
		editableCost.setTextColor(style.textColor);
		editableCost.setFocusable(false);
		editableCost.setCursorVisible(false);
		editableCost.setOnLongClickListener(new OnLongClickListener(){public boolean onLongClick(View v){return true;}});
		editableCost.setOnTouchListener(new InputListenerTemplate(false)
		{
			protected boolean handlePress(){return false;}
			protected boolean handleHold(long holdTime){return false;}
			protected boolean handleRelease(){return false;}
			protected boolean handleClick(){onEditCostClick(billable);return false;}
			protected boolean handleLongClick(){return false;}
		});

		editTextLayout = new LinearLayout(context);
		editTextLayout.setOrientation(LinearLayout.VERTICAL);
		editTextLayout.addView(valueTitle);
		editTextLayout.addView(editableValue);
		editTextLayout.addView(costTitle);
		editTextLayout.addView(editableCost);
		linearLayout.addView(editTextLayout,Grouptuity.gravWrapWrapLP(Gravity.CENTER_VERTICAL));		
	}

	protected void dispatchDraw(Canvas canvas)
	{
		super.dispatchDraw(canvas);
		canvas.drawLine(0, getHeight()-1, getWidth(), getHeight()-1, linePaint);
	}

	protected void onEditValueClick(Billable billable){};
	protected void onEditCostClick(Billable billable){};
	protected void onDeleteItemClick(Billable billable){};
	protected void onPayerListClick(Billable billable){};
	protected void onPayeeListClick(Billable billable){};

	protected void refresh()
	{
		if(billable.billableType==null)
		{
			if(layoutType!=LayoutType.SEPARATOR)
			{
				layoutType = LayoutType.SEPARATOR;
				linearLayout.setVisibility(View.GONE);
				separatorTitle.setVisibility(View.VISIBLE);
				setViewState(ViewState.AUXILIARY);
			}
			BillableSeparator separator = (BillableSeparator) billable;
			if(separator.type.isItem)
				separatorTitle.setText("  Items: "+((diner==null)?Grouptuity.getBill().items.size():diner.items.size()));
			else if(separator.type.isDebt)
				separatorTitle.setText("  Debts: "+((diner==null)?Grouptuity.getBill().debts.size():diner.debts.size()));
			else
				separatorTitle.setText("  Discounts: "+((diner==null)?Grouptuity.getBill().discounts.size():diner.discounts.size()));
		}
		else if(billable.billableType.isItem)
		{
			if(layoutType!=LayoutType.ITEM)
			{
				layoutType = LayoutType.ITEM;
				linearLayout.setVisibility(View.VISIBLE);
				separatorTitle.setVisibility(View.GONE);
				valueTitle.setText("Cost:");
				costTitle.setVisibility(View.GONE);
				editableCost.setVisibility(View.GONE);
				payeeList.setVisibility(View.GONE);
				payerList.setVisibility(View.VISIBLE);
				deleteButton.setVisibility(View.VISIBLE);
				editableValue.setVisibility(View.VISIBLE);
				setViewState(ViewState.DEFAULT);
			}

			billableType.setText(billable.billableType.name);

			String listOfNames = "Purchased by:";
			for(Diner d: billable.payers)
				listOfNames += "\n\t\u2022 "+d.name;
			payerList.setText(listOfNames);

			editableValue.setText(Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY, billable.value));
		}
		else if(billable.billableType.isDebt)
		{
			if(layoutType!=LayoutType.DEBT)
			{
				layoutType = LayoutType.DEBT;
				linearLayout.setVisibility(View.VISIBLE);
				separatorTitle.setVisibility(View.GONE);
				costTitle.setVisibility(View.GONE);
				editableCost.setVisibility(View.GONE);
				payeeList.setVisibility(View.VISIBLE);
				payerList.setVisibility(View.VISIBLE);
				setViewState(ViewState.DEFAULT);
			}

			if(((Debt)billable).linkedDiscount!=null)
			{
				billableType.setText("Group Deal Payback");
				deleteButton.setVisibility(View.INVISIBLE);
				valueTitle.setText("Amount:\n"+Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY, billable.value));
				editableValue.setVisibility(View.GONE);
			}
			else
			{
				billableType.setText(billable.billableType.name);
				deleteButton.setVisibility(View.VISIBLE);
				valueTitle.setText("Amount:");
				editableValue.setVisibility(View.VISIBLE);
				editableValue.setText(Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY, billable.value));
			}

			String listOfNames = "Paying Money:";
			for(Diner d: billable.payers)
				listOfNames += "\n\t\u2022 "+d.name;
			payerList.setText(listOfNames);

			listOfNames = "Receiving Money:";
			for(Diner d: billable.payees)
				listOfNames += "\n\t\u2022 "+d.name;
			payeeList.setText(listOfNames);
		}
		else
		{
			if(layoutType!=LayoutType.DEAL)
			{
				layoutType = LayoutType.DEAL;
				linearLayout.setVisibility(View.VISIBLE);
				separatorTitle.setVisibility(View.GONE);
				payeeList.setVisibility(View.VISIBLE);
				deleteButton.setVisibility(View.VISIBLE);
				editableValue.setVisibility(View.VISIBLE);
				setViewState(ViewState.DEFAULT);
			}

			String listOfNames = "Using Discount:";
			for(Diner d: billable.payees)
				listOfNames += "\n\t\u2022 "+d.name;
			payeeList.setText(listOfNames);

			editableValue.setText(Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY, billable.value));
			billableType.setText(billable.billableType.name);

			Discount discount = (Discount) billable;
			switch(billable.billableType)
			{
				case DEAL_PERCENT_ITEM:		
				case DEAL_PERCENT_BILL:		valueTitle.setText("Percent:");
											payerList.setVisibility(View.GONE);
											costTitle.setVisibility(View.GONE);
											editableCost.setVisibility(View.GONE);
											editableValue.setText(Grouptuity.formatNumber(Grouptuity.NumberFormat.TAX_PERCENT, discount.percent));
											break;
				case DEAL_FIXED:			valueTitle.setText("Value:");
											payerList.setVisibility(View.GONE);
											costTitle.setVisibility(View.GONE);
											editableCost.setVisibility(View.GONE);
											editableValue.setText(Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY, discount.value));
											break;
				case DEAL_GROUP_VOUCHER:	costTitle.setVisibility(View.VISIBLE);
											editableCost.setVisibility(View.VISIBLE);
											editableCost.setText(Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY, discount.cost));
											editableValue.setText(Grouptuity.formatNumber(Grouptuity.NumberFormat.CURRENCY, discount.value));
											valueTitle.setText("Value:");

											listOfNames = "Purchased By:";
											for(Diner d: discount.payers)
												listOfNames += "\n\t\u2022 "+d.name;
											payerList.setText(listOfNames);
											payerList.setVisibility(View.VISIBLE);
											break;
				default:					break;
			}
		}
	}
}