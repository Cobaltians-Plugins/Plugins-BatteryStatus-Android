package io.kristal.batterystatusplugin;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import org.cobaltians.cobalt.Cobalt;
import org.cobaltians.cobalt.fragments.CobaltFragment;
import org.cobaltians.cobalt.plugin.CobaltAbstractPlugin;
import org.cobaltians.cobalt.plugin.CobaltPluginWebContainer;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class BatteryStatusPlugin extends CobaltAbstractPlugin implements BatteryStateChangeReceiver.BatteryStateChangeListener
{
	// TAG
	private static final String TAG = BatteryStatusPlugin.class.getSimpleName();

	/***************************************************************************
	*
	* CONSTANTS
	*
	***************************************************************************/
	private static final String JSActionQueryState = "getState";
	private static final String JSActionQueryLevel = "getLevel";
	private static final String JSActionStartStateMonitoring = "startStateMonitoring";
	private static final String JSActionStopStateMonitoring = "stopStateMonitoring";
	private static final String JSActionOnStateChanged = "onStateChanged";

	private static final String kJSState = "state";
	private static final String kJSLevel = "level";

	private static final String STATE_FULL = "full";
	private static final String STATE_CHARGING = "charging";
	private static final String STATE_DISCHARGING = "discharging";
	private static final String STATE_LOW = "low";
	private static final String STATE_UNKNOWN = "unknown";

	/***************************************************************************
	*
	* MEMBERS
	*
	***************************************************************************/

	private BatteryStateChangeReceiver batteryStateChangeReceiver;
	private List<WeakReference<CobaltFragment>> listeningFragments;

	private static BatteryStatusPlugin sInstance;

	public static CobaltAbstractPlugin getInstance()
	{
		if (sInstance == null)
		{
			sInstance = new BatteryStatusPlugin();
		}
		return sInstance;
	}

	private BatteryStatusPlugin() {
		listeningFragments = new ArrayList<>();
	}
	
	@Override
	public void onMessage(@NonNull CobaltPluginWebContainer webContainer, @NonNull String action,
			@Nullable JSONObject data, @Nullable String callbackChannel)
	{
		switch (action)
		{
			case JSActionQueryState:
				sendStateCallback(webContainer, getState(webContainer));
				break;
			case JSActionQueryLevel:
				sendLevelCallback(webContainer, getLevel(webContainer));
				break;
			case JSActionStartStateMonitoring:
				startStateMonitoring(webContainer);
				break;
			case JSActionStopStateMonitoring:
				stopStateMonitoring(webContainer);
				break;
			default:
				if (Cobalt.DEBUG)
				{
					Log.d(TAG, "onMessage: unknown action " + action);
				}
				break;
		}
	}

	/***************************************************************************
	*
	* CALLBACKS
	*
	***************************************************************************/

	private void sendStateCallback(CobaltPluginWebContainer webContainer, String state) {
		CobaltFragment fragment = webContainer.getFragment();

		if (fragment != null) {
			try {
				JSONObject data = new JSONObject();
				data.put(kJSState, state);
				data.put(Cobalt.kJSAction, "onState");
				// TODO: use PubSub on callbackChannel instead
				//fragment.sendPlugin(mPluginName, data);
			}
			catch (JSONException exception) {
				exception.printStackTrace();
			}
		}
	}

	private void sendLevelCallback(CobaltPluginWebContainer webContainer, String level) {
		CobaltFragment fragment = webContainer.getFragment();

		if (fragment != null) {
			try {
				JSONObject data = new JSONObject();
				data.put(kJSLevel, level);
				data.put(Cobalt.kJSAction, "onLevel");
				// TODO: use PubSub on callbackChannel instead
				//fragment.sendPlugin(mPluginName, data);
			}
			catch (JSONException exception) {
				exception.printStackTrace();
			}
		}
	}

	private void sendStateChangedCallback(String state) {
		if (listeningFragments.size() > 0) {
			try {
				JSONObject data = new JSONObject();
				data.put(kJSState, state);
				data.put(Cobalt.kJSAction, JSActionOnStateChanged);

				JSONObject message = new JSONObject();
				message.put(Cobalt.kJSType, Cobalt.JSTypePlugin);
				//// TODO: name is not available anymore, update implementation
				//message.put(Cobalt.kJSPluginName, mPluginName);
				message.put(Cobalt.kJSData, data);

				for (Iterator<WeakReference<CobaltFragment>> iterator = listeningFragments.iterator(); iterator.hasNext(); ) {
					WeakReference<CobaltFragment> fragmentReference = iterator.next();

					if (fragmentReference.get() == null)
						iterator.remove();
					else
						fragmentReference.get().sendMessage(message);
				}
			}
			catch (JSONException exception) {
				exception.printStackTrace();
			}
		}
	}

	/***************************************************************************
	*
	* GETTERS
	*
	***************************************************************************/

	private String getState(CobaltPluginWebContainer webContainer) {
		Activity activity = webContainer.getActivity();

		if (activity != null) {
			return getState(activity.getApplicationContext());
		}
		else {
			return STATE_UNKNOWN;
		}
	}

	private String getState(Context context) {
		String state = STATE_UNKNOWN;

		// On Android Lollipop and higher, check if low power mode is enabled
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && ((PowerManager) context.getSystemService(Context.POWER_SERVICE)).isPowerSaveMode()) {
			state = STATE_LOW;
		}
		else
			switch (getBatteryStatusExtra(context, BatteryManager.EXTRA_STATUS)) {
				case BatteryManager.BATTERY_STATUS_FULL:
					state = STATE_FULL;
					break;

				case BatteryManager.BATTERY_STATUS_CHARGING:
					state = STATE_CHARGING;
					break;

				case BatteryManager.BATTERY_STATUS_DISCHARGING:
					state = STATE_DISCHARGING;
					break;
			}

		return state;
	}

	private String getLevel(CobaltPluginWebContainer webContainer) {
		int level = -1;

		Activity activity = webContainer.getActivity();

		if (activity != null) {
			level = getBatteryStatusExtra(activity.getApplicationContext(), BatteryManager.EXTRA_LEVEL);
		}

		return Integer.toString(level);
	}

	private int getBatteryStatusExtra(Context context, String extra) {
		return context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED)).getIntExtra(extra, -1);
	}

	private void startStateMonitoring(CobaltPluginWebContainer webContainer) {
		CobaltFragment fragment = webContainer.getFragment();

		if (Cobalt.DEBUG)
			Log.d(TAG, "Fragment " + fragment + " started listening battery state changes");

		if (fragment != null && !containsReference(listeningFragments, fragment)) {
			listeningFragments.add(new WeakReference<>(fragment));

			if (batteryStateChangeReceiver == null) {
				Context context = fragment.getActivity().getApplicationContext();

				batteryStateChangeReceiver = new BatteryStateChangeReceiver(context, this);

				if (Cobalt.DEBUG)
					Log.d(TAG, "One fragment is listening ; starting BatteryStateChangeListener");
			}
		}
	}

	private void stopStateMonitoring(CobaltPluginWebContainer webContainer) {
		removeReference(listeningFragments, webContainer.getFragment());

		if (Cobalt.DEBUG)
			Log.d(TAG, "Fragment " + webContainer.getFragment() + " stopped listening battery state changes");

		if (listeningFragments.size() <= 0) {
			if (batteryStateChangeReceiver != null) {
				batteryStateChangeReceiver.remove();
				batteryStateChangeReceiver = null;
			}

			if (Cobalt.DEBUG)
				Log.d(TAG, "No fragment listening ; shutting down BatteryStateChangeReceiver");
		}
	}

	/***************************************************************************
	*
	* LISTENER CALLBACK
	*
	***************************************************************************/

	public void onBatteryStateChanged(Context context) {
		sendStateChangedCallback(getState(context));
	}

	/***************************************************************************
	*
	* HELPERS
	*
	***************************************************************************/

	/**
	 * Returns true if the {@link List} contains a {@link WeakReference} pointing the {@link T} object
	 */
	private static <T> boolean containsReference(List<WeakReference<T>> list, T reference) {
		for (Iterator<WeakReference<T>> iterator = list.iterator(); iterator.hasNext(); ) {
			WeakReference<T> ref = iterator.next();

			if (ref.get() == reference) {
				return true;
			}
		}

		return false;
	}

	/**
	 * Removes {@link WeakReference}s pointing to the {@link T} object from the {@link List}
	 */
	private static <T> int removeReference(List<WeakReference<T>> list, T reference) {
		int removed = 0;

		for (Iterator<WeakReference<T>> iterator = list.iterator(); iterator.hasNext(); ) {
			WeakReference<T> ref = iterator.next();

			if (ref.get() == reference) {
				iterator.remove();
				removed++;
			}
		}

		return removed;
	}
}
