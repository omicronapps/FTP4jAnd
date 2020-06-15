package com.omicronapplications.ftplib;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import android.util.Log;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(AndroidJUnit4.class)
public class FTPControllerTest {
    private static final int TEST_TIMEOUT = 5000; // ms

    // FTP Test site (https://dlptest.com/ftp-test/)
    private static final String TEST_HOST = "ftp.dlptest.com";
    private static final int TEST_PORT = 21;
    private static final String TEST_REPLY = "#########################################################";

    // UCONN FTP Test site (http://ftp.uconn.edu/pcsecurity/windows/)
    private static final String TEST_ANONYMOUS_HOST = "ftp.uconn.edu";
    private static final int TEST_ANONYMOUS_PORT = 21;
    private static final String TEST_ANONYMOUS_USERNAME = "anonymous";
    private static final String TEST_ANONYMOUS_PASSWORD = "";
    private static final String TEST_ANONYMOUS_FOLDER = "pcsecurity";
    private static final String TEST_ANONYMOUS_SUBFOLDER = "windows";
    private static final String TEST_ANONYMOUS_REPLY = "ProFTPD 1.2.10 Server (ftp.uconn.edu) [137.99.26.52]";

    private static final String TEST_ROOT = "/";
    private static final String TEST_REMOTE = "LICENSE.txt";

    private FTPController mController;
    private TestCallback mCallback;
    private TestListener mDownload;
    private CountDownLatch mMessageLatch;
    private int mException;
    private String mString;
    private String[] mStrings;
    private FTPFile[] mFiles;
    private boolean mDownloadStarted;
    private boolean mDownloadTransferred;
    private boolean mDownloadCompleted;
    private boolean mDownloadAborted;
    private boolean mDownloadFailed;

    private class TestCallback implements FTPController.IFTPCallback {
        @Override
        public void start() {
            mMessageLatch.countDown();
        }

        @Override
        public void stop() {
            mMessageLatch.countDown();
        }

        @Override
        public void connect(int exception, String[] message) {
            mException = exception;
            mStrings = message;
            mMessageLatch.countDown();
        }

        @Override
        public void disconnect(int exception) {
            mException = exception;
            mMessageLatch.countDown();
        }

        @Override
        public void login(int exception) {
            mException = exception;
            mMessageLatch.countDown();
        }

        @Override
        public void logout(int exception) {
            mException = exception;
            mMessageLatch.countDown();
        }

        @Override
        public void currentDirectory(int exception, String path) {
            mException = exception;
            mString = path;
            mMessageLatch.countDown();
        }

        @Override
        public void changeDirectory(int exception) {
            mException = exception;
            mMessageLatch.countDown();
        }

        @Override
        public void changeDirectoryUp(int exception) {
            mException = exception;
            mMessageLatch.countDown();
        }

        @Override
        public void list(int exception, FTPFile[] files) {
            mException = exception;
            mFiles = files;
            mMessageLatch.countDown();
        }

        @Override
        public void listNames(int exception, String[] names) {
            mException = exception;
            mStrings = names;
            mMessageLatch.countDown();
        }

        @Override
        public void download(int exception) {
            mException = exception;
            mMessageLatch.countDown();
        }

        @Override
        public void abortCurrentDataTransfer(int exception) {
            mException = exception;
            mMessageLatch.countDown();
        }
    }

    private class TestListener implements FTPController.IFTPDownload {
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

        @Override
        public void queue(int size) {}
    }

    @Before
    public void setup() {
        mCallback = new TestCallback();
        mDownload = new TestListener();
    }

    private void await() {
        try {
            boolean timedOut = mMessageLatch.await(TEST_TIMEOUT, TimeUnit.MILLISECONDS);
            assertTrue("await", timedOut);
        } catch (InterruptedException e) {
            assertFalse(e.getMessage(), false);
        }
    }

    private void start() {
        mController = new FTPController(InstrumentationRegistry.getInstrumentation().getTargetContext());
        mController.setCallbacks(mCallback, mDownload);
        mMessageLatch = new CountDownLatch(1);
        assertTrue("start", mController.start());
        await();
    }

    private void stop() {
        mController.stop();
        mController = null;
    }

    private String connect(String host, int port, String expected) {
        mStrings = null;
        mMessageLatch = new CountDownLatch(1);
        assertTrue("connect", mController.connect(host, port));
        await();
        assertEquals("exception", mException, FTPService.EXCEPTION_OK);
        assertEquals("string", mStrings[0], expected);
        return mStrings[0];
    }

    private String connect(String host, String expected) {
        mStrings = null;
        mMessageLatch = new CountDownLatch(1);
        assertTrue("connect", mController.connect(host));
        await();
        assertEquals("exception", mException, FTPService.EXCEPTION_OK);
        assertEquals("string", mStrings[0], expected);
        return mStrings[0];
    }

    private void disconnect() {
        mMessageLatch = new CountDownLatch(1);
        assertTrue("disconnect", mController.disconnect());
        await();
        assertEquals("exception", mException, FTPService.EXCEPTION_OK);
    }

    private void login(String username, String password) {
        mMessageLatch = new CountDownLatch(1);
        assertTrue("login", mController.login(username, password));
        await();
        assertEquals("exception", mException, FTPService.EXCEPTION_OK);
    }

    // Default username, default password
    private void login() {
        login(null, null);
    }

    private void logout() {
        mMessageLatch = new CountDownLatch(1);
        assertTrue("logout", mController.logout());
        await();
        assertEquals("exception", mException, FTPService.EXCEPTION_FTP);
    }

    private String currentDirectory() {
        mString = null;
        mMessageLatch = new CountDownLatch(1);
        assertTrue("currentDirectory", mController.currentDirectory());
        await();
        assertEquals("exception", mException, FTPService.EXCEPTION_OK);
        return mString;
    }

    private void changeDirectory(String path) {
        mMessageLatch = new CountDownLatch(1);
        assertTrue("changeDirectory", mController.changeDirectory(path));
        await();
        assertEquals("exception", mException, FTPService.EXCEPTION_OK);
    }

    private void changeDirectoryUp() {
        mMessageLatch = new CountDownLatch(1);
        assertTrue("changeDirectoryUp", mController.changeDirectoryUp());
        await();
        assertEquals("exception", mException, FTPService.EXCEPTION_OK);
    }

    private FTPFile[] list(String fileSpec) {
        mFiles = null;
        mMessageLatch = new CountDownLatch(1);
        assertTrue("list", mController.list(fileSpec));
        await();
        assertEquals("exception", mException, FTPService.EXCEPTION_OK);
        return mFiles;
    }

    private FTPFile[] list() {
        mFiles = null;
        mMessageLatch = new CountDownLatch(1);
        assertTrue("list", mController.list());
        await();
        assertEquals("exception", mException, FTPService.EXCEPTION_OK);
        return mFiles;
    }

    private String[] listNames() {
        mStrings = null;
        mMessageLatch = new CountDownLatch(1);
        assertTrue("listNames", mController.listNames());
        await();
        assertEquals("exception", mException, FTPService.EXCEPTION_OK);
        return mStrings;
    }

    private void download(String remoteFileName, String localFileName) {
        mMessageLatch = new CountDownLatch(1);
        mDownloadStarted = false;
        mDownloadTransferred = false;
        mDownloadCompleted = false;
        mDownloadAborted = false;
        mDownloadFailed = false;
        assertTrue("download", mController.download(remoteFileName, localFileName));
        await();
        assertEquals("exception", mException, FTPService.EXCEPTION_OK);
        assertTrue("started", mDownloadStarted);
//        assertTrue("transferred", mDownloadTransferred);
        assertTrue("completed", mDownloadCompleted);
        assertFalse("aborted", mDownloadAborted);
        assertFalse("failed", mDownloadFailed);
    }

    private void abortCurrentDataTransfer() throws TimeoutException {
        mMessageLatch = new CountDownLatch(1);
        assertTrue("abortDownload", mController.abortCurrentDataTransfer());
        await();
        assertEquals("exception", mException, FTPService.EXCEPTION_OK);
    }

    @Test
    public void testStartStop() throws TimeoutException {
        start();
        stop();
    }

    @Test
    public void testConnect() throws TimeoutException {
        start();

        // Connect - disconnect
        connect(TEST_HOST, TEST_PORT, TEST_REPLY);
        disconnect();

        // Connect - disconnect, default port
        connect(TEST_HOST, TEST_REPLY);
        disconnect();

        stop();
    }

    @Test
    public void testLogin() throws TimeoutException {
        start();
        connect(TEST_ANONYMOUS_HOST, TEST_ANONYMOUS_PORT, TEST_ANONYMOUS_REPLY);

        // Login - logout
        login(TEST_ANONYMOUS_USERNAME, TEST_ANONYMOUS_PASSWORD);
        logout();
        disconnect();

        // Login - logout, default username, default password
        connect(TEST_ANONYMOUS_HOST, TEST_ANONYMOUS_PORT, TEST_ANONYMOUS_REPLY);
        login();
        logout();

        disconnect();
        stop();
    }

    @Test
    public void testDirectory() throws TimeoutException {
        start();
        connect(TEST_ANONYMOUS_HOST, TEST_ANONYMOUS_PORT, TEST_ANONYMOUS_REPLY);
        login();

        // Current directory
        String path = currentDirectory();

        // Change directory
        changeDirectory(TEST_ANONYMOUS_FOLDER);
        // Current directory
        path = currentDirectory();
        assertEquals("currentDirectory", path, TEST_ROOT + TEST_ANONYMOUS_FOLDER);

        // Change directory
        changeDirectoryUp();
        // Current directory
        path = currentDirectory();
        assertEquals("currentDirectory", path, TEST_ROOT);

        logout();
        disconnect();
        stop();
    }

    @Test
    public void testList() throws TimeoutException {
        start();
        connect(TEST_ANONYMOUS_HOST, TEST_ANONYMOUS_PORT, TEST_ANONYMOUS_REPLY);
        login();

        FTPFile[] files = list(TEST_ROOT);
        assertEquals(files[0].getName(), "48_hour");
        assertEquals(files[1].getName(), "huskypc");
        assertEquals(files[2].getName(), "pcsecurity");
        assertEquals(files[3].getName(), "restricted");
        for (FTPFile file : files) {
            Log.d("FTPControllerTest", file.getLink() + ", " + file.getName() + ", " + file.getModifiedDate() + ", " + file.getSize() + ", " + file.getType());
        }

        // Default filespec
        list();

        logout();
        disconnect();
        stop();
    }

    @Test
    public void testListNames() throws TimeoutException {
        start();
        connect(TEST_ANONYMOUS_HOST, TEST_ANONYMOUS_PORT, TEST_ANONYMOUS_REPLY);
        login();

        String[] names = listNames();
        assertEquals(names[0], "pcsecurity");
        assertEquals(names[1], "restricted");
        assertEquals(names[2], "48_hour");
        assertEquals(names[3], "huskypc");
        for (String name : names) {
            Log.d("FTPControllerTest", name);
        }

        logout();
        disconnect();
        stop();
    }

    @Test
    public void testDownload() throws  TimeoutException {
        start();
        connect(TEST_ANONYMOUS_HOST, TEST_ANONYMOUS_PORT, TEST_ANONYMOUS_REPLY);
        login();

        // Change directory
        changeDirectory(TEST_ANONYMOUS_FOLDER);
        changeDirectory(TEST_ANONYMOUS_SUBFOLDER);

        String localPath = InstrumentationRegistry.getInstrumentation().getTargetContext().getFilesDir().getAbsolutePath();
        String localFileName = localPath + "/" + TEST_REMOTE;
        download(TEST_REMOTE, localFileName);
        abortCurrentDataTransfer();

        logout();
        disconnect();
        stop();
    }
}
