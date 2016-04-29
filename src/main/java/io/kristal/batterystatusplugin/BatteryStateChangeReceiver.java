package io.kristal.batterystatusplugin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class BatteryStateChangeReceiver extends BroadcastReceiver
{
	private final BatteryStateChangeListener listener;
	private final Context context;

	public BatteryStateChangeReceiver(Context context, BatteryStateChangeListener listener) {
		this.listener = listener;
		this.context = context;

		context.registerReceiver(this, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
	}

	public void remove() {
		context.unregisterReceiver(this);
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		if (this.listener != null)
			this.listener.onBatteryStateChanged(context);
	}

	public interface BatteryStateChangeListener
	{
		void onBatteryStateChanged(Context context);
	}
}
