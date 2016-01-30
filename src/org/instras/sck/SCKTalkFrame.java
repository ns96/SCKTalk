/*
 * Created by JFormDesigner on Sat Apr 13 08:17:57 EDT 2013
 */

package org.instras.sck;

import java.awt.*;
import javax.swing.*;
import com.jgoodies.forms.factories.*;
import com.jgoodies.forms.layout.*;

/**
 * @author Nathan Stevens
 */
public class SCKTalkFrame extends JFrame {
    public SCKTalkFrame() {
        initComponents();
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        // Generated using JFormDesigner non-commercial license
        dialogPane = new JPanel();
        contentPanel = new JPanel();
        connectButton = new JButton();
        label1 = new JLabel();
        comTextField = new JTextField();
        stopButton = new JButton();
        upButton = new JButton();
        downButton = new JButton();
        label3 = new JLabel();
        incrementTextField = new JTextField();
        setPWMButton = new JButton();
        setPWMTextField = new JTextField();
        setRPMButton = new JButton();
        setRPMTextField = new JTextField();
        motorProfileButton = new JButton();
        scrollPane1 = new JScrollPane();
        consoleTextArea = new JTextArea();
        buttonBar = new JPanel();
        okButton = new JButton();
        CellConstraints cc = new CellConstraints();

        //======== this ========
        setTitle("SCKTalk v0.2 (11/25/2014)");
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== dialogPane ========
        {
            dialogPane.setBorder(Borders.DIALOG_BORDER);
            dialogPane.setLayout(new BorderLayout());

            //======== contentPanel ========
            {
                contentPanel.setLayout(new FormLayout(
                    new ColumnSpec[] {
                        FormFactory.DEFAULT_COLSPEC,
                        FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                        FormFactory.DEFAULT_COLSPEC,
                        FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                        FormFactory.DEFAULT_COLSPEC,
                        FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                        FormFactory.DEFAULT_COLSPEC,
                        FormFactory.LABEL_COMPONENT_GAP_COLSPEC,
                        new ColumnSpec(ColumnSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW)
                    },
                    new RowSpec[] {
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.LINE_GAP_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.LINE_GAP_ROWSPEC,
                        FormFactory.DEFAULT_ROWSPEC,
                        FormFactory.LINE_GAP_ROWSPEC,
                        new RowSpec(RowSpec.FILL, Sizes.DEFAULT, FormSpec.DEFAULT_GROW)
                    }));

                //---- connectButton ----
                connectButton.setText("Connect");
                contentPanel.add(connectButton, cc.xy(1, 1));

                //---- label1 ----
                label1.setText(" COM");
                contentPanel.add(label1, cc.xy(3, 1));
                contentPanel.add(comTextField, cc.xy(5, 1));

                //---- stopButton ----
                stopButton.setText("Stop");
                contentPanel.add(stopButton, cc.xy(1, 3));

                //---- upButton ----
                upButton.setText("Up");
                contentPanel.add(upButton, cc.xy(3, 3));

                //---- downButton ----
                downButton.setText("Down");
                contentPanel.add(downButton, cc.xy(5, 3));

                //---- label3 ----
                label3.setText(" Increment");
                contentPanel.add(label3, cc.xy(7, 3));

                //---- incrementTextField ----
                incrementTextField.setText("10");
                contentPanel.add(incrementTextField, cc.xy(9, 3));

                //---- setPWMButton ----
                setPWMButton.setText("Set PWM");
                contentPanel.add(setPWMButton, cc.xy(1, 5));
                contentPanel.add(setPWMTextField, cc.xy(3, 5));

                //---- setRPMButton ----
                setRPMButton.setText("Set RPM");
                contentPanel.add(setRPMButton, cc.xy(5, 5));
                contentPanel.add(setRPMTextField, cc.xy(7, 5));

                //---- motorProfileButton ----
                motorProfileButton.setText("Motor Profile");
                contentPanel.add(motorProfileButton, cc.xy(9, 5));

                //======== scrollPane1 ========
                {
                    scrollPane1.setViewportView(consoleTextArea);
                }
                contentPanel.add(scrollPane1, cc.xywh(1, 7, 9, 1));
            }
            dialogPane.add(contentPanel, BorderLayout.CENTER);

            //======== buttonBar ========
            {
                buttonBar.setBorder(Borders.BUTTON_BAR_GAP_BORDER);
                buttonBar.setLayout(new FormLayout(
                    new ColumnSpec[] {
                        FormFactory.GLUE_COLSPEC,
                        FormFactory.BUTTON_COLSPEC,
                        FormFactory.RELATED_GAP_COLSPEC,
                        FormFactory.BUTTON_COLSPEC
                    },
                    RowSpec.decodeSpecs("pref")));

                //---- okButton ----
                okButton.setText("Close");
                buttonBar.add(okButton, cc.xy(4, 1));
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
    private JButton connectButton;
    private JLabel label1;
    private JTextField comTextField;
    private JButton stopButton;
    private JButton upButton;
    private JButton downButton;
    private JLabel label3;
    private JTextField incrementTextField;
    private JButton setPWMButton;
    private JTextField setPWMTextField;
    private JButton setRPMButton;
    private JTextField setRPMTextField;
    private JButton motorProfileButton;
    private JScrollPane scrollPane1;
    private JTextArea consoleTextArea;
    private JPanel buttonBar;
    private JButton okButton;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}
