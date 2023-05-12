package org.instras.sck;

import gnu.io.NRSerialPort;

import javax.swing.*;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

import static java.lang.Math.abs;

/**
 * Created with IntelliJ IDEA.
 * User: nathan
 * Date: 6/8/2021
 * Time: 9:59 AM
 *
 * Simple class for connecting SCK-300 units which make use of the MiM or MiM/Nano Every control board
 */
public class MiMTalk {
    enum MotorType {
        BLDC,
        STEPPER
    }

    public boolean testMode = false;

    public int minMotorRPM = 0;

    public int maxMotorRPM = 0;

    public int excitation = 4;

    public int stepsPerRev = 96;

    public int maxFrequency = 32000; // maximum pwm frequency aka clocks to drive stepper motor

    private JTextArea console;

    private NRSerialPort serial;

    private DataInputStream ins;

    private DataOutputStream outs;

    public MotorType currentMotor = MotorType.BLDC;

    private final int RESPONSE_DELAY_MS = 100; // The response delay in milliseconds

    private boolean runRamp = false; // used to breakout of the run ramp program

    /**
     * Set the motor type
     *
     * @param motorType
     */
    public void setMotorType(MotorType motorType) {
        currentMotor = motorType;
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
        try {
            // wait 0.100 second so data can arrive from MiM
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
                print("Invalid RPM data: " + response);
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
     * @param currentTime The current time in seconds
     * @param speedLabel used to update speed
     * @param timeLabel used to update the time label
     * @return The time in seconds it took to run ramp
     */
    public int rampToRPM(int desiredRPM, float acceleration, int currentRPM, int currentTime,
                         JLabel speedLabel, JLabel timeLabel) {
        try {
            runRamp = true;

            // calculate the time to desired rpm in milliseconds
            float timeToDesiredRPM = (desiredRPM/acceleration)*1000;
            System.out.println("Time to Desired RPM: " + timeToDesiredRPM);
            int timeTotal = currentTime*1000;
            int cps = 4; // the commands to send per second
            int delayMS = 1000/cps - RESPONSE_DELAY_MS;
            if(delayMS < 0) delayMS = 0;


            int step = (int)acceleration/cps;
            int startRPM;
            if(currentRPM == 0) {
                startRPM = (step >= 250) ? step : 250;
            } else {
                startRPM = currentRPM;
            }

            for (int i = startRPM; i <= (desiredRPM + step); i += step) {
                // check to see to continue running the ramp program
                if(!runRamp) break;

                int speed = i;

                if (speed > desiredRPM) {
                    speed = desiredRPM;
                }

                //System.out.println("Setting Speed " + speed + " index: " + i + " delay: " + delayMS);
                sendCommand("SetRPM," + speed);

                if(speedLabel != null) {
                    String speedString = SCKUtils.zeroPad(speed);
                    speedLabel.setText(speedString);

                    String speedTime = SCKUtils.zeroPad(timeTotal/1000);
                    timeLabel.setText(speedTime);
                }

                Thread.sleep(delayMS);
                timeTotal += delayMS + RESPONSE_DELAY_MS;
            }

            System.out.println("Time Actually Taken: " + timeTotal);

            return timeTotal/1000;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Method to move to desired stepper rpm in steps. This prevents the stepper motor from
     * miss stepping up to a certain point
     *
     * @param desiredRPM
     */
    public void rampStepperToRPM(int currentRPM, int desiredRPM) {
        try {
            // power on motor and set the steps to move to big number
            if(currentRPM == 0) {
                //sendCommand("SetFreq,1");
                sendCommand("SleepOff");

                // this is how we get the stepper motor to spin clockwise continuously
                sendCommand("MoveUp,10000000");
            }

            int step = 100; // move in steps of 100 rpms
            int frequency;
            int rpmDiff = desiredRPM - currentRPM;

            // the the desired rpm is less than the step size or we moving down in speed go
            // to speed directly without ramping up
            if(desiredRPM < step) {
                frequency = convertRPMToFrequency(desiredRPM);
                sendCommand("SetFreq," + frequency);
            } else {
                if(rpmDiff > 0) { // moving to higher speed
                    for (int i = (currentRPM + step); i <= (desiredRPM + step); i += step) {
                        int speed = i;

                        if (speed > desiredRPM) {
                            speed = desiredRPM;
                        }

                        frequency = convertRPMToFrequency(speed);
                        sendCommand("SetFreq," + frequency);

                        System.out.println("Set Stepper Speed " + speed + ", Frequency: " + frequency);
                        Thread.sleep(2);
                    }
                } else {
                    // moving to lower speed
                    for (int i = (currentRPM - step); i >= (desiredRPM - step); i -= step) {
                        int speed = i;

                        if (speed < desiredRPM) {
                            speed = desiredRPM;
                        }

                        frequency = convertRPMToFrequency(speed);
                        sendCommand("SetFreq," + frequency);

                        System.out.println("Set Stepper Speed " + speed + ", Frequency: " + frequency);
                        Thread.sleep(2);
                    }
                }
            }

        } catch (InterruptedException e) {
            e.printStackTrace();
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

        int rpm = -1;

        if(currentMotor == MotorType.BLDC) {
            String response = sendCommand("GetRPM");
            rpm = Integer.parseInt(getResponseValue(response));
        } else {
            // stepper motor. Just get current frequency and convert to RPM
            String response = sendCommand("GetFreq");
            int frequency = Integer.parseInt(getResponseValue(response));
            rpm = convertFrequencyToRPM(frequency);
        }

        if(roundTo > 0) {
            rpm = (int) (Math.round(rpm/roundTo) * roundTo);
        }

        return rpm;
    }

    /**
     * Method to convert stepper motor frequency to rpm by just doing the calculation
     * assuming a linear response to the stepper motor
     * @param frequency
     * @return
     */
    private int convertFrequencyToRPM(int frequency) {
        int rpm = (frequency * 60/excitation)/stepsPerRev;

        return rpm;
    }

    /**
     * Convert the rpm to desired frequency to stepper motor
     *
     * @param rpm
     * @return
     */
    private int convertRPMToFrequency(int rpm) {
        int frequency = (rpm * stepsPerRev * excitation) / 60;
        return frequency;
    }

    /**
     * Method to turn the BLDC motor on
     */
    public void motorOn() {
        if(currentMotor == MotorType.BLDC) {
            sendCommand("BLDCon");
        } else {
            // must be stepper motor
            sendCommand("STEPon");
            sendCommand("SleepOn");
        }
    }

    /**
     * Method to turn the BLDC motor on
     */
    public void motorOff() {
        if(currentMotor == MotorType.BLDC) {
            sendCommand("BLDCoff");
        } else {
            // must be stepper motor
            sendCommand("STEPoff");
        }
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
     * Method to do a linear fit on motor speed profile to get the slope, and intercept
     *
     * @param avg
     * @return
     */
    public void fitMotorProfile(int avg) throws Exception {
        sendCommand("BLDCon");

        ArrayList<LinearRegression> lms = new ArrayList<LinearRegression>();

        for(int i = 0; i < 10; i++) {
            LinearRegression lm = new LinearRegression(getMotorProfile(50));
            lms.add(lm);
            print(i + ":: " + lm.toString());

            // take a break to allow motor to cool off a bit
            sendCommand("BLDCoff");
            Thread.sleep(20000);
            sendCommand("BLDCon");
            Thread.sleep(5000);
        }

        print("\n\n");

        int size = lms.size();
        double slope= 0;
        double intercept = 0;

        for(LinearRegression lm: lms) {
            if(!Double.isNaN(lm.slope())) {
                slope += lm.slope();
                intercept += lm.intercept();
                print(lm.toString());
            } else {
                size--;
            }
        }

        print("Avg slope: " + slope / size);
        print("Avg intercept: " + intercept/size);

        // stop the motor just in case we didn't before
        sendCommand("BLDCoff");
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
        MiMTalk miMTalk = new MiMTalk();

        miMTalk.connect("COM4");

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

            // stop the motor just in case we didn't before
            miMTalk.sendCommand("BLDCoff");
        }

        miMTalk.close();
        System.exit(0);
    }

}