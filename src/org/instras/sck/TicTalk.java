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

    // stepper motor parameters
    private int excitation;
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
     * Method to send a command to the MiM board
     *
     * @param command
     * @return
     */
    public synchronized String sendCommand(byte command, byte[] data) {
        if(testMode) return "OK";

        byte[] cmdWithData = new byte[data.length + 1];
        cmdWithData[0] = command;
        for(int i = 1; i < cmdWithData.length; i++) {
            cmdWithData[i] = data[i-1];
        }

        try {
            outs.write(command);
        } catch (IOException e) {
            e.printStackTrace();
            return "ERR";
        }

        return "OK";
    }

    /**
     * Method to read the results after a command has been sent
     *
     * @return
     */
    public byte[] readResponse(byte offset, byte length) {
        if(testMode) return new byte[]{-1,-1};

        try {
            byte[] data = {offset, length};
            sendCommand((byte)0xA1, data);

            // wait 0.100 second so data can arrive from Tic Board
            Thread.sleep(100);

            byte[] buffer = new byte[length];
            int result = ins.read(buffer);

            return buffer;
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * Method to turn the BLDC motor on
     */
    public void motorOff() {
        //sendCommand("STEPoff");
    }

    /**
     * Get the version. Just return Tic for now
     *
     * @return
     */
    public String getVersion() {
        return "Tic";
    }

    /**
     * Set the parameters for the stepper motors
     *
     * @param excitation This is the microsteping for the motor. For SCK-300S its fixed at 4
     * @param stepsPerRev The steps per revolution of stepper motor, 96 for SCK-300S
     * @return
     */
    public String setStepperParameters(int excitation, int stepsPerRev, int maxMotorRPM) {
        this.excitation  = excitation;
        this.stepsPerRev = stepsPerRev;
        this.maxMotorRPM = maxMotorRPM;

        return "OK";
    }

    /**
     * Method to close the serial port
     */
    public void close() {
        if(testMode) return;
        serial.disconnect();
    }
}
