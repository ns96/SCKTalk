/*
 * Created by JFormDesigner on Sat Apr 13 08:17:57 EDT 2013
 */

package org.instras.sck;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import com.jgoodies.forms.factories.*;
import com.jgoodies.forms.layout.*;
import gnu.io.NRSerialPort;

/**
 * @author Nathan Stevens
 */
public class SCKTalkFrame extends JFrame {
    private MiMTalk miMTalk = null; // used to connect to the SCK unit through the serial port

    private int maxSpeed = 8000; // the max speed

    private int minSpeed = 0; // the minimum speed

    private int currentSpeed = 1500; // the current speed

    private boolean sckRunning = false;

    private double roundToValue = 25.0; // used for rounding the rpm

    public SCKTalkFrame() {
        initComponents();

        // set the default comm port
        portComboBox.removeAllItems();
        for(String s: NRSerialPort.getAvailableSerialPorts()) {
            portComboBox.addItem(s);
        }

        sckComboBox.setSelectedIndex(1);

        speedTextField.setText("" + currentSpeed);
    }

    /**
     * Close the program
     *
     * @param e
     */
    private void exitButtonActionPerformed(ActionEvent e) {
        if(miMTalk != null) {
            miMTalk.motorOff();
            miMTalk.close();
        }

        System.exit(0);
    }

    // method to connect to the SCK-300 unit
    private void connectButtonActionPerformed(ActionEvent e) {
        String portName = portComboBox.getSelectedItem().toString();

        try {
            consoleTextArea.setText("Connecting to SCK ...\n");

            miMTalk = new MiMTalk();
            miMTalk.connect(portName);

            String response = miMTalk.getVersion();

            printMessage("SCK Response: " + response);

            if (response.contains("MIM")) {
                printMessage("Connected to SCK unit ...\n");
                sendSCKParameters();
                connectButton.setBackground(Color.ORANGE);
                connectButton.setEnabled(false);
            } else {
                printMessage("Error Connecting to MIM ...\n");
                miMTalk.close();
                miMTalk = null;
            }
        } catch(Exception ex) {
            printMessage("\n\nCOMM PORT ERROR -- " + portName);
            miMTalk = null;
            ex.printStackTrace();
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

            String response = miMTalk.setMotorParameters(startPWM, slope, intercept);
            printMessage("Setting SCK parameters: " + response);
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
        }
    }

    /**
     * Start and stop the SCK
     * @param e
     */
    private void startStopButtonActionPerformed(ActionEvent e) {
        if(miMTalk == null) { return; }

        if(startStopButton.isSelected() && !sckRunning) {
            sckRunning = true;

            // set the current speed
            speedTextFieldActionPerformed(null);

            // send command to go to the desired
            miMTalk.motorOn();
            miMTalk.rampToRPM(currentSpeed);

            // start the thre
            Thread thread = new Thread() {
                public void run() {
                    int ticks = 0;
                    int oldSpeed = currentSpeed;

                    while(sckRunning) {
                        // update the timer
                        String timeString = String.format("%05d", ticks/2);
                        spinTimeLabel.setText(timeString);

                        try {
                            sleep(500);
                        } catch (InterruptedException ex) {
                            break;
                        }

                        // read the rpm and update the speed label
                        String speedString = String.format("%05d", miMTalk.getRPM(roundToValue));
                        speedLabel.setText(speedString);

                        // check to make sure we don't have to update the speed
                        if(currentSpeed != oldSpeed) {
                            miMTalk.setRPM(currentSpeed);
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

            thread.start();
        } else {
            sckRunning = false;

            System.out.println("Stop motor ...");
        }
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
            startStepSequence();
        }
    }

    /**
     * Method to run the ramp step sequence
     */
    private void startStepSequence() {
        // check that the sequence is good
        final String[] stepSeqences = checkStepSequences();
        if(stepSeqences == null) { return; }

        // create a swing worker to run the sequence in the background
        SwingWorker worker = new SwingWorker<Boolean, Void>() {
            @Override
            public Boolean doInBackground() {
                sckRunning = true;

                printMessage("\nStarting Step Sequence ...");

                // iterate over the lines containing the sequences
                for(int i = 1; i < stepSeqences.length; i++) {
                    String[] stepInfo = stepSeqences[i].split("\\s*,\\s*");
                    int targetSpeed = Integer.parseInt(stepInfo[1]);
                    int targetSpinTime = Integer.parseInt(stepInfo[2]);

                    printMessage(stepInfo[0] + ", " + targetSpeed + " rpms, " + targetSpinTime + " sec");

                    // start the motor spinning
                    miMTalk.motorOn();
                    if(i == 1) {
                        miMTalk.rampToRPM(targetSpeed);
                    } else {
                        miMTalk.setRPM(targetSpeed);
                    }

                    // use a loop to keep track of time this step is running
                    int count = 0;
                    while(count < targetSpinTime) {
                        // check to if the motor was stop
                        if(!sckRunning) {
                            printMessage("\nRamp Sequenced Stopped ...");
                            return false;
                        }

                        // update the count timer
                        String countDownTime = String.format("%05d", (targetSpinTime - count));
                        spinTimeLabel.setText(countDownTime);

                        try {
                            Thread.sleep(1000);
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }

                        count++;
                    }
                }

                // stop the motor now
                miMTalk.motorOff();

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

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        // Generated using JFormDesigner Evaluation license - Nathan Stevens
        dialogPane = new JPanel();
        contentPanel = new JPanel();
        connectButton = new JButton();
        label1 = new JLabel();
        portComboBox = new JComboBox<>();
        closePortButton = new JButton();
        startStopButton = new JToggleButton();
        upButton = new JButton();
        downButton = new JButton();
        label3 = new JLabel();
        incrementTextField = new JTextField();
        label5 = new JLabel();
        speedTextField = new JTextField();
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
        setTitle("SCKTalk [MiM-nano] v1.0 (06/11/2021)");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== dialogPane ========
        {
            dialogPane.setBorder(Borders.DIALOG_BORDER);
            dialogPane.setBorder(new javax.swing.border.CompoundBorder(new javax.swing.border.TitledBorder(new javax.swing.border.EmptyBorder(
            0,0,0,0), "JF\u006frmD\u0065sig\u006eer \u0045val\u0075ati\u006fn",javax.swing.border.TitledBorder.CENTER,javax.swing.border.TitledBorder
            .BOTTOM,new java.awt.Font("Dia\u006cog",java.awt.Font.BOLD,12),java.awt.Color.
            red),dialogPane. getBorder()));dialogPane. addPropertyChangeListener(new java.beans.PropertyChangeListener(){@Override public void propertyChange(java.
            beans.PropertyChangeEvent e){if("\u0062ord\u0065r".equals(e.getPropertyName()))throw new RuntimeException();}});
            dialogPane.setLayout(new BorderLayout());

            //======== contentPanel ========
            {
                contentPanel.setLayout(new FormLayout(
                    "4*(default, $lcgap), default:grow",
                    "3*(default, $lgap), fill:default:grow, $lgap, default"));

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

                //---- startStopButton ----
                startStopButton.setText("Start/Stop");
                startStopButton.setBackground(UIManager.getColor("Button.background"));
                startStopButton.addActionListener(e -> startStopButtonActionPerformed(e));
                contentPanel.add(startStopButton, CC.xy(1, 3));

                //---- upButton ----
                upButton.setText("Up");
                upButton.addActionListener(e -> upButtonActionPerformed(e));
                contentPanel.add(upButton, CC.xy(3, 3));

                //---- downButton ----
                downButton.setText("Down");
                downButton.addActionListener(e -> downButtonActionPerformed(e));
                contentPanel.add(downButton, CC.xy(5, 3));

                //---- label3 ----
                label3.setText(" Increment (rpm)");
                contentPanel.add(label3, CC.xy(7, 3));

                //---- incrementTextField ----
                incrementTextField.setText("100");
                incrementTextField.addActionListener(e -> incrementTextFieldActionPerformed(e));
                contentPanel.add(incrementTextField, CC.xy(9, 3));

                //---- label5 ----
                label5.setText("Set Speed");
                contentPanel.add(label5, CC.xy(1, 5));

                //---- speedTextField ----
                speedTextField.setText("3250");
                speedTextField.addActionListener(e -> speedTextFieldActionPerformed(e));
                contentPanel.add(speedTextField, CC.xywh(3, 5, 3, 1));

                //---- rampButton ----
                rampButton.setText("Run Ramp Sequence");
                rampButton.addActionListener(e -> rampButtonActionPerformed(e));
                contentPanel.add(rampButton, CC.xy(7, 5));

                //---- motorProfileButton ----
                motorProfileButton.setText("Run Motor Profile");
                contentPanel.add(motorProfileButton, CC.xy(9, 5));

                //======== scrollPane2 ========
                {

                    //---- rampTextArea ----
                    rampTextArea.setText("Step, Speed (rpm), Dwell Time (s)\n1, 500, 30\n2, 1400, 40\n3, 3200, 60\n4, 1500, 10");
                    scrollPane2.setViewportView(rampTextArea);
                }
                contentPanel.add(scrollPane2, CC.xywh(1, 7, 5, 1));

                //======== scrollPane1 ========
                {

                    //---- consoleTextArea ----
                    consoleTextArea.setText("Output Console:");
                    scrollPane1.setViewportView(consoleTextArea);
                }
                contentPanel.add(scrollPane1, CC.xywh(7, 7, 3, 1));

                //---- label2 ----
                label2.setText("Spin Speed (rpm)");
                contentPanel.add(label2, CC.xy(1, 9));

                //---- speedLabel ----
                speedLabel.setText("00000");
                speedLabel.setForeground(Color.red);
                speedLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
                contentPanel.add(speedLabel, CC.xy(3, 9));

                //---- label4 ----
                label4.setText("Spin Time (s)");
                contentPanel.add(label4, CC.xy(5, 9));

                //---- spinTimeLabel ----
                spinTimeLabel.setText("00000");
                spinTimeLabel.setForeground(Color.red);
                spinTimeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
                contentPanel.add(spinTimeLabel, CC.xy(7, 9));

                //---- rampStepLabel ----
                rampStepLabel.setText("Ramp Step # 0");
                rampStepLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
                contentPanel.add(rampStepLabel, CC.xy(9, 9));
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
                    "SCK-300: 6000, 5,  740, 200",
                    "SCK-300P: 8000, 0, 960, 500",
                    "SCK-300S: 2000, 0, 0,0",
                    "SCK-TEST:10000, 5, 740,200"
                }));
                sckComboBox.setEditable(true);
                sckComboBox.addActionListener(e -> sckComboBoxActionPerformed(e));
                buttonBar.add(sckComboBox, CC.xywh(2, 1, 4, 1));

                //---- clearButton ----
                clearButton.setText("Clear");
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
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    // Generated using JFormDesigner Evaluation license - Nathan Stevens
    private JPanel dialogPane;
    private JPanel contentPanel;
    private JButton connectButton;
    private JLabel label1;
    private JComboBox<String> portComboBox;
    private JButton closePortButton;
    private JToggleButton startStopButton;
    private JButton upButton;
    private JButton downButton;
    private JLabel label3;
    private JTextField incrementTextField;
    private JLabel label5;
    private JTextField speedTextField;
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
