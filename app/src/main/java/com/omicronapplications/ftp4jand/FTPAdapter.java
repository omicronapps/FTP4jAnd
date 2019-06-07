package com.omicronapplications.ftp4jand;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.omicronapplications.ftplib.FTPFile;

class FTPAdapter extends ArrayAdapter<FTPFile> implements AdapterView.OnItemClickListener {
    private final LayoutInflater mInflater;
    private final int mResource;
    private final IAdapterCallback mCallback;

    interface IAdapterCallback {
        boolean userChangeDirectory(String path);
        boolean userChangeDirectoryUp();
        void userDownload(String remoteFileName);
    }

    FTPAdapter(@NonNull Context context, IAdapterCallback callback) {
        super(context, 0);
        mInflater = LayoutInflater.from(context);
        mResource = R.layout.file_item;
        mCallback = callback;
    }

    void replace(FTPFile[] files) {
        clear();
        insert(null, 0);
        addAll(files);
        notifyDataSetChanged();
    }

    @Override
    public @NonNull View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
        final View view;
        final TextView left;
        final TextView right;
        final String left_text;
        final String right_text;

        if (convertView == null) {
            view = mInflater.inflate(mResource, parent, false);
        } else {
            view = convertView;
        }

        left = (TextView) view.findViewById(R.id.left_item);
        right = (TextView) view.findViewById(R.id.right_item);

        FTPFile file = getItem(position);
        if (position == 0) {
            left_text = "Up to higher level directory";
            right_text = "";
        } else if ((file != null) && file.getType() == FTPFile.TYPE_DIRECTORY) {
            left_text = file.getName() + "/";
            right_text = "";
        } else {
            left_text = file.getName();
            right_text = String.valueOf(file.getSize());
        }

        left.setText(left_text);
        right.setText(right_text);

        return view;
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        FTPFile file = getItem(position);
        if (position == 0) {
            mCallback.userChangeDirectoryUp();
        } else if ((file != null) && file.getType() == FTPFile.TYPE_DIRECTORY) {
            mCallback.userChangeDirectory(file.getName());
        } else {
            String remoteFileName = file.getName();
            mCallback.userDownload(remoteFileName);
        }
    }
}
