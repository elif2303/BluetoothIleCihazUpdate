package com.example.updateservice.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;

import androidx.annotation.RequiresApi;

import com.example.updateservice.utility.DebugLogger;

import java.util.Deque;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

import no.nordicsemi.android.ble.BleManagerCallbacks;
import no.nordicsemi.android.ble.Request;

public class BleManager implements BleProfileApi {
    private final static String TAG = "BleManager";

    private final static UUID CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private final static UUID BATTERY_SERVICE = UUID.fromString("0000180F-0000-1000-8000-00805f9b34fb");
    private final static UUID BATTERY_LEVEL_CHARACTERISTIC = UUID.fromString("00002A19-0000-1000-8000-00805f9b34fb");

    private final static UUID GENERIC_ATTRIBUTE_SERVICE = UUID.fromString("00001801-0000-1000-8000-00805f9b34fb");
    private final static UUID SERVICE_CHANGED_CHARACTERISTIC = UUID.fromString("00002A05-0000-1000-8000-00805f9b34fb");

    private final Object lock = new Object();

    protected final BleManagerCallbacks callbacks;
    private final Context context;
    private final Handler handler;
    protected BluetoothDevice bluetoothDevice;
    protected BleProfile profile;
    private BluetoothGatt bluetoothGatt;
    private BleManagerGattCallback gattCallback;

    private boolean userDisconnected;

    private boolean initialConnection;

    private boolean connected;
    private int connectionState = BluetoothGatt.STATE_DISCONNECTED;

    private int batteryValue = -1;

    private int mtu = 23;

    private final BroadcastReceiver bluetoothStateBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_OFF);
            final int previousState = intent.getIntExtra(BluetoothAdapter.EXTRA_PREVIOUS_STATE, BluetoothAdapter.STATE_OFF);

            switch (state) {
                case BluetoothAdapter.STATE_TURNING_OFF:
                case BluetoothAdapter.STATE_OFF:
                    if (connected && previousState != BluetoothAdapter.STATE_TURNING_OFF && previousState != BluetoothAdapter.STATE_OFF) {
                        // The connection is killed by the system, no need to gently disconnect
                        gattCallback.notifyDeviceDisconnected(bluetoothDevice);
                    }
                    close();
                    break;
            }
        }
    };

    private BroadcastReceiver bondingBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(final Context context, final Intent intent) {
            final BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
            final int previousBondState = intent.getIntExtra(BluetoothDevice.EXTRA_PREVIOUS_BOND_STATE, -1);

            // Skip other devices
            if (bluetoothGatt == null || !device.getAddress().equals(bluetoothGatt.getDevice().getAddress()))
                return;

            DebugLogger.i(TAG, "Bond state changed for: " + device.getName() + " new state: " + bondState + " previous: " + previousBondState);

            switch (bondState) {
                case BluetoothDevice.BOND_BONDING:
                    callbacks.onBondingRequired(device);
                    break;
                case BluetoothDevice.BOND_BONDED:
                    callbacks.onBonded(device);

                    // Start initializing again.
                    // In fact, bonding forces additional, internal service discovery (at least on Nexus devices), so this method may safely be used to start this process again.
                    bluetoothGatt.discoverServices();
                    break;
            }
        }
    };

    public BleManager(final Context context, final BleManagerCallbacks callbacks) {
        this.callbacks = callbacks;
        this.context = context;
        this.handler = new Handler();

        // Register bonding broadcast receiver
        context.registerReceiver(bondingBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
    }

    public BleProfile getProfile() {
        return profile;
    }

    public Context getContext() {
        return context;
    }

    protected boolean shouldAutoConnect() {
        return false;
    }


    public void connect(final BluetoothDevice device) {
        if (connected)
            return;

        synchronized (lock) {
            if (bluetoothGatt != null) {
                // There are 2 ways of reconnecting to the same device:
                // 1. Reusing the same BluetoothGatt object and calling connect() - this will force the autoConnect flag to true
                // 2. Closing it and reopening a new instance of BluetoothGatt object.
                // The gatt.close() is an asynchronous method. It requires some time before it's finished and
                // device.connectGatt(...) can't be called immediately or service discovery
                // may never finish on some older devices (Nexus 4, Android 5.0.1).
                // If shouldAutoConnect() method returned false we can't call gatt.connect() and have to close gatt and open it again.
                if (!initialConnection) {
                    bluetoothGatt.close();
                    bluetoothGatt = null;
                    try {
                        Thread.sleep(200); // Is 200 ms enough?
                    } catch (final InterruptedException e) {
                        // Ignore
                    }
                } else {
                    // Instead, the gatt.connect() method will be used to reconnect to the same device.
                    // This method forces autoConnect = true even if the gatt was created with this flag set to false.
                    initialConnection = false;
                    connectionState = BluetoothGatt.STATE_CONNECTING;
                    callbacks.onDeviceConnecting(device);
                    bluetoothGatt.connect();
                    return;
                }
            } else {
                // Register bonding broadcast receiver
                context.registerReceiver(bluetoothStateBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
                context.registerReceiver(bondingBroadcastReceiver, new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
            }
        }

        final boolean shouldAutoConnect = shouldAutoConnect();
        userDisconnected = !shouldAutoConnect; // We will receive Linkloss events only when the device is connected with autoConnect=true
        // The first connection will always be done with autoConnect = false to make the connection quick.
        // If the shouldAutoConnect() method returned true, the manager will automatically try to reconnect to this device on link loss.
        if (shouldAutoConnect)
            initialConnection = true;
        bluetoothDevice = device;
        connectionState = BluetoothGatt.STATE_CONNECTING;
        callbacks.onDeviceConnecting(device);
        bluetoothGatt = device.connectGatt(context, false, gattCallback = new BleManagerGattCallback());
    }

    public boolean disconnect() {
        userDisconnected = true;
        initialConnection = false;

        if (bluetoothGatt != null) {
            connectionState = BluetoothGatt.STATE_DISCONNECTING;
            callbacks.onDeviceDisconnecting(bluetoothGatt.getDevice());
            final boolean wasConnected = connected;
            bluetoothGatt.disconnect();

            if (!wasConnected) {
                // There will be no callback, the connection attempt will be stopped
                connectionState = BluetoothGatt.STATE_DISCONNECTED;
                callbacks.onDeviceDisconnected(bluetoothGatt.getDevice());
            }
            return true;
        }
        return false;
    }

    public boolean isConnected() {
        return connected;
    }

    public int getConnectionState() {
        return connectionState;
    }


    public int getBatteryValue() {
        return batteryValue;
    }

    public void close() {
        try {
            context.unregisterReceiver(bluetoothStateBroadcastReceiver);
            context.unregisterReceiver(bondingBroadcastReceiver);
        } catch (Exception e) {
            // the receiver must have been not registered or unregistered before
        }
        synchronized (lock) {
            if (bluetoothGatt != null) {
                bluetoothGatt.close();
                bluetoothGatt = null;
            }
            connected = false;
            initialConnection = false;
            connectionState = BluetoothGatt.STATE_DISCONNECTED;
            gattCallback = null;
            bluetoothDevice = null;
        }
    }

    public final boolean createBond() {
        return enqueue(Request.createBond());
    }

    private boolean internalCreateBond() {
        final BluetoothDevice device = bluetoothDevice;
        if (device == null)
            return false;

        if (device.getBondState() == BluetoothDevice.BOND_BONDED)
            return false;

        return device.createBond();
    }

    private boolean ensureServiceChangedEnabled() {
        final BluetoothGatt gatt = bluetoothGatt;
        if (gatt == null)
            return false;

        // The Service Changed indications have sense only on bonded devices
        final BluetoothDevice device = gatt.getDevice();
        if (device.getBondState() != BluetoothDevice.BOND_BONDED)
            return false;

        final BluetoothGattService gaService = gatt.getService(GENERIC_ATTRIBUTE_SERVICE);
        if (gaService == null)
            return false;

        final BluetoothGattCharacteristic scCharacteristic = gaService.getCharacteristic(SERVICE_CHANGED_CHARACTERISTIC);
        if (scCharacteristic == null)
            return false;

        return internalEnableIndications(scCharacteristic);
    }

    public final boolean enableNotifications(final BluetoothGattCharacteristic characteristic) {
        return enqueue(Request.newEnableNotificationsRequest(characteristic));
    }

    private boolean internalEnableNotifications(final BluetoothGattCharacteristic characteristic) {
        final BluetoothGatt gatt = bluetoothGatt;
        if (gatt == null || characteristic == null)
            return false;

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0)
            return false;

        gatt.setCharacteristicNotification(characteristic, true);
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            return internalWriteDescriptorWorkaround(descriptor);
        }
        return false;
    }

    public final boolean enableIndications(final BluetoothGattCharacteristic characteristic) {
        return enqueue(Request.newEnableIndicationsRequest(characteristic));
    }

    private boolean internalEnableIndications(final BluetoothGattCharacteristic characteristic) {
        final BluetoothGatt gatt = bluetoothGatt;
        if (gatt == null || characteristic == null)
            return false;

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) == 0)
            return false;

        gatt.setCharacteristicNotification(characteristic, true);
        final BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
        if (descriptor != null) {
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
            return internalWriteDescriptorWorkaround(descriptor);
        }
        return false;
    }

    public final boolean readCharacteristic(final BluetoothGattCharacteristic characteristic) {
        return enqueue(Request.newReadRequest(characteristic));
    }

    private boolean internalReadCharacteristic(final BluetoothGattCharacteristic characteristic) {
        final BluetoothGatt gatt = bluetoothGatt;
        if (gatt == null || characteristic == null)
            return false;

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) == 0)
            return false;

        return gatt.readCharacteristic(characteristic);
    }

    public final boolean writeCharacteristic(final BluetoothGattCharacteristic characteristic) {
        return enqueue(Request.newWriteRequest(characteristic, characteristic.getValue()));
    }

    private boolean internalWriteCharacteristic(final BluetoothGattCharacteristic characteristic) {
        final BluetoothGatt gatt = bluetoothGatt;
        if (gatt == null || characteristic == null)
            return false;

        // Check characteristic property
        final int properties = characteristic.getProperties();
        if ((properties & (BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)) == 0)
            return false;

        return gatt.writeCharacteristic(characteristic);
    }

    public final boolean readDescriptor(final BluetoothGattDescriptor descriptor) {
        return enqueue(Request.newReadRequest(descriptor));
    }

    private boolean internalReadDescriptor(final BluetoothGattDescriptor descriptor) {
        final BluetoothGatt gatt = bluetoothGatt;
        if (gatt == null || descriptor == null)
            return false;

        return gatt.readDescriptor(descriptor);
    }

    public final boolean writeDescriptor(final BluetoothGattDescriptor descriptor) {
        return enqueue(Request.newWriteRequest(descriptor, descriptor.getValue()));
    }

    private boolean internalWriteDescriptor(final BluetoothGattDescriptor descriptor) {
        final BluetoothGatt gatt = bluetoothGatt;
        if (gatt == null || descriptor == null)
            return false;

        return internalWriteDescriptorWorkaround(descriptor);
    }

    public final boolean readBatteryLevel() {
        return enqueue(Request.newReadBatteryLevelRequest());
    }

    private boolean internalReadBatteryLevel() {
        final BluetoothGatt gatt = bluetoothGatt;
        if (gatt == null)
            return false;

        final BluetoothGattService batteryService = gatt.getService(BATTERY_SERVICE);
        if (batteryService == null)
            return false;

        final BluetoothGattCharacteristic batteryLevelCharacteristic = batteryService.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC);
        if (batteryLevelCharacteristic == null)
            return false;

        // Check characteristic property
        final int properties = batteryLevelCharacteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_READ) == 0)
            return false;

        return internalReadCharacteristic(batteryLevelCharacteristic);
    }

    public final boolean setBatteryNotifications(final boolean enable) {
        if (enable)
            return enqueue(Request.newEnableBatteryLevelNotificationsRequest());
        else
            return enqueue(Request.newDisableBatteryLevelNotificationsRequest());
    }

    private boolean internalSetBatteryNotifications(final boolean enable) {
        final BluetoothGatt gatt = bluetoothGatt;
        if (gatt == null) {
            return false;
        }

        final BluetoothGattService batteryService = gatt.getService(BATTERY_SERVICE);
        if (batteryService == null)
            return false;

        final BluetoothGattCharacteristic batteryLevelCharacteristic = batteryService.getCharacteristic(BATTERY_LEVEL_CHARACTERISTIC);
        if (batteryLevelCharacteristic == null)
            return false;

        // Check characteristic property
        final int properties = batteryLevelCharacteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) == 0)
            return false;

        gatt.setCharacteristicNotification(batteryLevelCharacteristic, enable);
        final BluetoothGattDescriptor descriptor = batteryLevelCharacteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
        if (descriptor != null) {
            if (enable) {
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            } else {
                descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
            }
            return internalWriteDescriptorWorkaround(descriptor);
        }
        return false;
    }

    private boolean internalWriteDescriptorWorkaround(final BluetoothGattDescriptor descriptor) {
        final BluetoothGatt gatt = bluetoothGatt;
        if (gatt == null || descriptor == null)
            return false;

        final BluetoothGattCharacteristic parentCharacteristic = descriptor.getCharacteristic();
        final int originalWriteType = parentCharacteristic.getWriteType();
        parentCharacteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        final boolean result = gatt.writeDescriptor(descriptor);
        parentCharacteristic.setWriteType(originalWriteType);
        return result;
    }

    public final boolean requestMtu(final int mtu) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && enqueue(Request.newMtuRequest(mtu));
    }

    public final int getMtu() {
        return mtu;
    }

    public final void overrideMtu(final int mtu) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            BleManager.this.mtu = mtu;
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private boolean internalRequestMtu(final int mtu) {
        final BluetoothGatt gatt = bluetoothGatt;
        if (gatt == null)
            return false;

        return gatt.requestMtu(mtu);
    }

    public final boolean requestConnectionPriority(final int priority) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP
                && enqueue(Request.newConnectionPriorityRequest(priority));
    }

    @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
    private boolean internalRequestConnectionPriority(final int priority) {
        final BluetoothGatt gatt = bluetoothGatt;
        if (gatt == null)
            return false;

        return gatt.requestConnectionPriority(priority);
    }


    public boolean enqueue(final Request request) {
        if (gattCallback != null) {
            // Add the new task to the end of the queue
            gattCallback.taskQueue.add(request);
            gattCallback.nextRequest();
            return true;
        }
        return false;
    }

    private final class BleManagerGattCallback extends BluetoothGattCallback {
        private final static String ERROR_CONNECTION_STATE_CHANGE = "Error on connection state change";
        private final static String ERROR_DISCOVERY_SERVICE = "Error on discovering services";
        private final static String ERROR_AUTH_ERROR_WHILE_BONDED = "Phone has lost bonding information";
        private final static String ERROR_READ_CHARACTERISTIC = "Error on reading characteristic";
        private final static String ERROR_WRITE_CHARACTERISTIC = "Error on writing characteristic";
        private final static String ERROR_READ_DESCRIPTOR = "Error on reading descriptor";
        private final static String ERROR_WRITE_DESCRIPTOR = "Error on writing descriptor";
        private final static String ERROR_MTU_REQUEST = "Error on mtu request";
        private final static String ERROR_CONNECTION_PRIORITY_REQUEST = "Error on connection priority request";

        private final Queue<Request> taskQueue = new LinkedList<>();
        private Deque<Request> initQueue;
        private boolean initInProgress;
        private boolean operationInProgress = true;
        /**
         * This flag is required to resume operations after the connection priority request was made.
         * It is used only on Android Oreo and newer, as only there there is onConnectionUpdated callback.
         * However, as this callback is triggered every time the connection parameters change, even
         * when such request wasn't made, this flag ensures the nextRequest() method won't be called
         * during another operation.
         */
        private boolean connectionPriorityOperationInProgress = false;

        private void notifyDeviceDisconnected(final BluetoothDevice device) {
            connected = false;
            connectionState = BluetoothGatt.STATE_DISCONNECTED;
            if (userDisconnected) {
                callbacks.onDeviceDisconnected(device);
                close();
            } else {
                callbacks.onLinkLossOccurred(device);
                // We are not closing the connection here as the device should try to reconnect automatically.
                // This may be only called when the shouldAutoConnect() method returned true.
            }
            if (profile != null)
                profile.release();
        }

        private void onError(final BluetoothDevice device, final String message, final int errorCode) {
            callbacks.onError(device, message, errorCode);
            if (profile != null)
                profile.onError(message, errorCode);
        }

        @Override
        public final void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothProfile.STATE_CONNECTED) {
                // Notify the parent activity/service
                connected = true;
                connectionState = BluetoothGatt.STATE_CONNECTED;
                callbacks.onDeviceConnected(gatt.getDevice());

                /*
                 * The onConnectionStateChange event is triggered just after the Android connects to a device.
                 * In case of bonded devices, the encryption is reestablished AFTER this callback is called.
                 * Moreover, when the device has Service Changed indication enabled, and the list of services has changed (e.g. using the DFU),
                 * the indication is received few hundred milliseconds later, depending on the connection interval.
                 * When received, Android will start performing a service discovery operation on its own, internally,
                 * and will NOT notify the app that services has changed.
                 *
                 * If the gatt.discoverServices() method would be invoked here with no delay, if would return cached services,
                 * as the SC indication wouldn't be received yet.
                 * Therefore we have to postpone the service discovery operation until we are (almost, as there is no such callback) sure,
                 * that it has been handled.
                 * TODO: Please calculate the proper delay that will work in your solution.
                 * It should be greater than the time from LLCP Feature Exchange to ATT Write for Service Change indication.
                 * If your device does not use Service Change indication (for example does not have DFU) the delay may be 0.
                 */
                final boolean bonded = gatt.getDevice().getBondState() == BluetoothDevice.BOND_BONDED;
                final int delay = bonded ? 1600 : 0; // around 1600 ms is required when connection interval is ~45ms.
                handler.postDelayed(() -> {
                    // Some proximity tags (e.g. nRF PROXIMITY) initialize bonding automatically when connected.
                    if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_BONDING) {
                        gatt.discoverServices();
                    }
                }, delay);
            } else {
                if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    operationInProgress = true; // no more calls are possible
                    initQueue = null;
                    taskQueue.clear();
                    final boolean wasConnected = connected;
                    // if (connected) { // Checking connected prevents from calling onDeviceDisconnected if connection attempt failed. This check is not necessary
                    notifyDeviceDisconnected(gatt.getDevice()); // This sets the connected flag to false
                    // }
                    // Try to reconnect if the initial connection was lost because of a link loss or timeout, and shouldAutoConnect() returned true during connection attempt.
                    // This time it will set the autoConnect flag to true (gatt.connect() forces autoConnect true)
                    if (initialConnection) {
                        connect(gatt.getDevice());
                    }

                    if (wasConnected || status == BluetoothGatt.GATT_SUCCESS)
                        return;
                }

                // TODO Should the disconnect method be called or the connection is still valid? Does this ever happen?
                profile.onError(ERROR_CONNECTION_STATE_CHANGE, status);
            }
        }

        @Override
        public final void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                final BleProfile profile = BleProfileProvider.findProfile(gatt);
                if (profile != null) {
                    profile.setApi(BleManager.this);
                    BleManager.this.profile = profile;

                    // Obtain the queue of initialization requests
                    initInProgress = true;
                    initQueue = profile.initGatt(gatt);

                    // Before we start executing the initialization queue some other tasks need to be done.
                    if (initQueue == null)
                        initQueue = new LinkedList<>();

                    // Note, that operations are added in reverse order to the front of the queue.

                    // 3. Enable Battery Level notifications if required (if this char. does not exist, this operation will be skipped)
                    if (callbacks.shouldEnableBatteryLevelNotifications(gatt.getDevice()))
                        initQueue.addFirst(Request.newEnableBatteryLevelNotificationsRequest());
                    // 2. Read Battery Level characteristic (if such does not exist, this will be skipped)
                    initQueue.addFirst(Request.newReadBatteryLevelRequest());
                    // 1. On devices running Android 4.3-5.x, 8.x and 9.0 the Service Changed
                    //    characteristic needs to be enabled by the app (for bonded devices).
                    //    The request will be ignored if there is no Service Changed characteristic.
                    // This "fix" broke this in Android 8:
                    // https://android-review.googlesource.com/c/platform/system/bt/+/239970
                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                            || Build.VERSION.SDK_INT == Build.VERSION_CODES.O
                            || Build.VERSION.SDK_INT == Build.VERSION_CODES.O_MR1
                            || Build.VERSION.SDK_INT == Build.VERSION_CODES.P)
                        initQueue.addFirst(Request.newEnableServiceChangedIndicationsRequest());

                    operationInProgress = false;
                    nextRequest();
                } else {
                    callbacks.onDeviceNotSupported(gatt.getDevice());
                    disconnect();
                }
            } else {
                DebugLogger.e(TAG, "onServicesDiscovered error " + status);
                onError(gatt.getDevice(), ERROR_DISCOVERY_SERVICE, status);
            }
        }

        @Override
        public final void onCharacteristicRead(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (isBatteryLevelCharacteristic(characteristic)) {
                    final int batteryValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    BleManager.this.batteryValue = batteryValue;
                    profile.onBatteryValueReceived(gatt, batteryValue);
                } else {
                    // The value has been read. Notify the profile and proceed with the initialization queue.
                    profile.onCharacteristicRead(gatt, characteristic);
                }
                operationInProgress = false;
                nextRequest();
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
                    // This should never happen but it used to: http://stackoverflow.com/a/20093695/2115352
                    DebugLogger.w(TAG, ERROR_AUTH_ERROR_WHILE_BONDED);
                    onError(gatt.getDevice(), ERROR_AUTH_ERROR_WHILE_BONDED, status);
                }
            } else {
                DebugLogger.e(TAG, "onCharacteristicRead error " + status);
                onError(gatt.getDevice(), ERROR_READ_CHARACTERISTIC, status);
            }
        }

        @Override
        public final void onCharacteristicWrite(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // The value has been written. Notify the profile and proceed with the initialization queue.
                profile.onCharacteristicWrite(gatt, characteristic);
                operationInProgress = false;
                nextRequest();
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
                    // This should never happen but it used to: http://stackoverflow.com/a/20093695/2115352
                    DebugLogger.w(TAG, ERROR_AUTH_ERROR_WHILE_BONDED);
                    onError(gatt.getDevice(), ERROR_AUTH_ERROR_WHILE_BONDED, status);
                }
            } else {
                DebugLogger.e(TAG, "onCharacteristicWrite error " + status);
                onError(gatt.getDevice(), ERROR_WRITE_CHARACTERISTIC, status);
            }
        }

        @Override
        public void onDescriptorRead(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // The value has been read. Notify the profile and proceed with the initialization queue.
                profile.onDescriptorRead(gatt, descriptor);
                operationInProgress = false;
                nextRequest();
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
                    // This should never happen but it used to: http://stackoverflow.com/a/20093695/2115352
                    DebugLogger.w(TAG, ERROR_AUTH_ERROR_WHILE_BONDED);
                    onError(gatt.getDevice(), ERROR_AUTH_ERROR_WHILE_BONDED, status);
                }
            } else {
                DebugLogger.e(TAG, "onDescriptorRead error " + status);
                onError(gatt.getDevice(), ERROR_READ_DESCRIPTOR, status);
            }
        }

        @Override
        public final void onDescriptorWrite(final BluetoothGatt gatt, final BluetoothGattDescriptor descriptor, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // The value has been written. Notify the profile and proceed with the initialization queue.
                profile.onDescriptorWrite(gatt, descriptor);
                operationInProgress = false;
                nextRequest();
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                if (gatt.getDevice().getBondState() != BluetoothDevice.BOND_NONE) {
                    // This should never happen but it used to: http://stackoverflow.com/a/20093695/2115352
                    DebugLogger.w(TAG, ERROR_AUTH_ERROR_WHILE_BONDED);
                    onError(gatt.getDevice(), ERROR_AUTH_ERROR_WHILE_BONDED, status);
                }
            } else {
                DebugLogger.e(TAG, "onDescriptorWrite error " + status);
                onError(gatt.getDevice(), ERROR_WRITE_DESCRIPTOR, status);
            }
        }

        @Override
        public final void onCharacteristicChanged(final BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            if (isBatteryLevelCharacteristic(characteristic)) {
                final int batteryValue = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                BleManager.this.batteryValue = batteryValue;
                profile.onBatteryValueReceived(gatt, batteryValue);
            } else {
                final BluetoothGattDescriptor cccd = characteristic.getDescriptor(CLIENT_CHARACTERISTIC_CONFIG_DESCRIPTOR_UUID);
                final boolean notifications = cccd == null || cccd.getValue() == null || cccd.getValue().length != 2 || cccd.getValue()[0] == 0x01;

                if (notifications) {
                    profile.onCharacteristicNotified(gatt, characteristic);
                } else { // indications
                    profile.onCharacteristicIndicated(gatt, characteristic);
                }
            }
        }

        @Override
        public void onMtuChanged(final BluetoothGatt gatt, final int mtu, final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                profile.onMtuChanged(mtu);
            } else {
                DebugLogger.e(TAG, "onMtuChanged error: " + status + ", mtu: " + mtu);
                onError(gatt.getDevice(), ERROR_MTU_REQUEST, status);
            }
            operationInProgress = false;
            nextRequest();
        }


        public void onConnectionUpdated(final BluetoothGatt gatt, final int interval, final int latency, final int timeout,	final int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                profile.onConnectionUpdated(interval, latency, timeout);
            } else if (status == 0x3b) { // HCI_ERR_UNACCEPT_CONN_INTERVAL
                DebugLogger.e(TAG, "onConnectionUpdated received status: Unacceptable connection interval, interval: " + interval + ", latency: " + latency + ", timeout: " + timeout);
            } else {
                DebugLogger.e(TAG, "onConnectionUpdated received status: " + status + ", interval: " + interval + ", latency: " + latency + ", timeout: " + timeout);
                callbacks.onError(gatt.getDevice(), ERROR_CONNECTION_PRIORITY_REQUEST, status);
            }
            if (connectionPriorityOperationInProgress) {
                connectionPriorityOperationInProgress = false;
                operationInProgress = false;
                nextRequest();
            }
        }


        private void nextRequest() {
            if (operationInProgress)
                return;

            // Get the first request from the init queue
            Request request = initQueue != null ? initQueue.poll() : null;

            // Are we done with initializing?
            if (request == null) {
                if (initInProgress) {
                    initQueue = null; // release the queue
                    initInProgress = false;
                    callbacks.onDeviceReady(bluetoothDevice);
                }
                // If so, we can continue with the task queue
                request = taskQueue.poll();
                if (request == null) {
                    // Nothing to be done for now
                    return;
                }
            }

            operationInProgress = true;
            boolean result = false;
            switch (request.type) {
                case CREATE_BOND: {
                    result = internalCreateBond();
                    break;
                }
                case READ: {
                    result = internalReadCharacteristic(request.characteristic);
                    break;
                }
                case WRITE: {
                    final BluetoothGattCharacteristic characteristic = request.characteristic;
                    characteristic.setValue(request.data);
                    characteristic.setWriteType(request.writeType);
                    result = internalWriteCharacteristic(characteristic);
                    break;
                }
                case READ_DESCRIPTOR: {
                    result = internalReadDescriptor(request.descriptor);
                    break;
                }
                case WRITE_DESCRIPTOR: {
                    final BluetoothGattDescriptor descriptor = request.descriptor;
                    descriptor.setValue(request.data);
                    result = internalWriteDescriptor(descriptor);
                    break;
                }
                case ENABLE_NOTIFICATIONS: {
                    result = internalEnableNotifications(request.characteristic);
                    break;
                }
                case ENABLE_INDICATIONS: {
                    result = internalEnableIndications(request.characteristic);
                    break;
                }
                case READ_BATTERY_LEVEL: {
                    result = internalReadBatteryLevel();
                    break;
                }
                case ENABLE_BATTERY_LEVEL_NOTIFICATIONS: {
                    result = internalSetBatteryNotifications(true);
                    break;
                }
                case DISABLE_BATTERY_LEVEL_NOTIFICATIONS: {
                    result = internalSetBatteryNotifications(false);
                    break;
                }
                case ENABLE_SERVICE_CHANGED_INDICATIONS: {
                    result = ensureServiceChangedEnabled();
                    break;
                }
                case REQUEST_MTU: {
                    result = internalRequestMtu(request.value);
                    break;
                }
                case REQUEST_CONNECTION_PRIORITY: {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        connectionPriorityOperationInProgress = true;
                        result = internalRequestConnectionPriority(request.value);
                    } else {
                        result = internalRequestConnectionPriority(request.value);
                        // There is no callback for requestConnectionPriority(...) before Android Oreo.\
                        // Let's give it some time to finish as the request is an asynchronous operation.
                        if (result) {
                            handler.postDelayed(() -> {
                                operationInProgress = false;
                                nextRequest();
                            }, 100);
                        }
                    }
                    break;
                }
            }
            // The result may be false if given characteristic or descriptor were not found on the device.
            // In that case, proceed with next operation and ignore the one that failed.
            if (!result) {
                connectionPriorityOperationInProgress = false;
                operationInProgress = false;
                nextRequest();
            }
        }

        private boolean isBatteryLevelCharacteristic(final BluetoothGattCharacteristic characteristic) {
            if (characteristic == null)
                return false;

            return BATTERY_LEVEL_CHARACTERISTIC.equals(characteristic.getUuid());
        }
    }
}