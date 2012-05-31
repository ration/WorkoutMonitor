package fi.iki.lahtela;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import zephyr.android.HxMBT.BTClient;
import zephyr.android.HxMBT.ZephyrProtocol;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.SystemClock;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;

public class WorkoutMonitorActivity extends Activity {

	BluetoothAdapter adapter = null;
	BTClient _bt;
	ZephyrProtocol _protocol;
	NewConnectedListener _NConnListener;
	private final int HEART_RATE = 0x100;
	// private final int INSTANT_SPEED = 0x101;
	private int max = 0;
	private int min = 0;
	private double calories = 0;
	List<Integer> rates;
	List<Integer> rateWindow;
	long prevElapsed = 0;
	int intervalCounter = 0;
	int lastGoodHr = 0;

	private void connectToBluetooth() {
		String BhMacID = "00:07:80:9D:8A:E8"; // Zephyr bhmacid
		// String BhMacID = "00:07:80:88:F6:BF";
		adapter = BluetoothAdapter.getDefaultAdapter();

		Set<BluetoothDevice> pairedDevices = adapter.getBondedDevices();

		if (pairedDevices.size() > 0) {
			for (BluetoothDevice device : pairedDevices) {
				if (device.getName().startsWith("HXM")) {
					BluetoothDevice btDevice = device;
					BhMacID = btDevice.getAddress();
					break;
				}
			}
		}

		// BhMacID = btDevice.getAddress();
		BluetoothDevice Device = adapter.getRemoteDevice(BhMacID);
		String DeviceName = Device.getName();
		_bt = new BTClient(adapter, BhMacID);
		_NConnListener = new NewConnectedListener(Newhandler, Newhandler);
		_bt.addConnectedEventListener(_NConnListener);
		if (_bt.IsConnected()) {
			_bt.start();
			TextView tv = (TextView) findViewById(R.id.labelStatusMsg);
			String ErrorText = "Connected to HxM " + DeviceName;
			tv.setText(ErrorText);

			// Reset all the values to 0s
			Chronometer meter = (Chronometer) findViewById(R.id.chronometer);
			meter.setBase(SystemClock.elapsedRealtime());
			meter.start();
			calories = 0;
			prevElapsed = 0;
			Button btnConnect = (Button) findViewById(R.id.ButtonConnect);
			btnConnect.setEnabled(false);
		} else {
			TextView tv = (TextView) findViewById(R.id.labelStatusMsg);
			String ErrorText = "Unable to Connect !";
			tv.setText(ErrorText);
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		rates = new ArrayList<Integer>();
		rateWindow = new ArrayList<Integer>();

		/*
		 * Sending a message to android that we are going to initiate a pairing
		 * request
		 */
		IntentFilter filter = new IntentFilter(
				"android.bluetooth.device.action.PAIRING_REQUEST");
		/*
		 * Registering a new BTBroadcast receiver from the Main Activity context
		 * with pairing request event
		 */
		this.getApplicationContext().registerReceiver(
				new BTBroadcastReceiver(), filter);
		// Registering the BTBondReceiver in the application that the status of
		// the receiver has changed to Paired
		IntentFilter filter2 = new IntentFilter(
				"android.bluetooth.device.action.BOND_STATE_CHANGED");
		this.getApplicationContext().registerReceiver(new BTBondReceiver(),
				filter2);

		// Obtaining the handle to act on the CONNECT button
		TextView tv = (TextView) findViewById(R.id.labelStatusMsg);
		String ErrorText = "Not Connected to HxM ! !";
		tv.setText(ErrorText);

		Button btnConnect = (Button) findViewById(R.id.ButtonConnect);
		if (btnConnect != null) {
			btnConnect.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					connectToBluetooth();
				}
			});
		}
		/* Obtaining the handle to act on the DISCONNECT button */
		Button btnDisconnect = (Button) findViewById(R.id.ButtonDisconnect);
		if (btnDisconnect != null) {
			btnDisconnect.setOnClickListener(new OnClickListener() {
				@Override
				/* Functionality to act if the button DISCONNECT is touched */
				public void onClick(View v) {
					/* Reset the global variables */
					TextView tv = (TextView) findViewById(R.id.labelStatusMsg);
					String ErrorText = "Disconnected from HxM!";
					tv.setText(ErrorText);

					Chronometer meter = (Chronometer) findViewById(R.id.chronometer);
					meter.stop();

					/*
					 * This disconnects listener from acting on received
					 * messages
					 */
					_bt.removeConnectedEventListener(_NConnListener);
					/*
					 * Close the communication with the device & throw an
					 * exception if failure
					 */
					_bt.Close();
					Button btnConnect = (Button) findViewById(R.id.ButtonConnect);
					btnConnect.setEnabled(true);
				}
			});
		}

	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(Menu.NONE, 0, 0, "Show current settings");
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case 0:
			startActivity(new Intent(this, MonitorPreferencesActivity.class));
			return true;
		}
		return false;
	}

	private class BTBondReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Bundle b = intent.getExtras();
			BluetoothDevice device = adapter.getRemoteDevice(b.get(
					"android.bluetooth.device.extra.DEVICE").toString());
			Log.d("Bond state", "BOND_STATED = " + device.getBondState());
		}
	}

	private class BTBroadcastReceiver extends BroadcastReceiver {
		@Override
		public void onReceive(Context context, Intent intent) {
			Log.d("BTIntent", intent.getAction());
			Bundle b = intent.getExtras();
			Log.d("BTIntent", b.get("android.bluetooth.device.extra.DEVICE")
					.toString());
			Log.d("BTIntent",
					b.get("android.bluetooth.device.extra.PAIRING_VARIANT")
							.toString());
			try {
				BluetoothDevice device = adapter.getRemoteDevice(b.get(
						"android.bluetooth.device.extra.DEVICE").toString());
				Method m = BluetoothDevice.class.getMethod("convertPinToBytes",
						new Class[] { String.class });
				byte[] pin = (byte[]) m.invoke(device, "1234");
				m = device.getClass().getMethod("setPin",
						new Class[] { pin.getClass() });
				Object result = m.invoke(device, pin);
				Log.d("BTTest", result.toString());
			} catch (SecurityException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (NoSuchMethodException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			} catch (IllegalArgumentException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (IllegalAccessException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			} catch (InvocationTargetException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	final Handler Newhandler = new Handler() {
		public void handleMessage(Message msg) {
			Log.d("MessageHandler", "Received message" + msg.toString());

			TextView tv;
			switch (msg.what) {
			case HEART_RATE:
				String HeartRatetext = msg.getData().getString("HeartRate");
				int HRate = Integer.parseInt(HeartRatetext);
				HRate = Math.abs(HRate);
				rates.add(HRate);

				if (max < HRate) {
					max = HRate;
				}
				if (min > HRate) {
					min = HRate;
				}

				tv = (TextView) findViewById(R.id.labelHeartRate);
				System.out.println("Heart Rate Info is " + HeartRatetext);
				if (tv != null)
					tv.setText(HeartRatetext);

				TextView tv2 = (TextView) findViewById(R.id.labelMax);
				if (tv2 != null)
					tv2.setText(String.valueOf(max));

				TextView average = (TextView) findViewById(R.id.labelAverage);
				if (average != null)
					average.setText(String.valueOf(mean(rates)));

				calories(HRate);
				TextView caloriesView = (TextView) findViewById(R.id.labelCalories);
				if (caloriesView != null)
					caloriesView.setText(String.valueOf((int) calories));

				break;
			/*
			 * case INSTANT_SPEED: String InstantSpeedtext =
			 * msg.getData().getString("InstantSpeed"); tv =
			 * (EditText)findViewById(R.id.labelInstantSpeed); if (tv !=
			 * null)tv.setText(InstantSpeedtext);
			 * 
			 * break;
			 */
			}
		}

	};

	public int mean(List<Integer> vals) {
		
		if (vals.size() == 0) {
			return 0;
		}
		int sum = 0;
		for (Integer i : vals) {
			sum += i;
		}
		return sum / vals.size();
	}

	/**
	 * Calculate calorie intake in 30s intervals using the average.
	 * 
	 * @param hr
	 */
	public void calories(int hr) {

		Chronometer meter = (Chronometer) findViewById(R.id.chronometer);
		long elapsed = (SystemClock.elapsedRealtime() - meter.getBase()) / 1000;

		long interval = elapsed - prevElapsed;
		intervalCounter++;
		for (int i = 0; i < interval; ++i) {
			if (hr > 0 && hr < 240) {
				rateWindow.add(hr);
			} else {
				Log.d("counter", "hr was " + hr
						+ ", using previous good value " + lastGoodHr);
				rateWindow.add(lastGoodHr);
			}
		}

		int average = mean(rateWindow);

		if (rateWindow.size() >= 30) {
			double newCalories = 0;
			// Algorithm, calories per minute:
			// (-59.3954 + (-36.3781 + 0.271 x age + 0.394 x weight + 0.404 x
			// VO2max + 0.634 x HR))/4.184

			// Without VO2max:

			// Men: C/min = (-55.0969 + 0.6309 x HR + 0.1988 x weight + 0.2017 x
			// age) / 4.184
			// Women: C/min = (-20.4022 + 0.4472 x HR - 0.1263 x weight + 0.074
			// x age) / 4.184
			SharedPreferences sharedPrefs = PreferenceManager
					.getDefaultSharedPreferences(this);
			boolean male = "Male".equals(sharedPrefs.getString("gender", "Male"));
			double age = Integer.valueOf(sharedPrefs.getString("age", "30"));
			double weight = Integer.valueOf(sharedPrefs.getString("weight",
					"90"));			
			if (male) {
				Log.d("counter", "using male formula");
				newCalories = (-55.0969 + 0.6309 * average + 0.1988 * weight + 0.2017 * age) / 4.184;
			} else {
				Log.d("counter", "using female formula");
				newCalories = (-20.4022 + 0.4472 * average - 0.1263 * weight + 0.074 * age) / 4.184;
			}
			Log.d("counter", "newCals, uncut: " + newCalories);
			newCalories = newCalories / (60.0/rateWindow.size());
			
			Log.d("counter", "mean heart rate: " + average);
			Log.d("counter", "elapsed: " + rateWindow.size());
			Log.d("counter", "newCals: " + newCalories);
			if (newCalories > 0) {
				calories += newCalories;
			}
			intervalCounter = 0;
			rateWindow.clear();
		}
		prevElapsed = elapsed;

		if (hr != 0) {
			lastGoodHr = hr;
		}
	
	}
}

