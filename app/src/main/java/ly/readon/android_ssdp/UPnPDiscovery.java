package ly.readon.android_ssdp;

import android.content.Context;
import android.net.wifi.WifiManager;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * @author Bernd Verst(@berndverst)
 * @author Jonathan Fether (@jfether)
 */

public class UPnPDiscovery
{
    public static String ST_ALL = "ssdp:all";
    public static String ST_ROOTDEVICE = "upnp:rootdevice";
    public static String ST_AVTRANSPORT = "urn:schemas-upnp-org:service:AVTransport:1";

    public static Future<String[]> discoverDevicesAsync(Context ctx, String serviceType) {
        ExecutorService executor = Executors.newSingleThreadExecutor();

        return executor.submit(() -> {
            HashSet<String> addresses = new HashSet<>();
            WifiManager wifi = (WifiManager)ctx.getSystemService(Context.WIFI_SERVICE);

            if(wifi != null) {

                WifiManager.MulticastLock lock = wifi.createMulticastLock("UPnP Discovery Lock");
                lock.acquire();

                DatagramSocket socket;

                try {
                    InetAddress group = InetAddress.getByName("239.255.255.250");
                    int port = 1900;
                    String query =
                            "M-SEARCH * HTTP/1.1\r\n" +
                                    "Host: 239.255.255.250:1900\r\n"+
                                    "ST: " + serviceType + "\r\n"+
                                    "Man: \"ssdp:discover\"\r\n"+
                                    "MX: 3\r\n"+
                                    "\r\n";
                    socket = new DatagramSocket();
                    socket.setSoTimeout(300);

                    DatagramPacket dgram = new DatagramPacket(query.getBytes(), query.length(),
                            group, port);
                    socket.send(dgram);

                    long time = System.currentTimeMillis();
                    long curTime = System.currentTimeMillis();

                    // Let's consider all the responses we can get in 1 second
                    while (curTime - time < 1000) {

                        byte[] receiveData = new byte[256];
                        DatagramPacket p = new DatagramPacket(receiveData, receiveData.length);
                        try {
                            socket.receive(p);

                            String s = new String(p.getData(), 0, p.getLength());
                            if (s.split("[\\r\\n]+")[0].toUpperCase().startsWith("HTTP/1.1 200")) {
                                addresses.add(p.getAddress().getHostAddress());
                            }
                        }
                        catch (Exception e) {
                            e.printStackTrace();
                        }
                        curTime = System.currentTimeMillis();
                    }
                    socket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
                lock.release();
            }
            return addresses.toArray(new String[0]);
        });
    }

    public static String[] discoverDevices(Context ctx, String serviceType ) {
        try {
            return discoverDevicesAsync(ctx, serviceType).get(2000, TimeUnit.MILLISECONDS);
        } catch (InterruptedException | ExecutionException | TimeoutException e) {
            return null;
        }
    }

    public static String[] discoverAllDevices(Context ctx) {
        return discoverDevices(ctx, ST_ALL);
    }

    public static String[] discoverRootDevices(Context ctx) {
        return discoverDevices(ctx, ST_ROOTDEVICE);
    }

    public static String[] discoverDevices(Context ctx) {
        // Compatibility API for original Sonos application
        return discoverDevices(ctx, ST_AVTRANSPORT);
    }
}
