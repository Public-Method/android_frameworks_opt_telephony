/*
 * Copyright (C) 2006 The Android Open Source Project
 * Copyright (c) 2012-2013, The Linux Foundation. All rights reserved.
 *
 * Not a Contribution.
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
 */

package com.android.internal.telephony;

import static android.telephony.SmsManager.RESULT_ERROR_GENERIC_FAILURE;

import java.util.ArrayList;
import java.util.HashMap;

import android.app.PendingIntent;
import android.app.PendingIntent.CanceledException;
import android.os.AsyncResult;
import android.os.Message;
import android.provider.Telephony.Sms.Intents;
import android.telephony.Rlog;

import com.android.internal.telephony.cdma.CdmaSMSDispatcher;
import com.android.internal.telephony.gsm.GsmSMSDispatcher;

public class ImsSMSDispatcher extends SMSDispatcher {
    private static final String TAG = "RIL_ImsSms";

    protected SMSDispatcher mCdmaDispatcher;
    protected SMSDispatcher mGsmDispatcher;

    /** true if IMS is registered and sms is supported, false otherwise.*/
    private boolean mIms = false;
    private String mImsSmsFormat = SmsConstants.FORMAT_UNKNOWN;

    public ImsSMSDispatcher(PhoneBase phone, SmsStorageMonitor storageMonitor,
            SmsUsageMonitor usageMonitor) {
        super(phone, storageMonitor, usageMonitor);

        initDispatchers(phone, storageMonitor, usageMonitor);

        mCi.registerForOn(this, EVENT_RADIO_ON, null);
        mCi.registerForImsNetworkStateChanged(this, EVENT_IMS_STATE_CHANGED, null);
    }

    protected void initDispatchers(PhoneBase phone, SmsStorageMonitor storageMonitor,
            SmsUsageMonitor usageMonitor) {
        Rlog.d(TAG, "ImsSMSDispatcher: initDispatchers()");
        mCdmaDispatcher = new CdmaSMSDispatcher(phone,
                storageMonitor, usageMonitor, this);
        mGsmDispatcher = new GsmSMSDispatcher(phone,
                storageMonitor, usageMonitor, this);
    }

    /* Updates the phone object when there is a change */
    @Override
    protected void updatePhoneObject(PhoneBase phone) {
        Rlog.d(TAG, "In IMS updatePhoneObject ");
        super.updatePhoneObject(phone);
        mCdmaDispatcher.updatePhoneObject(phone);
        mGsmDispatcher.updatePhoneObject(phone);
    }

    public void dispose() {
        mCi.unregisterForOn(this);
        mCi.unregisterForImsNetworkStateChanged(this);
        mCdmaDispatcher.dispose();
        mGsmDispatcher.dispose();
    }

    /**
     * Handles events coming from the phone stack. Overridden from handler.
     *
     * @param msg the message to handle
     */
    @Override
    public void handleMessage(Message msg) {
        AsyncResult ar;

        switch (msg.what) {
        case EVENT_RADIO_ON:
        case EVENT_IMS_STATE_CHANGED: // received unsol
            mCi.getImsRegistrationState(this.obtainMessage(EVENT_IMS_STATE_DONE));
            break;

        case EVENT_IMS_STATE_DONE:
            ar = (AsyncResult) msg.obj;

            if (ar.exception == null) {
                updateImsInfo(ar);
            } else {
                Rlog.e(TAG, "IMS State query failed with exp "
                        + ar.exception);
            }
            break;

        default:
            super.handleMessage(msg);
        }
    }

    private void setImsSmsFormat(int format) {
        // valid format?
        switch (format) {
            case PhoneConstants.PHONE_TYPE_GSM:
                mImsSmsFormat = SmsConstants.FORMAT_3GPP;
                break;
            case PhoneConstants.PHONE_TYPE_CDMA:
                mImsSmsFormat = SmsConstants.FORMAT_3GPP2;
                break;
            default:
                mImsSmsFormat = SmsConstants.FORMAT_UNKNOWN;
                break;
        }
    }

    private void updateImsInfo(AsyncResult ar) {
        int[] responseArray = (int[])ar.result;

        mIms = false;
        if (responseArray[0] == 1) {  // IMS is registered
            Rlog.d(TAG, "IMS is registered!");
            mIms = true;
        } else {
            Rlog.d(TAG, "IMS is NOT registered!");
        }

        setImsSmsFormat(responseArray[1]);

        if ((SmsConstants.FORMAT_UNKNOWN.equals(mImsSmsFormat))) {
            Rlog.e(TAG, "IMS format was unknown!");
            // failed to retrieve valid IMS SMS format info, set IMS to unregistered
            mIms = false;
        }
    }

    @Override
    protected void acknowledgeLastIncomingSms(boolean success, int result,
        Message response) {
        // EVENT_NEW_SMS is registered only by Gsm/CdmaSMSDispatcher.
        Rlog.e(TAG, "acknowledgeLastIncomingSms should never be called from here!");
    }

    @Override
    protected int dispatchMessage(SmsMessageBase sms) {
        // EVENT_NEW_SMS is registered only by Gsm/CdmaSMSDispatcher.
        Rlog.e(TAG, "dispatchMessage should never be called from here!");
        return Intents.RESULT_SMS_GENERIC_ERROR;
    }

    @Override
    protected void sendData(String destAddr, String scAddr, int destPort,
            byte[] data, PendingIntent sentIntent, PendingIntent deliveryIntent) {
        if (isCdmaMo()) {
            mCdmaDispatcher.sendData(destAddr, scAddr, destPort,
                    data, sentIntent, deliveryIntent);
        } else {
            mGsmDispatcher.sendData(destAddr, scAddr, destPort,
                    data, sentIntent, deliveryIntent);
        }
    }

    @Override
    protected void sendMultipartText(String destAddr, String scAddr,
            ArrayList<String> parts, ArrayList<PendingIntent> sentIntents,
            ArrayList<PendingIntent> deliveryIntents) {
        if (isCdmaMo()) {
            mCdmaDispatcher.sendMultipartText(destAddr, scAddr,
                    parts, sentIntents, deliveryIntents);
        } else {
            mGsmDispatcher.sendMultipartText(destAddr, scAddr,
                    parts, sentIntents, deliveryIntents);
        }
    }

    @Override
    protected void sendSms(SmsTracker tracker) {
        //  sendSms is a helper function to other send functions, sendText/Data...
        //  it is not part of ISms.stub
        Rlog.e(TAG, "sendSms should never be called from here!");
    }

    @Override
    protected void sendText(String destAddr, String scAddr, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent) {
        Rlog.d(TAG, "sendText");
        if (isCdmaMo()) {
            mCdmaDispatcher.sendText(destAddr, scAddr,
                    text, sentIntent, deliveryIntent);
        } else {
            mGsmDispatcher.sendText(destAddr, scAddr,
                    text, sentIntent, deliveryIntent);
        }
    }

    @Override
    protected void sendTextWithPriority(String destAddr, String scAddr, String text,
            PendingIntent sentIntent, PendingIntent deliveryIntent, int priority) {
        Rlog.d(TAG, "sendTextWithPriority");
        if (isCdmaMo()) {
            mCdmaDispatcher.sendTextWithPriority(destAddr, scAddr,
                    text, sentIntent, deliveryIntent, priority);
        } else {
            Rlog.e(TAG, "priority is not supported in 3gpp text message!");
        }
    }

    @Override
    public void sendRetrySms(SmsTracker tracker) {
        String oldFormat = tracker.mFormat;

        // newFormat will be based on voice technology
        String newFormat =
            (PhoneConstants.PHONE_TYPE_CDMA == mPhone.getPhoneType()) ?
                    mCdmaDispatcher.getFormat() :
                        mGsmDispatcher.getFormat();

        // was previously sent sms format match with voice tech?
        if (oldFormat.equals(newFormat)) {
            if (isCdmaFormat(newFormat)) {
                Rlog.d(TAG, "old format matched new format (cdma)");
                mCdmaDispatcher.sendSms(tracker);
                return;
            } else {
                Rlog.d(TAG, "old format matched new format (gsm)");
                mGsmDispatcher.sendSms(tracker);
                return;
            }
        }

        // format didn't match, need to re-encode.
        HashMap map = tracker.mData;

        // to re-encode, fields needed are:  scAddr, destAddr, and
        //   text if originally sent as sendText or
        //   data and destPort if originally sent as sendData.
        if (!( map.containsKey("scAddr") && map.containsKey("destAddr") &&
               ( map.containsKey("text") ||
                       (map.containsKey("data") && map.containsKey("destPort"))))) {
            // should never come here...
            Rlog.e(TAG, "sendRetrySms failed to re-encode per missing fields!");
            if (tracker.mSentIntent != null) {
                int error = RESULT_ERROR_GENERIC_FAILURE;
                // Done retrying; return an error to the app.
                try {
                    tracker.mSentIntent.send(mContext, error, null);
                } catch (CanceledException ex) {}
            }
            return;
        }
        String scAddr = (String)map.get("scAddr");
        String destAddr = (String)map.get("destAddr");

        SmsMessageBase.SubmitPduBase pdu = null;
        //    figure out from tracker if this was sendText/Data
        if (map.containsKey("text")) {
            Rlog.d(TAG, "sms failed was text");
            String text = (String)map.get("text");

            if (isCdmaFormat(newFormat)) {
                Rlog.d(TAG, "old format (gsm) ==> new format (cdma)");
                pdu = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(
                        scAddr, destAddr, text, (tracker.mDeliveryIntent != null), null);
            } else {
                Rlog.d(TAG, "old format (cdma) ==> new format (gsm)");
                pdu = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(
                        scAddr, destAddr, text, (tracker.mDeliveryIntent != null), null);
            }
        } else if (map.containsKey("data")) {
            Rlog.d(TAG, "sms failed was data");
            byte[] data = (byte[])map.get("data");
            Integer destPort = (Integer)map.get("destPort");

            if (isCdmaFormat(newFormat)) {
                Rlog.d(TAG, "old format (gsm) ==> new format (cdma)");
                pdu = com.android.internal.telephony.cdma.SmsMessage.getSubmitPdu(
                            scAddr, destAddr, destPort.intValue(), data,
                            (tracker.mDeliveryIntent != null));
            } else {
                Rlog.d(TAG, "old format (cdma) ==> new format (gsm)");
                pdu = com.android.internal.telephony.gsm.SmsMessage.getSubmitPdu(
                            scAddr, destAddr, destPort.intValue(), data,
                            (tracker.mDeliveryIntent != null));
            }
        }

        // replace old smsc and pdu with newly encoded ones
        map.put("smsc", pdu.encodedScAddress);
        map.put("pdu", pdu.encodedMessage);

        SMSDispatcher dispatcher = (isCdmaFormat(newFormat)) ?
                mCdmaDispatcher : mGsmDispatcher;

        tracker.mFormat = dispatcher.getFormat();
        dispatcher.sendSms(tracker);
    }

    @Override
    protected String getFormat() {
        // this function should be defined in Gsm/CdmaDispatcher.
        Rlog.e(TAG, "getFormat should never be called from here!");
        return SmsConstants.FORMAT_UNKNOWN;
    }

    @Override
    protected GsmAlphabet.TextEncodingDetails calculateLength(
            CharSequence messageBody, boolean use7bitOnly) {
        Rlog.e(TAG, "Error! Not implemented for IMS.");
        return null;
    }

    @Override
    protected void sendNewSubmitPdu(String destinationAddress, String scAddress, String message,
            SmsHeader smsHeader, int format, PendingIntent sentIntent,
            PendingIntent deliveryIntent, boolean lastPart) {
        Rlog.e(TAG, "Error! Not implemented for IMS.");
    }

    @Override
    public boolean isIms() {
        return mIms;
    }

    @Override
    public String getImsSmsFormat() {
        return mImsSmsFormat;
    }

    /**
     * Determines whether or not to use CDMA format for MO SMS.
     * If SMS over IMS is supported, then format is based on IMS SMS format,
     * otherwise format is based on current phone type.
     *
     * @return true if Cdma format should be used for MO SMS, false otherwise.
     */
    private boolean isCdmaMo() {
        if (!isIms()) {
            // IMS is not registered, use Voice technology to determine SMS format.
            return (PhoneConstants.PHONE_TYPE_CDMA == mPhone.getPhoneType());
        }
        // IMS is registered with SMS support
        return isCdmaFormat(mImsSmsFormat);
    }

    /**
     * Determines whether or not format given is CDMA format.
     *
     * @param format
     * @return true if format given is CDMA format, false otherwise.
     */
    private boolean isCdmaFormat(String format) {
        return (mCdmaDispatcher.getFormat().equals(format));
    }
}
