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
        button5 = new JButton();
        label1 = new JLabel();
        comTextField = new JTextField();
        label2 = new JLabel();
        baudTextField = new JTextField();
        button1 = new JButton();
        button2 = new JButton();
        button3 = new JButton();
        label3 = new JLabel();
        textField3 = new JTextField();
        button4 = new JButton();
        textField1 = new JTextField();
        scrollPane1 = new JScrollPane();
        textArea1 = new JTextArea();
        buttonBar = new JPanel();
        okButton = new JButton();
        CellConstraints cc = new CellConstraints();

        //======== this ========
        setTitle("SCKTalk v0.1 (05/05/2013)");
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

                //---- button5 ----
                button5.setText("Connect");
                contentPanel.add(button5, cc.xy(1, 1));

                //---- label1 ----
                label1.setText(" COM");
                contentPanel.add(label1, cc.xy(3, 1));
                contentPanel.add(comTextField, cc.xy(5, 1));

                //---- label2 ----
                label2.setText(" Baud");
                contentPanel.add(label2, cc.xy(7, 1));

                //---- baudTextField ----
                baudTextField.setText("9600");
                contentPanel.add(baudTextField, cc.xy(9, 1));

                //---- button1 ----
                button1.setText("Stop");
                contentPanel.add(button1, cc.xy(1, 3));

                //---- button2 ----
                button2.setText("Up");
                contentPanel.add(button2, cc.xy(3, 3));

                //---- button3 ----
                button3.setText("Down");
                contentPanel.add(button3, cc.xy(5, 3));

                //---- label3 ----
                label3.setText(" Increment");
                contentPanel.add(label3, cc.xy(7, 3));

                //---- textField3 ----
                textField3.setText("10");
                contentPanel.add(textField3, cc.xy(9, 3));

                //---- button4 ----
                button4.setText("Set PWM");
                contentPanel.add(button4, cc.xy(1, 5));
                contentPanel.add(textField1, cc.xywh(3, 5, 7, 1));

                //======== scrollPane1 ========
                {
                    scrollPane1.setViewportView(textArea1);
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
    private JButton button5;
    private JLabel label1;
    private JTextField comTextField;
    private JLabel label2;
    private JTextField baudTextField;
    private JButton button1;
    private JButton button2;
    private JButton button3;
    private JLabel label3;
    private JTextField textField3;
    private JButton button4;
    private JTextField textField1;
    private JScrollPane scrollPane1;
    private JTextArea textArea1;
    private JPanel buttonBar;
    private JButton okButton;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}
