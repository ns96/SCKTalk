package org.instras.sck;

import gnu.io.NRSerialPort;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: nathan
 * Date: 4/14/13
 * Time: 9:59 AM
 *
 * Simple class for connecting to ST-V3 with PDC2 firmware
 */
public class MiMTalk {
    private boolean testMode = false;
    public int minMotorRPM = 0;
    public int maxMotorRPM = 0;
    private JTextArea console;
    private NRSerialPort serial;
    private DataInputStream ins;
    private DataOutputStream outs;

    public void setConsole(JTextArea console) {
        this.console = console;
    }

    public void setTestMode(boolean test) {
        this.testMode = test;
    }

    /**
     * Method to connect to the serial port
     * @param portName
     */
    public void connect(String portName) {
        if(testMode) return;

        serial = new NRSerialPort(portName, 19200);
        serial.connect();

        ins = new DataInputStream(serial.getInputStream());
        outs = new DataOutputStream(serial.getOutputStream());
    }

    public String sendCommand(String command) {
        return sendCommand(command, true);
    }
    /**
     * Method to send a command to the ST-V3
     *
     * @param command
     * @param wfr wait for response
     * @return
     */
    public String sendCommand(String command, boolean wfr) {
        if(testMode) return "OK";

        try {
            command += "\r\n";
            outs.writeBytes(command);
            if(wfr) {
                return readResponse();
            } else {
                return "";
            }
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Method to read the results after a command has been sent
     * @return
     */
    public String readResponse() {
        if(testMode) return "TESTMODE";

        try {
            // wait 0.250 second so data can arrive from ST-V3
            Thread.sleep(500);

            StringBuilder sb = new StringBuilder(); //ins.readUTF();
            byte[] buffer = new byte[128];
            int len = -1;

            while ((len = ins.read(buffer)) > 0 ) {
                sb.append(new String(buffer,0,len));
            }

            String response = sb.toString().trim();
            //System.out.println("Response: " + response);
            return response;
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
    public String getVersion() {
        return sendCommand("GetVersion");
    }

    /**
     * Function to kick start the motor to prvent it from automatically shutting down
     * when trying to spin at rpms below 1000 rpms
     */
    public void kickStart() {
        try {
            sendCommand("SetPWM,200", false);
            Thread.sleep(50);
            System.out.println("Kick Started@200\n\n");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method to get a performance profile for a motor connected to S1
     *
     * @param increment
     * @return
     */
    public HashMap<Integer, Integer> getMotorProfile(int increment) throws Exception {
        HashMap<Integer, Integer> motorProfileMap = new HashMap<Integer, Integer>();

        print("PWM\tRPM");

        for(int i = 70; i <= 1000; i += increment) {
            sendCommand("SetPWM," + i);
            Thread.sleep(1000);

            Integer rpm = -1;
            String response = sendCommand("GetRPM");
            response = getResponseValue(response);

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

    public String getResponseValue(String response) {
        int idx1 = response.indexOf(",") + 1;
        int idx2 = response.indexOf(":");
        return response.substring(idx1, idx2);
    }

    /**
     * Method to close the serial port
     */
    public void close() {
        if(testMode) return;
        serial.disconnect();
    }



    /**
     * Main method. This is just to test library now
     * @param args
     */
    public static void main(String[] args) throws Exception {
        MiMTalk miMTalk = new MiMTalk();

        miMTalk.connect("COM8");

        String response = miMTalk.getVersion();

        if(response.contains("MIM")) {
            System.out.println("Connected to MIM\n");
            miMTalk.sendCommand("BLDCon");

            /*
            miMTalk.sendCommand("SetRPM,8000");

            for(int i = 1; i <= 1200; i += 1) {
                //miMTalk.sendCommand("SetRPM," + (4*i + 50));
                //miMTalk.sendCommand("SetPWM," + i);
                Thread.sleep(1000);
                String rpm = miMTalk.sendCommand("GetRPM");
                rpm = miMTalk.getResponseValue(rpm);
                System.out.println( i + ", " + rpm);
            }*/




            miMTalk.sendCommand("BLDCoff");
        }

        System.exit(0);
    }

}