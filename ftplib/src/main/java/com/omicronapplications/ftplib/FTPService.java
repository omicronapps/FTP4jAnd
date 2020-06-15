package com.omicronapplications.ftplib;

import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;

import it.sauronsoftware.ftp4j.FTPAbortedException;
import it.sauronsoftware.ftp4j.FTPClient;
import it.sauronsoftware.ftp4j.FTPDataTransferException;
import it.sauronsoftware.ftp4j.FTPDataTransferListener;
import it.sauronsoftware.ftp4j.FTPException;
import it.sauronsoftware.ftp4j.FTPFile;
import it.sauronsoftware.ftp4j.FTPIllegalReplyException;
import it.sauronsoftware.ftp4j.FTPListParseException;

public class FTPService extends Service {
    private final static String TAG = "FTPService";
    // Commands
    public static final int WHAT_START = 1;
    public static final int WHAT_STOP = 2;
    public static final int WHAT_CONNECT = 3;
    public static final int WHAT_DISCONNECT = 4;
    public static final int WHAT_LOGIN = 5;
    public static final int WHAT_LOGOUT = 6;
    public static final int WHAT_CURRENT_DIRECTORY = 7;
    public static final int WHAT_CHANGE_DIRECTORY = 8;
    public static final int WHAT_CHANGE_DIRECTORY_UP = 9;
    public static final int WHAT_LIST = 10;
    public static final int WHAT_LIST_NAMES = 11;
    public static final int WHAT_DOWNLOAD = 12;
    public static final int WHAT_ABORT_CURRENT_DATA_TRANSFER = 13;
    public static final int WHAT_COMMAND_MAX = 100;
    // Download updates
    public static final int WHAT_DOWNLOAD_STARTED = 101;
    public static final int WHAT_DOWNLOAD_TRANSFERRED = 102;
    public static final int WHAT_DOWNLOAD_COMPLETED = 103;
    public static final int WHAT_DOWNLOAD_ABORTED = 104;
    public static final int WHAT_DOWNLOAD_FAILED = 105;
    public static final int WHAT_DOWNLOAD_QUEUE = 106;
    // Arguments, return values
    public static final String KEY_HOST = "com.omicronapplications.ftplib.key.HOST";
    public static final String KEY_USERNAME = "com.omicronapplications.ftplib.key.USERNAME";
    public static final String KEY_PASSWORD = "com.omicronapplications.ftplib.key.PASSWORD";
    public static final String KEY_PATH = "com.omicronapplications.ftplib.key.PATH";
    public static final String KEY_FILESPEC = "com.omicronapplications.ftplib.key.FILESPEC";
    public static final String KEY_REMOTE_FILE_NAME = "com.omicronapplications.ftplib.key.REMOTE_FILE_NAME";
    public static final String KEY_LOCAL_FILE_NAME = "com.omicronapplications.ftplib.key.LOCAL_FILE_NAME";
    public static final String KEY_MESSAGE = "com.omicronapplications.ftplib.key.MESSAGE";
    public static final String KEY_FILES = "com.omicronapplications.ftplib.key.FILES";
    public static final String KEY_NAMES = "com.omicronapplications.ftplib.key.NAMES";
    // Exceptions
    public static final int EXCEPTION_OK = 0;
    public static final int EXCEPTION_UNKNOWN = -1;
    public static final int EXCEPTION_ILLEGAL_STATE = -2;
    public static final int EXCEPTION_FILE_NOT_FOUND = -3;
    public static final int EXCEPTION_IO = -4;
    public static final int EXCEPTION_FTP_ILLEGAL_REPLY = -5;
    public static final int EXCEPTION_FTP = -6;
    public static final int EXCEPTION_FTP_DATA_TRANSFER = -7;
    public static final int EXCEPTION_FTP_ABORTED = -8;
    public static final int EXCEPTION_FTP_LIST_PARSE = -9;
    private final IBinder mBinder = new PlayerBinder();
    private HandlerThread mMessageThread;
    private MessageCallback mMessageCallback;
    private Handler mMessageHandler;
    private HandlerThread mDownloadThread;
    private DownloadRunner mDownloadRunner;
    private Handler mDownloadHandler;
    private Messenger mRemoteMessenger;
    private FTPTransferListener mTransferListener;
    // DownloadRunner/MessageCallback variables
    private final FTPClient mClient = new FTPClient();
    private ConcurrentLinkedQueue<DownloadElement> mDownloadQueue = new ConcurrentLinkedQueue<>();

    public final class PlayerBinder extends Binder {
        Handler getHandler() {
            return mMessageHandler;
        }
    }

    @Override
    public void onCreate() {
        mMessageThread = new HandlerThread("FTPHandler", Process.THREAD_PRIORITY_BACKGROUND);
        try {
            mMessageThread.start();
        } catch (IllegalThreadStateException e) {
            Log.w(TAG, "onCreate: IllegalThreadStateException: " + e.getMessage());
        }
        mMessageCallback = new MessageCallback();
        Looper looper = mMessageThread.getLooper();
        mMessageHandler = new Handler(looper, mMessageCallback);

        mDownloadThread = new HandlerThread("FTPClient", Process.THREAD_PRIORITY_BACKGROUND);
        try {
            mDownloadThread.start();
        } catch (IllegalThreadStateException e) {
            Log.w(TAG, "onCreate: IllegalThreadStateException: " + e.getMessage());
        }
        mDownloadRunner = new DownloadRunner();
        looper = mDownloadThread.getLooper();
        mDownloadHandler = new Handler(looper);

        mTransferListener = new FTPTransferListener();
        mDownloadQueue.clear();
    }

    @Override
    public void onDestroy() {
        if (mMessageHandler != null) {
            mMessageHandler.removeCallbacksAndMessages(null);
        }
        if (mMessageThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mMessageThread.quitSafely();
            } else {
                mMessageThread.quit();
            }
        }
        mMessageThread = null;
        mMessageCallback = null;
        mMessageHandler = null;

        if (mDownloadHandler != null) {
            mDownloadHandler.removeCallbacksAndMessages(null);
        }
        if (mDownloadThread != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR2) {
                mDownloadThread.quitSafely();
            } else {
                mDownloadThread.quit();
            }
        }
        mDownloadThread = null;
        mDownloadRunner = null;
        mDownloadHandler = null;

        mTransferListener = null;
        mDownloadQueue.clear();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        return false;
    }

    private final class DownloadRunner implements Runnable {
        @Override
        public void run() {
            DownloadElement element = mDownloadQueue.poll();
            if (element == null) {
                Log.w(TAG, "download: no file enqueued for download");
                if (mTransferListener != null) {
                    mTransferListener.failed();
                }
                if (mMessageCallback != null) {
                    mMessageCallback.sendReply(WHAT_DOWNLOAD_QUEUE, EXCEPTION_UNKNOWN, mDownloadQueue.size());
                }
                return;
            }
            int result = EXCEPTION_OK;
            String remoteFileName = element.remoteFileName;
            File localFile = element.localFile;
            int restartAt = element.restartAt;
            if (remoteFileName == null || localFile == null || restartAt < 0 || mTransferListener == null) {
                Log.e(TAG, "run: not initialized: remoteFileName:" + remoteFileName + ", localFile:" + localFile + ", restartAt:" + restartAt + ", mTransferListener:" + mTransferListener);
                return;
            }
            try {
                if (restartAt > 0) {
                    mClient.download(remoteFileName, localFile, restartAt, mTransferListener);
                } else {
                    mClient.download(remoteFileName, localFile, mTransferListener);
                }
            } catch (Throwable t) {
                Log.e(TAG, "run: failed to download: " + localFile + " to: " + remoteFileName + " from: " + restartAt);
                result = whatException(t);
            }
            if (mMessageCallback != null) {
                mMessageCallback.sendReply(WHAT_DOWNLOAD, result);
                mMessageCallback.sendReply(WHAT_DOWNLOAD_QUEUE, result, mDownloadQueue.size());
            }
        }
    }

    private final static class DownloadElement {
        DownloadElement(String remoteFileName, File localFile, int restartAt) {
            this.remoteFileName = remoteFileName;
            this.localFile = localFile;
            this.restartAt = restartAt;
        }
        String remoteFileName;
        File localFile;
        int restartAt;
    }

    private final class MessageCallback implements Handler.Callback {
        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case WHAT_START:
                    mRemoteMessenger = msg.replyTo;
                    sendReply(msg.what);
                    break;

                case WHAT_STOP:
                    sendReply(msg.what);
                    mRemoteMessenger = null;
                    break;

                case WHAT_CONNECT:
                    connect(msg);
                    break;

                case WHAT_DISCONNECT:
                    disconnect(msg);
                    break;

                case WHAT_LOGIN:
                    login(msg);
                    break;

                case WHAT_LOGOUT:
                    logout(msg);
                    break;

                case WHAT_CURRENT_DIRECTORY:
                    currentDirectory(msg);
                    break;

                case WHAT_CHANGE_DIRECTORY:
                    changeDirectory(msg);
                    break;

                case WHAT_CHANGE_DIRECTORY_UP:
                    changeDirectoryUp(msg);
                    break;

                case WHAT_LIST:
                    list(msg);
                    break;

                case WHAT_LIST_NAMES:
                    listNames(msg);
                    break;

                case WHAT_DOWNLOAD:
                    download(msg);
                    break;

                case WHAT_ABORT_CURRENT_DATA_TRANSFER:
                    abortCurrentDataTransfer(msg);
                    break;

                default:
                    Log.w(TAG, "handleMessage: unknown message: " + msg.what);
                    break;
            }

            return true;
        }

        private String getMessageString(Message msg, String key) {
            Bundle data = msg.getData();
            return data.getString(key);
        }

        private void sendReply(int what) {
            sendReply(what, 0, null);
        }

        private void sendReply(int what, int arg1) {
            sendReply(what, arg1, null);
        }

        private void sendReply(int what, int arg1, int arg2) {
            sendReply(what, arg1, arg2, null);
        }

        private void sendReply(int what, int arg1, Bundle data) {
            sendReply(what, arg1, 0, data);
        }

        private void sendReply(int what, int arg1, int arg2, Bundle data) {
            if (mRemoteMessenger == null) {
                Log.w(TAG, "sendReply: no message handler");
                return;
            }
            Message returnMessage = mMessageHandler.obtainMessage();
            returnMessage.what = what;
            returnMessage.arg1 = arg1;
            returnMessage.arg2 = arg2;
            if (data != null) {
                returnMessage.setData(data);
            }
            try {
                mRemoteMessenger.send(returnMessage);
            } catch (RemoteException e) {
                Log.e(TAG, "sendReply: failed to send message");
            }
        }

        private void connect(Message msg) {
            String host = getMessageString(msg, KEY_HOST);
            if (host == null) {
                host = "/";
            }
            int port = msg.arg1;
            Bundle data = new Bundle();
            int result = EXCEPTION_OK;
            try {
                String[] messages;
                if (port < 0) {
                    messages = mClient.connect(host);
                } else {
                    messages = mClient.connect(host, port);
                }
                data.putStringArray(KEY_MESSAGE, messages);
            } catch (Throwable t) {
                Log.e(TAG, "connect: failed to connect to: " + host + ":" + port);
                result = whatException(t);
            }
            sendReply(msg.what, result, data);
        }

        private void disconnect(Message msg) {
            int result = EXCEPTION_OK;
            try {
                mClient.disconnect(true);
            } catch (Throwable t) {
                Log.e(TAG, "disconnect: failed");
                result = whatException(t);
           }
            sendReply(msg.what, result);
        }

        private void login(Message msg) {
            String username = getMessageString(msg, KEY_USERNAME);
            if (username == null) {
                username = "anonymous";
            }
            String password = getMessageString(msg, KEY_PASSWORD);
            if (password == null) {
                password = "";
            }
            int result = EXCEPTION_OK;
            try {
                mClient.login(username, password);
            } catch (Throwable t) {
                Log.e(TAG, "login: failed to log in with: " + "username");
                result = whatException(t);
            }
            sendReply(msg.what, result);
        }

        private void logout(Message msg) {
            int result = EXCEPTION_OK;
            try {
                mClient.logout();
            } catch (Throwable t) {
                Log.e(TAG, "logout: failed");
                result = whatException(t);
            }
            sendReply(msg.what, result);
        }

        private void currentDirectory(Message msg) {
            Bundle data = new Bundle();
            int result = EXCEPTION_OK;
            try {
                String path = mClient.currentDirectory();
                data.putString(KEY_PATH, path);
            } catch (Throwable t) {
                Log.e(TAG, "currentDirectory: failed");
                result = whatException(t);
            }
            sendReply(msg.what, result, data);
        }

        private void changeDirectory(Message msg) {
            String path = getMessageString(msg, KEY_PATH);
            if (path == null) {
                path = "/";
            }
            int result = EXCEPTION_OK;
            try {
                mClient.changeDirectory(path);
            } catch (Throwable t) {
                Log.e(TAG, "changeDirectory: failed to change path to: " + path);
                result = whatException(t);
            }
            sendReply(msg.what, result);
        }

        private void changeDirectoryUp(Message msg) {
            int result = EXCEPTION_OK;
            try {
                mClient.changeDirectoryUp();
            } catch (Throwable t) {
                Log.e(TAG, "changeDirectoryUp: failed");
                result = whatException(t);
            }
            sendReply(msg.what, result);
        }

        private void list(Message msg) {
            String fileSpec = getMessageString(msg, KEY_FILESPEC);
            Bundle data = new Bundle();
            int result = EXCEPTION_OK;
            try {
                FTPFile[] files;
                if (fileSpec == null) {
                    files = mClient.list();
                } else {
                    files = mClient.list(fileSpec);
                }
                FTPFiles parcelable = new FTPFiles(files);
                data.putParcelable(KEY_FILES, parcelable);
            } catch (Throwable t) {
                Log.e(TAG, "list: failed");
                result = whatException(t);
            }
            sendReply(msg.what, result, data);
        }

        private void listNames(Message msg) {
            Bundle data = new Bundle();
            int result = EXCEPTION_OK;
            try {
                String[] names = mClient.listNames();
                data.putStringArray(KEY_NAMES, names);
            } catch (Throwable t) {
                Log.e(TAG, "listNames: failed");
                result = whatException(t);
            }
            sendReply(msg.what, result, data);
        }

        private void download(Message msg) {
            final String remoteFileName = getMessageString(msg, KEY_REMOTE_FILE_NAME);
            final String localFileName = getMessageString(msg, KEY_LOCAL_FILE_NAME);
            final int restartAt = msg.arg1;
            if (remoteFileName == null || localFileName == null || restartAt < 0) {
                sendReply(msg.what, EXCEPTION_FILE_NOT_FOUND);
                return;
            }
            final File localFile = new File(localFileName);
            if (mDownloadHandler != null) {
                mDownloadQueue.add(new DownloadElement(remoteFileName, localFile, restartAt));
                if (mMessageCallback != null) {
                    mMessageCallback.sendReply(WHAT_DOWNLOAD_QUEUE, EXCEPTION_OK, mDownloadQueue.size());
                }
                mDownloadHandler.post(mDownloadRunner);
            }
        }

        private void abortCurrentDataTransfer(Message msg) {
            int result = EXCEPTION_OK;
            try {
                mClient.abortCurrentDataTransfer(true);
            } catch (Throwable t) {
                Log.e(TAG, "abortCurrentDataTransfer: failed");
                result = whatException(t);
            }
            sendReply(msg.what, result);
        }
    }

    private class FTPTransferListener implements FTPDataTransferListener {
        @Override
        public void started() {
            Message msg = Message.obtain();
            msg.what = WHAT_DOWNLOAD_STARTED;
            if (mMessageCallback != null) {
                mMessageCallback.sendReply(msg.what);
            }
        }

        @Override
        public void transferred(int length) {
            Message msg = Message.obtain();
            msg.what = WHAT_DOWNLOAD_TRANSFERRED;
            if (mMessageCallback != null) {
                mMessageCallback.sendReply(msg.what, EXCEPTION_OK, length);
            }
        }

        @Override
        public void completed() {
            Message msg = Message.obtain();
            msg.what = WHAT_DOWNLOAD_COMPLETED;
            if (mMessageCallback != null) {
                mMessageCallback.sendReply(msg.what);
            }
        }

        @Override
        public void aborted() {
            Message msg = Message.obtain();
            msg.what = WHAT_DOWNLOAD_ABORTED;
            if (mMessageCallback != null) {
                mMessageCallback.sendReply(msg.what);
            }
        }

        @Override
        public void failed() {
            Message msg = Message.obtain();
            msg.what = WHAT_DOWNLOAD_FAILED;
            if (mMessageCallback != null) {
                mMessageCallback.sendReply(msg.what);
            }
        }
    }

    private static int whatException(Throwable t) {
        int result = EXCEPTION_UNKNOWN;
        if (t instanceof IllegalStateException) {
            Log.w(TAG, "IllegalStateException: " + t.getMessage());
            result = EXCEPTION_ILLEGAL_STATE;
        } else if (t instanceof IOException) {
            Log.w(TAG, "IOException: " + t.getMessage());
            result = EXCEPTION_IO;
        } else if (t instanceof FTPIllegalReplyException) {
            Log.w(TAG, "FTPIllegalReplyException: " + t.getMessage());
            result = EXCEPTION_FTP_ILLEGAL_REPLY;
        } else if (t instanceof FTPException) {
            Log.w(TAG, "FTPException: " + ((FTPException)t).toString());
            result = EXCEPTION_FTP;
        } else if (t instanceof FTPDataTransferException) {
            Log.w(TAG, "FTPDataTransferException: " + t.getMessage());
            result = EXCEPTION_FTP_DATA_TRANSFER;
        } else if (t instanceof FTPAbortedException) {
            Log.w(TAG, "FTPAbortedException: " + t.getMessage());
            result = EXCEPTION_FTP_ABORTED;
        } else if (t instanceof FTPListParseException) {
            Log.w(TAG, "FTPAbortedException: " + t.getMessage());
            result = EXCEPTION_FTP_LIST_PARSE;
        } else {
            Log.w(TAG, "whatException: " + t.getMessage());
        }
        return result;
    }
}
