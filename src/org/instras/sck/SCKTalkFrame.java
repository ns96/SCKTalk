/*
 * Created by JFormDesigner on Sat Apr 13 08:17:57 EDT 2013
 */

package org.instras.sck;

import java.awt.*;
import java.awt.event.*;
import java.io.File;
import javax.swing.*;
import com.jgoodies.forms.factories.*;
import com.jgoodies.forms.layout.*;
import gnu.io.NRSerialPort;

/**
 * @author Nathan Stevens
 */
public class SCKTalkFrame extends JFrame {
    private MiMTalk miMTalk = null; // used to connect to the SCK unit through the serial port

    private TicTalk ticTalk = null; // used to connect to the Tic board based SCK-300S

    private int maxSpeed = 8000; // the max speed

    private int minSpeed = 0; // the minimum speed

    private int currentSpeed = 2500; // the current speed

    private boolean sckRunning = false;

    private double roundToValue = 25.0; // used for rounding the rpm

    private String sckType = "SCK-300P";

    private int ticks = 0; // used to keep track of motor run time

    public SCKTalkFrame() {
        initComponents();

        // set the default comm port
        portComboBox.removeAllItems();
        for(String s: NRSerialPort.getAvailableSerialPorts()) {
            portComboBox.addItem(s);
        }

        sckComboBox.setSelectedIndex(1);

        speedTextField.setText("" + currentSpeed);

        // read the step sequence
        readSavedStepSequence();
    }

    /**
     * Close the program
     *
     * @param e
     */
    private void exitButtonActionPerformed(ActionEvent e) {
        if(miMTalk != null && miMTalk.isConnected()) {
            miMTalk.motorOff();
            miMTalk.close();
        } else if(ticTalk != null && ticTalk.isConnected()) {
            ticTalk.motorOff();
            ticTalk.close();
        }

        // save the step sequence
        saveStepSequence(null);

        System.exit(0);
    }

    // method to connect to the SCK-300 unit
    private void connectButtonActionPerformed(ActionEvent e) {
        String portName = portComboBox.getSelectedItem().toString();

        try {
            consoleTextArea.setText("Connecting to SCK ...\n");
            if(mimModelRadioButton.isSelected()) {
                connectToMiM(portName);
            } else {
                connectToTic(portName);
            }
        } catch(Exception ex) {
            printMessage("\n\nCOMM PORT ERROR -- " + portName);
            miMTalk = null;

            ex.printStackTrace();
        }
    }

    /**
     * Connect to the MiM board for SCK-300/300P
     */
    private void connectToMiM(String portName) {
        miMTalk = new MiMTalk();
        miMTalk.connect(portName);

        String response = miMTalk.getVersion();

        printMessage("SCK Response: " + response);

        if (response != null && response.contains("MIM") || miMTalk.testMode) {
            printMessage("Connected to SCK unit ...\n");
            sendSCKParameters();
            connectButton.setBackground(Color.ORANGE);
            connectButton.setEnabled(false);
        } else {
            printMessage("Error Connecting to MIM ...\n");
            miMTalk.close();
            miMTalk = null;
        }
    }

    /**
     * Connect to the Tic board for SCK-300S
     */
    private void connectToTic(String portName) {
        ticTalk = new TicTalk();
        ticTalk.connect(portName);

        String response = ticTalk.getVersion();

        printMessage("SCK-300S Response: " + response);

        if (response != null && response.contains("TIC_SCK")) {
            printMessage("Connected to SCK-300S unit ...\n");
            sendSCKParameters();
            connectButton.setBackground(Color.ORANGE);
            connectButton.setEnabled(false);
        } else {
            printMessage("Error Connecting to TIC_300S ...\n");
            ticTalk.close();
            ticTalk = null;
        }
    }

    /**
     * Send the correct parameters to the SCK based on the version
     *
     */
    private void sendSCKParameters() {
        try {
            String sckModel = sckComboBox.getSelectedItem().toString().trim();
            String[] sa1 = sckModel.split(":");
            String[] sa2 = sa1[1].split(",");

            maxSpeed = Integer.parseInt(sa2[0].trim());
            int startPWM = Integer.parseInt(sa2[1].trim());
            int slope = Integer.parseInt(sa2[2].trim());
            int intercept = Integer.parseInt(sa2[3].trim());

            // see if to set the motor type based on the choice of user
            sckType = sa1[0].trim();
            if(sckType.contains("SCK-300S")) {
                //miMTalk.setMotorType(MiMTalk.MotorType.STEPPER);
                String response = ticTalk.setStepperParameters(startPWM, slope, maxSpeed);
                printMessage("Setting SCK Stepper parameters: " + response);
            } else {
                String response = miMTalk.setMotorParameters(startPWM, slope, intercept);
                printMessage("Setting SCK BLDC parameters: " + response);
            }
        } catch(NumberFormatException ex) {
            printMessage("Error setting SCK parameters");
        }
    }

    /**
     * Print a message to the console
     *
     * @param message
     */
    private void printMessage(String message) {
        consoleTextArea.append(message + "\n");
    }

    /**
     * Close the comm port
     *
     * @param e
     */
    private void closePortButtonActionPerformed(ActionEvent e) {
        if(miMTalk != null) {
            miMTalk.close();
            connectButton.setBackground(Color.YELLOW);
            connectButton.setEnabled(true);
            printMessage("\nClosed SCK connection ...");
        } else if(ticTalk != null) {
            ticTalk.close();
            connectButton.setBackground(Color.YELLOW);
            connectButton.setEnabled(true);
            printMessage("\nClosed SCK-300S connection ...");
        }
    }

    /**
     * Start and stop the SCK
     * @param e
     */
    private void startStopButtonActionPerformed(ActionEvent e) {
        if(miMTalk == null && ticTalk == null) { return; }

        if(startStopButton.isSelected()) {
            sckRunning = true;
            ticks = 0;

            // set the current speed
            speedTextFieldActionPerformed(null);

            if(miMTalk != null) {
                runMimTalkMotor();
            } else if(ticTalk != null) {
                runTicTalkMotor();
            }
        } else {
            if(miMTalk != null) {
                miMTalk.stopRamp(); // stop the ramp process just in case
            }

            sckRunning = false;
            System.out.println("Stop motor ...");
        }
    }

    /**
     * Run the mim talk driven motor
     */
    private void runMimTalkMotor() {
        // now send command to go to the desired speed
        miMTalk.motorOn();

        // crete a timer thread to update the time and check for new speed settings
        Thread timerThread = new Thread() {
            public void run() {
                int oldSpeed = currentSpeed;

                while(sckRunning) {
                    try {
                        sleep(500);
                    } catch (InterruptedException ex) {
                        break;
                    }

                    // update the timer
                    String timeString = SCKUtils.zeroPad(ticks/2);
                    spinTimeLabel.setText(timeString);

                    // read the rpm and update the speed label
                    String speedString = SCKUtils.zeroPad(miMTalk.getRPM(roundToValue));
                    speedLabel.setText(speedString);

                    // check to make sure we don't have to update the speed
                    if(currentSpeed != oldSpeed) {
                        if (miMTalk.currentMotor == MiMTalk.MotorType.BLDC) {
                            miMTalk.setRPM(currentSpeed);
                        } else {
                            miMTalk.rampStepperToRPM(oldSpeed, currentSpeed);
                        }

                        oldSpeed = currentSpeed;
                    }

                    ticks++;
                }

                // stop the motor and reset the timer
                miMTalk.motorOff();
                spinTimeLabel.setText("00000");
                speedLabel.setText("00000");
            }
        };

        // start motor in swing worker thread
        SwingWorker sw1 = new SwingWorker() {
            @Override
            protected String doInBackground() {
                int acceleration = Integer.parseInt(accTextField.getText());

                // ramp to the motor speed in swing worker
                if (miMTalk.currentMotor == MiMTalk.MotorType.BLDC) {
                    int rampTime = miMTalk.rampToRPM(currentSpeed, acceleration, 0, speedLabel, spinTimeLabel);
                    ticks = rampTime*2;
                } else {
                    miMTalk.rampStepperToRPM(0, currentSpeed);
                }

                return "Done";
            }

            @Override
            protected void done()  {
                timerThread.start();
            }
        };

        // Executes the swing worker on worker thread
        sw1.execute();
    }

    /**
     * Run the mim talk driven motor
     */
    private void runTicTalkMotor() {
        // now send command to go to the desired speed
        ticTalk.motorOn();

        // ramp to the motor speed
        try {
            int rpmPerSec = Integer.parseInt(accTextField.getText());
            ticTalk.setAcceleration(rpmPerSec);
            ticTalk.setRPM(currentSpeed);
        } catch(NumberFormatException nfe) {
            nfe.printStackTrace();
            ticTalk.motorOff();
        }

        // start the thread to update the time and check for new speed settings
        Thread timerThread = new Thread() {
            public void run() {
                int ticks = 0;
                int oldSpeed = currentSpeed;

                while(sckRunning) {
                    try {
                        sleep(500);
                    } catch (InterruptedException ex) {
                        break;
                    }

                    // update the timer
                    String timeString = SCKUtils.zeroPad(ticks/2);
                    spinTimeLabel.setText(timeString);

                    // read the rpm and update the speed label
                    String speedString = SCKUtils.zeroPad(ticTalk.getRPM(roundToValue));
                    speedLabel.setText(speedString);

                    // check to make sure we don't have to update the speed
                    if(currentSpeed != oldSpeed) {
                        ticTalk.setRPM(currentSpeed);
                        oldSpeed = currentSpeed;
                    }

                    ticks++;
                }

                // stop the motor and reset the timer
                ticTalk.motorOff();
                spinTimeLabel.setText("00000");
                speedLabel.setText("00000");
            }
        };

        timerThread.start();
    }

    /**
     * Increase the spin speed by the increment amount
     *
     * @param e
     */
    private void upButtonActionPerformed(ActionEvent e) {
        try {
            int increment = Integer.parseInt(incrementTextField.getText().trim());
            int newSpeed = currentSpeed + increment;

            if(newSpeed > maxSpeed) {
                currentSpeed = maxSpeed;
            } else {
                currentSpeed = newSpeed;
            }

            speedTextField.setText("" + currentSpeed);
        } catch(NumberFormatException ex) { }
    }

    /**
     * Decrease the spin speed by the increment amount
     *
     * @param e
     */
    private void downButtonActionPerformed(ActionEvent e) {
        try {
            int increment = Integer.parseInt(incrementTextField.getText().trim());
            int newSpeed = currentSpeed - increment;

            if(newSpeed < minSpeed) {
                currentSpeed = minSpeed;
            } else {
                currentSpeed = newSpeed;
            }

            speedTextField.setText("" + currentSpeed);
        } catch(NumberFormatException ex) { }
    }

    /**
     * Used for update the speed setting
     *
     * @param e
     */
    private void speedTextFieldActionPerformed(ActionEvent e) {
        try {
            int speed = Integer.parseInt(speedTextField.getText());

            if (speed > maxSpeed) {
                currentSpeed = maxSpeed;
                speedTextField.setText("" + currentSpeed);
            } else if(speed < minSpeed) {
                currentSpeed = minSpeed;
                speedTextField.setText("" + currentSpeed);
            } else {
                currentSpeed = speed;
            }

            System.out.println("Updating speed: " + currentSpeed);
        } catch(NumberFormatException nfe) {}
    }

    /**
     * Update the motor parameters
     *
     * @param e
     */
    private void sckComboBoxActionPerformed(ActionEvent e) {
        if(miMTalk != null) {
            sendSCKParameters();
        }
    }

    /**
     * Action resulting when the increment textfield is pressed
     * @param e
     */
    private void incrementTextFieldActionPerformed(ActionEvent e) {
        try {
            double value = Double.parseDouble(incrementTextField.getText());
            roundToValue = value;
        } catch(NumberFormatException ex) { }
    }

    /**
     * Run the ramp step sequence
     * @param e
     */
    private void rampButtonActionPerformed(ActionEvent e) {
        if(!sckRunning) {
            startStopButton.setSelected(true);
            rampButton.setEnabled(false);
            ticks = 0;
            runStepSequence();
        }
    }

    /**
     * Method to run the ramp step sequence
     */
    private void runStepSequence() {
        // check that the sequence is good
        final String[] stepSeqences = checkStepSequences();
        if(stepSeqences == null) { return; }

        // create a swing worker to run the sequence in the background
        SwingWorker worker = new SwingWorker<Boolean, Void>() {
            @Override
            public Boolean doInBackground() {
                sckRunning = true;

                int acceleration = Integer.parseInt(accTextField.getText());

                // turn the motor on
                if(miMTalk != null) {
                    miMTalk.motorOn();
                } else {
                    ticTalk.motorOn();
                }

                // clear the console
                consoleTextArea.setText("");

                printMessage("Starting Ramp Sequence ...");

                int currentSpeed = 0; // keep track of the current speed to the stepper motor
                String countDownTime;
                String speedString;

                // iterate over the lines containing the sequences
                outerloop:
                for(int i = 1; i < stepSeqences.length; i++) {
                    String[] stepInfo = stepSeqences[i].split("\\s*,\\s*");
                    int targetSpeed = Integer.parseInt(stepInfo[1]);
                    int targetSpinTime = Integer.parseInt(stepInfo[2]);

                    printMessage(stepInfo[0] + ", " + targetSpeed + " rpms, " + targetSpinTime + " sec");

                    if(miMTalk !=null) {
                        if(miMTalk.currentMotor == MiMTalk.MotorType.BLDC) {
                            if(targetSpeed > currentSpeed) {
                                miMTalk.rampToRPM(targetSpeed, acceleration, currentSpeed, speedLabel, spinTimeLabel);
                            } else {
                                // we are slowing down
                                miMTalk.setRPM(targetSpeed);
                            }

                            currentSpeed = targetSpeed;
                        } else {
                            miMTalk.rampStepperToRPM(currentSpeed, targetSpeed);
                            currentSpeed = targetSpeed;
                        }
                    } else {
                        // assume we are using tic stepper driver
                        ticTalk.setAcceleration(acceleration);
                        ticTalk.setRPM(targetSpeed);

                        // TO-DO wait for motor to get to final speed based on acceleration
                        float speedRange = Math.abs(targetSpeed - currentSpeed);
                        float timeToDesiredSpeed = (speedRange/acceleration)*1000;
                        int speed = 0;
                        int rampTime = 0;
                        int delay = 300;

                        while(rampTime < timeToDesiredSpeed && speed < targetSpeed) {
                            try {
                                if(!sckRunning) break;

                                Thread.sleep(delay);
                                rampTime += delay + ticTalk.RESPONSE_DELAY_MS;

                                String countUpTime = SCKUtils.zeroPad(rampTime/1000);
                                spinTimeLabel.setText(countUpTime);

                                speed = ticTalk.getRPM(roundToValue);
                                speedString = SCKUtils.zeroPad(speed);
                                speedLabel.setText(speedString);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }

                        System.out.println("Done Ramping Stepper Motor ...");
                    }

                    // update the ramp step label
                    rampStepLabel.setText("Ramp Step # " + stepInfo[0] + " / " + targetSpeed + " rpms");

                    // use a loop to keep track of time this step is running
                    int count = 0;
                    while(count <= targetSpinTime) {
                        // check to if the motor was stop
                        if(!sckRunning) {
                            printMessage("\nRamp Sequenced Cancelled ...");
                            break outerloop;
                        }

                        // update the count timer
                        countDownTime = SCKUtils.zeroPad(targetSpinTime - count);
                        spinTimeLabel.setText(countDownTime);

                        // get the current rpm
                        if(miMTalk !=null) {
                            speedString = SCKUtils.zeroPad(miMTalk.getRPM(roundToValue));
                        } else {
                            speedString = SCKUtils.zeroPad(ticTalk.getRPM(roundToValue));
                        }

                        speedLabel.setText(speedString);

                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }

                        count++;
                    }
                }

                printMessage("\nRamp Sequence Completed ...");

                // stop the motor now
                if(miMTalk != null) {
                    miMTalk.motorOff();
                } else {
                    ticTalk.motorOff();
                }

                sckRunning = false;

                // reset the labels and start stop button
                startStopButton.setSelected(false);
                rampButton.setEnabled(true);
                spinTimeLabel.setText("00000");
                rampStepLabel.setText("Ramp Step # 0");

                // sequence complete so return true
                return true;
            }
        };

        worker.execute();
    }

    /**
     * Check that the step sequence has no errors
     *
     * @return a string array containing the step sequence
     */
    private String[] checkStepSequences() {
        String[] stepSeqences = rampTextArea.getText().split("\n");

        // interate over the lines containing the seqences
        for (int i = 1; i < stepSeqences.length; i++) {
            String[] stepInfo = stepSeqences[i].split("\\s*,\\s*");
            String step = stepInfo[0];

            try {
                int speed = Integer.parseInt(stepInfo[1]);
                int time = Integer.parseInt(stepInfo[2]);

                System.out.println("Checked: " + step + ", " + speed + " rpms, " + time + " sec");
            } catch(NumberFormatException nfe) {
                JOptionPane.showMessageDialog(this,
                        "There is an error in step sequence #" + step + ". Please dobule check ...",
                        "Step Sequence Error",
                        JOptionPane.ERROR_MESSAGE);

                stepSeqences = null;
                break;
            }
        }

        return stepSeqences;
    }

    /**
     * Method to read the saved step sequence data
     */
    private void readSavedStepSequence() {
        String filePath = System.getProperty("user.dir") + File.separator + SCKUtils.RAMP_SEQUENCE_FILE;
        String stepSequence = SCKUtils.readFileAsString(filePath);

        if(stepSequence !=null) {
            rampTextArea.setText(stepSequence);
        }
    }

    /**
     * Method to save the step sequence
     * @param filePath
     */
    private void saveStepSequence(String filePath) {
        if(filePath == null) {
            filePath = System.getProperty("user.dir") + File.separator + SCKUtils.RAMP_SEQUENCE_FILE;
        }

        String content = rampTextArea.getText();
        SCKUtils.writeStringToFile(content, filePath);
    }

    private void thisWindowClosed(WindowEvent e) {
        exitButtonActionPerformed(null);
    }

    /**
     * Run the motor profile
     *
     * @param e
     */
    private void motorProfileButtonActionPerformed(ActionEvent e) {
        if(!sckRunning && miMTalk.currentMotor == MiMTalk.MotorType.BLDC) {
            startStopButton.setSelected(true);
            motorProfileButton.setEnabled(false);

            consoleTextArea.setText("");
            miMTalk.setConsole(consoleTextArea);

            runMotorProfile();
        } else {
            consoleTextArea.setText("Not supported for SCK-300S");
        }
    }

    /**
     * Run profile of the motor
     */
    private void runMotorProfile() {
        // create a swing worker to run the sequence in the background
        SwingWorker worker = new SwingWorker<Boolean, Void>() {
            @Override
            public Boolean doInBackground() {
                sckRunning = true;

                // turn the motor on
                miMTalk.motorOn();

                try {
                    miMTalk.getMotorProfile(50);
                } catch (Exception e) {
                    printMessage("Error Running Profile ...");
                    e.printStackTrace();
                }

                // stop the motor now
                miMTalk.motorOff();
                miMTalk.setConsole(null);
                sckRunning = false;

                // reset the labels and start stop button
                startStopButton.setSelected(false);
                motorProfileButton.setEnabled(true);

                return true;
            }
        };

        worker.execute();
    }

    /**
     * Clear the output console
     *
     * @param e
     */
    private void clearButtonActionPerformed(ActionEvent e) {
        consoleTextArea.setText("");
    }

    /**
     * If tick model is selected then we need to set the selection to SCK-300S
     * @param e
     */
    private void ticModel(ActionEvent e) {
        sckComboBox.setSelectedIndex(2);
    }

    /**
     * Set that SCK-300 or SCK-300P is selected
     * @param e
     */
    private void mimModel(ActionEvent e) {
        sckComboBox.setSelectedIndex(1);
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        // Generated using JFormDesigner non-commercial license
        dialogPane = new JPanel();
        contentPanel = new JPanel();
        connectButton = new JButton();
        label1 = new JLabel();
        portComboBox = new JComboBox<>();
        closePortButton = new JButton();
        label7 = new JLabel();
        mimModelRadioButton = new JRadioButton();
        ticModelRadioButton = new JRadioButton();
        startStopButton = new JToggleButton();
        upButton = new JButton();
        downButton = new JButton();
        label3 = new JLabel();
        incrementTextField = new JTextField();
        label5 = new JLabel();
        speedTextField = new JTextField();
        accTextField = new JTextField();
        rampButton = new JButton();
        motorProfileButton = new JButton();
        scrollPane2 = new JScrollPane();
        rampTextArea = new JTextArea();
        scrollPane1 = new JScrollPane();
        consoleTextArea = new JTextArea();
        label2 = new JLabel();
        speedLabel = new JLabel();
        label4 = new JLabel();
        spinTimeLabel = new JLabel();
        rampStepLabel = new JLabel();
        buttonBar = new JPanel();
        sckComboBox = new JComboBox<>();
        clearButton = new JButton();
        exitButton = new JButton();

        //======== this ========
        setTitle("SCKTalk [MiM-nano & Tic] v1.2.1 (05/16/2023)");
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                thisWindowClosed(e);
            }
        });
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== dialogPane ========
        {
            dialogPane.setBorder(Borders.DIALOG_BORDER);
            dialogPane.setLayout(new BorderLayout());

            //======== contentPanel ========
            {
                contentPanel.setLayout(new FormLayout(
                    "4*(default, $lcgap), default:grow, $lcgap, default",
                    "4*(default, $lgap), fill:default:grow, $lgap, default"));

                //---- connectButton ----
                connectButton.setText("CONNECT");
                connectButton.setBackground(Color.yellow);
                connectButton.addActionListener(e -> connectButtonActionPerformed(e));
                contentPanel.add(connectButton, CC.xy(1, 1));

                //---- label1 ----
                label1.setText(" COMM Port");
                contentPanel.add(label1, CC.xy(3, 1));

                //---- portComboBox ----
                portComboBox.setEditable(true);
                portComboBox.setModel(new DefaultComboBoxModel<>(new String[] {
                    "COM1",
                    "COM2",
                    "COM3",
                    "COM4",
                    "COM5",
                    "COM6"
                }));
                contentPanel.add(portComboBox, CC.xywh(5, 1, 3, 1));

                //---- closePortButton ----
                closePortButton.setText("Close Port");
                closePortButton.addActionListener(e -> closePortButtonActionPerformed(e));
                contentPanel.add(closePortButton, CC.xy(9, 1));

                //---- label7 ----
                label7.setText("Select Model");
                contentPanel.add(label7, CC.xy(1, 3));

                //---- mimModelRadioButton ----
                mimModelRadioButton.setText("SCK 300/P (MiM)");
                mimModelRadioButton.setSelected(true);
                mimModelRadioButton.addActionListener(e -> mimModel(e));
                contentPanel.add(mimModelRadioButton, CC.xywh(3, 3, 3, 1));

                //---- ticModelRadioButton ----
                ticModelRadioButton.setText("SCK-300S (Tic)");
                ticModelRadioButton.addActionListener(e -> ticModel(e));
                contentPanel.add(ticModelRadioButton, CC.xy(7, 3));

                //---- startStopButton ----
                startStopButton.setText("Start/Stop");
                startStopButton.setBackground(UIManager.getColor("Button.background"));
                startStopButton.addActionListener(e -> startStopButtonActionPerformed(e));
                contentPanel.add(startStopButton, CC.xy(1, 5));

                //---- upButton ----
                upButton.setText("Up");
                upButton.addActionListener(e -> upButtonActionPerformed(e));
                contentPanel.add(upButton, CC.xy(3, 5));

                //---- downButton ----
                downButton.setText("Down");
                downButton.addActionListener(e -> downButtonActionPerformed(e));
                contentPanel.add(downButton, CC.xy(5, 5));

                //---- label3 ----
                label3.setText(" Increment (rpm)");
                contentPanel.add(label3, CC.xy(7, 5));

                //---- incrementTextField ----
                incrementTextField.setText("100");
                incrementTextField.addActionListener(e -> incrementTextFieldActionPerformed(e));
                contentPanel.add(incrementTextField, CC.xy(9, 5));

                //---- label5 ----
                label5.setText("Set Speed / Acc.");
                contentPanel.add(label5, CC.xy(1, 7));

                //---- speedTextField ----
                speedTextField.setText("3250");
                speedTextField.addActionListener(e -> speedTextFieldActionPerformed(e));
                contentPanel.add(speedTextField, CC.xy(3, 7));

                //---- accTextField ----
                accTextField.setText("500");
                contentPanel.add(accTextField, CC.xy(5, 7));

                //---- rampButton ----
                rampButton.setText("Run Ramp Sequence");
                rampButton.addActionListener(e -> rampButtonActionPerformed(e));
                contentPanel.add(rampButton, CC.xy(7, 7));

                //---- motorProfileButton ----
                motorProfileButton.setText("Get Motor Profile");
                motorProfileButton.addActionListener(e -> motorProfileButtonActionPerformed(e));
                contentPanel.add(motorProfileButton, CC.xy(9, 7));

                //======== scrollPane2 ========
                {

                    //---- rampTextArea ----
                    rampTextArea.setText("Step, Speed (rpm), Dwell Time (s)\n1, 500, 30\n2, 1400, 40\n3, 3200, 60\n4, 1500, 10");
                    scrollPane2.setViewportView(rampTextArea);
                }
                contentPanel.add(scrollPane2, CC.xywh(1, 9, 5, 1));

                //======== scrollPane1 ========
                {

                    //---- consoleTextArea ----
                    consoleTextArea.setText("Output Console:");
                    scrollPane1.setViewportView(consoleTextArea);
                }
                contentPanel.add(scrollPane1, CC.xywh(7, 9, 3, 1));

                //---- label2 ----
                label2.setText("Spin Speed (rpm)");
                contentPanel.add(label2, CC.xy(1, 11));

                //---- speedLabel ----
                speedLabel.setText("00000");
                speedLabel.setForeground(Color.red);
                speedLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
                contentPanel.add(speedLabel, CC.xy(3, 11));

                //---- label4 ----
                label4.setText("Spin Time (s)");
                contentPanel.add(label4, CC.xy(5, 11));

                //---- spinTimeLabel ----
                spinTimeLabel.setText("00000");
                spinTimeLabel.setForeground(Color.red);
                spinTimeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
                contentPanel.add(spinTimeLabel, CC.xy(7, 11));

                //---- rampStepLabel ----
                rampStepLabel.setText("Ramp Step # 0");
                rampStepLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
                contentPanel.add(rampStepLabel, CC.xy(9, 11));
            }
            dialogPane.add(contentPanel, BorderLayout.CENTER);

            //======== buttonBar ========
            {
                buttonBar.setBorder(Borders.BUTTON_BAR_GAP_BORDER);
                buttonBar.setLayout(new FormLayout(
                    "2*($lcgap, default), $glue, $button, $rgap, $button",
                    "pref"));

                //---- sckComboBox ----
                sckComboBox.setModel(new DefaultComboBoxModel<>(new String[] {
                    "SCK-300_MIM: 6000, 5,  740, 200",
                    "SCK-300P_MIM: 8000, 0, 960, 500",
                    "SCK-300S_TIC: 5000, 4, 96, 0",
                    "SCK-TEST:10000, 5, 740, 200"
                }));
                sckComboBox.setEditable(true);
                sckComboBox.addActionListener(e -> sckComboBoxActionPerformed(e));
                buttonBar.add(sckComboBox, CC.xywh(2, 1, 4, 1));

                //---- clearButton ----
                clearButton.setText("Clear");
                clearButton.addActionListener(e -> clearButtonActionPerformed(e));
                buttonBar.add(clearButton, CC.xy(6, 1));

                //---- exitButton ----
                exitButton.setText("Exit");
                exitButton.addActionListener(e -> exitButtonActionPerformed(e));
                buttonBar.add(exitButton, CC.xy(8, 1));
            }
            dialogPane.add(buttonBar, BorderLayout.SOUTH);
        }
        contentPane.add(dialogPane, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(getOwner());

        //---- buttonGroup1 ----
        ButtonGroup buttonGroup1 = new ButtonGroup();
        buttonGroup1.add(mimModelRadioButton);
        buttonGroup1.add(ticModelRadioButton);
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    // Generated using JFormDesigner non-commercial license
    private JPanel dialogPane;
    private JPanel contentPanel;
    private JButton connectButton;
    private JLabel label1;
    private JComboBox<String> portComboBox;
    private JButton closePortButton;
    private JLabel label7;
    private JRadioButton mimModelRadioButton;
    private JRadioButton ticModelRadioButton;
    private JToggleButton startStopButton;
    private JButton upButton;
    private JButton downButton;
    private JLabel label3;
    private JTextField incrementTextField;
    private JLabel label5;
    private JTextField speedTextField;
    private JTextField accTextField;
    private JButton rampButton;
    private JButton motorProfileButton;
    private JScrollPane scrollPane2;
    private JTextArea rampTextArea;
    private JScrollPane scrollPane1;
    private JTextArea consoleTextArea;
    private JLabel label2;
    private JLabel speedLabel;
    private JLabel label4;
    private JLabel spinTimeLabel;
    private JLabel rampStepLabel;
    private JPanel buttonBar;
    private JComboBox<String> sckComboBox;
    private JButton clearButton;
    private JButton exitButton;
    // JFormDesigner - End of variables declaration  //GEN-END:variables

    // Main method for lunching the application
    public static void main(String[] args) {
        SCKTalkFrame frame = new SCKTalkFrame();
        frame.pack();
        frame.setSize(650, 400);
        frame.setVisible(true);
    }
}
