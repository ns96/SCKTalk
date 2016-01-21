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
    // variebles for the linear stage
    double stepsPerRev = 200;
    double mmPerStep = 0.003175;
    double mmPerRev = stepsPerRev*mmPerStep;
    int mmPerMin = 0;

    // used this to keep track of the time
    private long startTime = 0;
    private long stopTime = 0;
    private int moveTime = 0;

    private int speed = 0; // speed in RPMs

    private int travel = 0; // the distance travel

    private int enterCount = 0; // keep track of the number of time enter was pressed

    private boolean stopped = false;

    private String statusText = "STOPPED";

    private SCKTalk sckTalk = new SCKTalk();

    public DipCoater() {
        initComponents();
    }

    private void speedSpinnerStateChanged() {
        mmPerMin = (Integer) speedSpinner.getValue();
        speedLabel1.setText(mmPerMin + "MM/MIN");

        // calculate the speed in rpm
        speed  = (int)(mmPerMin/mmPerRev);
        speedLabel2.setText(speed + "REV/MIN");
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
        sckTalk.connect("COM8");

        String response = sckTalk.setModePC();

        if (response.equals("OK")) {
            System.out.println("Connected to ST-V3\n\n");
            connButton.setBackground(Color.GREEN);
            connButton.setEnabled(false);

            enterButton.setEnabled(true);
            upButton.setEnabled(true);
            downButton.setEnabled(true);
        }
    }

    private void upButtonItemStateChanged(ItemEvent e) {
        // reset the counter for that allows for automatic up and down
        enterCount= 0;

        if (e.getStateChange() == ItemEvent.SELECTED) {
            sckTalk.sendCommand("SET S2 CW");
            sckTalk.sendCommand("SET S1 " + speed);
            startTime = System.currentTimeMillis();
        } else {
            sckTalk.sendCommand("SET S1 0");
            stopTime = System.currentTimeMillis();
            calculateTime("UP");
        }
    }

    private void downButtonItemStateChanged(ItemEvent e) {
        // reset the counter for that allows for automatic up and down
        enterCount= 0;

        if (e.getStateChange() == ItemEvent.SELECTED) {
            sckTalk.sendCommand("SET S2 CCW");
            sckTalk.sendCommand("SET S1 " + speed);
            startTime = System.currentTimeMillis();
        } else {
            sckTalk.sendCommand("SET S1 0");
            stopTime = System.currentTimeMillis();
            calculateTime("DOWN");
        }
    }

    private void calculateTime(String direction) {
        long elapseTime = stopTime - startTime;
        int timeInSeconds =  (int)(elapseTime / 1000);

        System.out.println("Time in Second: " + timeInSeconds);
        displayLocation(timeInSeconds);

        if (direction.equals("DOWN")) {
            moveTime += timeInSeconds;
            System.out.println("Total Move Time/Travel " + moveTime + " / -" + travel);
        } else {
            System.out.println("Total Travel +" + travel);
            moveTime = 0; // reset the move time
        }
    }

    /**
     * If window is closed make sure to disconnect the ST-V3
     */
    private void thisWindowClosed() {
        exitButtonActionPerformed();
    }

    // this starts the main up and down sequence
    private void enterButtonActionPerformed() {
        enterButton.setEnabled(false);

        // check that we didn't just enter a move time already
        getMoveTime();

        if (enterCount == 0) {
            enterCount++;

            Thread thread = new Thread() {
                public void run() {
                    stopped = false;

                    // now move the arm back up if not stopped
                    sckTalk.sendCommand("SET S2 CW");
                    sckTalk.sendCommand("SET S1 " + speed);

                    int time = 0;
                    while (!stopped && time < moveTime) {
                        displayLocation(time);

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

                    enterButton.setEnabled(true);
                }
            };

            thread.start();
        } else {
            Thread thread = new Thread() {
                public void run() {
                    stopped = false;

                    // start the arm moving down
                    sckTalk.sendCommand("SET S2 CCW");
                    sckTalk.sendCommand("SET S1 " + speed);

                    int time = 0;
                    while (!stopped && time < moveTime) {
                        displayLocation(time);

                        // pause for a second
                        try {
                            sleep(1000);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        time++;
                    }

                    // stop arm at bottom
                    sckTalk.sendCommand("SET S1 " + 0);

                    if (!stopped) {
                        // now move the arm back up if not stopped
                        sckTalk.sendCommand("SET S2 CW");
                        sckTalk.sendCommand("SET S1 " + speed);

                        time = 0;
                        while (!stopped && time < moveTime) {
                            displayLocation(time);

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
                    }

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
    }

    /**
     * Method to allow the move time to be conveniently set
     *
     * @return
     */
    private void getMoveTime() {
        try {
            int value = Integer.parseInt(moveTimeTextField.getText());
            if(value > 0) {
                moveTime = value;
                enterCount = 1;
            }
        } catch (NumberFormatException nfe) {}
    }

    /**
     * Displays the location based on the time
     * @param time
     */
    private void displayLocation(int time) {
        double minutes = (double)time/60;
        travel = (int)(mmPerMin*minutes);
        distanceLabel.setText(travel + " MM");
        System.out.println("Seconds/Travel " + time + "/" + travel);
    }

    /**
     * Calculate the distance traveled
     */
    private void moveTimeTextFieldActionPerformed() {
        try {
            int time = Integer.parseInt(moveTimeTextField.getText());
            displayLocation(time);
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
        distanceLabel = new JLabel();
        speedLabel2 = new JLabel();
        speedLabel1 = new JLabel();
        buttonBar = new JPanel();
        enterButton = new JButton();
        upButton = new JToggleButton();
        downButton = new JToggleButton();
        backButton = new JButton();
        connButton = new JButton();
        exitButton = new JButton();
        CellConstraints cc = new CellConstraints();

        //======== this ========
        setTitle("Dip Coater 0.1");
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
            dialogPane.setBorder(Borders.createEmptyBorder("7dlu, 7dlu, 7dlu, 7dlu"));
            dialogPane.setLayout(new BorderLayout());

            //======== contentPanel ========
            {
                contentPanel.setLayout(new FormLayout(
                    "default:grow, max(default;5dlu), default:grow",
                    "default"));

                //======== scrollPane1 ========
                {

                    //---- consoleTextArea ----
                    consoleTextArea.setRows(6);
                    consoleTextArea.setFont(new Font("Monospaced", Font.BOLD, 24));
                    consoleTextArea.setText("DIP COATER\n\nTRAVEL\t0 MM\t\nSPEED\t000 MM/MIN\nSTATUS\tSTOP/AUTO/MANUAL");
                    scrollPane1.setViewportView(consoleTextArea);
                }
                contentPanel.add(scrollPane1, cc.xy(1, 1));

                //======== panel1 ========
                {
                    panel1.setLayout(new FormLayout(
                        ColumnSpec.decodeSpecs("default:grow"),
                        new RowSpec[] {
                            new RowSpec(RowSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW),
                            FormFactory.LINE_GAP_ROWSPEC,
                            FormFactory.DEFAULT_ROWSPEC,
                            FormFactory.LINE_GAP_ROWSPEC,
                            FormFactory.DEFAULT_ROWSPEC,
                            FormFactory.LINE_GAP_ROWSPEC,
                            FormFactory.DEFAULT_ROWSPEC,
                            FormFactory.LINE_GAP_ROWSPEC,
                            new RowSpec(RowSpec.FILL, Sizes.DEFAULT, FormSpec.NO_GROW)
                        }));

                    //---- speedSpinner ----
                    speedSpinner.setFont(new Font("Tahoma", Font.BOLD, 18));
                    speedSpinner.setModel(new SpinnerNumberModel(0, 0, 200, 10));
                    speedSpinner.addChangeListener(new ChangeListener() {
                        @Override
                        public void stateChanged(ChangeEvent e) {
                            speedSpinnerStateChanged();
                        }
                    });
                    panel1.add(speedSpinner, cc.xy(1, 1));

                    //---- moveTimeTextField ----
                    moveTimeTextField.setText("0");
                    moveTimeTextField.setFont(new Font("Tahoma", Font.BOLD, 12));
                    moveTimeTextField.addActionListener(new ActionListener() {
                        @Override
                        public void actionPerformed(ActionEvent e) {
                            moveTimeTextFieldActionPerformed();
                        }
                    });
                    panel1.add(moveTimeTextField, cc.xy(1, 3));

                    //---- distanceLabel ----
                    distanceLabel.setText("0 mm");
                    distanceLabel.setFont(new Font("Tahoma", Font.BOLD, 12));
                    panel1.add(distanceLabel, cc.xy(1, 5));

                    //---- speedLabel2 ----
                    speedLabel2.setText("0 rpm");
                    speedLabel2.setFont(new Font("Tahoma", Font.BOLD, 12));
                    panel1.add(speedLabel2, cc.xy(1, 7));

                    //---- speedLabel1 ----
                    speedLabel1.setText("0 mm/min");
                    speedLabel1.setFont(new Font("Tahoma", Font.BOLD, 12));
                    speedLabel1.setForeground(Color.blue);
                    panel1.add(speedLabel1, cc.xy(1, 9));
                }
                contentPanel.add(panel1, cc.xy(3, 1, CellConstraints.DEFAULT, CellConstraints.FILL));
            }
            dialogPane.add(contentPanel, BorderLayout.CENTER);

            //======== buttonBar ========
            {
                buttonBar.setBorder(Borders.createEmptyBorder("5dlu, 0dlu, 0dlu, 0dlu"));
                buttonBar.setLayout(new FormLayout(
                    "default:grow, default:grow, default:grow, default:grow, default:grow, default:grow",
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
                buttonBar.add(enterButton, cc.xy(1, 1));

                //---- upButton ----
                upButton.setText("UP");
                upButton.setEnabled(false);
                upButton.addItemListener(new ItemListener() {
                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        upButtonItemStateChanged(e);
                    }
                });
                buttonBar.add(upButton, cc.xy(2, 1));

                //---- downButton ----
                downButton.setText("DOWN");
                downButton.setEnabled(false);
                downButton.addItemListener(new ItemListener() {
                    @Override
                    public void itemStateChanged(ItemEvent e) {
                        downButtonItemStateChanged(e);
                    }
                });
                buttonBar.add(downButton, cc.xy(3, 1));

                //---- backButton ----
                backButton.setText("BACK");
                backButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        backButtonActionPerformed();
                    }
                });
                buttonBar.add(backButton, cc.xy(4, 1));

                //---- connButton ----
                connButton.setText("CONN");
                connButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        connButtonActionPerformed();
                    }
                });
                buttonBar.add(connButton, cc.xy(5, 1));

                //---- exitButton ----
                exitButton.setText("EXIT");
                exitButton.addActionListener(new ActionListener() {
                    @Override
                    public void actionPerformed(ActionEvent e) {
                        exitButtonActionPerformed();
                    }
                });
                buttonBar.add(exitButton, cc.xy(6, 1));
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
    private JLabel distanceLabel;
    private JLabel speedLabel2;
    private JLabel speedLabel1;
    private JPanel buttonBar;
    private JButton enterButton;
    private JToggleButton upButton;
    private JToggleButton downButton;
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
