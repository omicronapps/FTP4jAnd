package com.omicronapplications.ftplib;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.lang.ref.WeakReference;

/**
 * FTPController provides a way of running an FTP Client in a separate thread.
 * Implement FTPCallback callback interface to receive responses to requests.
 */
public class FTPController {
    private static final String TAG = "FTPController";
    private final Context mContext;
    private IFTPCallback mCallback;
    private IFTPDownload mDownload;
    private FTPConnection mConnection;
    private Messenger mLocalMessenger;
    private Messenger mRemoteMessenger;
    private FTPState mState;

    public enum FTPState {
        SERVICE_STOPPED, SERVICE_STARTED, FTP_CONNECTED, FTP_LOGGED_IN
    }

    /**
     * FTPController callback interface
     */
    public interface IFTPCallback {
        void start();
        void stop();
        void connect(int exception, String[] message);
        void disconnect(int exception);
        void login(int exception);
        void logout(int exception);
        void currentDirectory(int exception, String path);
        void changeDirectory(int exception);
        void changeDirectoryUp(int exception);
        void list(int exception, FTPFile[] files);
        void listNames(int exception, String[] names);
        void download(int exception);
        void abortCurrentDataTransfer(int exception);
    }

    /**
     * FTPController download callback interface
     */
    public interface IFTPDownload {
        void started();
        void transferred(int length);
        void completed();
        void aborted();
        void failed();
    }

    /*
     * FTPController public interface
     *
     * @param context   Application context
     */
    public FTPController(Context context) {
        mContext = context;
        mState = FTPState.SERVICE_STOPPED;
    }

    /*
     * Set callbacks
     *
     * @param callback  Instance of FTPCallback implementation
     * @param download  Instance of FTPDownload implementation
     */
    public void setCallbacks(IFTPCallback callback, IFTPDownload download) {
        mCallback = callback;
        mDownload = download;
    }

    /**
     * Start FTP service
     *
     * @return  <code>true</code< if the service was successfully started
     *          <code>false</code> otherwise
     * @see     IFTPCallback#start()
     */
    public boolean start() {
        if (mContext == null || mCallback == null) {
            Log.e(TAG, "start: Failed to set up ServiceConnection");
            return false;
        }
        mConnection = new FTPConnection(this);
        Intent intent = new Intent(mContext, FTPService.class);
        if (!mContext.bindService(intent, mConnection, Context.BIND_AUTO_CREATE)) {
            Log.w(TAG, "start: Failed to bind to service");
            return false;
        }
        return true;
    }

    /**
     * Stop FTP service
     *
     * @see  IFTPCallback#stop()
     */
    public void stop() {
        if (mContext == null || mConnection == null) {
            return;
        }
        mContext.unbindService(mConnection);
        mConnection = null;
    }

    /**
     * Connect to FTP server, default port
     *
     * @param host  Host address
     * @return      <code>true</code> if the connect request succeeded
     *              <code>false</code> otherwise
     * @see         IFTPCallback#connect(int, String[])
     */
    public boolean connect(String host) {
        if (mState != FTPState.SERVICE_STARTED) {
            Log.e(TAG, "connect: Incorrect state: " + mState);
            return false;
        }
        return sendCommand(FTPService.WHAT_CONNECT, -1, FTPService.KEY_HOST, host);
    }

    /**
     * Connect to FTP server
     *
     * @param host  Host address
     * @param port  Host port
     * @return      <code>true</code> if the connect request succeeded
     *              <code>false</code> otherwise
     * @see         IFTPCallback#connect(int, String[])
     */
    public boolean connect(String host, int port) {
        if (mState != FTPState.SERVICE_STARTED) {
            Log.e(TAG, "connect: Incorrect state: " + mState);
            return false;
        }
        return sendCommand(FTPService.WHAT_CONNECT, port, FTPService.KEY_HOST, host);
    }

    /**
     * Disconnect from FTP server
     *
     * @return  <code>true</code> if the disconnect request succeeded
     *          <code>false</code> otherwise
     * @see     IFTPCallback#disconnect(int)
     */
    public boolean disconnect() {
        return sendCommand(FTPService.WHAT_DISCONNECT);
    }

    /**
     * Anonymous login
     *
     * @return  <code>true</code> if the login request succeeded
     *          <code>false</code> otherwise
     * @see     IFTPCallback#login(int)
     */
    public boolean login() {
        if (mState != FTPState.FTP_CONNECTED) {
            Log.e(TAG, "login: Incorrect state: " + mState);
            return false;
        }
        return sendCommand(FTPService.WHAT_LOGIN);
    }

    /**
     * Login
     *
     * @param username  User name
     * @param password  Password
     * @return          <code>true</code> if the login request succeeded
     *                  <code>false</code> otherwise
     * @see             IFTPCallback#login(int)
     */
    public boolean login(String username, String password) {
        if (mState != FTPState.FTP_CONNECTED) {
            Log.e(TAG, "login: Incorrect state: " + mState);
            return false;
        }
        return sendCommand(FTPService.WHAT_LOGIN, FTPService.KEY_USERNAME, username, FTPService.KEY_PASSWORD, password);
    }

    /**
     * Logout
     *
     * @return  <code>true</code> if the logout request succeeded
     *          <code>false</code> otherwise
     * @see     IFTPCallback#logout(int)
     */
    public boolean logout() {
        if (mState != FTPState.FTP_LOGGED_IN) {
            Log.e(TAG, "logout: Incorrect state: " + mState);
            return false;
        }
        return sendCommand(FTPService.WHAT_LOGOUT);
    }

    /**
     * Current directory
     *
     * @return  <code>true</code> if the current directory request succeeded
     *          <code>false</code> otherwise
     * @see     IFTPCallback#currentDirectory(int, String)
     */
    public boolean currentDirectory() {
        return sendCommand(FTPService.WHAT_CURRENT_DIRECTORY);
    }

    /**
     * Change directory
     *
     * @param path  Path
     * @return      <code>true</code> if the change directory request succeeded
     *              <code>false</code> otherwise
     * @see         IFTPCallback#changeDirectory(int)
     */
    public boolean changeDirectory(String path) {
        return sendCommand(FTPService.WHAT_CHANGE_DIRECTORY, FTPService.KEY_PATH, path);
    }

    /**
     * Change directory up
     *
     * @return  <code>true</code> if the change directory request succeeded
     *          <code>false</code> otherwise
     * @see     IFTPCallback#changeDirectoryUp(int)
     */
    public boolean changeDirectoryUp() {
        return sendCommand(FTPService.WHAT_CHANGE_DIRECTORY_UP);
    }

    /**
     * List files
     *
     * @param fileSpec  Path
     * @return          <code>true</code> if the list directory request succeeded
     *                  <code>false</code> otherwise
     * @see             IFTPCallback#list(int, FTPFile[])
     */
    public boolean list(String fileSpec) {
        return sendCommand(FTPService.WHAT_LIST, FTPService.KEY_FILESPEC, fileSpec);
    }

    /**
     * List files
     *
     * @return          <code>true</code> if the list directory request succeeded
     *                  <code>false</code> otherwise
     * @see             IFTPCallback#list(int, FTPFile[])
     */
    public boolean list() {
        return sendCommand(FTPService.WHAT_LIST);
    }

    /**
     * List names
     *
     * @return  <code>true</code> if the list names request succeeded
     *          <code>false</code> otherwise
     * @see     IFTPCallback#listNames(int, String[])
     */
    public boolean listNames() {
        return sendCommand(FTPService.WHAT_LIST_NAMES);
    }

    /**
     * Download file
     *
     * @param remoteFileName  FTP server file name
     * @param localFileName   Local file name including path
     * @return                <code>true</code> if the download request succeeded
     *                        <code>false</code> otherwise
     * @see                   IFTPCallback#download(int)
     */
    public boolean download(String remoteFileName, String localFileName) {
        return sendCommand(FTPService.WHAT_DOWNLOAD, FTPService.KEY_REMOTE_FILE_NAME, remoteFileName, FTPService.KEY_LOCAL_FILE_NAME, localFileName);
    }

    /**
     * Download file
     *
     * @param remoteFileName  FTP server file name
     * @param localFileName   Local file name including path
     * @param restartAt       Position to resume download at
     * @return                <code>true</code> if the download request succeeded
     *                        <code>false</code> otherwise
     * @see                   IFTPCallback#download(int)
     */
    public boolean download(String remoteFileName, String localFileName, long restartAt) {
        if (restartAt > 2_147_483_647) {
            Log.e(TAG, "download: unable to resume at " + restartAt);
            return false;
        }
        return sendCommand(FTPService.WHAT_DOWNLOAD, (int) restartAt, FTPService.KEY_REMOTE_FILE_NAME, remoteFileName, FTPService.KEY_LOCAL_FILE_NAME, localFileName);
    }

    /**
     * Abort file download
     *
     * @return  <code>true</code> if the download abort request succeeded
     *          <code>false</code> otherwise
     * @see     IFTPCallback#abortCurrentDataTransfer(int)
     */
    public boolean abortCurrentDataTransfer() {
        return sendCommand(FTPService.WHAT_ABORT_CURRENT_DATA_TRANSFER);
    }

    /**
     * Get FTP service state
     *
     * @return  <code>SERVICE_STOPPED</code> if the service is not started
     *          <code>SERVICE_STARTED</code> if the service has started
     *          <code>FTP_CONNECTED</code> if connected to an FTP server
     *          <code>FTP_LOGGED_IN</code> if the user logged in
     * @see     FTPState
     */
    public FTPState getState() {
        return mState;
    }

    void setState(FTPState state) {
        mState = state;
    }

    /**
     * Internal implementation
     */
    private static class FTPControllerHandler extends Handler {
        private final WeakReference<FTPController> mController;

        private FTPControllerHandler(FTPController controller) {
            mController = new WeakReference<>(controller);
        }

        private void handleCallback(Message msg) {
            FTPController controller = mController.get();
            if (controller == null) {
                return;
            }
            IFTPCallback callback = controller.mCallback;
            if (callback == null) {
                return;
            }
            int exception = msg.arg1;
            Bundle data = msg.getData();

            switch (msg.what) {
                case FTPService.WHAT_START:
                    callback.start();
                    break;
                case FTPService.WHAT_STOP:
                    callback.stop();
                    break;
                case FTPService.WHAT_CONNECT:
                    controller.setState(FTPController.FTPState.FTP_CONNECTED);
                    callback.connect(exception, data.getStringArray(FTPService.KEY_MESSAGE));
                    break;
                case FTPService.WHAT_DISCONNECT:
                    controller.setState(FTPController.FTPState.SERVICE_STARTED);
                    callback.disconnect(exception);
                    break;
                case FTPService.WHAT_LOGIN:
                    controller.setState(FTPController.FTPState.FTP_LOGGED_IN);
                    callback.login(exception);
                    break;
                case FTPService.WHAT_LOGOUT:
                    controller.setState(FTPController.FTPState.FTP_CONNECTED);
                    callback.logout(exception);
                    break;
                case FTPService.WHAT_CURRENT_DIRECTORY:
                    callback.currentDirectory(exception, data.getString(FTPService.KEY_PATH));
                    break;
                case FTPService.WHAT_CHANGE_DIRECTORY:
                    callback.changeDirectory(exception);
                    break;
                case FTPService.WHAT_CHANGE_DIRECTORY_UP:
                    callback.changeDirectoryUp(exception);
                    break;
                case FTPService.WHAT_LIST:
                    FTPFile[] files = null;
                    FTPFiles parcelable = data.getParcelable(FTPService.KEY_FILES);
                    if (parcelable != null) {
                        files = parcelable.getFiles();
                    } else {
                        Log.w(TAG, "handleCallback: KEY_FILES missing");
                    }
                    callback.list(exception, files);
                    break;
                case FTPService.WHAT_LIST_NAMES:
                    callback.listNames(exception, data.getStringArray(FTPService.KEY_NAMES));
                    break;
                case FTPService.WHAT_DOWNLOAD:
                    callback.download(exception);
                    break;
                case FTPService.WHAT_ABORT_CURRENT_DATA_TRANSFER:
                    callback.abortCurrentDataTransfer(exception);
                    break;
                default:
                    Log.w(TAG, "handleCallback: unsupported command:" + msg.what);
            }
            data.clear();
        }

        private void handleDownload(Message msg) {
            FTPController controller = mController.get();
            if (controller == null) {
                return;
            }
            IFTPDownload download = controller.mDownload;
            if (download == null) {
                return;
            }

            switch (msg.what) {
                case FTPService.WHAT_DOWNLOAD_STARTED:
                    download.started();
                    break;
                case FTPService.WHAT_DOWNLOAD_TRANSFERRED:
                    download.transferred(msg.arg2);
                    break;
                case FTPService.WHAT_DOWNLOAD_COMPLETED:
                    download.completed();
                    break;
                case FTPService.WHAT_DOWNLOAD_FAILED:
                    download.failed();
                    break;
                case FTPService.WHAT_DOWNLOAD_ABORTED:
                    download.aborted();
                    break;
                default:
                    Log.w(TAG, "handleDownload: unsupported command:" + msg.what);
            }
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.what < FTPService.WHAT_COMMAND_MAX) {
                handleCallback(msg);
            } else {
                handleDownload(msg);
            }
        }
    }

    /**
     * Internal implementation
     */
    private static class FTPConnection implements ServiceConnection {
        private final WeakReference<FTPController> mController;
        private FTPControllerHandler mReplyHandler;

        private FTPConnection(FTPController controller) {
            mController = new WeakReference<>(controller);
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            FTPController controller = mController.get();
            if (controller == null) {
                return;
            }
            mReplyHandler = new FTPControllerHandler(controller);
            controller.mLocalMessenger = new Messenger(mReplyHandler);
            controller.mRemoteMessenger = new Messenger(service);
            Message message = Message.obtain();
            message.what = FTPService.WHAT_START;
            message.replyTo = controller.mLocalMessenger;
            try {
                controller.mRemoteMessenger.send(message);
            } catch (RemoteException e) {
                Log.e(TAG, "onServiceConnected: failed to send message");
            }
            controller.setState(FTPState.SERVICE_STARTED);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            FTPController controller = mController.get();
            if (controller == null) {
                return;
            }
            Message message = Message.obtain();
            message.what = FTPService.WHAT_STOP;
            mReplyHandler.handleCallback(message);
            mReplyHandler = null;
            controller.mLocalMessenger = null;
            controller.mRemoteMessenger = null;
            controller.setState(FTPState.SERVICE_STOPPED);
        }
    }

    private boolean sendCommand(int what, String ... keyvals) {
        return sendCommand(what, 0, keyvals);
    }

    private boolean sendCommand(int what, int arg1, String ... keyvals) {
        return sendCommand(what, arg1, 0, keyvals);
    }

    private boolean sendCommand(int what, int arg1, int arg2, String ... keyvals) {
        if (mRemoteMessenger == null) {
            Log.w(TAG, "sendCommand: no message handler");
            return false;
        }
        Message message = Message.obtain();
        message.what = what;
        message.arg1 = arg1;
        message.arg2 = arg2;
        message.replyTo = mLocalMessenger;
        Bundle data = new Bundle();
        for (int i = 0; i < keyvals.length; i += 2) {
            String key = keyvals[i];
            String value = keyvals[i + 1];
            if ((key != null) && (value != null)) {
                data.putString(key, value);
            }
        }
        if (!data.isEmpty()) {
            message.setData(data);
        }
        try {
            mRemoteMessenger.send(message);
        } catch (RemoteException e) {
            Log.e(TAG , "sendCommand: failed to send message");
            return false;
        }
        return true;
    }
}
