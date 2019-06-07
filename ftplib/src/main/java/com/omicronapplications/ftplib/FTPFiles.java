package com.omicronapplications.ftplib;

import android.os.Parcel;
import android.os.Parcelable;

import java.util.Date;

class FTPFiles implements Parcelable {
    private final String names[];
    private final String links[];
    private final long modifiedDates[];
    private final long sizes[];
    private final int types[];

    FTPFiles(it.sauronsoftware.ftp4j.FTPFile[] files) {
        int length = files.length;
        names = new String[length];
        links = new String[length];
        modifiedDates = new long[length];
        sizes = new long[length];
        types = new int[length];

        for (int i = 0; i < length; i++) {
            names[i] = files[i].getName();
            links[i] = files[i].getLink();
            modifiedDates[i] = files[i].getModifiedDate().getTime();
            sizes[i] = files[i].getSize();
            types[i] = files[i].getType();
        }
    }

    FTPFile[] getFiles() {
        int length = names.length;
        com.omicronapplications.ftplib.FTPFile[] files = new FTPFile[length];

        for (int i = 0; i < length; i++) {
            files[i] = new FTPFile();
            files[i].setName(names[i]);
            files[i].setLink(links[i]);
            files[i].setModifiedDate(new Date(modifiedDates[i]));
            files[i].setSize(sizes[i]);
            files[i].setType(types[i]);
        }
        return files;
    }

    private FTPFiles(Parcel source) {
        names = new String[3];
        links = new String[3];
        modifiedDates = new long[3];
        sizes = new long[3];
        types = new int[3];

        source.readStringArray(names);
        source.readStringArray(links);
        source.readLongArray(modifiedDates);
        source.readLongArray(sizes);
        source.readIntArray(types);
    }

    private static class FTPCreator implements Parcelable.Creator<FTPFiles> {
        @Override
        public FTPFiles createFromParcel(Parcel source) {
            return new FTPFiles(source);
        }

        @Override
        public FTPFiles[] newArray(int size) {
            return new FTPFiles[size];
        }
    }

    public static final Parcelable.Creator<FTPFiles> CREATOR = new FTPCreator();

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeStringArray(names);
        dest.writeStringArray(links);
        dest.writeLongArray(modifiedDates);
        dest.writeLongArray(sizes);
        dest.writeIntArray(types);
    }
}
