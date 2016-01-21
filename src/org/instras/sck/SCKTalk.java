package org.instras.sck;

import gnu.io.NRSerialPort;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: nathan
 * Date: 4/14/13
 * Time: 9:59 AM
 *
 * Simple class for connecting to ST-V3 with PDC2 firmware
 */
public class SCKTalk {
    public int minMotorRPM = 0;
    public int maxMotorRPM = 0;
    private JTextArea console;
    private NRSerialPort serial;
    private DataInputStream ins;
    private DataOutputStream outs;

    public void setConsole(JTextArea console) {
        this.console = console;
    }

    /**
     * Method to connect to the serial port
     * @param portName
     */
    public void connect(String portName) {
        serial = new NRSerialPort(portName, 9600);
        serial.connect();

        ins = new DataInputStream(serial.getInputStream());
        outs = new DataOutputStream(serial.getOutputStream());
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
            outs.writeBytes(command);
            return readResponse();
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
        try {
            // wait 1 second so data can arrive from ST-V3
            Thread.sleep(1000);

            StringBuilder sb = new StringBuilder(); //ins.readUTF();
            byte[] buffer = new byte[128];
            int len = -1;

            while ((len = ins.read(buffer)) > 0 ) {
                sb.append(new String(buffer,0,len));
            }

            return sb.toString().trim();
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
            Thread.sleep(4000);

            Integer rpm = new Integer(sendCommand("GET RPM"));
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

    /**
     * Method to close the serial port
     */
    public void close() {
        setModeNormal();
        serial.disconnect();
    }

    /**
     * Main method. This is just to test library now
     * @param args
     */
    public static void main(String[] args) throws Exception {
        SCKTalk sckTalk = new SCKTalk();

        sckTalk.connect("COM8");

        String response = sckTalk.setModePC();

        if(response.equals("OK")) {
            System.out.println("Connected to ST-V3\n\n");
            //sckTalk.getMotorProfile(10, "S1");
        }

        /*Set<String> ports = NRSerialPort.getAvailableSerialPorts();

        for(String port: ports) {
            System.out.println("Port: " + port);
            sckTalk.connect(port);

            String response = sckTalk.setModePC();

            if(response.equals("OK")) {
                System.out.println("Connected to ST-V3\n\n");
                sckTalk.getMotorProfile(10, "S1");
            }

            sckTalk.setModeNormal();
        }*/

        System.exit(0);
    }

}