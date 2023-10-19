# FTP4jAnd

> FTP client for Android

## Description

FTP4jAnd is an FTP client library for Android, based on the ftp4j Java library. Control of ftp4j is implemented as an application service, with a helper class provided for service access. Two separate interfaces are also provided for service and download status callbacks.

FTP4jAnd is used in [AndPlug](https://play.google.com/store/apps/details?id=com.omicronapplications.andplug) music player application for Android devices.

## Table of Contents

- [Prerequisites](#prerequisites)
- [Installation](#installation)
- [Testing](#testing)
- [Usage](#usage)
- [Example](#example)
- [Credits](#credits)
- [Release History](#release-history)
- [License](#license)

## Prerequisites

- [Android 4.0.3](https://developer.android.com/about/versions/android-4.0.3) (API Level: 15) or later (`ICE_CREAM_SANDWICH_MR1`)
- [Android Gradle Plugin](https://developer.android.com/studio/releases/gradle-plugin) 7.2.2 or later (`gradle:7.2.2`)
- [ftp4j](http://www.sauronsoftware.it/projects/ftp4j) Version 1.7.2

## Installation

1. Check out a local copy of FTP4jAnd repository
2. Download ftp4j and extract JAR package
3. Build library with Gradle, using Android Studio or directly from the command line

Setup steps:
```
$ git clone https://github.com/omicronapps/FTP4jAnd.git
$ cd FTP4jAnd/ftplib/libs/
$ unzip -j ftp4j-1.7.2.zip ftp4j-1.7.2/ftp4j-1.7.2.jar
```

## Testing

FTP4jAnd includes both instrumented unit tests and a simple test application.

### Instrumented tests

Located under `ftplib/src/androidTest`.

These tests are run on a hardware device or emulator, and verifies correct operation of the `FTPService` and `FTPController` implementations and their usage of the ftp4j library.

### Test application

Located under `app/src/main`.

## Usage

FTP4jAnd is controlled through the following class and interfaces:
- `FTPController` - service management class 
- `FTPController.IFTPCallback` - service callback interface
- `FTPController.IFTPDownload` - download status callback interface

## Example

Implement `FTPController.IFTPCallback` callback interface to receive command completion callbacks:

```
import com.omicronapplications.ftplib.FTPController;

class FTPCallback implements FTPController.IFTPCallback {
...
}
```

Implement `FTPController.IFTPDownload` callback interface to receive download status updates:

```
import com.omicronapplications.ftplib.FTPController;

class FTPDownload implements FTPController.IFTPDownload {
...
}
```

Create callback instances and controller, and start FTP client service:

```
FTPCallback callback = new FTPCallback();
FTPDownload download = new FTPDownload();
FTPController controller = new FTPController(getApplicationContext());
controller.setCallbacks(callback, download);
controller.start();
```

Once service is running, connect to FTP server:

```
class FTPCallback implements FTPController.IFTPCallback {
    @Override
    public void start() {
        controller.connect("ftp.server.name", 21);
    }
}
```

Log in user after a connection has been established:

```
class FTPCallback implements FTPController.IFTPCallback {
    @Override
    public void connect(int exception, String[] message) {
        controller.login("anonymous", "");
    }
}
```

After the used has been logged in, change the remote directory:

```
class FTPCallback implements FTPController.IFTPCallback {
    @Override
    public void login(int exception) {
        controller.changeDirectory("...");
    }
}
```

On entering new remote directory, request list of files:

```
class FTPCallback implements FTPController.IFTPCallback {
    @Override
    public void changeDirectory(int exception) {
        controller.list();
    }
}
```

List files in current directory:

```
class FTPCallback implements FTPController.IFTPCallback {
    @Override
    public void list(int exception, FTPFile[] files) {
        for (FTPFile file : files) {
            // List files
        }
    }
}
```

Download file from FTP server to local storage:

```
String remoteFileName = "...";
String localFileName = "...";
controller.download(remoteFileName, localFileName);
```

Wait for download to complete:

```
class FTPDownload implements FTPController.IFTPDownload {
    @Override
    public void completed() {
    }
}
```

Disconnect from FTP server:

```
controller.disconnect();
```

After disconnecting from FTP server, stop FTP client service:

```
class FTPCallback implements FTPController.IFTPCallback {
    @Override
    public void disconnect(int exception) {
        controller.stop();
    }
}
```

## Credits

Copyright (C) 2019-2023 [Fredrik Claesson](https://www.omicronapplications.com/)

## Release History

- 1.0.0 Initial release
- 1.1.0 Migrated to AndroidX
- 1.2.0 FTP service refactored, support for multiple download requests
- 1.3.0 Minor bugfix
- 1.4.0 Change to Apache License Version 2.0

## License

FTP4jAnd is licensed under [Apache License Version 2.0](LICENSE).
