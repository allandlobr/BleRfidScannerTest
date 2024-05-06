package com.example.blescannerrfid

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.blescannerrfid.ui.theme.BLEScannerRFIDTheme


const val TAG = "BLE DEBUG"

class MainActivity : ComponentActivity() {
    private lateinit var bluetoothManager: BluetoothManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        bluetoothManager = getSystemService(BluetoothManager::class.java)
        setContent {
            BLEScannerRFIDTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    Text(text = "")
                    Main(bluetoothManager, this.applicationContext)
                }
            }
        }
    }
}

@Composable
fun Main(bluetoothManager: BluetoothManager, applicationContext: Context) {
    val viewModel = viewModel<MainViewModel>(
        factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MainViewModel(bluetoothManager) as T
            }
        }
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row {
            Button(onClick = {viewModel.scanLeDevice()}) {
                Text(text = if (viewModel.scanning.value) "Stop" else "Scan")
            }
            if(viewModel.bleDevice.value != null){
                Button(onClick = {viewModel.toggleConnect(applicationContext) }) {
                    Text(text = if (viewModel.bleDeviceGatt.value != null) "Disconnect" else "Connect")
                }
            }
        }

    }
}

class MainViewModel(bluetoothManager: BluetoothManager) : ViewModel() {
    private val bluetoothAdapter: BluetoothAdapter = bluetoothManager.adapter
    private val bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner

    var bleDevice = mutableStateOf<BluetoothDevice?>(null)
    var bleDeviceGatt = mutableStateOf<BluetoothGatt?>(null)
    var scanning = mutableStateOf(false)
    val bleDevices = mutableStateListOf<BleDevice>()

    private val connected =  mutableStateOf(false)

    fun toggleConnect(applicationContext: Context){
        if(connected.value){
            disconnectDevice()
            connected.value = false;
        }else {
            connectDevice(applicationContext)
            connected.value = true;
        }

    }
    @SuppressLint("MissingPermission")
    fun connectDevice(applicationContext: Context){
        bleDeviceGatt.value = bleDevice.value?.connectGatt(applicationContext, false, gattCallback)
    }
    @SuppressLint("MissingPermission")
    fun disconnectDevice(){
        bleDeviceGatt.value?.close()
        bleDeviceGatt.value?.disconnect()
        bleDeviceGatt.value = null
    }

    @SuppressLint("MissingPermission")
    fun scanLeDevice() {
        if (!scanning.value) { // Stops scanning after a pre-defined scan period.
            scanning.value = true
            bluetoothLeScanner.startScan(leScanCallback)
        } else {
            scanning.value = false
            bluetoothLeScanner.stopScan(leScanCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            // Handle connection state changes here
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // Device connected, proceed with service discovery or other operations
                Log.d(TAG, "Connected to device")
                gatt?.discoverServices()

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Device disconnected
                Log.d(TAG, "Disconnected from device")
            }
        }

        @SuppressLint("MissingPermission", "NewApi")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val services = gatt?.services
                services?.forEach { service ->
                    if(service.uuid.toString() == "00001101-0000-1000-8000-00805f9b34fb"){
                        Log.d(TAG, "Service discovered: ${service.uuid}")

                        gatt.setCharacteristicNotification(service.characteristics[0], true)

                        val command = "T1"
                        val commandBytes = command.toByteArray(Charsets.UTF_8)
                        gatt.writeCharacteristic(service.characteristics[1], commandBytes, WRITE_TYPE_DEFAULT)
                    }
                }
            } else {
                Log.e(TAG, "Service discovery failed with status: $status")
            }
        }

        @SuppressLint("MissingPermission", "NewApi")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            Log.d(TAG, "onCharacteristicRead: UUID - ${characteristic.uuid}; Value - ${value.toString(Charsets.UTF_8)}")
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Log.d(TAG, "onCharacteristicChanged: UUID - ${characteristic.uuid}; Value - ${value.toString(Charsets.UTF_8)}")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            Log.d(TAG, "onCharacteristicWrite: UUID - ${characteristic?.uuid}")
        }

    }

    private val leScanCallback: ScanCallback = object : ScanCallback() {
        val deviceName = "RFID-M-IDH2-C49FA28D3A08"

        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val device = result.device
            Log.d(TAG, "Found device: ${device?.name}, ${device?.address}")
            if(device?.name == deviceName){
                Log.d(TAG, "RFID Reader device found: ${device.name}, ${device.address}")
                scanLeDevice()
                bleDevice.value = device
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            // Handle scan failure
            Log.e(TAG, "Scan failed with error code: $errorCode")
        }
    }

}

data class BleDevice(val name: String?)