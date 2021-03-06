/*
 * The MIT License (MIT)
 * 
 * Copyright (c) 2010-2015 Carnegie Mellon University
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * 
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package edu.cmu.cylab.starslinger.exchange;

import java.nio.ByteBuffer;
import java.util.Date;

import android.content.Context;
import android.util.Log;

public abstract class ConnectionEngine {

    private static ConnectionEngine sInstance;
    private static final String TAG = ExchangeConfig.LOG_TAG;
    protected boolean mCancelable = false;
    protected Date mExchStartTimer;
    protected static Context mCtx;
    private int mLatestServerVersion = 0;
    private int mVersion = ExchangeConfig.getMinVersionCode();

    public static ConnectionEngine getServerInstance(Context ctx, String hostName) {
        mCtx = ctx;
        if (sInstance == null) {
            instantiateImplementedClass(WebEngine.class.getSimpleName());
            ((WebEngine) sInstance).setHost(hostName);
        }
        return sInstance;
    }

    private static void instantiateImplementedClass(String className) throws IllegalStateException {
        try {
            String fullClass = ConnectionEngine.class.getPackage().getName() + "." + className;
            Class<? extends ConnectionEngine> clazz;
            clazz = Class.forName(fullClass).asSubclass(ConnectionEngine.class);
            sInstance = clazz.newInstance();
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException(e);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException(e);
        } catch (InstantiationException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isCancelable() {
        return mCancelable;
    }

    public void setCancelable(boolean cancelable) {
        mCancelable = cancelable;
    }

    public Date getExchStartTimer() {
        return mExchStartTimer;
    }

    public abstract void shutdownConnection();

    /**
     * send commitment, receives unique short user id
     */
    public byte[] assign_user(byte[] msg) throws ExchangeException {
        if (msg == null || msg.length == 0) {
            return null;
        }
        byte[] resp = assignUser(msg);
        handleResponseExceptions(resp, 0);
        Log.i(TAG, "User id created");
        return resp;
    }

    public byte[] sync_commits(byte[] msg) throws ExchangeException {
        if (msg == null || msg.length == 0) {
            return null;
        }
        handleTimeoutException();
        byte[] resp = syncUsers(msg);
        handleResponseExceptions(resp, 0);
        Log.i(TAG, "User created");
        return resp;
    }

    public byte[] sync_data(byte[] msg) throws ExchangeException {
        if (msg == null || msg.length == 0) {
            return null;
        }
        handleTimeoutException();
        byte[] resp = syncData(msg);
        handleResponseExceptions(resp, 0);
        Log.i(TAG, "User updated");
        return resp;
    }

    public byte[] sync_signatures(byte[] msg) throws ExchangeException {
        if (msg == null || msg.length == 0) {
            return null;
        }
        handleTimeoutException();
        byte[] resp = syncSignatures(msg);
        handleResponseExceptions(resp, 0);
        Log.i(TAG, "Signature sent");
        return resp;
    }

    public byte[] sync_keynodes(byte[] msg) throws ExchangeException {
        if (msg == null || msg.length == 0) {
            return null;
        }
        handleTimeoutException();
        byte[] resp = syncKeyNodes(msg);
        handleResponseExceptions(resp, 2);
        Log.i(TAG, "key node sent");
        return resp;
    }

    public byte[] sync_match(byte[] msg) throws ExchangeException {
        if (msg == null || msg.length == 0) {
            return null;
        }
        handleTimeoutException();
        byte[] resp = syncMatch(msg);
        handleResponseExceptions(resp, 0);
        Log.i(TAG, "Match nonce sent");
        return resp;
    }

    protected abstract byte[] assignUser(byte[] requestBody) throws ExchangeException;

    protected abstract byte[] syncUsers(byte[] requestBody) throws ExchangeException;

    protected abstract byte[] syncData(byte[] requestBody) throws ExchangeException;

    protected abstract byte[] syncSignatures(byte[] requestBody) throws ExchangeException;

    protected abstract byte[] syncKeyNodes(byte[] requestBody) throws ExchangeException;

    protected abstract byte[] syncMatch(byte[] requestBody) throws ExchangeException;

    private void handleTimeoutException() throws ExchangeException {
        long elapsedMs = new Date().getTime() - mExchStartTimer.getTime();
        if (elapsedMs > ExchangeConfig.MSSVR_EXCH_PROT_MAX) {
            throw new ExchangeException(
                    mCtx.getString(R.string.error_ExchangeProtocolTimeoutExceeded));
        }
    }

    private byte[] handleResponseExceptions(byte[] resp, int errMax) throws ExchangeException {
        int firstInt = 0;
        ByteBuffer result = ByteBuffer.wrap(resp);
        if (mCancelable)
            throw new ExchangeException(mCtx.getString(R.string.error_WebCancelledByUser));
        else if (resp == null)
            throw new ExchangeException(mCtx.getString(R.string.error_ServerNotResponding));
        else if (resp.length < 4)
            throw new ExchangeException(mCtx.getString(R.string.error_ServerNotResponding));
        else {
            firstInt = result.getInt();
            byte[] bytes = new byte[result.remaining()];
            result.get(bytes);
            if (firstInt <= errMax) { // error int
                Log.e(TAG, "server error code: " + firstInt);
                throw new ExchangeException(String.format(
                        mCtx.getString(R.string.error_ServerAppMessage), new String(bytes).trim()));
            }
            // else strip off server version
            mLatestServerVersion = firstInt;
            return bytes;
        }
    }

    public int getLatestServerVersion() {
        return mLatestServerVersion;
    }
}
