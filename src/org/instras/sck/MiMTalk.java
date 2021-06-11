package org.instras.sck;

import gnu.io.NRSerialPort;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Created with IntelliJ IDEA.
 * User: nathan
 * Date: 6/8/2021
 * Time: 9:59 AM
 *
 * Simple class for connecting SCK-300 units which make use of the MiM or MiM/Nano Every control board
 */
public class MiMTalk {
    private boolean testMode = false;
    public int minMotorRPM = 0;
    public int maxMotorRPM = 0;
    private JTextArea console;
    private NRSerialPort serial;
    private DataInputStream ins;
    private DataOutputStream outs;

    /**
     * Used to pass messages back to the GUI application
     *
     * @param console
     */
    public void setConsole(JTextArea console) {
        this.console = console;
    }

    /**
     * Used for testing the API
     * @param test
     */
    public void setTestMode(boolean test) {
        this.testMode = test;
    }

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
     *
     * @return
     */
    public String readResponse() {
        if(testMode) return "TESTMODE";

        try {
            // wait 0.200 second so data can arrive from MiM
            Thread.sleep(200);

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
     * Method to get the version of MiM firmware
     *
     * @return
     */
    public String getVersion() {
        return sendCommand("GetVersion");
    }

    /**
     * Function to kick start the motor to prevent it from automatically shutting down
     * when trying to spin at speed below 1000 rpm. The SCK-300 version 2 do not need this anymore
     *
     * @param kickstart The kickstart value
     */
    public void kickStart(int kickstart) {
        try {
            sendCommand("SetPWM," + kickstart, false);
            Thread.sleep(50);
            System.out.println("Kick Started ...\n\n");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * Method to get a performance profile
     *
     * @param increment
     * @return
     */
    public HashMap<String, Double[]> getMotorProfile(int increment) throws Exception {
        HashMap<String, Double[]> motorProfileMap = new HashMap<String, Double[]>();
        ArrayList<Double> xlist = new ArrayList<Double>();
        ArrayList<Double> ylist = new ArrayList<Double>();

        kickStart(75); // 200 for regular 2838, 75 for ball bearing 2838

        print("PWM\tRPM");

        for(int i = 110; i <= 1000   ; i += increment) {
            sendCommand("SetPWM," + i);

            Double rpm = -1.0;
            String response = sendCommand("GetRPM");
            response = getResponseValue(response);

            // wait 2 seconds
            Thread.sleep(2000);

            try {
                rpm = new Double(response);
            } catch(NumberFormatException nfe) {
                System.out.println("Invalid RPM data: " + response);
            }

            xlist.add(new Double(i));
            ylist.add(rpm);

            // set the min and max rpm
            int irpm = rpm.intValue();
            if(irpm > 0 && minMotorRPM == 0) {
                minMotorRPM = irpm;
            } else if(irpm > maxMotorRPM) {
                maxMotorRPM = irpm;
            }

            print(i + "\t" + irpm);
        }

        // create the double arrays
        Double[] x = new Double[xlist.size()];
        Double[] y = new Double[ylist.size()];

        motorProfileMap.put("x", xlist.toArray(x));
        motorProfileMap.put("y", ylist.toArray(y));

        return motorProfileMap;
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
     * Method to move to desired rpm in steps. This prevents the RPM overshoot for the new ball bearing motors
     *
     * @param desiredRPM
     */
    public void rampToRPM(int desiredRPM) {
        try {
            if(desiredRPM <= 500) {
                sendCommand("SetRPM," + desiredRPM);
                System.out.println("Setting Desired RPM Directly: " + desiredRPM);
            } else {
                int step = 300;
                for(int i = 500; i <= (desiredRPM + step); i += step) {
                    int speed = i;

                    if(speed > desiredRPM) {
                        speed = desiredRPM;
                    }

                    System.out.println("Setting Speed " + speed + " index: " + i);
                    sendCommand("SetRPM," + speed);
                    Thread.sleep(2);
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /**
     * A convenience method to get the RPM value as an it
     *
     * @return
     */
    public int getRPM(double roundTo) {
        String response = sendCommand("GetRPM");
        int rpm = Integer.parseInt(getResponseValue(response));

        if(roundTo > 0) {
            rpm = (int) (Math.round(rpm/roundTo) * roundTo);
        }

        return rpm;
    }

    /**
     * Method to turn the BLDC motor on
     */
    public void motorOn() {
        sendCommand("BLDCon");
    }

    /**
     * Method to turn the BLDC motor on
     */
    public void motorOff() {
        sendCommand("BLDCoff");
    }

    /**
     * Method to cycle motor up and down quickly
     *
     * @param maxRPM
     * @param step
     * @throws InterruptedException
     */
    public void cycleMotor(int maxRPM, int step) throws InterruptedException {
        String rpm;

        int i;
        int lastRPM = 0;
        for(i = step; i <= maxRPM; i += step) {
            sendCommand("SetRPM," + i);
            //rampToRPM(i, 0.20f);
            rpm = sendCommand("GetRPM");
            rpm = getResponseValue(rpm);
            System.out.println( i + "\t" + rpm);
            lastRPM = Integer.parseInt(rpm);
            Thread.sleep(2000);
        }

        Thread.sleep(5000);
        rpm = sendCommand("GetRPM");
        rpm = getResponseValue(rpm);
        float currentRPM = Integer.parseInt(rpm);
        float diff = (lastRPM - currentRPM);
        float percentDiff = (diff/currentRPM)*100.0f;
        System.out.println((i - step) + "@5s\t" + rpm + "\tDiff " +  diff + "/" + percentDiff);
        sendCommand("SetRPM,0");
        Thread.sleep(5000);
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

    /**
     * Extract the value from the response string
     *
     * @param response
     * @return
     */
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
     * Method to set the slope intercept start pwm used to start and accurately set the RPM by doing a linear fit
     */
    public void setMotorParameters() {
        System.out.println("Setting Motor Parameters");

        System.out.println(sendCommand("SetStartPWM,0"));
        System.out.println(sendCommand("SetSlope,930"));
        System.out.println(sendCommand("SetIntercept,350"));
    }

    /**
     * Method to set the motor parameters
     *
     * @param startPWM
     * @param slope
     * @param intercept
     */
    public String setMotorParameters(int startPWM, int slope, int intercept) {
        String response = sendCommand("SetStartPWM," + startPWM) + " / ";
        response += sendCommand("SetSlope," + slope) + " / ";
        response += sendCommand("SetIntercept," + intercept);

        return response;
    }

    /**
     * Main method. allows running and testing motors at the command line
     *
     * @param args
     */
    public static void main(String[] args) throws Exception {
        MiMTalk miMTalk = new MiMTalk();

        miMTalk.connect("COM3");

        String response = miMTalk.getVersion();

        System.out.println("Connection response: " + response);

        if(response.contains("MIM")) {
            System.out.println("Connected to MIM\n");
            miMTalk.sendCommand("BLDCon");

            /**
            for(int i = 0; i <= 1000; i += 50) {
                if(i == 0) {
                    System.out.println( "S\tRPM");
                    miMTalk.sendCommand("SetRPM,1200");
                }

                Thread.sleep(2000);
                String rpm = miMTalk.sendCommand("GetRPM");
                rpm = miMTalk.getResponseValue(rpm);
                System.out.println( i + "\t" + rpm);
            }
             */

            /*
            miMTalk.setMotorParameters();

            for(int i = 0; i < 50d; i++) {
                miMTalk.sendCommand("BLDCon");
                miMTalk.cycleMotor(5000, 3000);
                miMTalk.sendCommand("BLDCoff");
            }
            */

            //**
            ArrayList<LinearRegression> lms = new ArrayList<LinearRegression>();

            for(int i = 0; i < 10; i++) {
                LinearRegression lm = new LinearRegression(miMTalk.getMotorProfile(50));
                lms.add(lm);
                miMTalk.print(i + ":: " + lm.toString());

                miMTalk.sendCommand("BLDCoff");
                Thread.sleep(20000);
                miMTalk.sendCommand("BLDCon");
                Thread.sleep(5000);
            }

            miMTalk.print("\n\n");

            int size = lms.size();
            double slope= 0;
            double intercept = 0;

            for(LinearRegression lm: lms) {
                if(!Double.isNaN(lm.slope())) {
                    slope += lm.slope();
                    intercept += lm.intercept();
                    miMTalk.print(lm.toString());
                } else {
                    size--;
                }
            }

            System.out.println("Avg slope: " + slope / size);
            System.out.println("Avg intercept: " + intercept/size);

            //**/

            // stop the motor just in case we didn't before
            miMTalk.sendCommand("BLDCoff");
        }

        miMTalk.close();
        System.exit(0);
    }

}