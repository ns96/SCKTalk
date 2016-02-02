/*
 * Created by JFormDesigner on Sat Jan 30 17:18:28 EST 2016
 */

package org.instras.sck;

import java.awt.*;
import javax.swing.*;
import javax.swing.border.*;

/**
 * @author Nathan Stevens
 */
public class DipCoaterSimulator extends JDialog {
    public DipCoaterSimulator(Frame parent) {
        super(parent);
        initComponents();
    }

    public void setPosition(int position) {
        stageSlider.setValue(position);
    }

    public int getPosition() {
        return stageSlider.getValue();
    }

    private void initComponents() {
        // JFormDesigner - Component initialization - DO NOT MODIFY  //GEN-BEGIN:initComponents
        // Generated using JFormDesigner non-commercial license
        dialogPane = new JPanel();
        contentPanel = new JPanel();
        stageSlider = new JSlider();
        buttonBar = new JPanel();

        //======== this ========
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        Container contentPane = getContentPane();
        contentPane.setLayout(new BorderLayout());

        //======== dialogPane ========
        {
            dialogPane.setBorder(new EmptyBorder(12, 12, 12, 12));
            dialogPane.setLayout(new BorderLayout());

            //======== contentPanel ========
            {
                contentPanel.setLayout(new BorderLayout());

                //---- stageSlider ----
                stageSlider.setOrientation(SwingConstants.VERTICAL);
                stageSlider.setMajorTickSpacing(10);
                stageSlider.setMinorTickSpacing(1);
                stageSlider.setPaintTicks(true);
                stageSlider.setValue(90);
                stageSlider.setEnabled(false);
                stageSlider.setPaintLabels(true);
                contentPanel.add(stageSlider, BorderLayout.CENTER);
            }
            dialogPane.add(contentPanel, BorderLayout.CENTER);

            //======== buttonBar ========
            {
                buttonBar.setBorder(new EmptyBorder(12, 0, 0, 0));
                buttonBar.setLayout(new GridBagLayout());
                ((GridBagLayout)buttonBar.getLayout()).columnWidths = new int[] {0, 80};
                ((GridBagLayout)buttonBar.getLayout()).columnWeights = new double[] {1.0, 0.0};
            }
            dialogPane.add(buttonBar, BorderLayout.SOUTH);
        }
        contentPane.add(dialogPane, BorderLayout.CENTER);
        setSize(120, 615);
        setLocationRelativeTo(getOwner());
        // JFormDesigner - End of component initialization  //GEN-END:initComponents
    }

    // JFormDesigner - Variables declaration - DO NOT MODIFY  //GEN-BEGIN:variables
    // Generated using JFormDesigner non-commercial license
    private JPanel dialogPane;
    private JPanel contentPanel;
    private JSlider stageSlider;
    private JPanel buttonBar;
    // JFormDesigner - End of variables declaration  //GEN-END:variables
}
