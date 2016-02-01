/*
 * Created by JFormDesigner on Thu Jan 21 07:48:42 EST 2016
 */

package org.instras.sck;

import java.awt.*;
import java.awt.event.*;
import javax.swing.*;
import javax.swing.event.*;

import com.jgoodies.forms.factories.*;
import com.jgoodies.forms.layout.*;

/**
 * @author Nathan Stevens
 */
public class DipCoater extends JFrame {
    // variables for the linear stage
    double stepsPerRev = 200;
    double mmPerStep = 0.003175;
    double mmPerRev = stepsPerRev*mmPerStep;

    int mmPerMin = 0; // used to compute the distance

    private int currentTime;  // this keeps track of how long the motor has been moving
    private int moveTime = 0; // keeps track of how long the motor should move in auto mode

    private int speed = 0; // speed in RPMs

    private int travel = 0; // the distance traveled in mm

    private String travelDirection = "+";  // the direction of travel + down, - for up

    private String status = "MANUAL";  // Ready, Manual, or Auto

    private int enterCount = 0; // keep track of the number of time enter was pressed

    private int backCount = 1; // used to see when to return to the main menu

    private boolean stopped = false; // used for stopping the motor moving

    private SCKTalk sckTalk = new SCKTalk(); // thjis provides serial port access to the ST-V3

    private DipCoaterSimulator dipCoaterSimulator;

    private int simulatorPosition = 0; // used to set the position of the simulated stage

    private int simulatorIncrement = 1; // used to simulate the speed of the stage values are 1,2,3,4

    public DipCoater() {
        initComponents();
    }

    /**
     * Set the current speed
     */
    private void speedSpinnerStateChanged() {
        mmPerMin = (Integer) speedSpinner.getValue();

        // calculate the speed in rpm
        speed  = (int)(mmPerMin/mmPerRev);
        speedLabel2.setText(speed + " REV/MIN");

        // if we were in READY mode we need to adjust the moveTime so we move the same distance
        if(status.equals("READY")) {
            boolean hasValue = getMoveTime();
            double timeInMinutes = new Double(travel)/new Double(mmPerMin);
            moveTime = (int)(timeInMinutes*60);

            moveTimeLabel.setText(moveTime + " S");

            if(hasValue) {
                moveTimeTextField.setText("" + moveTime);
            }
        }

        // set the simulated stage speed
        setSimulatorSpeed();

        // update the display
        updateDisplay(0);
    }

    /**
     * Method to set the simulator stage speed
     */
    private void setSimulatorSpeed() {
        int s = mmPerMin/20;

        if(s == 0) {
            simulatorIncrement = 1;
        } else {
            simulatorIncrement = s;
        }
    }


    private void exitButtonActionPerformed() {
        // must catch an exception in case the ST-V3 in not connected
        try {
            sckTalk.close();
        } catch(Exception e) {}

        System.exit(0);
    }

    /**
     * Method to connect to the STV3 controller running the PDC3.2 firmware
     */
    private void connButtonActionPerformed() {
        sckTalk.setTestMode(testModeCheckBox.isSelected());

        sckTalk.connect("COM8");

        String response = sckTalk.setModePC();

        if (response.equals("OK")) {
            System.out.println("Connected to ST-V3\n\n");
            connButton.setBackground(Color.GREEN);
            connButton.setEnabled(false);

            enterButton.setEnabled(true);
            upButton.setEnabled(true);
            downButton.setEnabled(true);
            backButton.setEnabled(true);
        }

        // display the simulator now
        if(testModeCheckBox.isSelected()) {
            dipCoaterSimulator = new DipCoaterSimulator(this);
            dipCoaterSimulator.setVisible(true);
            simulatorPosition = dipCoaterSimulator.getPosition();
        }
    }

    /**
     * Move stage up while the up button is pressed
     */
    private void upButtonStateChanged() {
        boolean pressed = upButton.getModel().isPressed();

        if(pressed) {
            startTimerThread(upButton);
        }
    }

    /**
     * Move the stage down while the button is pressed
     */
    private void downButtonStateChanged() {
        boolean pressed = downButton.getModel().isPressed();

        if(pressed) {
            startTimerThread(downButton);
        }
    }

    /**
     * Method to start a counter thread which keeps track of how long
     * the unit has been moving
     * @param button
     */
    private void startTimerThread(final JButton button) {
        Thread thread = new Thread() {
            public void run() {
                currentTime = 0; // used for calculating distance in real time
                enterCount = 0; // this needs to be reset otherwise the system may think it still in auto mode
                backCount = 0; // reset this so that if the ba ck button is pressed we just stop
                status = "MANUAL";

                // start moving motor up or down depending on which one of the button were pressed
                if(button == upButton) {
                    travelDirection = "-";
                    sckTalk.sendCommand("SET S2 CW");
                } else {
                    travelDirection = "+";
                    sckTalk.sendCommand("SET S2 CCW");
                }

                sckTalk.sendCommand("SET S1 " + speed);

                // start loop now waiting for the button to be released
                while (button.getModel().isPressed()) {
                    calculateTravel(currentTime);
                    updateDisplay(currentTime);

                    if (dipCoaterSimulator != null) {
                        if (button == upButton) {
                            simulatorPosition += simulatorIncrement;
                        } else {
                            simulatorPosition -= simulatorIncrement;
                        }

                        dipCoaterSimulator.setPosition(simulatorPosition);
                    }

                    // pause for a second
                    try {
                        sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }

                    currentTime++;
                }

                // stop the motor now
                sckTalk.sendCommand("SET S1 0");

                if(button == upButton) {
                    calculateMoveTime("UP");
                } else {
                    calculateMoveTime("DOWN");
                }
            }
        };

        thread.start();
    }

    /**
     * Calculate the time in seconds the stepper motor should be moving
     *
     * @param direction
     */
    private void calculateMoveTime(String direction) {
        calculateTravel(currentTime);

        if (direction.equals("DOWN")) {
            moveTime += currentTime;
            //System.out.println("Total Move Time/Travel " + moveTime + " / " + travelDirection + travel);
        } else {
            //System.out.println("Total Travel " + travelDirection + travel);
            moveTime = 0; // reset the move time
        }

        // update the move time label for debugging purposes
        moveTimeLabel.setText(moveTime + " s");
    }

    /**
     * If window is closed make sure to disconnect the ST-V3
     */
    private void thisWindowClosed() {
        exitButtonActionPerformed();
    }

    // this starts the main up and down sequence
    private void enterButtonActionPerformed() {
        // if we coming from the main menu just display the dcoat control screen
        if(backCount == 2) {
            backCount = 1;
            updateDisplay(0);
            return;
        }

        enterButton.setEnabled(false);

        // set the status to AUTO
        status = "AUTO";

        // check that we didn't just enter a move time already for debugging
        // on the ST-V3 this might just be a variable that stored in memory while device is powered on?
        getMoveTime();

        if (enterCount == 0) {
            enterCount++;

            Thread thread = new Thread() {
                public void run() {
                    stopped = false;
                    travelDirection = "-";

                    // now move the arm back up
                    sckTalk.sendCommand("SET S2 CW");
                    sckTalk.sendCommand("SET S1 " + speed);

                    int time = 0;
                    while (!stopped && time < moveTime) {
                        calculateTravel(time);
                        updateDisplay(time);

                        if (dipCoaterSimulator != null) {
                            simulatorPosition += simulatorIncrement;
                            dipCoaterSimulator.setPosition(simulatorPosition);
                        }

                        // pause for a second
                        try {
                            sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        time++;
                    }

                    // now stop the arm at top
                    sckTalk.sendCommand("SET S1 " + 0);

                    // set the status to ready
                    status = "READY";
                    travelDirection = "+";
                    updateDisplay(0);

                    enterButton.setEnabled(true);
                }
            };

            thread.start();
        } else {
            Thread thread = new Thread() {
                public void run() {
                    stopped = false;
                    currentTime = 0;

                    // start the arm moving down
                    travelDirection = "+";
                    sckTalk.sendCommand("SET S2 CCW");
                    sckTalk.sendCommand("SET S1 " + speed);

                    int time = 0;
                    while (!stopped && time < moveTime) {
                        calculateTravel(time);
                        updateDisplay(currentTime);

                        if (dipCoaterSimulator != null) {
                            simulatorPosition -= simulatorIncrement;
                            dipCoaterSimulator.setPosition(simulatorPosition);
                        }

                        // pause for a second
                        try {
                            sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        time++;
                        currentTime++;
                    }

                    // stop arm at bottom
                    sckTalk.sendCommand("SET S1 " + 0);

                    if (!stopped) {
                        // now move the arm back up
                        travelDirection = "-";
                        sckTalk.sendCommand("SET S2 CW");
                        sckTalk.sendCommand("SET S1 " + speed);

                        time = 0;
                        while (!stopped && time < moveTime) {
                            calculateTravel(time);
                            updateDisplay(currentTime);

                            if (dipCoaterSimulator != null) {
                                simulatorPosition += simulatorIncrement;
                                dipCoaterSimulator.setPosition(simulatorPosition);
                            }

                            // pause for a second
                            try {
                                sleep(1000);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }

                            time++;
                            currentTime++;
                        }

                        // now stop the arm at top
                        sckTalk.sendCommand("SET S1 " + 0);
                    }

                    status = "READY";
                    travelDirection = "+";
                    updateDisplay(0);

                    enterButton.setEnabled(true);
                }
            };

            thread.start();
        }
    }

    /**
     * The back button resets everything
     */
    private void backButtonActionPerformed() {
        stopped = true;
        moveTime = 0;
        backCount++;

        if(backCount == 2) {
            displayMainMenu();
        }
    }

    /**
     * Method to allow the move time to be conveniently set
     *
     * @return
     */
    private boolean getMoveTime() {
        try {
            int value = Integer.parseInt(moveTimeTextField.getText());

            if(value > 0) {
                moveTime = value;
                enterCount = 1;
                return true;
            }
        } catch (NumberFormatException nfe) {}

        return false;
    }

    /**
     * Displays the location based on the time
     * @param time
     */
    private void calculateTravel(int time) {
        double minutes = (double)time/60;
        travel = (int)(mmPerMin*minutes);
    }

    /**
     * Update the console with needed information
     * @param time
     */
    private void updateDisplay(int time) {
        String message = "DCOAT CONTROL\n" +
                "\n" +
                "SPEED\t" + String.format("%03d", mmPerMin) + " MM/MIN\n" +
                "TRAVEL\t"  + travelDirection +  String.format("%02d", travel) + " MM\n" +
                "TIME\t" + String.format("%04d", time) + " S\n" +
                "MODE\t" + status;

        consoleTextArea.setText(message);
    }

    /**
     * Display the main menu
     */
    private void displayMainMenu() {
        String message = "SELECT MODE\n" +
                "  ANALOG\n" +
                "  DIGITAL\n" +
                "  RAMP\n" +
                " *DCOAT\n" +
                "  SETUP";

        consoleTextArea.setText(message);
    }

    /**
     * Calculate the distance traveled
     */
    private void moveTimeTextFieldActionPerformed() {
        try {
            int time = Integer.parseInt(moveTimeTextField.getText());
            calculateTravel(time);

            status = "READY";
            travelDirection = "+";
            moveTimeLabel.setText(time + " s");

            updateDisplay(0);
        } catch(NumberFormatException nfe) {}
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        // Generated using JFormDesigner non-commercial license
        dialogPane = new JPanel();
        contentPanel = new JPanel();
        scrollPane1 = new JScrollPane();
        consoleTextArea = new JTextArea();
        panel1 = new JPanel();
        speedSpinner = new JSpinner();
        moveTimeTextField = new JTextField();
        moveTimeLabel = new JLabel();
        speedLabel2 = new JLabel();
        testModeCheckBox = new JCheckBox();
        buttonBar = new JPanel();
        enterButton = new JButton();
        upButton = new JButton();
        downButton = new JButton();
        backButton = new JButton();
        connButton = new JButton();
        exitButton = new JButton();

        //======== this ========
        setTitle("Dip Coater Controller 1.1");
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                thisWindowClosed();
            }
        });
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== dialogPane ========
        {
            dialogPane.setBorder(Borders.createEmptyBorder("9dlu, 9dlu, 9dlu, 9dlu"));
            dialogPane.setLayout(new BorderLayout());

            //======== contentPanel ========
            {
                contentPanel.setLayout(new FormLayout(
                    "default:grow, [5dlu,default], default:grow",
                    "default"));

                //======== scrollPane1 ========
                {

                    //---- consoleTextArea ----
                    consoleTextArea.setRows(6);
                    consoleTextArea.setFont(new Font("Monospaced", Font.BOLD, 24));
                    consoleTextArea.setText("DCOAT CONTROL\n\nSPEED\t000 MM/MIN\nTRAVEL\t+00 MM\t\nTIME\t0000 S\nMODE\tMANUAL");
                    scrollPane1.setViewportView(consoleTextArea);
                }
                contentPanel.add(scrollPane1, CC.xy(1, 1));

                //======== panel1 ========
                {
                    panel1.setLayout(new FormLayout(
                        "default:grow",
                        "fill:default:grow, 3*($lgap, default), $lgap, fill:default"));

                    //---- speedSpinner ----
                    speedSpinner.setFont(new Font("Tahoma", Font.BOLD, 18));
                    speedSpinner.setModel(new SpinnerNumberModel(0, 0, 200, 10));
                    speedSpinner.addChangeListener(new ChangeListener() {
                        @Override
                        public void stateChanged(ChangeEvent e) {
                            speedSpinnerStateChanged();
                        }
                    });
                    panel1.add(speedSpinner, CC.xy(1, 1));

                    //---- moveTimeTextField ----
                    moveTimeTextField.setText("move time (s)");
                    moveTimeTextField.setFont(new Font("Tahoma", Font.BOLD, 12));
                    moveTimeTextField.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            moveTimeTextFieldActionPerformed();
                        }
                    });
                    panel1.add(moveTimeTextField, CC.xy(1, 3));

                    //---- moveTimeLabel ----
                    moveTimeLabel.setText("moveTime");
                    moveTimeLabel.setForeground(new Color(255, 51, 51));
                    panel1.add(moveTimeLabel, CC.xy(1, 5));

                    //---- speedLabel2 ----
                    speedLabel2.setText("0 rpm");
                    speedLabel2.setFont(new Font("Tahoma", Font.BOLD, 12));
                    panel1.add(speedLabel2, CC.xy(1, 7));

                    //---- testModeCheckBox ----
                    testModeCheckBox.setText("Test Mode");
                    testModeCheckBox.setSelected(true);
                    panel1.add(testModeCheckBox, CC.xy(1, 9));
                }
                contentPanel.add(panel1, CC.xy(3, 1, CC.DEFAULT, CC.FILL));
            }
            dialogPane.add(contentPanel, BorderLayout.CENTER);

            //======== buttonBar ========
            {
                buttonBar.setBorder(Borders.createEmptyBorder("4dlu, 0dlu, 0dlu, 0dlu"));
                buttonBar.setLayout(new FormLayout(
                    "6*(default:grow)",
                    "default:grow"));

                //---- enterButton ----
                enterButton.setText("ENTER");
                enterButton.setEnabled(false);
                enterButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        enterButtonActionPerformed();
                    }
                });
                buttonBar.add(enterButton, CC.xy(1, 1));

                //---- upButton ----
                upButton.setText("UP");
                upButton.setEnabled(false);
                upButton.addChangeListener(new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        upButtonStateChanged();
                    }
                });
                buttonBar.add(upButton, CC.xy(2, 1));

                //---- downButton ----
                downButton.setText("DOWN");
                downButton.setEnabled(false);
                downButton.addChangeListener(new ChangeListener() {
                    @Override
                    public void stateChanged(ChangeEvent e) {
                        downButtonStateChanged();
                    }
                });
                buttonBar.add(downButton, CC.xy(3, 1));

                //---- backButton ----
                backButton.setText("BACK");
                backButton.setEnabled(false);
                backButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        backButtonActionPerformed();
                    }
                });
                buttonBar.add(backButton, CC.xy(4, 1));

                //---- connButton ----
                connButton.setText("CONN");
                connButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        connButtonActionPerformed();
                    }
                });
                buttonBar.add(connButton, CC.xy(5, 1));

                //---- exitButton ----
                exitButton.setText("EXIT");
                exitButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        exitButtonActionPerformed();
                    }
                });
                buttonBar.add(exitButton, CC.xy(6, 1));
            }
            dialogPane.add(buttonBar, BorderLayout.SOUTH);
        }
        contentPane.add(dialogPane, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    // Generated using JFormDesigner non-commercial license
    private JPanel dialogPane;
    private JPanel contentPanel;
    private JScrollPane scrollPane1;
    private JTextArea consoleTextArea;
    private JPanel panel1;
    private JSpinner speedSpinner;
    private JTextField moveTimeTextField;
    private JLabel moveTimeLabel;
    private JLabel speedLabel2;
    private JCheckBox testModeCheckBox;
    private JPanel buttonBar;
    private JButton enterButton;
    private JButton upButton;
    private JButton downButton;
    private JButton backButton;
    private JButton connButton;
    private JButton exitButton;
    // JFormDesigner - End of variables declaration  //GEN-END:variables

    /**
     * Main method for running the gui program
     *
     * @param args
     */
    public static void main(String[] args) {
        DipCoater dipCoater = new DipCoater();
        dipCoater.pack();
        dipCoater.setVisible(true);
    }
}
