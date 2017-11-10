/**
 * Simple class to communicate with the SCK device over bluetooth. Original code from
 * http://www.aviyehuda.com/blog/2010/01/08/connecting-to-bluetooth-devices-with-java
 * http://stackoverflow.com/questions/15343369/sending-a-string-via-bluetooth-from-a-pc-as-client-to-a-mobile-as-server
 *
 */
package org.instras.sck;

import javax.bluetooth.*;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.swing.*;
import java.io.*;
import java.util.HashMap;

public class SCKTalkBluetooth implements DiscoveryListener{
    
    private static Object lock = new Object();
    private static RemoteDevice sckDevice = null;
    private static String connectionURL;
    private PrintWriter printWriter;
    private BufferedReader bufferReader;
    private boolean connected = false;

    public int minMotorRPM = 0;
    public int maxMotorRPM = 0;
    private JTextArea console;

    public SCKTalkBluetooth() { }

    /**
     * Method to connect to the SCK. The paring code for the HC-06 adapter is 1234
     */
    public boolean connectToSCK() {
        System.out.println("Bluetooth Connection URL: " + connectionURL);

        //connect to the sck and send a line of text
        try {
            StreamConnection streamConnection = (StreamConnection) Connector.open(connectionURL);

            // get the output and input stream for writing and reading data
            OutputStream outStream = streamConnection.openOutputStream();
            printWriter = new PrintWriter(new OutputStreamWriter(outStream));

            InputStream inStream = streamConnection.openInputStream();
            bufferReader = new BufferedReader(new InputStreamReader(inStream));

            if(setModePC().equals("OK")) {
                connected = true;
                System.out.println("Connected to SCK-200");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        return connected;
    }

    /**
     * Method to send a command to the ST-V3
     *
     * @param command
     * @return
     */
    public String sendCommand(String command) {
        try {
            command += "\n\r";
            printWriter.write(command);
            printWriter.flush();
            return readResponse();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Method to read the results after a command has been sent
     * @return
     */
    public String readResponse() {
        try {
            // wait 1 second so data can arrive from ST-V3
            Thread.sleep(1000);

            return bufferReader.readLine();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return "ERROR";
    }

    /**
     * Method to set the ST-V3 to PC mode
     * @return
     */
    public String setModePC() {
        return sendCommand("MODE PC");
    }

    /**
     * Method to set the ST-V3 in normal mode
     * @return
     */
    public String setModeNormal() {
        connected = false;
        return sendCommand("MODE NORMAL");
    }

    /**
     * Method to get a performance profile for a motor connected to S1
     *
     * @param increment
     * @return
     */
    public HashMap<Integer, Integer> getMotorProfile(int increment, String pin) throws Exception {
        HashMap<Integer, Integer> motorProfileMap = new HashMap<Integer, Integer>();

        print("PWM\tRPM");

        for(int i = 1000; i <= 2000; i += increment) {
            sendCommand("SET " + pin + " " + i);
            Thread.sleep(8000);

            Integer rpm = -1;
            String response = sendCommand("GET RPM");
            try {
                rpm = new Integer(response);
            } catch(NumberFormatException nfe) {
                System.out.println("Invalid RPM data: " + response);
            }

            motorProfileMap.put(i, rpm);

            // set the min and max rpm
            if(rpm > 0 && minMotorRPM == 0) {
                minMotorRPM = rpm;
            } else if(rpm > maxMotorRPM) {
                maxMotorRPM = rpm;
            }

            print(i + "\t" + rpm);
        }

        return motorProfileMap;
    }

    /**
     * Method to print to the sout and the JTextArea console if it's not null
     * @param string
     */
    public void print(String string) {
        System.out.println(string);
        if(console != null) {
            console.append(string + "\n");
        }
    }

    @Override
    public void deviceDiscovered(RemoteDevice btDevice, DeviceClass arg1) {
        String name;
        try {
            name = btDevice.getFriendlyName(false);
        } catch (Exception e) {
            name = btDevice.getBluetoothAddress();
        }

        if(name.equals("HC-06")) {
            sckDevice = btDevice;
            System.out.println("SCK device found: " + name);
        }
    }

    @Override
    public void servicesDiscovered(int i, ServiceRecord[] serviceRecords) {
        //System.out.println("Services discovered ..." + serviceRecords[0]);

        if(serviceRecords !=null && serviceRecords.length > 0) {
            connectionURL = serviceRecords[0].getConnectionURL(0,false);
        }

        synchronized(lock){
            lock.notify();
        }
    }

    @Override
    public void inquiryCompleted(int arg0) {
        synchronized(lock){
            lock.notify();
        }
    }

    @Override
    public void serviceSearchCompleted(int arg0, int arg1) {
        synchronized (lock) {
            lock.notify();
        }
    }

    /**
     * Main method for testing outside of the gui environment
     *
     * @param args
     */
    public static void main(String[] args) {
        SCKTalkBluetooth sckTalkBluetooth =  new SCKTalkBluetooth();

        try{
            LocalDevice localDevice = LocalDevice.getLocalDevice();
            DiscoveryAgent agent = localDevice.getDiscoveryAgent();
            agent.startInquiry(DiscoveryAgent.GIAC, sckTalkBluetooth);

            try {
                synchronized(lock){
                    lock.wait();
                }
            }
            catch (InterruptedException e) {
                e.printStackTrace();
                return;
            }

            System.out.println("Device Inquiry Completed. ");

            // set the uuid for spp, make sure second param is true
            UUID[] uuidSet = new UUID[1];
            uuidSet[0] = new UUID("1101", true);

            System.out.println("\nSearching for service...");
            agent.searchServices(null, uuidSet, sckDevice, sckTalkBluetooth);

            try {
                synchronized(lock){
                    lock.wait();
                }
            }
            catch (InterruptedException e) {
                e.printStackTrace();
            }

            if(connectionURL == null){
                System.out.println("Device does not support Simple SPP Service.");
                System.exit(0);
            } else {
                if(sckTalkBluetooth.connectToSCK()) {
                    sckTalkBluetooth.getMotorProfile(50, "S1");
                    sckTalkBluetooth.setModeNormal();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
