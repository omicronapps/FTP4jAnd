package com.omicronapplications.ftplib;

import android.os.Parcel;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import it.sauronsoftware.ftp4j.FTPFile;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Date;
import java.util.concurrent.TimeoutException;

import static org.junit.Assert.assertEquals;

@RunWith(AndroidJUnit4.class)
public class FTPFilesTest {
    private static int TEST_COUNT = 3;

    private FTPFile[] mExpected;

    @Before
    public void setup() {
        String name = "TestName";
        String link = "TestLink";
        long modifiedDate = 123;
        long size = 1024;
        int type = FTPFile.TYPE_FILE;

        mExpected = new FTPFile[TEST_COUNT];
        for (int i = 0; i < TEST_COUNT; i++) {
            mExpected[i] = new FTPFile();
            mExpected[i].setName(name + i);
            mExpected[i].setLink(link + i);
            mExpected[i].setModifiedDate(new Date(modifiedDate + i));
            mExpected[i].setSize(size + i);
            mExpected[i].setType(type + i);
        }
    }

    @Test
    public void testParcelable() throws TimeoutException {
        FTPFiles parcelable = new FTPFiles(mExpected);
        Parcel parcel = Parcel.obtain();
        parcelable.writeToParcel(parcel, 0);
        parcel.setDataPosition(0);
        FTPFiles created = FTPFiles.CREATOR.createFromParcel(parcel);
        com.omicronapplications.ftplib.FTPFile[] result = created.getFiles();

        assertEquals("length", mExpected.length, result.length);
        for (int i = 0; i < mExpected.length; i++) {
            assertEquals("name", mExpected[i].getName(), result[i].getName());
            assertEquals("link", mExpected[i].getLink(), result[i].getLink());
            assertEquals("modifiedDate", mExpected[i].getModifiedDate(), result[i].getModifiedDate());
            assertEquals("size", mExpected[i].getSize(), result[i].getSize());
            assertEquals("type", mExpected[i].getType(), result[i].getType());
        }
    }
}
