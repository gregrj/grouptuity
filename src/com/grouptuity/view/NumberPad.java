package com.grouptuity.view;

import java.util.ArrayList;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ImageView.ScaleType;
import com.grouptuity.*;
import com.grouptuity.Grouptuity.*;

public class NumberPad extends ViewTemplate<ModelTemplate,NumberPad.NumberPadController>
{
	final private EditText numberDisplay;
	final private Button[] numberButtons;
	final private Button[] itemTypeButtons;
	final private Button taxMethodButton, tipMethodButton;
	final private BillableType[] itemTypes;
	final private Button clear, add, subtract, multiply, divide, equals, decimal;
	final private ImageButton cancel, confirm;
	final private LinearLayout itemTypeRow, discountMethodRow;
	final private InstructionsBar instructions;

	private NumberFormat formatStyle;
	private ArrayList<String> numbers1, numbers2;
	private boolean negative1, negative2, notReady;
	private double displayValue, minValue;
	private MathOp mathOp;
	private BillableType selectedItemType;
	private boolean defaultTaxDiscountMethod, defaultTipDiscountMethod;
	private boolean taxDiscountMethodSelected, tipDiscountMethodSelected;
	private int selectedItemButtonIndex;
	private BillableType defaultItemType = BillableType.ENTREE;

	private enum MathOp
	{
		ADD("+"), SUBTRACT("-"), MULTIPLY("x"), DIVIDE("\u00F7");
		final private String text;
		private MathOp(String str){text = str;}
	}

	public static interface NumberPadController extends ControllerTemplate
	{
		public void onCancel();
		public void onConfirm(double returnValue, BillableType billableType, boolean taxMethod, boolean tipMethod);
	}

	@SuppressWarnings("unchecked")
	public NumberPad(ActivityTemplate<?> context)
	{
		super((ActivityTemplate<ModelTemplate>)context);
		context.assignID(this);
		formatStyle = NumberFormat.CURRENCY_NO_ZERO;

		itemTypes = new BillableType[]{BillableType.ENTREE,BillableType.DRINK,BillableType.SIDE,BillableType.OTHER};
		selectedItemType = defaultItemType;
		selectedItemButtonIndex = -1;
		for(int i=0; i<itemTypes.length; i++)
			if(itemTypes[i]==selectedItemType)
			{
				selectedItemButtonIndex = i;
				break;
			}

		setLayoutParams(Grouptuity.fillFillLP());
		setBackgroundColor(Color.argb(255,22,22,22));

		instructions = new InstructionsBar(context);

		numbers1 = new ArrayList<String>();
		numbers2 = new ArrayList<String>();

		numberDisplay = new EditText(context);
		numberDisplay.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
		numberDisplay.setTextSize(TypedValue.COMPLEX_UNIT_DIP,25.f);
		numberDisplay.setFocusable(false);
		numberDisplay.setCursorVisible(false);
		numberDisplay.setHorizontallyScrolling(true);
		numberDisplay.setSingleLine(true);
		numberDisplay.setOnTouchListener(new OnTouchListener(){public boolean onTouch(View v, MotionEvent event){return true;}});

		clear = new Button(context);
		clear.setText("Clear");
		clear.setTextSize(TypedValue.COMPLEX_UNIT_DIP,25.f);
		clear.setSingleLine(true);
		clear.setOnClickListener(new OnClickListener(){public void onClick(View v){clear();}});
		clear.setOnLongClickListener(new OnLongClickListener(){public boolean onLongClick(View v){clearAll();return true;}});

		add = new Button(context);
		add.setText(MathOp.ADD.text);
		add.setTextSize(TypedValue.COMPLEX_UNIT_DIP,25.f);
		add.setOnClickListener(new OnClickListener(){public void onClick(View v){add();}});

		subtract = new Button(context);
		subtract.setText(MathOp.SUBTRACT.text);
		subtract.setTextSize(TypedValue.COMPLEX_UNIT_DIP,25.f);
		subtract.setOnClickListener(new OnClickListener(){public void onClick(View v){subtract();}});

		multiply = new Button(context);
		multiply.setText(MathOp.MULTIPLY.text);
		multiply.setTextSize(TypedValue.COMPLEX_UNIT_DIP,25.f);
		multiply.setOnClickListener(new OnClickListener(){public void onClick(View v){multiply();}});

		divide = new Button(context);
		divide.setText(MathOp.DIVIDE.text);
		divide.setTextSize(TypedValue.COMPLEX_UNIT_DIP,25.f);
		divide.setOnClickListener(new OnClickListener(){public void onClick(View v){divide();}});

		cancel = new ImageButton(context);
		cancel.setScaleType(ScaleType.FIT_CENTER);
		cancel.setImageResource(R.drawable.cancel);
		cancel.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				controller.onCancel();
				close();
			}
		});

		confirm = new ImageButton(context);
		confirm.setScaleType(ScaleType.FIT_CENTER);
		confirm.setImageResource(R.drawable.confirm);
		confirm.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				doMath();
				controller.onConfirm(displayValue,selectedItemType,taxDiscountMethodSelected,tipDiscountMethodSelected);
				close();
			}
		});

		numberButtons = new Button[10];
		for(int i=0; i<numberButtons.length; i++)
		{
			final int buttonNumber = i;
			final String buttonString = String.valueOf(buttonNumber);
			Button button = new Button(context);
			button.setText(buttonString);
			button.setOnClickListener(new OnClickListener(){public void onClick(View v){number(buttonString);}});
			button.setTextSize(TypedValue.COMPLEX_UNIT_DIP,25.f);
			numberButtons[buttonNumber] = button;
		}

		itemTypeButtons = new Button[itemTypes.length];
		for(int i=0; i<itemTypeButtons.length; i++)
		{
			final int buttonNumber = i;
			final String buttonString = itemTypes[i].name;
			final Button button = new Button(context);
			button.setText(buttonString);
			button.setOnTouchListener(new InputListenerTemplate(false,true)
			{
				protected boolean handlePress(){button.setPressed(true);return true;}
				protected boolean handleHold(long holdTime){return false;}
				protected boolean handleRelease()
				{
					if(buttonNumber!=selectedItemButtonIndex)
						button.setPressed(false);
					return true;
				}
				protected boolean handleClick()
				{
					if(selectedItemButtonIndex!=-1)
						itemTypeButtons[selectedItemButtonIndex].setPressed(false);
					selectedItemType = itemTypes[buttonNumber];
					selectedItemButtonIndex = buttonNumber;
					button.setPressed(true);
					return true;
				}
				protected boolean handleLongClick(){return false;}
			});
			button.setTextSize(TypedValue.COMPLEX_UNIT_DIP,16.f);
			itemTypeButtons[i] = button;
		}

		taxMethodButton = new Button(context);
		taxMethodButton.setText("Tax this value");
		taxMethodButton.setOnTouchListener(new InputListenerTemplate(false,true)
		{
			protected boolean handlePress(){taxMethodButton.setPressed(true);return true;}
			protected boolean handleHold(long holdTime){return false;}
			protected boolean handleRelease(){if(!taxDiscountMethodSelected)taxMethodButton.setPressed(false);return true;}
			protected boolean handleClick()
			{
				if(taxDiscountMethodSelected)
				{
					taxDiscountMethodSelected = false;
					taxMethodButton.setPressed(false);
				}
				else
				{
					taxDiscountMethodSelected = true;
					taxMethodButton.setPressed(true);
				}
				return true;
			}
			protected boolean handleLongClick(){return false;}
		});
		taxMethodButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP,16.f);

		tipMethodButton = new Button(context);
		tipMethodButton.setText("Tip this value");
		tipMethodButton.setOnTouchListener(new InputListenerTemplate(false,true)
		{
			protected boolean handlePress(){tipMethodButton.setPressed(true);return true;}
			protected boolean handleHold(long holdTime){return false;}
			protected boolean handleRelease(){if(!tipDiscountMethodSelected)tipMethodButton.setPressed(false);return true;}
			protected boolean handleClick()
			{
				if(tipDiscountMethodSelected){tipDiscountMethodSelected = false;tipMethodButton.setPressed(false);}
				else{tipDiscountMethodSelected = true;tipMethodButton.setPressed(true);}
				return true;
			}
			protected boolean handleLongClick(){return false;}
		});
		tipMethodButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP,16.f);

		equals = new Button(context);
		equals.setText("=");
		equals.setOnClickListener(new OnClickListener(){public void onClick(View v){equalsButton();}});
		equals.setTextSize(TypedValue.COMPLEX_UNIT_DIP,25.f);

		decimal = new Button(context);
		decimal.setText(Grouptuity.DECIMAL_SYMBOL.getValue());
		decimal.setOnClickListener(new OnClickListener(){public void onClick(View v){number(Grouptuity.DECIMAL_SYMBOL.getValue());}});
		decimal.setTextSize(TypedValue.COMPLEX_UNIT_DIP,25.f);

		//TODO break here for orientation
		if(context.isHorizontal())
		{
			//make a horizontal layout
			itemTypeRow = null;
			discountMethodRow = null;
		}
		else
		{
			LinearLayout topRow = new LinearLayout(context);
			topRow.setOrientation(LinearLayout.HORIZONTAL);
			topRow.addView(numberDisplay,Grouptuity.fillFillLP(1.0f));
			addView(topRow,Grouptuity.fillFillLP(1.0f));
			
			LinearLayout.LayoutParams ibLP = new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.WRAP_CONTENT);
			ibLP.bottomMargin = -instructions.panel.style.bottomContraction/2;
			addView(instructions,ibLP);

			itemTypeRow = new LinearLayout(context);
			itemTypeRow.setOrientation(LinearLayout.HORIZONTAL);
			for(Button b : itemTypeButtons) itemTypeRow.addView(b,Grouptuity.fillFillLP(1.0f));
			addView(itemTypeRow,Grouptuity.fillFillLP(1.0f));

			discountMethodRow = new LinearLayout(context);
			discountMethodRow.setOrientation(LinearLayout.HORIZONTAL);
			discountMethodRow.addView(taxMethodButton,Grouptuity.fillFillLP(1.0f));
			discountMethodRow.addView(tipMethodButton,Grouptuity.fillFillLP(1.0f));
			addView(discountMethodRow,Grouptuity.fillFillLP(1.0f));

			LinearLayout operatorRow = new LinearLayout(context);
			operatorRow.setOrientation(LinearLayout.HORIZONTAL);
			operatorRow.addView(add,Grouptuity.fillFillLP(1.0f));
			operatorRow.addView(subtract,Grouptuity.fillFillLP(1.0f));
			operatorRow.addView(multiply,Grouptuity.fillFillLP(1.0f));
			operatorRow.addView(divide,Grouptuity.fillFillLP(1.0f));
			operatorRow.addView(clear,Grouptuity.wrapFillLP());
			addView(operatorRow,Grouptuity.fillFillLP(1.0f));

			LinearLayout numRow1 = new LinearLayout(context);
			numRow1.setOrientation(LinearLayout.HORIZONTAL);
			numRow1.addView(numberButtons[7],Grouptuity.fillFillLP(1.0f));
			numRow1.addView(numberButtons[8],Grouptuity.fillFillLP(1.0f));
			numRow1.addView(numberButtons[9],Grouptuity.fillFillLP(1.0f));
			addView(numRow1,Grouptuity.fillFillLP(1.0f));

			LinearLayout numRow2 = new LinearLayout(context);
			numRow2.setOrientation(LinearLayout.HORIZONTAL);
			numRow2.addView(numberButtons[4],Grouptuity.fillFillLP(1.0f));
			numRow2.addView(numberButtons[5],Grouptuity.fillFillLP(1.0f));
			numRow2.addView(numberButtons[6],Grouptuity.fillFillLP(1.0f));
			addView(numRow2,Grouptuity.fillFillLP(1.0f));

			LinearLayout numRow3 = new LinearLayout(context);
			numRow3.setOrientation(LinearLayout.HORIZONTAL);
			numRow3.addView(numberButtons[1],Grouptuity.fillFillLP(1.0f));
			numRow3.addView(numberButtons[2],Grouptuity.fillFillLP(1.0f));
			numRow3.addView(numberButtons[3],Grouptuity.fillFillLP(1.0f));
			addView(numRow3,Grouptuity.fillFillLP(1.0f));

			LinearLayout numRow4 = new LinearLayout(context);
			numRow4.setOrientation(LinearLayout.HORIZONTAL);
			numRow4.addView(numberButtons[0],Grouptuity.fillFillLP(1.0f));
			numRow4.addView(decimal,Grouptuity.fillFillLP(1.0f));
			numRow4.addView(equals,Grouptuity.fillFillLP(1.0f));
			addView(numRow4,Grouptuity.fillFillLP(1.0f));

			LinearLayout bottomRow = new LinearLayout(context);
			bottomRow.setOrientation(LinearLayout.HORIZONTAL);

			if(Grouptuity.OK_ON_LEFT)
			{
				bottomRow.addView(confirm,Grouptuity.fillFillLP(1.0f));
				bottomRow.addView(cancel,Grouptuity.fillFillLP(1.0f));
			}
			else
			{
				bottomRow.addView(cancel,Grouptuity.fillFillLP(1.0f));
				bottomRow.addView(confirm,Grouptuity.fillFillLP(1.0f));
			}
			
			addView(bottomRow,Grouptuity.fillFillLP(1.0f));
		}

		clearAll();
	}

	protected void saveState(Bundle bundle)
	{
		bundle.putStringArrayList("com.grouptuity."+getId()+"numbers1",numbers1);
		bundle.putStringArrayList("com.grouptuity."+getId()+"numbers2",numbers2);
		bundle.putBoolean("com.grouptuity."+getId()+"negative1",negative1);
		bundle.putBoolean("com.grouptuity."+getId()+"negative2",negative2);
		bundle.putInt("com.grouptuity."+getId()+"mathOp",(mathOp==null)?-1:mathOp.ordinal());
	}
	protected void restoreState(Bundle bundle)
	{
		numbers1 = bundle.getStringArrayList("com.grouptuity."+getId()+"numbers1");
		numbers2 = bundle.getStringArrayList("com.grouptuity."+getId()+"numbers2");
		negative1 = bundle.getBoolean("com.grouptuity."+getId()+"negative1");
		negative2 = bundle.getBoolean("com.grouptuity."+getId()+"negative2");
		int temp = bundle.getInt("com.grouptuity."+getId()+"mathOp");
		mathOp = (temp==-1)?null:MathOp.values()[temp];
	}
	protected void applyStyle(){}
	protected void refresh()
	{
		String first = formatFirstNumber();
		String second = formatSecondNumber();
		double firstValue = getFirstValue();
		double secondValue = getSecondValue();

		if(mathOp==null)
			numberDisplay.setText(first);
		else
			numberDisplay.setText(first + " "+mathOp.text+" "+second);

		doMath();

		add.setEnabled(true);
		subtract.setEnabled(true);
		multiply.setEnabled(true);
		divide.setEnabled(true);

		if(mathOp==null || notReady || (numbers2.isEmpty() || (numbers2.size()==1 && numbers2.get(0).equals(Grouptuity.DECIMAL_SYMBOL.getValue()))))
			equals.setEnabled(false);
		else
			equals.setEnabled(true);

		if((mathOp==null && first.contains(Grouptuity.DECIMAL_SYMBOL.getValue())) || (mathOp!=null && second.contains(Grouptuity.DECIMAL_SYMBOL.getValue())))
			decimal.setEnabled(false);
		else
			decimal.setEnabled(true);

		if(notReady || displayValue < minValue)
		{
			confirm.setEnabled(false);
			confirm.setImageResource(R.drawable.confirm_disabled);
		}
		else
		{
			confirm.setEnabled(true);
			confirm.setImageResource(R.drawable.confirm);
		}		

		boolean numbuttons = true;

		//check for decimals longer than two, disable numbers
		String[] val1split = first.replace(formatStyle.getPrefix(),"").replace(formatStyle.getPostfix(),"").split("\\.");
		String[] val2split = second.replace(formatStyle.getPrefix(),"").replace(formatStyle.getPostfix(),"").split("\\.");
		if(mathOp==null)
		{
			if(val1split.length > 1 && val1split[1].length() >= 2)
				numbuttons = false;
		}
		else if(mathOp==MathOp.ADD || mathOp==MathOp.SUBTRACT) //permit longer decimals if multiplying or dividing
		{
			if(val2split.length > 1 && val2split[1].length() >= 2)
				numbuttons = false;
		}

		//check for numbers that are too long, disable numbers
		boolean disableZero = false, disableDecimal = false;
		if(mathOp==null)
		{
			if(firstValue > formatStyle.maxValue)
			{
				add.setEnabled(false);
				if(!first.contains(Grouptuity.DECIMAL_SYMBOL.getValue()))
					numbuttons = false;
			}
			else if(firstValue < -formatStyle.maxValue)
			{
				subtract.setEnabled(false);
				if(!first.contains(Grouptuity.DECIMAL_SYMBOL.getValue()))
					numbuttons = false;
			}
			if(firstValue==0.0)
			{
				multiply.setEnabled(false);
				divide.setEnabled(false);
			}
		}
		else
		{
			switch(mathOp)
			{
				case ADD:		if(firstValue+secondValue > formatStyle.maxValue)
								{
									add.setEnabled(false);
									if(!second.contains(Grouptuity.DECIMAL_SYMBOL.getValue()))
										numbuttons = false;
								}
								else if(firstValue+secondValue < -formatStyle.maxValue)
								{
									subtract.setEnabled(false);
								}
								if(firstValue==0.0 && secondValue==0.0)
								{
									multiply.setEnabled(false);
									divide.setEnabled(false);
								}
								break;
				case SUBTRACT:	if(firstValue-secondValue > formatStyle.maxValue)
								{
									add.setEnabled(false);
								}
								else if(firstValue-secondValue < -formatStyle.maxValue)
								{
									subtract.setEnabled(false);
									if(!second.contains(Grouptuity.DECIMAL_SYMBOL.getValue()))
										numbuttons = false;
								}
								if(firstValue==0.0 && secondValue==0.0)
								{
									multiply.setEnabled(false);
									divide.setEnabled(false);
								}
								break;
				case MULTIPLY:	if(secondValue==0.0)
								{
									if(!second.contains(Grouptuity.DECIMAL_SYMBOL.getValue()) && firstValue>formatStyle.maxValue)
										numbuttons = false;
									if(firstValue > formatStyle.maxValue)
										add.setEnabled(false);
								}
								else if(firstValue*secondValue > formatStyle.maxValue)
								{
									add.setEnabled(false);
									if(!second.contains(Grouptuity.DECIMAL_SYMBOL.getValue()))
										numbuttons = false;
								}
								else if(firstValue*secondValue < -formatStyle.maxValue)
								{
									subtract.setEnabled(false);
									if(!second.contains(Grouptuity.DECIMAL_SYMBOL.getValue()))
										decimal.setEnabled(false);
								}
								if(firstValue==0.0)
								{
									multiply.setEnabled(false);
									divide.setEnabled(false);
								}
								break;
				case DIVIDE:	if(secondValue==0.0)
								{
									add.setEnabled(false);
									subtract.setEnabled(false);
									multiply.setEnabled(false);
									divide.setEnabled(false);
									if(second.contains(Grouptuity.DECIMAL_SYMBOL.getValue()))
									{
										if(firstValue/Math.pow(10, -val2split[1].length()-1) > formatStyle.maxValue)
											disableZero = true;
									}
									else if(firstValue/0.1 > formatStyle.maxValue)
									{
										disableZero = true;
										disableDecimal = true;
									}
								}
								else if(firstValue/secondValue > formatStyle.maxValue)
								{
									add.setEnabled(false);
									if(!second.contains(Grouptuity.DECIMAL_SYMBOL.getValue()))
										numbuttons = false;
								}
								else if(firstValue/secondValue < -formatStyle.maxValue)
								{
									subtract.setEnabled(false);
									if(!second.contains(Grouptuity.DECIMAL_SYMBOL.getValue()))
										disableDecimal = true;
								}
								if(firstValue==0.0)
								{
									multiply.setEnabled(false);
									divide.setEnabled(false);
								}
								break;
			}
		}
		
		//set visibility
		setNumberButtonsEnabled(numbuttons);
		if(disableZero)
			numberButtons[0].setEnabled(false);
		if(disableDecimal)
			decimal.setEnabled(false);

		for(int i=0; i<itemTypeButtons.length; i++)
		{
			if(itemTypes[i]==selectedItemType)
			{
				itemTypeButtons[i].setPressed(true);
				selectedItemButtonIndex = i;
			}
			else
				itemTypeButtons[i].setPressed(false);
		}

		if(formatStyle==NumberFormat.INTEGER)
		{
			decimal.setEnabled(false);
			add.setEnabled(false);
			subtract.setEnabled(false);
			multiply.setEnabled(false);
			divide.setEnabled(false);
		}
		else
		{
			taxMethodButton.setPressed(taxDiscountMethodSelected);
			tipMethodButton.setPressed(tipDiscountMethodSelected);
		}
	}

	public void open(NumberFormat format, String instruction, BillableType type){open(format, instruction, type, false, false, false, false);}
	public void open(NumberFormat format, String instruction, BillableType type, boolean taxMethod, boolean tipMethod, boolean showTax, boolean showTip)
	{
		formatStyle = format;
		minValue = Math.max(format.minValue,minValue);
		instructions.setInstructionsText(instruction);
		defaultItemType = type;
		selectedItemType = type;
		defaultTaxDiscountMethod = taxMethod;
		defaultTipDiscountMethod = tipMethod;
		taxDiscountMethodSelected = taxMethod;
		tipDiscountMethodSelected = tipMethod;
		if(type!=null)
		{
			if(type.isItem)
			{
				showDiscountMethodButtons(false, false);
				showItemTypeButtons(Grouptuity.ENABLE_ADVANCED_ITEMS.getValue());
			}
			else if(type.isDiscount)
			{
				showItemTypeButtons(false);
				showDiscountMethodButtons(showTax, showTip);
			}
			else
			{
				showItemTypeButtons(false);
				showDiscountMethodButtons(false, false);
			}
		}
		else
		{
			showItemTypeButtons(false);
			showDiscountMethodButtons(false, false);
		}
		refresh();
		bringToFront();
		setVisibility(VISIBLE);
	}
	public void close(){hide();clearAll();minValue = -Double.MAX_VALUE;}
	private void clearAll()
	{
		numbers1.clear();
		numbers2.clear();
		negative1 = false;
		negative2 = false;
		mathOp = null;
		selectedItemType = defaultItemType;
		taxDiscountMethodSelected = defaultTaxDiscountMethod;
		tipDiscountMethodSelected = defaultTipDiscountMethod;
		refresh();
	}
	public void setNumberButtonsEnabled(boolean enabled){for(Button numbutton: numberButtons)numbutton.setEnabled(enabled);}
	public void showItemTypeButtons(boolean show)
	{
		if(show)
			itemTypeRow.setVisibility(LinearLayout.VISIBLE);
		else
			itemTypeRow.setVisibility(LinearLayout.GONE);
	}
	public void showDiscountMethodButtons(boolean showTax, boolean showTip)
	{
		if(showTax || showTip)
		{
			discountMethodRow.setVisibility(LinearLayout.VISIBLE);
			if(showTax)
				taxMethodButton.setEnabled(true);
			else
				taxMethodButton.setEnabled(false);
			if(showTip)
				tipMethodButton.setEnabled(true);
			else
				tipMethodButton.setEnabled(false);
		}
		else
		{
			discountMethodRow.setVisibility(LinearLayout.GONE);
		}
	}
	public void setMinValue(double min){minValue = min;}
	private void clear()
	{
		if(mathOp==null)
		{
			if(!numbers1.isEmpty())
			{
				numbers1.remove(numbers1.size()-1);
				if(numbers1.isEmpty())
					negative1 = false;
			}
			else
				negative1 = false;
		}
		else
		{
			if(numbers2.isEmpty())
				mathOp = null;
			else
			{
				numbers2.remove(numbers2.size()-1);
				if(numbers2.isEmpty())
					negative2 = false;
			}
		}
		refresh();
	}
	private void add()
	{
		if(mathOp!=null)
			equalsButton();
		mathOp = MathOp.ADD;
		refresh();
	}
	private void subtract()
	{
		if(mathOp!=null)
			equalsButton();
		if(mathOp==null && (numbers1.isEmpty() || (numbers1.size()==1 && numbers1.get(0).equals(Grouptuity.DECIMAL_SYMBOL.getValue()))))
			negative1 = !negative1;
		else
			mathOp = MathOp.SUBTRACT;
		refresh();
	}
	private void multiply()
	{
		if(mathOp!=null)
			equalsButton();
		mathOp = MathOp.MULTIPLY;refresh();
	}
	private void divide()
	{
		if(mathOp!=null)
			equalsButton();
		mathOp = MathOp.DIVIDE;refresh();
	}
	private void equalsButton()
	{
		doMath();
		mathOp = null;
		numbers2.clear();
		numbers1.clear();
		negative1 = displayValue<0;
		negative2 = false;
		String temp = String.valueOf(Math.abs(displayValue));
		if(!temp.equals("0.0"))
		{
			if(temp.charAt(0)=='0')
				temp = temp.substring(1,temp.length());
			for(char c: temp.toCharArray())
				numbers1.add(String.valueOf(c));
		}
		refresh();
	}
	private void doMath()
	{
		double value1 = getFirstValue();

		if(mathOp==null)
		{
			displayValue = value1;
			notReady = false;
			return;
		}
		else
		{
			double value2 = getSecondValue();

			if(value2 == 0){notReady = true;return;}
			try
			{
				switch(mathOp)
				{
					case ADD:		displayValue = value1+value2;break;
					case SUBTRACT:	displayValue = value1-value2;break;
					case MULTIPLY:	displayValue = value1*value2;break;
					case DIVIDE:	displayValue = value1/value2;break;
				}
				//use this to ensure proper actual value, numbers will be rounded for display
				displayValue = Math.round(displayValue*100)/100.0; 

			}catch(Exception e){notReady = true;return;}
			notReady = false;
		}
	}
	private void number(String buttonString)
	{
		ArrayList<String> arrayList = (mathOp==null)?numbers1:numbers2;
		//empty and typed zero
		if(arrayList.isEmpty() && buttonString.equals("0"))
			return;
		//not empty and typed "." and last entry is also "."
		if(!arrayList.isEmpty() && (buttonString.equals(Grouptuity.DECIMAL_SYMBOL.getValue()) && arrayList.get(arrayList.size()-1).equals(Grouptuity.DECIMAL_SYMBOL.getValue())))
			return;
		arrayList.add(buttonString);
		refresh();
	}

	final public String formatFirstNumber()
	{
		String returnString = "";
		if(numbers1.isEmpty())
			returnString = "0";
		else if(numbers1.size()==1)
		{
			 if(numbers1.get(0).equals(Grouptuity.DECIMAL_SYMBOL.getValue()))
			 	returnString = "0"+Grouptuity.DECIMAL_SYMBOL.getValue();
			 else
			 	returnString = numbers1.get(0);
		}
		else
		{
			char[] digits;
			int decimalIndex = -1;
			if(numbers1.get(0).equals(Grouptuity.DECIMAL_SYMBOL.getValue()))
			{
				decimalIndex = 1;
				digits = new char[numbers1.size()+1];
				digits[0] = '0';
				for(int i=0; i<numbers1.size(); i++)
					digits[i+1] = (char)numbers1.get(i).charAt(0);
			}
			else
			{
				digits = new char[numbers1.size()];
				for(int i=0; i<numbers1.size(); i++)
				{
					if(numbers1.get(i).equals(Grouptuity.DECIMAL_SYMBOL.getValue()))
						decimalIndex = i;
					digits[i] = (char)numbers1.get(i).charAt(0);
				}
			}

			if(decimalIndex==-1)
				decimalIndex = digits.length;
			else {
				for(int i=decimalIndex; i<digits.length; i++) {
					//this is supposed to round off decimals longer than two places, which shouldn't exist anyway.
					if(i-decimalIndex == 2) {
						if (digits.length>(i+1) && Integer.valueOf(digits[i+1])>5)
							returnString += Integer.toString(Integer.valueOf(digits[i])+1).charAt(0);
						else
							returnString += digits[i];
						break;
					}
					else
						returnString += digits[i];
				}
			}

			int groupingCount = 0;
			for(int i=decimalIndex-1; i>-1; i--)
			{
				returnString = digits[i] + returnString;
				groupingCount++;
				if(groupingCount==Grouptuity.NUMBER_GROUPING_DIGITS.getValue() && i!=0)
				{
					returnString = Grouptuity.NUMBER_GROUPING_SYMBOL.getValue()+returnString;
					groupingCount = 0;
				}
			}
		}

		if(negative1)
			if(formatStyle.negativeBeforePrefix)
				return "-"+formatStyle.getPrefix()+returnString+formatStyle.getPostfix();
			else
				return formatStyle.getPrefix()+"-"+returnString+formatStyle.getPostfix();
		else
			return formatStyle.getPrefix()+returnString+formatStyle.getPostfix();
	}
	final public String formatSecondNumber()
	{
		String returnString = "";
		if(numbers2.isEmpty())
			returnString = "";
		else if(numbers2.size()==1)
		{
			 if(numbers2.get(0).equals(Grouptuity.DECIMAL_SYMBOL.getValue()))
				 returnString = "0"+Grouptuity.DECIMAL_SYMBOL.getValue();
			 else
			 	returnString = numbers2.get(0);
		}
		else
		{
			char[] digits;
			int decimalIndex = -1;
			if(numbers2.get(0).equals(Grouptuity.DECIMAL_SYMBOL.getValue()))
			{
				decimalIndex = 1;
				digits = new char[numbers2.size()+1];
				digits[0] = '0';
				for(int i=0; i<numbers2.size(); i++)
					digits[i+1] = (char)numbers2.get(i).charAt(0);
			}
			else
			{
				digits = new char[numbers2.size()];
				for(int i=0; i<numbers2.size(); i++)
				{
					if(numbers2.get(i).equals(Grouptuity.DECIMAL_SYMBOL.getValue()))
						decimalIndex = i;
					digits[i] = (char)numbers2.get(i).charAt(0);
				}
			}

			if(decimalIndex==-1)
				decimalIndex = digits.length;
			else
			{
				for(int i=decimalIndex; i<digits.length; i++)
					returnString += digits[i];
			}

			int groupingCount = 0;
			for(int i=decimalIndex-1; i>-1; i--)
			{
				returnString = digits[i] + returnString;
				groupingCount++;
				if(groupingCount==Grouptuity.NUMBER_GROUPING_DIGITS.getValue() && i!=0)
				{
					returnString = Grouptuity.NUMBER_GROUPING_SYMBOL.getValue()+returnString;
					groupingCount = 0;
				}
			}
		}

		if(negative2)
			return "-"+returnString;
		else
			return returnString;
	}
	final private double getFirstValue()
	{
		String string1 = "";
		for(String str: numbers1)
			string1 += str;

		if(!string1.trim().equals("") && !string1.equals(Grouptuity.DECIMAL_SYMBOL.getValue()))
			try
			{
				if(negative1)
					return -Double.valueOf(string1);
				else
					return Double.valueOf(string1);
			}catch(NumberFormatException e){notReady = true;Grouptuity.log(e);}
		return 0.0;
	}
	final private double getSecondValue()
	{
		String string2 = "";
		for(String str: numbers2)
			string2 += str;

		if(!string2.trim().equals("") && !string2.equals(Grouptuity.DECIMAL_SYMBOL.getValue()))
			try
			{
				if(negative2)
					return -Double.valueOf(string2);
				else
					return Double.valueOf(string2);
			}catch(NumberFormatException e){notReady = true;Grouptuity.log(e);}
		return 0.0;
	}
}