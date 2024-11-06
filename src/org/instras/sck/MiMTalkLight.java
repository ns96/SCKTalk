package org.instras.sck;

import gnu.io.NRSerialPort;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Created with IntelliJ IDEA.
 * User: nathan
 * Date: 6/8/2021
 * Time: 9:59 AM
 *
 * Simple class for connecting SCK-300 units which make use of the MiM or MiM/Nano Every control board. This class
 * provides a basic example for connecting to the SCK-300 unit
 */
public class MiMTalkLight {
    public boolean testMode = false;

    private NRSerialPort serial;

    private DataInputStream ins;

    private DataOutputStream outs;

    public final int RESPONSE_DELAY_MS = 200; // The response delay in milliseconds

    private boolean runRamp = false; // used to breakout of the run ramp program

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

        // set to TIC Mode
        try {
            Thread.sleep(2000);
            sendCommand("y");
        } catch(InterruptedException e) {}
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
            command += "\r\n";
            outs.writeBytes(command);
            if(wfr) {
                return readResponse();
            } else {
                return "";
            }
        } catch (IOException e) {
            //e.printStackTrace();
            System.out.println("COMM/IO Error. Ignore ...");
            return null;
        }
    }

    /**
     * Method to read the results after a command has been sent
     *
     * @return
     */
    public String readResponse() {
        try {
            // wait 0.n00 second so data can arrive from MiM
            Thread.sleep(RESPONSE_DELAY_MS);
            if(testMode) return "TESTMODE,0:TT";

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
     * Function to kickstart the motor to prevent it from automatically shutting down
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
        if (desiredRPM <= 500) {
            sendCommand("SetRPM," + desiredRPM);
            System.out.println("Setting Desired RPM Directly: " + desiredRPM);
        } else {
            int step = 300;
            for (int i = 500; i <= (desiredRPM + step); i += step) {
                int speed = i;

                if (speed > desiredRPM) {
                    speed = desiredRPM;
                }

                System.out.println("Setting Speed " + speed + " index: " + i);
                sendCommand("SetRPM," + speed);
            }
        }
    }

    /**
     * Method to move to desired rpm at a particular acceleration
     *
     * @param desiredRPM
     * @param acceleration in RPM per second
     * @param currentRPM The current speed
     *
     * @return The time in seconds it took to run ramp
     */
    public int rampToRPM(int desiredRPM, float acceleration, int currentRPM) {
        try {
            runRamp = true;

            // calculate the time to desired rpm in milliseconds
            int rpmDiff = Math.abs(desiredRPM - currentRPM);
            float timeToDesiredRPM = (rpmDiff/acceleration)*1000;

            String message = "Time to Desired RPM (ms): " + (int)timeToDesiredRPM;
            print("\n" + message);

            float timeTotal = 0;
            int cps = 4; // the commands to send per second
            int delayMS = 1000/cps - RESPONSE_DELAY_MS;
            if(delayMS < 0) delayMS = 0;


            int step = (int)acceleration/cps;
            int startRPM;

            // see if to set the current rpm to the lowest speed the SCK-300 can run at
            if(currentRPM == 0) {
                startRPM = (step >= 250) ? step : 250;
            } else {
                startRPM = currentRPM;
            }

            for (int i = startRPM; i <= (desiredRPM + step); i += step) {
                // check to see if to continue running the ramp program
                if(!runRamp) break;

                int speed = i;
                if (speed > desiredRPM) {
                    speed = desiredRPM;
                }

                //System.out.println("Setting Speed " + speed + " index: " + i + " delay: " + delayMS);
                sendCommand("SetRPM," + speed);

                Thread.sleep(delayMS);
                timeTotal += delayMS + RESPONSE_DELAY_MS;
            }

            message = "Time Actually Taken (ms): " + (int)timeTotal;
            print(message + "\n");

            return (int)timeTotal/1000;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Method used to stop the ramp program
     */
    public void stopRamp() {
        runRamp = false;
    }

    /**
     * A convenience method to get the RPM value as an it
     *
     * @return
     */
    public int getRPM(double roundTo) {
        if(testMode) return -1;

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
     * Method to print to the sout and the JTextArea console if it's not null
     *
     * @param string
     */
    public void print(String string) {
        System.out.println(string);
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

        try {
            sendCommand("y");
            Thread.sleep(500);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

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
     * Indicate whether the serial port is connected
     *
     * @return
     */
    public boolean isConnected() {
        if(serial != null) {
            return serial.isConnected();
        }
        return false;
    }

    /**
     * Main method. allows running and testing motors at the command line
     *
     * @param args
     */
    public static void main(String[] args) throws Exception {
        MiMTalkLight miMTalk = new MiMTalkLight();

        miMTalk.connect("COM3");

        String response = miMTalk.getVersion();

        System.out.println("Connection response: " + response);

        if(response.contains("MIM")) {
            System.out.println("Connected to MIM\n");
            miMTalk.setMotorParameters();
            miMTalk.sendCommand("BLDCon");

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

            // stop the motor just in case we didn't before
            miMTalk.sendCommand("BLDCoff");
        }

        miMTalk.close();
        System.exit(0);
    }
}