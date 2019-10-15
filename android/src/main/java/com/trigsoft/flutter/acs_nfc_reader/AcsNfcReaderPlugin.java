package com.trigsoft.flutter.acs_nfc_reader;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.tech.Ndef;
import android.os.AsyncTask;
import android.os.Handler;
import android.util.Log;

import com.acs.smartcard.Reader;
import com.acs.smartcard.ReaderException;

import java.math.BigDecimal;
import java.util.Arrays;

import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry.Registrar;

import static android.content.ContentValues.TAG;

/**
 * AcsNfcReaderPlugin
 */
public class AcsNfcReaderPlugin implements MethodCallHandler {

    final Context context;
    final MethodChannel channel;
    final PendingIntent permissionIntent;
    Reader reader;

    private static final String ACTION_USB_PERMISSION = "com.trigsoft.flutter.USB_PERMISSION";
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if (device != null) {
                            if (reader.isSupported(device)) {
                                new OpenTask().doInBackground(device);
                            }
                        }
                    } else {
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };

    public AcsNfcReaderPlugin(Context context, MethodChannel channel) {
        this.context = context;
        this.channel = channel;
        permissionIntent = PendingIntent.getBroadcast(context, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        context.registerReceiver(usbReceiver, filter);
    }

    public static void registerWith(Registrar registrar) {
        final MethodChannel channel = new MethodChannel(registrar.messenger(), "acs_nfc_reader");
        channel.setMethodCallHandler(new AcsNfcReaderPlugin(registrar.context(), channel));
    }

    @Override
    public void onMethodCall(MethodCall call, Result result) {
        if (call.method.equals("getPlatformVersion")) {
            result.success("Android " + android.os.Build.VERSION.RELEASE);
        } else if (call.method.equals("initialize")) {
            try {
                UsbManager manager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
                reader = new Reader(manager);
                for (UsbDevice device : manager.getDeviceList().values()) {
                    manager.requestPermission(device, permissionIntent);
                }
                reader.setOnStateChangeListener(new Reader.OnStateChangeListener() {
                    @Override
                    public void onStateChange(final int slotNum, final int previousStatus, final int currentStatus) {
                        Handler h = new Handler(context.getMainLooper());
                        h.post(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    if (currentStatus == Reader.CARD_PRESENT) {
                                        powerUpAndSetProtocol(reader, slotNum);
                                        byte[] data = readData(reader, slotNum, 4, 128);
                                        channel.invokeMethod("onScan", bytesToHex(data));
                                    }
                                } catch (Exception e) {
                                    channel.invokeMethod("pluginHandler", "" + e);
                                }

                            }
                        });
                    }
                });
                result.success(true);
            } catch (Exception ex) {
                result.success(false);
            }
        } else {
            result.notImplemented();
        }
    }

    private String bytesToHex(byte[] data) {
        return bytesToHex(data, null);
    }

    private String bytesToHex(byte[] data, String delimiter) {
        StringBuffer sb = new StringBuffer();
        if (data != null) {
            for (int i = 0; i < data.length; i++) {
                if (i > 0 && delimiter != null)
                    sb.append(delimiter);
                sb.append(String.format("%02x", data[i]));
            }
        }
        return sb.toString();
    }

    private void open(Reader reader, UsbDevice usbDevice) {
        if (reader != null && usbDevice != null)
            reader.open(usbDevice);
    }

    private void close(Reader reader) {
        if (reader != null)
            reader.close();
    }

    private byte[] powerUp(Reader reader, int slotNum) throws ReaderException {
        if (reader != null)
            return reader.power(slotNum, Reader.CARD_WARM_RESET);
        return new byte[]{};
    }

    private byte[] powerUpAndSetProtocol(Reader reader, int slotNum) throws ReaderException {
        if (reader != null) {
            byte[] atr = reader.power(slotNum, Reader.CARD_WARM_RESET);
            reader.setProtocol(slotNum, Reader.PROTOCOL_T0 | Reader.PROTOCOL_T1);
            return atr;
        }
        return new byte[]{};
    }

    private byte[] atr(Reader reader, int slotNum) throws ReaderException {
        return reader.getAtr(slotNum);
    }

    private byte[] challenge(Reader reader, int slotNum) throws ReaderException {
        //00 84 00 00 08h
        byte[] command = {(byte) 0x00, (byte) 0x84, (byte) 0x00, (byte) 0x00, (byte) 0x08};
        byte[] response = new byte[300];
        final int responseLength = reader.transmit(slotNum, command, command.length, response, response.length);
        return Arrays.copyOf(response, responseLength);
    }

    private byte[] uid(Reader reader, int slotNum) throws ReaderException {
        byte[] command = {(byte) 0xFF, (byte) 0xCA, (byte) 0x00, (byte) 0x00, (byte) 0x00};
        byte[] response = new byte[300];
        final int responseLength = reader.transmit(slotNum, command, command.length, response, response.length);
        return Arrays.copyOf(response, responseLength);
    }

    private byte[] ats(Reader reader, int slotNum) throws ReaderException {
        byte[] command = {(byte) 0xFF, (byte) 0xCA, (byte) 0x01, (byte) 0x00, (byte) 0x00};
        byte[] response = new byte[300];
        final int responseLength = reader.transmit(slotNum, command, command.length, response, response.length);
        return Arrays.copyOf(response, responseLength);
    }

    private byte[] readData(Reader reader, int slotNum, int blockNum, int bytesToRead) throws ReaderException {
        //FF B0 00 <blockNum> <bytesToRead>
        // data[] is to store actual complete response of all reads
        byte[] data = new byte[4096];
        int dataPos = 0;
        // currentBlock is to start with blockNum until required bytes are read
        int currentBlock = blockNum;
        int blockSize = 16;
        // loop until bytes remaining > 0, should also add check if currentBlock is out of card bounds
        int bytesRemaining = bytesToRead;
        while (bytesRemaining > 0) {
            // skip trailing block
            if ((currentBlock + 1) % 4 == 0) {
                currentBlock += 1;
            }
            // authenticate the block before read
            authenticate(reader, slotNum, currentBlock);
            // read currentBlock block
            byte[] command = {(byte) 0xFF, (byte) 0xB0, (byte) 0x00, (byte) currentBlock, (byte) blockSize};
            byte[] response = new byte[300];
            final int responseLength = reader.transmit(slotNum, command, command.length, response, response.length);
            // copy response to data, skip last 2 bytes as they are status 0x9000 = success or 0x6300 = failure
            for (int i = 0; i < responseLength-2; i++) {
                data[dataPos++] = response[i];
            }
            // reduce bytes remaining to control loop
            bytesRemaining -= blockSize;
            if (bytesRemaining < 0)
                bytesRemaining = 0;
            currentBlock++;
        }
        return Arrays.copyOf(data, dataPos);
    }

    private byte[] authenticate(Reader reader, int slotNum, int blockNum) throws ReaderException {
        //FF 86 00 00 05 01 00 04 60 00h
        loadAuthenticationKey(reader, slotNum);
        byte[] command = {(byte) 0xFF, (byte) 0x86, (byte) 0x00, (byte) 0x00, (byte) 0x05, (byte) 0x01, (byte) 0x00, (byte) blockNum, (byte) 0x60, (byte) 0x00};
        byte[] response = new byte[300];
        final int responseLength = reader.transmit(slotNum, command, command.length, response, response.length);
        return Arrays.copyOf(response, responseLength);
    }

    private byte[] authenticateObsolete(Reader reader, int slotNum, int blockNum) throws ReaderException {
        //FF 86 00 00 05 01 00 04 60 00h
        loadAuthenticationKey(reader, slotNum);
        byte[] command = {(byte) 0xFF, (byte) 0x88, (byte) 0x00, (byte) 0x00, (byte) blockNum, (byte) 0x60, (byte) 0x00};
        byte[] response = new byte[300];
        final int responseLength = reader.transmit(slotNum, command, command.length, response, response.length);
        return Arrays.copyOf(response, responseLength);
    }

    private byte[] loadAuthenticationKey(Reader reader, int slotNum) throws ReaderException {
        //FF 82 00 00h 06 FF FF FF FF FF FFh
        // 0xD3F7D3F7D3F7
//        byte[] command1 = {(byte) 0xFF, (byte) 0x82, (byte) 0x00, (byte) 0x00, (byte) 0x06, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        byte[] command1 = {(byte) 0xFF, (byte) 0x82, (byte) 0x00, (byte) 0x00, (byte) 0x06, (byte) 0xD3, (byte) 0xF7, (byte) 0xD3, (byte) 0xF7, (byte) 0xD3, (byte) 0xF7};
        byte[] response1 = new byte[300];
        final int responseLength1 = reader.transmit(slotNum, command1, command1.length, response1, response1.length);
        //FF 82 00 00h 06 FF FF FF FF FF FFh
//        byte[] command2 = {(byte) 0xFF, (byte) 0x82, (byte) 0x00, (byte) 0x01, (byte) 0x06, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
        byte[] command2 = {(byte) 0xFF, (byte) 0x82, (byte) 0x00, (byte) 0x01, (byte) 0x06, (byte) 0xD3, (byte) 0xF7, (byte) 0xD3, (byte) 0xF7, (byte) 0xD3, (byte) 0xF7};
        byte[] response2 = new byte[300];
        final int responseLength2 = reader.transmit(slotNum, command2, command2.length, response2, response2.length);
        return new byte[]{response1[0], response1[1], response2[0], response2[1]};
    }

    private byte[] readKeys(Reader reader, int slotNum, int blockNum) throws ReaderException {
        //FF B0 00 <blockNum> <bytesToRead>
        int keyBlock = ((blockNum / 4) * 4) + 3;
        byte[] command = {(byte) 0xFF, (byte) 0xB0, (byte) 0x00, (byte) keyBlock, (byte) 0x10};
        byte[] response = new byte[300];
        final int responseLength = reader.transmit(slotNum, command, command.length, response, response.length);
        return Arrays.copyOf(response, responseLength);
    }

    private class OpenTask extends AsyncTask<UsbDevice, byte[], Exception> {

        @Override
        protected Exception doInBackground(UsbDevice... usbDevices) {
            Exception result = null;
            try {
                open(reader, usbDevices[0]);
            } catch (Exception e) {
                result = e;
            }
            return result;
        }

        @Override
        protected void onPostExecute(Exception result) {
        }
    }


}
