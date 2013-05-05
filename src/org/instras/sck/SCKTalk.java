package org.instras.sck;

import gnu.io.NRSerialPort;

import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: nathan
 * Date: 4/14/13
 * Time: 9:59 AM
 * To change this template use File | Settings | File Templates.
 */
public class SCKTalk {

    /**
     * Main method. This is just to test library now
     * @param args
     */
    public static void main(String[] args) {
        Set<String> ports = NRSerialPort.getAvailableSerialPorts();

        for(String port: ports) {
            System.out.println("Port: " + port);
        }

    }

}
