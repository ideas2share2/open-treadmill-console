package android.hardware.usb; public class UsbInterface {public int getEndpointCount(){return 2;} public UsbEndpoint getEndpoint(int i){return new UsbEndpoint(i==0?128:0);} }
