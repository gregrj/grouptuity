package com.grouptuity.view;

import android.graphics.*;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.grouptuity.*;
import com.grouptuity.style.composites.*;

public class SlidingBar extends ViewTemplate<ModelTemplate,SlidingBar.SlidingBarController>
{
	final private static int ANIMATE_MESSAGE = -7234101;
	final private static int ANIMATION_FRAME_DURATION = 1000/60;
	final private static float MAXIMUM_ACCELERATION = 2000.0f;
	final private static float MAXIMUM_VELOCITY = 2000.0f;
	final private static int VELOCITY_UNITS = 1000;

	final private Orientation orientation;
	final private HandleView handleView;
	final protected View contentView;
	final protected TitleBar titleBarReference;
	final private Handler animationHandler;
	final private Rect frameRect;

	private Status status, previousDockedStatus;
	private VelocityTracker velocityTracker;
	private float position, velocity, acceleration;
	private long currentAnimationTime, previousAnimationTime;
	private int handleGrabOffset;
	private boolean lockedMotion, lockedButtons, acceptInput, interceptInput, movingfromClosed;

	public static interface SlidingBarController extends ControllerTemplate
	{
		public void onOpened();
		public void onClosed();
		public void onMotionStarted();
		public void onButtonClick(String buttonName);
		public void onDisabledButtonClick(String buttonName);
	}

	public static enum Orientation{TOP,BOTTOM,LEFT,RIGHT;}

	private static enum Status
	{
		CLOSED(true),
		OPENED(true),
		DRAGGING(false),
		AUTO_SLIDING(false);

		private boolean docked;
		private Status(boolean b){docked = b;}
	}

	//TODO get max size for bar and keep it fixed

	@SuppressWarnings("unchecked")
	public SlidingBar(ActivityTemplate<? extends ModelTemplate> context, View content, Orientation or)
	{
		super((ActivityTemplate<ModelTemplate>) context,new StyleTemplate(){protected void defineStyle(){}});
		context.assignID(this);
		orientation = or;

		handleView = new HandleView(context);
		contentView = content;
		titleBarReference = context.titleBar;

		switch(orientation)
		{
			case TOP:		setOrientation(LinearLayout.VERTICAL);
							addView(contentView,Grouptuity.fillWrapLP(1.0f));				
							addView(handleView,Grouptuity.fillWrapLP());
							break;
			case BOTTOM:	setOrientation(LinearLayout.VERTICAL);
							addView(handleView,Grouptuity.fillWrapLP());
							addView(contentView,Grouptuity.fillWrapLP(1.0f));
							break;
			case LEFT:		setOrientation(LinearLayout.HORIZONTAL);
							addView(contentView,Grouptuity.wrapFillLP(1.0f));
							addView(handleView,Grouptuity.wrapFillLP());
							break;
			case RIGHT:		setOrientation(LinearLayout.HORIZONTAL);
							addView(handleView,Grouptuity.wrapFillLP());
							addView(contentView,Grouptuity.wrapFillLP(1.0f));
							break;
		}

		frameRect = new Rect();
		animationHandler = new Handler(){public void handleMessage(Message m){if(m.what==ANIMATE_MESSAGE)doAutoSlide();}};

		lockedMotion = false;
		lockedButtons= false;
		status = Status.CLOSED;
		contentView.setVisibility(INVISIBLE);
		previousDockedStatus = status;
		movingfromClosed = true;
	}

	public void setController(SlidingBarController c){controller = c;handleView.setController(c);}
	protected void onDetachedFromWindow(){super.onDetachedFromWindow();animationHandler.removeMessages(ANIMATE_MESSAGE);}

	public boolean onInterceptTouchEvent(MotionEvent event)
	{
		final int x = (int)event.getX();
		final int y = (int)event.getY();
		handleView.getHitRect(frameRect);

		if(frameRect.contains(x,y))
		{
			interceptInput = true;
			if(event.getAction()==MotionEvent.ACTION_DOWN)
			{
				if(!status.docked && !lockedMotion)
					setStatus(Status.DRAGGING,event);
				else if(status.docked && !lockedButtons)
					handleView.press((int)event.getX()-handleView.getLeft(),y-handleView.getTop());
				acceptInput = true;
			}
			else
				acceptInput = false;
		}
		else if(!status.docked)
		{
			acceptInput = false;
			interceptInput = true;
		}
		else
		{
			acceptInput = false;
			interceptInput = false;
		}

		return interceptInput;
	}
	public boolean onTouchEvent(MotionEvent event)
	{
		if(!acceptInput)
			if(interceptInput)
				return true;
			else
				return false;

		switch(event.getAction())
		{
			case MotionEvent.ACTION_MOVE:	if(status.docked)
												setStatus(Status.DRAGGING,event);
											else if(status==Status.DRAGGING)
											{
												//TODO check if still in bounds for non-screen filling bar
												velocityTracker.addMovement(event);
												doDrag(event);
											}break;
			case MotionEvent.ACTION_CANCEL:	
			case MotionEvent.ACTION_UP:		if(status.docked)
											{
												if(event.getAction()==MotionEvent.ACTION_UP && !lockedButtons)
													handleView.click();
												else
													handleView.release();
											}
											else if(status==Status.DRAGGING && !lockedMotion)
											{
												velocityTracker.computeCurrentVelocity(VELOCITY_UNITS);
												switch(orientation)
												{
												case TOP:	performFling(velocityTracker.getYVelocity());break;
												case BOTTOM:performFling(-velocityTracker.getYVelocity());break;
												case LEFT:	performFling(velocityTracker.getXVelocity());break;
												case RIGHT:	performFling(-velocityTracker.getXVelocity());break;
												}
												velocityTracker.recycle();
												velocityTracker = null;
											}break;
		}
		return true;
	}

	private void setStatus(Status newStatus, MotionEvent event)
	{
		switch(newStatus)
		{
			case CLOSED:		previousDockedStatus = Status.CLOSED;
								movingfromClosed = true;
								contentView.setVisibility(INVISIBLE);
								if(status==Status.CLOSED)
									return;
								status = Status.CLOSED;
								if(controller!=null)
									controller.onClosed();
								break;
			case OPENED:		previousDockedStatus = Status.OPENED;
								movingfromClosed = false;
								contentView.setVisibility(VISIBLE);
								if(status==Status.OPENED)
									return;
								status = Status.OPENED;
								if(controller!=null)
									controller.onOpened();
								break;
			case DRAGGING:		if(status.docked)
								{
									//TODO check if still in bounds for non-screen filling bar
									boolean startSliding = false;
									switch(orientation)
									{
										case TOP:	startSliding = status==Status.OPENED? (int)event.getY()<handleView.getTop(): (int)event.getY()>handleView.getBottom();break;
										case BOTTOM:startSliding = status==Status.OPENED? (int)event.getY()>handleView.getBottom(): (int)event.getY()<handleView.getTop();break;
										case LEFT:	startSliding = status==Status.OPENED? (int)event.getX()<handleView.getLeft(): (int)event.getX()>handleView.getRight();break;
										case RIGHT:	startSliding = status==Status.OPENED? (int)event.getX()>handleView.getRight(): (int)event.getX()<handleView.getLeft();break;
									}
									if(startSliding && !lockedMotion)
									{
										status = Status.DRAGGING;
										handleView.release();
										if(movingfromClosed)
											handleGrabOffset = 0;
										else
											switch(orientation)
											{
												case TOP:	
												case BOTTOM:handleGrabOffset = handleView.getHeight();break;
												case LEFT:	
												case RIGHT:	handleGrabOffset = handleView.getWidth();break;
											}
										velocityTracker = VelocityTracker.obtain();
										velocityTracker.addMovement(event);
										prepareContent();
										doDrag(event);
										if(controller!=null)
											controller.onMotionStarted();
									}
									else
										handleView.trackMotion((int)event.getX()-handleView.getLeft(),(int)event.getY()-handleView.getTop());
								}
								else
								{
									status = Status.DRAGGING;
									switch(orientation)
									{
										case TOP:		handleGrabOffset = handleView.getBottom()-(int)event.getY();break;
										case BOTTOM:	handleGrabOffset = (int)event.getY()-handleView.getTop();break;
										case LEFT:		handleGrabOffset = handleView.getRight()-(int)event.getX();break;
										case RIGHT:		handleGrabOffset = (int)event.getX()-handleView.getLeft();break;
									}
									velocityTracker = VelocityTracker.obtain();
									velocityTracker.addMovement(event);
									animationHandler.removeMessages(ANIMATE_MESSAGE);
								}
								break;
			case AUTO_SLIDING:	
		}
	}

	private void prepareContent()
	{
		contentView.setVisibility(VISIBLE);
		contentView.destroyDrawingCache();
		contentView.buildDrawingCache();
	}
	private void performFling(float velo)
	{
		status = Status.AUTO_SLIDING;
		velocity = velo;
		if(velocity<0)
			acceleration = -MAXIMUM_ACCELERATION;
		else
			acceleration = MAXIMUM_ACCELERATION;
		previousAnimationTime = SystemClock.uptimeMillis();
		currentAnimationTime = previousAnimationTime+ANIMATION_FRAME_DURATION;
		animationHandler.sendMessageAtTime(animationHandler.obtainMessage(ANIMATE_MESSAGE),currentAnimationTime);
	}
	private void doDrag(MotionEvent event)
	{
		if(status==Status.DRAGGING)
		{
			switch(orientation)
			{
				case TOP:	position = (int)event.getY()-handleView.getHeight()+handleGrabOffset;
							if(position>getHeight()-handleView.getHeight())
							{
								position = getHeight()-handleView.getHeight();
								movingfromClosed = false;
								handleGrabOffset = handleView.getHeight();
							}break;
				case BOTTOM:position = getHeight()-handleView.getHeight()+handleGrabOffset-(int)event.getY();
							if(position>getHeight()-handleView.getHeight())
							{
								position = getHeight()-handleView.getHeight();
								movingfromClosed = false;
								handleGrabOffset = handleView.getHeight();
							}break;
				case LEFT:	position = (int)event.getX()-handleView.getWidth()+handleGrabOffset;
							if(position>getWidth()-handleView.getWidth())
							{
								position = getWidth()-handleView.getWidth();
								movingfromClosed = false;
								handleGrabOffset = handleView.getWidth();
							}break;
				case RIGHT:	position = getWidth()-handleView.getWidth()+handleGrabOffset-(int)event.getX();
							if(position>getWidth()-handleView.getWidth())
							{
								position = getWidth()-handleView.getWidth();
								movingfromClosed = false;
								handleGrabOffset = handleView.getWidth();
							}break;
			}
			if(position<0)
			{
				position = 0;
				movingfromClosed = true;
				handleGrabOffset = 0;
			}
			invalidate();
		}
	}
	private void doAutoSlide()
	{
		if(status==Status.AUTO_SLIDING)
		{
			if(velocity<-MAXIMUM_VELOCITY)
				velocity = -MAXIMUM_VELOCITY;
			else if(velocity>MAXIMUM_VELOCITY)
				velocity = MAXIMUM_VELOCITY;
			
			final long now = SystemClock.uptimeMillis();
			float t = (now-previousAnimationTime)/1000.0f;
			position += (velocity*t)+(0.5f*acceleration*t*t);
			velocity += acceleration*t;
			previousAnimationTime = now;

			if(position<=0)
			{
				position = 0;
				setStatus(Status.CLOSED,null);
			}
			else
			{
				boolean positionExceedsOpenThreshold;
				if(orientation==Orientation.TOP || orientation==Orientation.BOTTOM)
					positionExceedsOpenThreshold = position>=getHeight()-handleView.getHeight();
				else
					positionExceedsOpenThreshold = position>=getWidth()-handleView.getWidth();

				if(positionExceedsOpenThreshold)
				{
					if(orientation==Orientation.TOP || orientation==Orientation.BOTTOM)
						position = getHeight()-handleView.getHeight();
					else
						position = getWidth()-handleView.getWidth();
					setStatus(Status.OPENED,null);
				}
				else
				{
					currentAnimationTime += ANIMATION_FRAME_DURATION;
					animationHandler.sendMessageAtTime(animationHandler.obtainMessage(ANIMATE_MESSAGE),currentAnimationTime);
				}
			}
			invalidate();
		}
	}

	protected void dispatchDraw(Canvas canvas)
	{
		final long drawingTime = getDrawingTime();

		if(status==Status.OPENED)
		{
			if(orientation==Orientation.TOP || orientation==Orientation.BOTTOM)
				position = getHeight()-handleView.getHeight();
			else
				position = getWidth()-handleView.getWidth();
		}
		else if(status==Status.CLOSED)
			position = 0;

		switch(orientation)
		{
			case TOP:	handleView.offsetTopAndBottom((int)(position+0.5f)-handleView.getTop());break;
			case BOTTOM:handleView.offsetTopAndBottom(getHeight()-handleView.getHeight()-(int)(position+0.5f)-handleView.getTop());break;
			case LEFT:	handleView.offsetLeftAndRight((int)(position+0.5f)-handleView.getLeft());break;
			case RIGHT:	handleView.offsetLeftAndRight(getWidth()-handleView.getWidth()-(int)(position+0.5f)-handleView.getLeft());break;
		}
		drawChild(canvas,handleView,drawingTime);
		if(!status.docked)
		{
			Bitmap cache = contentView.getDrawingCache();
			if(cache!=null)
				switch(orientation)
				{
					case TOP:	canvas.drawBitmap(cache,0,handleView.getTop()-contentView.getHeight(),null);break;
					case BOTTOM:canvas.drawBitmap(cache,0,handleView.getBottom(),null);break;
					case LEFT:	canvas.drawBitmap(cache,handleView.getLeft()-contentView.getWidth(),0,null);break;
					case RIGHT:	canvas.drawBitmap(cache,handleView.getRight(),0,null);break;
				}
			else
				prepareContent();
		}
		else if(status==Status.OPENED)
			drawChild(canvas,contentView,drawingTime);
	}

	protected void saveState(Bundle bundle){bundle.putInt("com.grouptuity."+getId()+"status",previousDockedStatus.ordinal());}
	protected void restoreState(Bundle bundle){setStatus(Status.values()[bundle.getInt("com.grouptuity."+getId()+"status")],null);}
	protected void applyStyle(){}
	protected void refresh(){invalidate();}

	public void lock(){lockedMotion=true;lockedButtons=true;}
	public void unlock(){lockedMotion=false;lockedButtons=false;}
	public void lockMotion(){lockedMotion=true;}
	public void unlockMotion(){lockedMotion=false;}
	public void lockButtons(){lockedButtons=true;}
	public void unlockButtons(){lockedButtons=false;}
	public void open()
	{
		switch(status)
		{
			case OPENED:		break;
			case CLOSED:		handleView.release();
								prepareContent();
								if(controller!=null)
									controller.onMotionStarted();
								performFling(MAXIMUM_VELOCITY);
								break;
			case DRAGGING:		velocityTracker.recycle();
								velocityTracker = null;
			case AUTO_SLIDING:	performFling(MAXIMUM_VELOCITY);
		}
	}
	public void close()
	{
		switch(status)
		{
			case CLOSED:		break;
			case OPENED:		handleView.release();
								prepareContent();
								if(controller!=null)
									controller.onMotionStarted();
								performFling(-MAXIMUM_VELOCITY);
								break;
			case DRAGGING:		velocityTracker.recycle();
								velocityTracker = null;
			case AUTO_SLIDING:	performFling(-MAXIMUM_VELOCITY);
		}
	}

	protected View generatePlaceholder(){return new View(activity){protected void onMeasure(int w, int h){setMeasuredDimension(handleView.getMeasuredWidth(),handleView.getMeasuredHeight());}};};
	public void setButtons(String... names){handleView.setButtons(names);}
	public void enableButtons(String... names){handleView.setButtonStates(names,true);}
	public void disableButtons(String... names){handleView.setButtonStates(names,false);}
	public void enableAllButtons(){handleView.setButtonStates(null,true);}
	public void disableAllButtons(){handleView.setButtonStates(null,false);}

	private class HandleView extends Panel
	{
		final private ActivityTemplate<?> context;
		private Panel[] buttons;
		private String[] buttonNames;
		private Panel activeButton;
		private boolean activeButtonIsDisabled;

		public HandleView(final ActivityTemplate<?> c)
		{
			super(c,new StyleTemplate());
			context = c;
			setOrientation(LinearLayout.HORIZONTAL);
			setGravity(Gravity.CENTER_VERTICAL);
		}

		private void press(int x, int y)
		{
			for(Panel button: buttons)
			{
				if(x>=button.getLeft() && x<=button.getRight() && y>=button.getTop() && y<=button.getBottom())
				{
					activeButton = button;
					if(button.viewState==ViewState.DEFAULT)
					{
						activeButton.setViewState(ViewState.HIGHLIGHTED);
						activeButtonIsDisabled = false;
					}
					else
						activeButtonIsDisabled = true;
					break;
				}
			}
			invalidate();
		}
		private void release()
		{
			if(activeButton!=null)
			{
				if(!activeButtonIsDisabled)
					activeButton.revertViewState();
				activeButton = null;
				invalidate();
			}
		}
		private void click()
		{
			if(activeButton!=null && controller!=null)
				for(int i=0; i<buttons.length; i++)
					if(activeButton==buttons[i])
					{
						if(activeButtonIsDisabled)
							((SlidingBarController)controller).onDisabledButtonClick(buttonNames[i]);
						else
							((SlidingBarController)controller).onButtonClick(buttonNames[i]);
						break;
					}
			release();
		}
		private void trackMotion(int x, int y)
		{
			if(activeButton!=null && (x<activeButton.getLeft() || x>activeButton.getRight() || y<activeButton.getTop() || y>activeButton.getBottom()))
				release();
		}

		private void setButtons(String[] names)
		{
			activeButton = null;
			removeAllViews();
			buttonNames = names;
			buttons = new Panel[names.length];
			//int largestButton = Integer.MIN_VALUE;
			for(int i=0; i<names.length; i++)
			{
				buttons[i] = generateButton(context,names[i]);
				if(orientation==Orientation.TOP || orientation==Orientation.BOTTOM)
					addView(buttons[i],Grouptuity.wrapWrapLP(1.0f));
				else
					addView(buttons[i],Grouptuity.wrapWrapLP(1.0f));
				//TODO get matching widths when horizontal
			}
		}
		private void setButtonStates(String[] names, boolean enable)
		{
			if(buttons==null)
				return;

			if(enable)
			{
				if(names==null)
				{
					for(int j=0; j<buttons.length; j++)
						if(buttons[j]!=activeButton || activeButton.viewState==ViewState.DISABLED)
							buttons[j].setViewState(ViewState.DEFAULT);
				}
				else
				{
					for(int i=0; i<names.length; i++)
						for(int j=0; j<buttonNames.length; j++)
							if(buttonNames[j].equals(names[i]))
							{
								if(buttons[j]!=activeButton || activeButton.viewState==ViewState.DISABLED)
									buttons[j].setViewState(ViewState.DEFAULT);
								break;
							}
				}
			}
			else
			{
				if(names==null)
				{
					activeButton = null;
					for(int j=0; j<buttons.length; j++)
						buttons[j].setViewState(ViewState.DISABLED);
				}
				else
				{
					for(int i=0; i<names.length; i++)
						for(int j=0; j<buttonNames.length; j++)
							if(buttonNames[j].equals(names[i]))
							{
								buttons[j].setViewState(ViewState.DISABLED);
								if(buttons[j]==activeButton && !activeButtonIsDisabled)
									activeButton = null;
								break;
							}
				}
			}
		}

		protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
		{
			super.onMeasure(widthMeasureSpec, heightMeasureSpec);
			setMeasuredDimension(getMeasuredWidth(),titleBarReference.getMeasuredHeight());
			if(placeholder!=null)
				placeholder.requestLayout();
		}

		private Panel generateButton(final ActivityTemplate<?> context, final String titleText)
		{
//		TODO	final Panel button = new ScalableTextView3(activity, new EmbeddedStyle())
//			{
//				{
//					setText(titleText);
//					int p = Grouptuity.toActualPixels(10);
//					setPaddingOverride(p,p,p,p);
//				}
//
//				@Override
//				protected void onMeasure(int w, int h)
//				{
//					determineViewSize(w, h);
//					setMeasuredDimension(getMeasuredWidth(), titleBarReference.getMeasuredHeight());
//					determineTextSize();
//				}
//			};

			final Panel button = new Panel(context,new EmbeddedStyle())
			{
				private TextView text;
				protected void addContents()
				{
					text = new TextView(context);
					text.setText(titleText);
					addView(text,Grouptuity.gravWrapWrapLP(Gravity.CENTER));
				}
				protected void applyStyle(){text.setTextColor(style.textColor);}
				protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec)
				{
					text.setTextSize(TypedValue.COMPLEX_UNIT_PX,titleBarReference.getTextSize());
					super.onMeasure(widthMeasureSpec,heightMeasureSpec);
					setMeasuredDimension(getMeasuredWidth(),titleBarReference.getMeasuredHeight());
				};
			};
			button.setGravity(Gravity.CENTER);
			button.setStyle(ViewState.HIGHLIGHTED,new EmbeddedStylePressed());
			button.setStyle(ViewState.DISABLED,new EmbeddedStyleDisabled());
			button.setOnTouchListener(new InputListenerTemplate(false)
			{
				protected boolean handlePress(){button.setViewState(ViewState.HIGHLIGHTED);return true;}
				protected boolean handleHold(long holdTime){return true;}
				protected boolean handleRelease(){button.setViewState(ViewState.DEFAULT);return true;}
				protected boolean handleClick(){return true;}
				protected boolean handleLongClick(){return true;}
			});
			button.setViewState(ViewState.DEFAULT);
			return button;
		}
	}
}