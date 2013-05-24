package org.instras.sck;

import gnu.io.NRSerialPort;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: nathan
 * Date: 4/14/13
 * Time: 9:59 AM
 * To change this template use File | Settings | File Templates.
 */
public class SCKTalk {
    private NRSerialPort serial;
    private DataInputStream ins;
    private DataOutputStream outs;

    public void setConsole(JTextArea console) {

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

    public void sendCommand(String command) {
        try {
            outs.writeBytes(command + "\r\n");
        } catch (IOException e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
    }

    /**
     * Method to read the results after a command has been sent
     * @return
     */
    public String readResult() {
        try {
            StringBuilder sb = new StringBuilder(); //ins.readUTF();
            byte[] buffer = new byte[128];
            int len = -1;

            while ((len = ins.read(buffer)) > 0 ) {
                sb.append(new String(buffer,0,len));
            }

            return sb.toString().trim();
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

    }

    /**
     * Method to close the serial port
     */
    public void close() {
        serial.disconnect();
    }

    /**
     * Main method. This is just to test library now
     * @param args
     */
    public static void main(String[] args) {
        SCKTalk sckTalk = new SCKTalk();

        Set<String> ports = NRSerialPort.getAvailableSerialPorts();

        for(String port: ports) {
            System.out.println("Port: " + port);
            sckTalk.connect(port);

            sckTalk.sendCommand("Test Test ...");

            String result = sckTalk.readResult();

            System.out.println("Result is : " + result + " End");
        }

        System.exit(0);
    }

}
