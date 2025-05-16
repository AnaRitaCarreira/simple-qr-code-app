**QR Code Reader and Creator Offline**
=====================================

**Introduction**
--------------

This repository aims to create a QR Code reader and creator that can be executed on different platforms (Windows, macOS, Linux, Android and iOS) without the need for internet connection or installation.
Desirably, it should be POSIX compliant.

**STATUS**
------------
Desktop version working with plain text.

**Requirements**
------------

* Hardware requirements: Basic computer or mobile device resources

**Features**
------------

* Create custom QR Codes - Check https://github.com/zxing/zxing/wiki/Barcode-Contents
    * Plain text
    * URL
    * Geo Location
    * Calendar data
    * Email Address
    * Phone number
    * SMS
    * Contact Data
* Read existing QR Codes with camera

**Getting Started**
------------------

1. Clone the repository to your computer.
2. Create a new branch for your changes.
3. Start developing the project.
4. Implement the required features.
5. Test and refine the application.

**To-Do List**
--------------

* Implement support for different OS
* Create a detailed documentation for the application
* Improve the application security

**Architecture**
--------------

Work in progress

**Contributing**
--------------

If you'd like to contribute to this project, please follow these steps:

* Clone the repository to your computer.
* Create a new branch for your changes.
* Make the necessary changes.
* Commit the changes to the repository.
* Open a pull request for the changes to be reviewed and approved.


**TO DO**
-------------

* Create executables for Linux and Mac
* pyinstaller --onefile --windowed qr-code-app.py.py