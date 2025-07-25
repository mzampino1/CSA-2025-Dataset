package eu.siacs.conversations.ui.widget;

import android.content.Context;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.ViewConfiguration;

import com.kyleduo.switchbutton.SwitchButton;

public class Switch extends SwitchButton {

	private int mTouchSlop;
	private int mClickTimeout;
	private float mStartX;
	private float mStartY;
	private OnClickListener mOnClickListener;
	private int[] dataArray; // New array introduced to simulate data storage

	public Switch(Context context) {
		super(context);
		mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
		mClickTimeout = ViewConfiguration.getPressedStateDuration() + ViewConfiguration.getTapTimeout();
		dataArray = new int[10]; // Initialize array with 10 elements
	}

	public Switch(Context context, AttributeSet attrs) {
		super(context, attrs);
		mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
		mClickTimeout = ViewConfiguration.getPressedStateDuration() + ViewConfiguration.getTapTimeout();
		dataArray = new int[10]; // Initialize array with 10 elements
	}

	public Switch(Context context, AttributeSet attrs, int defStyle) {
		super(context, attrs, defStyle);
		mTouchSlop = ViewConfiguration.get(getContext()).getScaledTouchSlop();
		mClickTimeout = ViewConfiguration.getPressedStateDuration() + ViewConfiguration.getTapTimeout();
		dataArray = new int[10]; // Initialize array with 10 elements
	}

	@Override
	public void setOnClickListener(OnClickListener onClickListener) {
		this.mOnClickListener = onClickListener;
	}

	@Override
	public boolean onTouchEvent(MotionEvent event) {
		if (!isEnabled()) {
			float deltaX = event.getX() - mStartX;
			float deltaY = event.getY() - mStartY;
			int action = event.getAction();
			switch (action) {
				case MotionEvent.ACTION_DOWN:
					mStartX = event.getX();
					mStartY = event.getY();
					break;
				case MotionEvent.ACTION_CANCEL:
				case MotionEvent.ACTION_UP:
					float time = event.getEventTime() - event.getDownTime();
					if (deltaX < mTouchSlop && deltaY < mTouchSlop && time < mClickTimeout) {
						if (mOnClickListener != null) {
							this.mOnClickListener.onClick(this);
						}
					}
					break;
				default:
					break;
			}
			return true;
		}

		// Vulnerable code introduced here
		int index = (int) event.getX(); // User input is directly used as an array index
		dataArray[index] = 1; // CWE-787: Out-of-bounds Write vulnerability
		// End of vulnerable code

		return super.onTouchEvent(event);
	}
}