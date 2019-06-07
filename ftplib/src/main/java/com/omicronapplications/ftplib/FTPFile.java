package com.omicronapplications.ftplib;

import java.util.Date;

public class FTPFile {
    public static final int TYPE_FILE = 0;
    public static final int TYPE_DIRECTORY = 1;
    public static final int TYPE_LINK = 2;

    private String name = null;
    private String link = null;
    private Date modifiedDate = null;
    private long size = -1;
    private int type;

    public Date getModifiedDate() {
        return modifiedDate;
    }

    public void setModifiedDate(Date modifiedDate) {
        this.modifiedDate = modifiedDate;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
