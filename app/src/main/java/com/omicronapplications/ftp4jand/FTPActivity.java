package com.omicronapplications.ftp4jand;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.omicronapplications.ftplib.FTPController;
import com.omicronapplications.ftplib.FTPFile;

import java.io.File;

public class FTPActivity extends Activity implements FTPAdapter.IAdapterCallback {
    private static final String TAG = "FTPActivity";
    private FTPController mController;
    private FTPCallback mCallback;
    private FTPDownload mDownload;
    private Button mConnectButton;
    private Button mDisconnectButton;
    private Button mLoginButton;
    private Button mLogoutButton;
    private EditText mServerEdit;
    private EditText mPortEdit;
    private EditText mUserEdit;
    private EditText mPasswordEdit;
    private TextView mRemotePathView;
    private ListView mRemoteList;
    private TextView mLocalPathView;
    private ListView mLocalList;
    private TextView mServiceStatus;
    private boolean mControllerStarted;
    private FTPAdapter mFTPadapter;
    private FileAdapter mFileAdapter;
    private File mLocalRoot;

    @Override
    public boolean userChangeDirectory(String path) {
        return mController.changeDirectory(path);
    }

    @Override
    public boolean userChangeDirectoryUp() {
        return mController.changeDirectoryUp();
    }

    @Override
    public void userDownload(String remoteFileName) {
        String localFileName = mLocalRoot + "/" + remoteFileName;
        Toast.makeText(getApplicationContext(), remoteFileName, Toast.LENGTH_SHORT).show();
        mController.download(remoteFileName, localFileName);
    }

    private class FTPCallback implements FTPController.IFTPCallback {
        @Override
        public void start() {
            mControllerStarted = true;
            mConnectButton.setEnabled(mControllerStarted);
            mDisconnectButton.setEnabled(mControllerStarted);
        }

        @Override
        public void stop() {
            mControllerStarted = false;
            mConnectButton.setEnabled(mControllerStarted);
            mDisconnectButton.setEnabled(mControllerStarted);
        }

        @Override
        public void connect(int exception, String[] message) {
            mServiceStatus.setText("connect: " + exception + ", " + (message != null ? message[0] : "null"));
        }

        @Override
        public void disconnect(int exception) {
        }

        @Override
        public void login(int exception) {
            mServiceStatus.setText("login: " + exception);
            mController.list();
            mController.currentDirectory();
        }

        @Override
        public void logout(int exception) {
            mServiceStatus.setText("logout: " + exception);
        }

        @Override
        public void currentDirectory(int exception, String path) {
            mServiceStatus.setText("currentDirectory: " + exception + ", " + path);
            mRemotePathView.setText(path);
        }

        @Override
        public void changeDirectory(int exception) {
            mServiceStatus.setText("changeDirectory: " + exception);
            mController.list();
            mController.currentDirectory();
        }

        @Override
        public void changeDirectoryUp(int exception) {
            mServiceStatus.setText("changeDirectoryUp: " + exception);
            mController.list();
            mController.currentDirectory();
        }

        @Override
        public void list(int exception, FTPFile[] files) {
            mServiceStatus.setText("list: " + exception + ", " + files);
            if (files != null) {
                for (FTPFile file : files) {
                    Log.d(TAG, "list: " + file.toString() + ", " + file.getName());
                }
                mFTPadapter.replace(files);
            }
        }

        @Override
        public void listNames(int exception, String[] names) {
            mServiceStatus.setText("listNames: " + exception + ", " + names);
            if (names != null) {
                for (String name : names) {
                    Log.d(TAG, "listNames: " + name);
                }
            }
        }

        @Override
        public void download(int exception) {
            mServiceStatus.setText("download: " + exception);
        }

        @Override
        public void abortCurrentDataTransfer(int exception) {
            mServiceStatus.setText("abortCurrentDataTransfer: " + exception);
        }
    }

    private class FTPDownload implements FTPController.IFTPDownload {
        @Override
        public void started() {
            Toast.makeText(getApplicationContext(), "started", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void transferred(int length) {
            Toast.makeText(getApplicationContext(), "transferred: " + length, Toast.LENGTH_SHORT).show();
        }

        @Override
        public void completed() {
            Toast.makeText(getApplicationContext(), "completed", Toast.LENGTH_SHORT).show();
            mFileAdapter.replace(mLocalRoot);
        }

        @Override
        public void aborted() {
            Toast.makeText(getApplicationContext(), "aborted", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void failed() {
            Toast.makeText(getApplicationContext(), "failed", Toast.LENGTH_SHORT).show();
        }

        @Override
        public void queue(int size) {
            Toast.makeText(getApplicationContext(), "queue: " + size, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mCallback = new FTPCallback();
        mDownload = new FTPDownload();
        mController = new FTPController(getApplicationContext());
        mController.setCallbacks(mCallback, mDownload);
        mController.start();
        mLocalRoot = getExternalFilesDir(null);

        mConnectButton = (Button) findViewById(R.id.connect_button);
        mConnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController != null) {
                    String host = mServerEdit.getText().toString();
                    int port = Integer.valueOf(mPortEdit.getText().toString());
                    mController.connect(host, port);
                }
            }
        });

        mDisconnectButton = (Button) findViewById(R.id.disconnect_button);
        mDisconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController != null) {
                    mController.disconnect();
                }
                mFTPadapter.clear();
                mFTPadapter.notifyDataSetChanged();
            }
        });

        mLoginButton = (Button) findViewById(R.id.login_button);
        mLoginButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController != null) {
                    String username = mUserEdit.getText().toString();
                    String password = mPasswordEdit.getText().toString();
                    mController.login(username, password);
                }
            }
        });

        mLogoutButton = (Button) findViewById(R.id.logout_button);
        mLogoutButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (mController != null) {
                    mController.logout();
                }
            }
        });

        mServerEdit = (EditText) findViewById(R.id.ftp_server);
        mPortEdit = (EditText) findViewById(R.id.ftp_port);
        mUserEdit = (EditText) findViewById(R.id.user_name);
        mPasswordEdit = (EditText) findViewById(R.id.password);
        mRemotePathView = (TextView) findViewById(R.id.remote_path);

        mFTPadapter = new FTPAdapter(getApplicationContext(), this);
        mRemoteList = (ListView) findViewById(R.id.ftp_list);
        mRemoteList.setAdapter(mFTPadapter);
        mRemoteList.setOnItemClickListener(mFTPadapter);

        mLocalPathView = (TextView) findViewById(R.id.local_path);
        mLocalPathView.setText(mLocalRoot.getAbsolutePath());

        mFileAdapter = new FileAdapter(getApplicationContext());
        mFileAdapter.replace(mLocalRoot);
        mLocalList = (ListView) findViewById(R.id.file_list);
        mLocalList.setAdapter(mFileAdapter);
        mLocalList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                Log.d(TAG, "onItemClick: " + parent + ", " + view + ", " + position + ", " + id);
            }
        });

        mServiceStatus = (TextView) findViewById(R.id.service_status);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mController != null) {
            mController.stop();
        }
        mCallback = null;
        mDownload = null;
        mController = null;
        mLocalRoot = null;
    }
}
