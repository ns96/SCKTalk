package org.instras.sck;

import gnu.io.NRSerialPort;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: nathan
 * Date: 1/5/2023
 * Time: 9:00 AM
 *
 * Simple class for connecting tp the new SCK-300S which make use of the Tic T249 control board
 */

public class TicTalk {
    private NRSerialPort serial;
    private DataInputStream ins;
    private DataOutputStream outs;

    private JTextArea console;

    private boolean testMode = false;

    public final int RESPONSE_DELAY_MS = 200; // The response delay in milliseconds

    // stepper motor parameters
    private int microstep;
    private int stepsPerRev;
    private int maxMotorRPM;

    /**
     * Method to connect to the serial port
     *
     * @param portName
     */
    public void connect(String portName) {
        if(testMode) return;

        serial = new NRSerialPort(portName, 19200);
        serial.connect();

        ins = new DataInputStream(serial.getInputStream());
        outs = new DataOutputStream(serial.getOutputStream());

        // set to TIC Mode
        try {
            Thread.sleep(2000);
            sendCommand("x");
        } catch(InterruptedException e) {}
    }

    /**
     * Used to pass messages back to the GUI application
     *
     * @param console
     */
    public void setConsole(JTextArea console) {
        this.console = console;
    }

    /**
     * Send the command string to MiM board
     *
     * @param command
     * @return
     */
    public String sendCommand(String command) {
        return sendCommand(command, true);
    }

    /**
     * Method to send a command to the MiM board
     *
     * @param command
     * @param wfr wait for response
     * @return
     */
    public synchronized String sendCommand(String command, boolean wfr) {
        if(testMode) return "OK";

        try {
            //System.out.println("Command: " + command);
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
     *
     * @return
     */
    public String readResponse() {
        if(testMode) return "TESTMODE";

        try {
            // wait 0.200 second so data can arrive from Tic
            Thread.sleep(RESPONSE_DELAY_MS);

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
     * Method to turn the Stepper motor on
     */
    public void motorOn() {
        sendCommand("STEPon,0");
    }

    /**
     * Method to turn the Stepper motor off
     */
    public void motorOff() {
        sendCommand("STEPoff,0");
    }

    /**
     * Get the version. Just return Tic for now
     *
     * @return
     */
    public String getVersion() {
        return sendCommand("GetVersion,0");
    }

    /**
     * Set the parameters for the stepper motors
     *
     * @param microstep This is the micro-stepping for the motor. For SCK-300S its fixed at 4
     * @param stepsPerRev The steps per revolution of stepper motor, 96 for SCK-300S
     * @param maxMotorRPM The maximum motor RPM
     * @return
     */
    public String setStepperParameters(int microstep, int stepsPerRev, int maxMotorRPM) {
        this.microstep  = microstep;
        this.stepsPerRev = stepsPerRev;
        this.maxMotorRPM = maxMotorRPM;

        sendCommand("SetMicro," + microstep);
        sendCommand("SetSPR," + stepsPerRev);
        sendCommand("SetMaxRPM," + maxMotorRPM);

        return "OK";
    }

    /**
     * Set the acceleration
     * @param rpmPerSec
     */
    public void setAcceleration(int rpmPerSec) {
        sendCommand("SetACC," + rpmPerSec);
    }

    /**
     * Method to set the rpm
     *
     * @param desiredRPM
     */
    public void setRPM(int desiredRPM) {
        sendCommand("SetRPM," + desiredRPM);
    }

    /**
     * A convenience method to get the RPM value as an int
     *
     * @return
     */
    public int getRPM(double roundTo) {
        int rpm = -1;

        try {
            String response = sendCommand("GetRPM,0");
            rpm = Integer.parseInt(response.trim());

            if (roundTo > 0) {
                rpm = (int) (Math.round(rpm / roundTo) * roundTo);
            }
        } catch(NumberFormatException nfe) {}

        return rpm;
    }

    /**
     * Method to close the serial port
     */
    public void close() {
        if(testMode) return;

        try {
            sendCommand("x");
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        serial.disconnect();
    }

    /**
     * Indicate whether the serial port is connected
     * @return
     */
    public boolean isConnected() {
        if(serial != null) {
            return serial.isConnected();
        }
        return false;
    }

    /**
     * Method to print to the sout and the JTextArea console if it's not null
     *
     * @param string
     */
    public void print(String string) {
        System.out.println(string);

        if(console != null) {
            console.append(string + "\n");
        }
    }
}