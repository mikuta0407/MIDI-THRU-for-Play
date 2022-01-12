/*
 * Copyright (C) 2019 The Android Open Source Project
 * Copyright (C) 2021 mikuta0407
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package net.mikuta0407.midithru4play;

import android.app.Activity;
import android.content.Context;

import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;

import android.os.Bundle;

import android.util.Log;
import android.view.View;

import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.TextView;

import android.os.Handler;

import java.util.ArrayList;

/**
 * Application MainActivity handles UI and Midi device hotplug event from
 * native side.
 */
public class MainActivity extends Activity
    implements View.OnClickListener,
               SeekBar.OnSeekBarChangeListener,
               AdapterView.OnItemSelectedListener {

    private static final String TAG = MainActivity.class.getName();;

    private AppMidiManager mAppMidiManager;

    // Connected devices
    private ArrayList<MidiDeviceInfo> mReceiveDevices = new ArrayList<MidiDeviceInfo>();
    private ArrayList<MidiDeviceInfo> mSendDevices = new ArrayList<MidiDeviceInfo>();

    //MIDI IN: Output(向こうからのアウトプット)
    //MIDI OUT: Input(こちらからインプットする)

    // 選ぶスピナー
    Spinner mOutputDevicesSpinner; // MIDI IN
    Spinner mInputDevicesSpinner; // MIDI OUT
    Spinner mChannelConvertSpinner; // Channel Convert
    Spinner mProgramChangeSpinner; // Program Change

    // シークバー
    SeekBar mVolumeSB;
    SeekBar mVelocitySB;
    SeekBar mControllerSB;
    SeekBar mPitchBendSB;

    // TextView
    TextView mReceiveMessageTx;
    TextView mNowVolume;
    TextView mNowVelocity;
    TextView mNowOct;
    TextView mNowKey;

    // Switch
    Switch mAllThruModeSW;
    Switch mChConvertSW;
    Switch mCcFixSW;
    Switch mVelFixSW;

    // Button
    //これらは後ほど直接貼り付け

    //EditText mProgNumberEdit;

    // その他変数
    boolean isAllThru = false;
    boolean isChConvert = false; // チャンネル変換をするかどうか
    boolean isCcFix = false; // CCのモードを固定するか
    boolean isVelFix = false; // Velocity Fixをするか

    int octave = 0; // オクターブ移動状態
    int transpose = 0; //キー状態

    int volume = 100; // Volume
    int velocity = 100; // Velocity (for Fix)

    // Force to load the native library
    static {
        AppMidiManager.loadNativeAPI();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //
        // Init JNI for data receive callback
        //
        initNative();

        // UIパーツにリスナー貼り付け

        // ==== Spinner ====
        // MIDI IN
        mInputDevicesSpinner = (Spinner)findViewById(R.id.inputDevicesSpinner);
        mInputDevicesSpinner.setOnItemSelectedListener(this);
        // MIDI OUT
        mOutputDevicesSpinner = (Spinner)findViewById(R.id.outputDevicesSpinner);
        mOutputDevicesSpinner.setOnItemSelectedListener(this);
        // Channel Convert
        mChannelConvertSpinner = (Spinner)findViewById(R.id.chConvertSpinner);
        mChannelConvertSpinner.setOnItemSelectedListener(this);
        // Program Change
        mProgramChangeSpinner = (Spinner)findViewById(R.id.programChangeSpinner);
        mProgramChangeSpinner.setOnItemSelectedListener(this);

        // ==== Seek Bar ===
        // Modulation Wheel
        mControllerSB = (SeekBar)findViewById(R.id.controllerSeekBar);
        mControllerSB.setMax(MidiSpec.MAX_CC_VALUE);
        mControllerSB.setOnSeekBarChangeListener(this);
        // PitchBend Wheel
        mPitchBendSB = (SeekBar)findViewById(R.id.pitchBendSeekBar);
        mPitchBendSB.setMax(MidiSpec.MAX_PITCHBEND_VALUE);
        mPitchBendSB.setProgress(MidiSpec.MID_PITCHBEND_VALUE);
        mPitchBendSB.setOnSeekBarChangeListener(this);
        // Volume
        mVolumeSB = (SeekBar)findViewById(R.id.volumeBar);
        mVolumeSB.setMax(8191);
        mVolumeSB.setMin(-8192);
        mVolumeSB.setProgress(0);
        mVolumeSB.setOnSeekBarChangeListener(this);
        // Velocity
        mVelocitySB = (SeekBar)findViewById(R.id.velocityVar);
        mVelocitySB.setOnSeekBarChangeListener(this);

        // ==== TextView ====
        mNowVolume = (TextView)findViewById(R.id.volumeValueText); // Volume
        mNowVelocity = (TextView)findViewById(R.id.velocityValueText); // Velocity
        mNowOct = (TextView)findViewById(R.id.nowOctText); // Now Octave
        mNowKey = (TextView)findViewById(R.id.nowKey); // Now Key

        // ==== CheckBox ====

        // All THRU Mode Switch
        // 全メッセージをTHRUさせるかどうか
        mAllThruModeSW = (Switch)findViewById(R.id.allThruSwitch);
        mAllThruModeSW.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    isAllThru = true;

                    // Spinner
                    mChannelConvertSpinner.setEnabled(false);
                    mProgramChangeSpinner.setEnabled(false);

                    // SeekBar
                    mControllerSB.setEnabled(false);
                    mPitchBendSB.setEnabled(false);
                    mVolumeSB.setEnabled(false);
                    mVelocitySB.setEnabled(false);

                    // TextView
                    mNowVolume.setEnabled(false);
                    mNowVelocity.setEnabled(false);
                    mNowOct.setEnabled(false);
                    mNowKey.setEnabled(false);

                    // Switch
                    mChConvertSW.setEnabled(false);
                    mCcFixSW.setEnabled(false);
                    mVelFixSW.setEnabled(false);

                    // Button
                    // Octave
                    ((Button)findViewById(R.id.octMinusButton)).setEnabled(false);
                    ((Button)findViewById(R.id.octResetButton)).setEnabled(false);
                    ((Button)findViewById(R.id.octPlusButton)).setEnabled(false);

                    // Transpose
                    ((Button)findViewById(R.id.keyMinusButton)).setEnabled(false);
                    ((Button)findViewById(R.id.keyResetButton)).setEnabled(false);
                    ((Button)findViewById(R.id.keyPlusButton)).setEnabled(false);

                    ((Button)findViewById(R.id.keyMinus12Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyMinus11Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyMinus10Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyMinus9Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyMinus8Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyMinus7Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyMinus6Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyMinus5Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyMinus4Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyMinus3Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyMinus2Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyMinus1Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyPlus1Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyPlus2Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyPlus3Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyPlus4Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyPlus5Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyPlus6Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyPlus7Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyPlus8Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyPlus9Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyPlus10Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyPlus11Button)).setEnabled(false);
                    ((Button)findViewById(R.id.keyPlus12Button)).setEnabled(false);

                    // Sound Module Reset
                    ((Button)findViewById(R.id.gmSystemOnButton)).setEnabled(false);
                    ((Button)findViewById(R.id.gsResetButton)).setEnabled(false);
                    ((Button)findViewById(R.id.xgSystemOnButton)).setEnabled(false);


                } else {
                    isAllThru = false;
                    // Spinner
                    mChannelConvertSpinner.setEnabled(true);
                    mProgramChangeSpinner.setEnabled(true);

                    // SeekBar
                    mControllerSB.setEnabled(true);
                    mPitchBendSB.setEnabled(true);
                    mVolumeSB.setEnabled(true);
                    mVelocitySB.setEnabled(true);

                    // TextView
                    mNowVolume.setEnabled(true);
                    mNowVelocity.setEnabled(true);
                    mNowOct.setEnabled(true);
                    mNowKey.setEnabled(true);

                    // Switch
                    mChConvertSW.setEnabled(true);
                    mCcFixSW.setEnabled(true);
                    mVelFixSW.setEnabled(true);

                    // Button
                    // Octave
                    ((Button)findViewById(R.id.octMinusButton)).setEnabled(true);
                    ((Button)findViewById(R.id.octResetButton)).setEnabled(true);
                    ((Button)findViewById(R.id.octPlusButton)).setEnabled(true);

                    // Transpose
                    ((Button)findViewById(R.id.keyMinusButton)).setEnabled(true);
                    ((Button)findViewById(R.id.keyResetButton)).setEnabled(true);
                    ((Button)findViewById(R.id.keyPlusButton)).setEnabled(true);

                    ((Button)findViewById(R.id.keyMinus12Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyMinus11Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyMinus10Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyMinus9Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyMinus8Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyMinus7Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyMinus6Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyMinus5Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyMinus4Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyMinus3Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyMinus2Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyMinus1Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyPlus1Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyPlus2Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyPlus3Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyPlus4Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyPlus5Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyPlus6Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyPlus7Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyPlus8Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyPlus9Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyPlus10Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyPlus11Button)).setEnabled(true);
                    ((Button)findViewById(R.id.keyPlus12Button)).setEnabled(true);

                    // Sound Module Reset
                    ((Button)findViewById(R.id.gmSystemOnButton)).setEnabled(true);
                    ((Button)findViewById(R.id.gsResetButton)).setEnabled(true);
                    ((Button)findViewById(R.id.xgSystemOnButton)).setEnabled(true);
                }
            }
        });

        //
        mChConvertSW = (Switch)findViewById(R.id.chConvertToggle);
        mChConvertSW.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    isChConvert = true;
                } else {
                    isChConvert = false;
                }
            }
        });

        mCcFixSW = (Switch)findViewById(R.id.ccFixSwitch);
        mCcFixSW.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    isCcFix= true;
                } else {
                    isCcFix = false;
                }
            }
        });

        mVelFixSW = (Switch)findViewById(R.id.velocityFix);
        mVelFixSW.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    isVelFix = true;
                } else {
                    isVelFix = false;
                }
            }
        });

        // Program Change入力欄
        //mProgNumberEdit = (EditText)findViewById(R.id.progNumEdit);


        // ==== Button ====

        // Octave
        ((Button)findViewById(R.id.octMinusButton)).setOnClickListener(this); //Note On
        ((Button)findViewById(R.id.octResetButton)).setOnClickListener(this); // Note Off
        ((Button)findViewById(R.id.octPlusButton)).setOnClickListener(this); // Program Change

        // Transpose
        ((Button)findViewById(R.id.keyMinusButton)).setOnClickListener(this); // key down button
        ((Button)findViewById(R.id.keyResetButton)).setOnClickListener(this); // key reset button
        ((Button)findViewById(R.id.keyPlusButton)).setOnClickListener(this); // key plus button

        ((Button)findViewById(R.id.keyMinus12Button)).setOnClickListener(this); // Key -12 Btn
        ((Button)findViewById(R.id.keyMinus11Button)).setOnClickListener(this); // Key -11 Btn
        ((Button)findViewById(R.id.keyMinus10Button)).setOnClickListener(this); // Key -10 Btn
        ((Button)findViewById(R.id.keyMinus9Button)).setOnClickListener(this); // Key -9 Btn
        ((Button)findViewById(R.id.keyMinus8Button)).setOnClickListener(this); // Key -8 Btn
        ((Button)findViewById(R.id.keyMinus7Button)).setOnClickListener(this); // Key -7 Btn
        ((Button)findViewById(R.id.keyMinus6Button)).setOnClickListener(this); // Key -6 Btn
        ((Button)findViewById(R.id.keyMinus5Button)).setOnClickListener(this); // Key -5 Btn
        ((Button)findViewById(R.id.keyMinus4Button)).setOnClickListener(this); // Key -4 Btn
        ((Button)findViewById(R.id.keyMinus3Button)).setOnClickListener(this); // Key -3 Btn
        ((Button)findViewById(R.id.keyMinus2Button)).setOnClickListener(this); // Key -2 Btn
        ((Button)findViewById(R.id.keyMinus1Button)).setOnClickListener(this); // Key -1 Btn
        ((Button)findViewById(R.id.keyPlus1Button)).setOnClickListener(this); // Key +1 Btn
        ((Button)findViewById(R.id.keyPlus2Button)).setOnClickListener(this); // Key +2 Btn
        ((Button)findViewById(R.id.keyPlus3Button)).setOnClickListener(this); // Key +3 Btn
        ((Button)findViewById(R.id.keyPlus4Button)).setOnClickListener(this); // Key +4 Btn
        ((Button)findViewById(R.id.keyPlus5Button)).setOnClickListener(this); // Key +5 Btn
        ((Button)findViewById(R.id.keyPlus6Button)).setOnClickListener(this); // Key +6 Btn
        ((Button)findViewById(R.id.keyPlus7Button)).setOnClickListener(this); // Key +7 Btn
        ((Button)findViewById(R.id.keyPlus8Button)).setOnClickListener(this); // Key +8 Btn
        ((Button)findViewById(R.id.keyPlus9Button)).setOnClickListener(this); // Key +9 Btn
        ((Button)findViewById(R.id.keyPlus10Button)).setOnClickListener(this); // Key +10 Btn
        ((Button)findViewById(R.id.keyPlus11Button)).setOnClickListener(this); // Key +11 Btn
        ((Button)findViewById(R.id.keyPlus12Button)).setOnClickListener(this); // Key +12 Btn

        // Sound Module Reset
        ((Button)findViewById(R.id.gmSystemOnButton)).setOnClickListener(this); // GM System On
        ((Button)findViewById(R.id.gsResetButton)).setOnClickListener(this); // GS Reset
        ((Button)findViewById(R.id.xgSystemOnButton)).setOnClickListener(this); // XG System On

        // MIDI INのMonitor
        mReceiveMessageTx = (TextView)findViewById(R.id.receiveMessageTx);

        // ==== 以下Manager周り。あまりいじらなくて良い気がする。====
        // MIDI Manager周り
        MidiManager midiManager = (MidiManager) getSystemService(Context.MIDI_SERVICE);
        midiManager.registerDeviceCallback(new MidiDeviceCallback(), new Handler());

        // Setup the MIDI interface
        mAppMidiManager = new AppMidiManager(midiManager);

        // Initial Scan
        ScanMidiDevices();
    }

    /**
     * Device Scanning
     * Methods are called by the system whenever the set of attached devices changes.
     */
    private class MidiDeviceCallback extends MidiManager.DeviceCallback {
        @Override
        public void onDeviceAdded(MidiDeviceInfo device) {
            ScanMidiDevices();
        }

        @Override
        public void onDeviceRemoved(MidiDeviceInfo device) {
            ScanMidiDevices();
        }
    }

    /**
     * Scans and gathers the list of connected physical devices,
     * then calls onDeviceListChange() to update the UI. This has the
     * side-effect of causing a list item to be selected, which then
     * invokes the listener logic which connects the device(s).
     */
    private void ScanMidiDevices() {
        mAppMidiManager.ScanMidiDevices(mSendDevices, mReceiveDevices);
        onDeviceListChange();
    }

    //
    // UI Helpers
    //
    /**
     * Formats a set of MIDI message bytes into a user-readable form.
     * @param message   The bytes comprising a Midi message.
     */
    private void showReceivedMessage(byte[] message) {
        switch ((message[0] & 0xF0) >> 4) {
            case MidiSpec.MIDICODE_NOTEON:
                mReceiveMessageTx.setText(
                        "NOTE_ON [ch:" + (message[0] & 0x0F) +
                                " key:" + message[1] +
                                " vel:" + message[2] + "]");
                break;

            case MidiSpec.MIDICODE_NOTEOFF:
                mReceiveMessageTx.setText(
                        "NOTE_OFF [ch:" + (message[0] & 0x0F) +
                                " key:" + message[1] +
                                " vel:" + message[2] + "]");
                break;

            // Potentially handle other messages here.
        }
    }

    //
    // View.OnClickListener overriden methods
    // ここでSendが分かる。
    //
    @Override
    public void onClick(View view) {
        byte[] keys = {60, 64, 67};         // C Major chord
        byte[] velocities = {60, 60, 60};   // Middling velocity
        byte channel = 0;    // send on channel 0
        switch (view.getId()) {
            case R.id.keyDownBtn:
                // Simulate a key-down
                mAppMidiManager.sendNoteOn(channel, keys, velocities) ;
                break;

            case R.id.keyUpBtn:
                // Simulate a key-up (converse of key-down above).
                mAppMidiManager.sendNoteOff(channel, keys, velocities) ;
                break;

            /*case R.id.channelChangeBtn: {
                // Send a MIDI program change message
                try {
                    //String progNumStr = mProgNumberEdit.getText().toString();
                    int progNum = Integer.parseInt(progNumStr);

                    mAppMidiManager.sendProgramChange(channel, (byte)progNum);
                } catch (NumberFormatException ex) {
                    // Maybe let the user know
                }
            }
                break;

             */
        }
    }

    //
    // SeekBar.OnSeekBarChangeListener overriden messages
    //
    @Override
    public void onProgressChanged(SeekBar seekBar, int pos, boolean fromUser) {
        switch (seekBar.getId()) {
        case R.id.controllerSeekBar:
            mAppMidiManager.sendController((byte)0, MidiSpec.MIDICC_MODWHEEL, (byte)pos);
            break;

        case R.id.pitchBendSeekBar:
            mAppMidiManager.sendPitchBend((byte)0, pos);
            break;
        }
    }

    //SeekBarのタッチ/タッチ離すの処理。PitchBendはここのStopで戻す?
    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {}

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        if (seekBar.getId() == R.id.pitchBendSeekBar){
            mPitchBendSB.setProgress(0);
        }
    }


    //
    // AdapterView.OnItemSelectedListener overriden methods
    //
    @Override
    public void onItemSelected(AdapterView<?> spinner, View view, int position, long id) {
        switch (spinner.getId()) {
        case R.id.outputDevicesSpinner: {
                MidiDeviceListItem listItem = (MidiDeviceListItem) spinner.getItemAtPosition(position);
                mAppMidiManager.openReceiveDevice(listItem.getDeviceInfo());
            }
            break;

        case R.id.inputDevicesSpinner: {
                MidiDeviceListItem listItem = (MidiDeviceListItem)spinner.getItemAtPosition(position);
                mAppMidiManager.openSendDevice(listItem.getDeviceInfo());
            }
            break;
        }
    }


    @Override
    public void onNothingSelected(AdapterView<?> adapterView) {}

    /**
     * A class to hold MidiDevices in the list controls.
     */
    private class MidiDeviceListItem {
        private MidiDeviceInfo mDeviceInfo;

        public MidiDeviceListItem(MidiDeviceInfo deviceInfo) {
            mDeviceInfo = deviceInfo;
        }

        public MidiDeviceInfo getDeviceInfo() { return mDeviceInfo; }

        @Override
        public String toString() {
            return mDeviceInfo.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);
        }
    }

    /**
     * Fills the specified list control with a set of MidiDevices
     * @param spinner   The list control.
     * @param devices   The set of MidiDevices.
     */
    private void fillDeviceList(Spinner spinner, ArrayList<MidiDeviceInfo> devices) {
        ArrayList<MidiDeviceListItem> listItems = new ArrayList<MidiDeviceListItem>();
        for(MidiDeviceInfo devInfo : devices) {
            listItems.add(new MidiDeviceListItem(devInfo));
        }

        // Creating adapter for spinner
        ArrayAdapter<MidiDeviceListItem> dataAdapter =
                new  ArrayAdapter<MidiDeviceListItem>(this,
                        android.R.layout.simple_spinner_item,
                        listItems);
        // Drop down layout style - list view with radio button
        dataAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

        // attaching data adapter to spinner
        spinner.setAdapter(dataAdapter);
    }

    /**
     * Fills the Input & Output UI device list with the current set of MidiDevices for each type.
     */
    private void onDeviceListChange() {
        fillDeviceList(mOutputDevicesSpinner, mReceiveDevices);
        fillDeviceList(mInputDevicesSpinner, mSendDevices);
    }

    //
    // Native Interface methods
    //
    private native void initNative();

    /**
     * Called from the native code when MIDI messages are received.
     * @param message
     */
    private void onNativeMessageReceive(final byte[] message) {
        // Messages are received on some other thread, so switch to the UI thread
        // before attempting to access the UI
        runOnUiThread(new Runnable() {
            public void run() {
                showReceivedMessage(message);
            }
        });
    }
}
