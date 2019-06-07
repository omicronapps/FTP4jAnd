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
import android.support.test.InstrumentationRegistry;
import android.support.test.rule.ServiceTestRule;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import it.sauronsoftware.ftp4j.FTPDataTransferListener;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FTPServiceTest {
    private static final int TEST_TIMEOUT = 5000; // ms

    // UCONN FTP Test site (http://ftp.uconn.edu/pcsecurity/windows/)
    private static final String TEST_ANONYMOUS_HOST = "ftp.uconn.edu";
    private static final int TEST_ANONYMOUS_PORT = 21;
    private static final String TEST_ANONYMOUS_USERNAME = "anonymous";
    private static final String TEST_ANONYMOUS_PASSWORD = "";
    private static final String TEST_ANONYMOUS_FOLDER = "pcsecurity";
    private static final String TEST_ANONYMOUS_SUBFOLDER = "windows";
    private static final String TEST_ANONYMOUS_REPLY = "ProFTPD 1.2.10 Server (ftp.uconn.edu) [137.99.26.52]";

    private static final String TEST_ROOT = "/";
    private static final int TEST_NO_PORT = -1;
    private static final String TEST_REMOTE = "LICENSE.txt";

    private static Messenger mRemoteMessenger;
    private static Messenger mLocalMessenger;
    private static Message mReplyMsg;
    private static CountDownLatch mMessageLatch;
    private static TestTransferListener mTransferListener;
    private boolean mDownloadStarted;
    private boolean mDownloadTransferred;
    private boolean mDownloadCompleted;
    private boolean mDownloadAborted;
    private boolean mDownloadFailed;

    private static class TestConnection implements ServiceConnection {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mRemoteMessenger = new Messenger(service);
            TestHandler handler = new TestHandler();
            mLocalMessenger = new Messenger(handler);
            mReplyMsg = new Message();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    }

    private class TestTransferListener implements FTPDataTransferListener {
        @Override
        public void started() {
            mDownloadStarted = true;
        }

        @Override
        public void transferred(int length) {
            mDownloadTransferred = true;
        }

        @Override
        public void completed() {
            mDownloadCompleted = true;
        }

        @Override
        public void aborted() {
            mDownloadAborted = true;
        }

        @Override
        public void failed() {
            mDownloadFailed = true;
        }
    }

    private void sendMessage(int what) {
        Message message = Message.obtain();
        message.what = what;
        message.replyTo = mLocalMessenger;

        mMessageLatch = new CountDownLatch(1);
        try {
            mRemoteMessenger.send(message);
        } catch (RemoteException e) {
            assertFalse(e.getMessage(), false);
        }
        try {
            boolean timedOut = mMessageLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
            assertTrue("await", timedOut);
        } catch (InterruptedException e) {
            assertFalse(e.getMessage(), false);
        }
    }

    private void sendMessage(int what, int arg1, int arg2, String... keyvals)
    {
        Message message = Message.obtain();
        message.what = what;
        message.arg1 = arg1;
        message.arg2 = arg2;

        Bundle data = new Bundle();
        for (int i = 0; i < keyvals.length; i += 2) {
            String key = keyvals[i];
            String value = keyvals[i + 1];
            if ((key != null) && (value != null)) {
                data.putString(key, value);
            }
        }
        assertFalse("isEmpty", data.isEmpty());
        message.setData(data);

        mMessageLatch = new CountDownLatch(1);
        try {
            mRemoteMessenger.send(message);
        } catch (RemoteException e) {
            assertFalse(e.getMessage(), false);
        }
        try {
            boolean timedOut = mMessageLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
            assertTrue("await", timedOut);
        } catch (InterruptedException e) {
            assertFalse(e.getMessage(), false);
        }
    }

    private static class TestHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            if (msg.what < FTPService.WHAT_DOWNLOAD_STARTED) {
                mReplyMsg.copyFrom(msg);
                mMessageLatch.countDown();
            } else {
                switch (msg.what) {
                    case FTPService.WHAT_DOWNLOAD_STARTED:
                        mTransferListener.started();
                        break;
                    case FTPService.WHAT_DOWNLOAD_TRANSFERRED:
                        mTransferListener.transferred(msg.arg2);
                        break;
                    case FTPService.WHAT_DOWNLOAD_COMPLETED:
                        mTransferListener.completed();
                        break;
                    case FTPService.WHAT_DOWNLOAD_FAILED:
                        mTransferListener.failed();
                        break;
                    case FTPService.WHAT_DOWNLOAD_ABORTED:
                        mTransferListener.aborted();
                        break;
                    default:
                        break;
                }
            }
        }
    }

    @ClassRule
    public static final ServiceTestRule mServiceRule = new ServiceTestRule();

    @BeforeClass
    public static void startService() throws TimeoutException {
        Intent intent = new Intent(InstrumentationRegistry.getTargetContext(), FTPService.class);
        TestConnection connection = new TestConnection();
        IBinder binder = mServiceRule.bindService(intent, connection, Context.BIND_AUTO_CREATE);
        assertNotEquals("IBinder", binder, null);
    }

    @Before
    public void setup() {
        sendMessage(FTPService.WHAT_START);
        assertEquals("what", mReplyMsg.what, FTPService.WHAT_START);
    }

    @After
    public void teardown() {
        sendMessage(FTPService.WHAT_STOP);
        assertEquals("what", mReplyMsg.what, FTPService.WHAT_STOP);
    }

    private String[] connect(String host, int port) {
        sendMessage(FTPService.WHAT_CONNECT, port, 0, FTPService.KEY_HOST, host);
        assertEquals("what", mReplyMsg.what, FTPService.WHAT_CONNECT);
        assertEquals(mReplyMsg.arg1, FTPService.EXCEPTION_OK);
        Bundle data = mReplyMsg.getData();
        String[] messages = data.getStringArray(FTPService.KEY_MESSAGE);
        assertNotNull("messages", messages);
        assertEquals(messages[0], TEST_ANONYMOUS_REPLY);
        return messages;
    }

    // Default port
    private String[] connect(String host) {
        return connect(host, TEST_NO_PORT);
    }

    private void login(String username, String password) {
        if ((username != null) && (password != null)) {
            sendMessage(FTPService.WHAT_LOGIN, 0, 0, FTPService.KEY_USERNAME, username, FTPService.KEY_PASSWORD, password);
        } else if (username != null) {
            sendMessage(FTPService.WHAT_LOGIN, 0, 0, FTPService.KEY_USERNAME, username);
        } else if (password != null) {
            sendMessage(FTPService.WHAT_LOGIN, 0, 0, FTPService.KEY_PASSWORD, password);
        } else {
            sendMessage(FTPService.WHAT_LOGIN);
        }
        assertEquals("what", mReplyMsg.what, FTPService.WHAT_LOGIN);
        assertEquals(mReplyMsg.arg1, FTPService.EXCEPTION_OK);
    }

    // Default username, default password
    private void login() {
        login(null, null);
    }

    private void logout() {
        sendMessage(FTPService.WHAT_LOGOUT);
        assertEquals("what", mReplyMsg.what, FTPService.WHAT_LOGOUT);
        assertEquals(mReplyMsg.arg1, FTPService.EXCEPTION_FTP);
    }

    private void disconnect() {
        sendMessage(FTPService.WHAT_DISCONNECT);
        assertEquals("what", mReplyMsg.what, FTPService.WHAT_DISCONNECT);
        assertEquals(mReplyMsg.arg1, FTPService.EXCEPTION_OK);
    }

    private String currentDirectory() {
        sendMessage(FTPService.WHAT_CURRENT_DIRECTORY);
        assertEquals("what", mReplyMsg.what, FTPService.WHAT_CURRENT_DIRECTORY);
        assertEquals(mReplyMsg.arg1, FTPService.EXCEPTION_OK);
        Bundle data = mReplyMsg.getData();
        String path = data.getString(FTPService.KEY_PATH);
        return path;
    }

    private void changeDirectory(String path) {
        if (path != null) {
            sendMessage(FTPService.WHAT_CHANGE_DIRECTORY, 0, 0, FTPService.KEY_PATH, path);
            assertEquals("what", mReplyMsg.what, FTPService.WHAT_CHANGE_DIRECTORY);
            assertEquals(mReplyMsg.arg1, FTPService.EXCEPTION_OK);
        } else {
            sendMessage(FTPService.WHAT_CHANGE_DIRECTORY_UP);
            assertEquals("what", mReplyMsg.what, FTPService.WHAT_CHANGE_DIRECTORY_UP);
            assertEquals(mReplyMsg.arg1, FTPService.EXCEPTION_OK);
        }
    }

    private void changeDirectory() {
        changeDirectory(null);
    }

    private FTPFile[] list(String fileSpec) {
        if (fileSpec != null) {
            sendMessage(FTPService.WHAT_LIST, 0, 0, FTPService.KEY_FILESPEC, fileSpec);
        } else {
            sendMessage(FTPService.WHAT_LIST);
        }
        assertEquals("what", mReplyMsg.what, FTPService.WHAT_LIST);
        assertEquals(mReplyMsg.arg1, FTPService.EXCEPTION_OK);
        Bundle data = mReplyMsg.getData();
        FTPFiles parcelable = data.getParcelable(FTPService.KEY_FILES);
        FTPFile[] files = parcelable.getFiles();
        return files;
    }

    private FTPFile[] list() {
        return list(null);
    }

    private String[] listNames() {
        sendMessage(FTPService.WHAT_LIST_NAMES);
        assertEquals("what", mReplyMsg.what, FTPService.WHAT_LIST_NAMES);
        assertEquals(mReplyMsg.arg1, FTPService.EXCEPTION_OK);
        Bundle data = mReplyMsg.getData();
        String[] names = data.getStringArray(FTPService.KEY_NAMES);
        return names;
    }

    private void download(int restartAt, String remoteFileName, String localFileName) {
        mTransferListener = new TestTransferListener();
        mDownloadStarted = false;
        mDownloadTransferred = false;
        mDownloadCompleted = false;
        mDownloadAborted = false;
        mDownloadFailed = false;
        sendMessage(FTPService.WHAT_DOWNLOAD, restartAt, 0, FTPService.KEY_REMOTE_FILE_NAME, remoteFileName, FTPService.KEY_LOCAL_FILE_NAME, localFileName);
        assertEquals("what", mReplyMsg.what, FTPService.WHAT_DOWNLOAD);
        assertEquals(mReplyMsg.arg1, FTPService.EXCEPTION_OK);
        assertTrue("started", mDownloadStarted);
//        assertTrue("transferred", mDownloadTransferred);
        assertTrue("completed", mDownloadCompleted);
        assertFalse("aborted", mDownloadAborted);
        assertFalse("failed", mDownloadFailed);
    }

    private void download(String remoteFileName, String localFileName) {
        download(-1, remoteFileName, localFileName);
    }

    private void abortCurrentDataTransfer() {
        sendMessage(FTPService.WHAT_ABORT_CURRENT_DATA_TRANSFER);
        assertEquals("what", mReplyMsg.what, FTPService.WHAT_ABORT_CURRENT_DATA_TRANSFER);
        assertEquals(mReplyMsg.arg1, FTPService.EXCEPTION_OK);
    }

    @Test
    public void testConnect() throws TimeoutException {
        // Connect - disconnect
        connect(TEST_ANONYMOUS_HOST, TEST_ANONYMOUS_PORT);
        disconnect();

        // Connect - disconnect, default port
        connect(TEST_ANONYMOUS_HOST);
        disconnect();
    }

    @Test
    public void testLogin() throws TimeoutException {
        connect(TEST_ANONYMOUS_HOST, TEST_ANONYMOUS_PORT);

        // Login - logout
        login(TEST_ANONYMOUS_USERNAME, TEST_ANONYMOUS_PASSWORD);
        logout();
        disconnect();

        // Login - logout, default username, default password
        connect(TEST_ANONYMOUS_HOST, TEST_ANONYMOUS_PORT);
        login();
        logout();

        disconnect();
    }

    @Test
    public void testDirectory() throws TimeoutException {
        connect(TEST_ANONYMOUS_HOST, TEST_ANONYMOUS_PORT);
        login();

        // Current directory
        String path = currentDirectory();
        assertEquals("KEY_PATH", path, TEST_ROOT);

        // Change directory
        changeDirectory(TEST_ANONYMOUS_FOLDER);
        // Current directory
        path = currentDirectory();
        assertEquals("KEY_PATH", path, TEST_ROOT + TEST_ANONYMOUS_FOLDER);

        // Change directory
        changeDirectory();
        // Current directory
        path = currentDirectory();
        assertEquals("KEY_PATH", path, TEST_ROOT);

        logout();
        disconnect();
    }

    @Test
    public void testList() throws TimeoutException {
        connect(TEST_ANONYMOUS_HOST, TEST_ANONYMOUS_PORT);
        login();

        FTPFile[] files = list(TEST_ROOT);
        String[] names = new String[files.length];
        for (int i = 0; i < files.length; i++) {
            names[i] = files[i].getName();
        }
        Arrays.sort(names);
        for (FTPFile file : files) {
            Log.d("FTPServiceTest", file.getLink() + ", " + file.getName() + ", " + file.getModifiedDate() + ", " + file.getSize() + ", " + file.getType());
        }
        assertEquals(names[0], "48_hour");
        assertEquals(names[1], "huskypc");
        assertEquals(names[2], "pcsecurity");
        assertEquals(names[3], "restricted");

        // Default filespec
        list();

        logout();
        disconnect();
    }

    @Test
    public void testListNames() throws TimeoutException {
        connect(TEST_ANONYMOUS_HOST, TEST_ANONYMOUS_PORT);
        login();

        String[] names = listNames();
        assertEquals(names[0], "pcsecurity");
        assertEquals(names[1], "restricted");
        assertEquals(names[2], "48_hour");
        assertEquals(names[3], "huskypc");
        for (String name : names) {
            Log.d("FTPServiceTest", name);
        }

        logout();
        disconnect();
    }

    @Test
    public void testDownload() throws TimeoutException {
        connect(TEST_ANONYMOUS_HOST, TEST_ANONYMOUS_PORT);
        login();

        // Change directory
        changeDirectory(TEST_ANONYMOUS_FOLDER);
        changeDirectory(TEST_ANONYMOUS_SUBFOLDER);

        String localPath = InstrumentationRegistry.getTargetContext().getFilesDir().getAbsolutePath();
        String localFileName = localPath + "/" + TEST_REMOTE;
        download(TEST_REMOTE, localFileName);
        abortCurrentDataTransfer();

        logout();
        disconnect();
    }
}
