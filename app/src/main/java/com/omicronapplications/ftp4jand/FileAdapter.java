package com.omicronapplications.ftp4jand;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import java.io.File;

class FileAdapter extends ArrayAdapter<File> {
    private final LayoutInflater mInflater;
    private final int mResource;

    FileAdapter(@NonNull Context context) {
        super(context, 0);
        mInflater = LayoutInflater.from(context);
        mResource = R.layout.file_item;
    }

    void replace(File path) {
        clear();
        addAll(path.listFiles());
        notifyDataSetChanged();
    }

    @Override
    public @NonNull
    View getView(int position, @Nullable View convertView, @NonNull ViewGroup parent) {
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

        File file = getItem(position);
        if (file != null) {
            left_text = file.getName();
        } else {
            left_text = "";
        }
        right_text = String.valueOf(file.length());

        left.setText(left_text);
        right.setText(right_text);

        return view;
    }
}
