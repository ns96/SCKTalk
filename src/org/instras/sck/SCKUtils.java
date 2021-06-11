package org.instras.sck;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * A simple utility class for housing utility methods
 */

public class SCKUtils {
    // name of the file that stores the ramp step sequence
    public static final String RAMP_SEQUENCE_FILE = "sck_ramp_sequence.txt";

    /**
     * Method to read a text file into string
     * @param filePath
     * @return string containing the contents of the file
     */
    public static String readFileAsString(String filePath) {
        String content = null;

        try {
            System.out.println("Reading file: " + filePath + " ...");
            content = new String ( Files.readAllBytes( Paths.get(filePath) ) );
        }
        catch (IOException e) {
            System.out.println("Missing file: " + filePath);
        }

        return content;
    }

    /**
     * Method to write a string to a file
     * @param content
     * @param filePath
     * @return
     */
    public static boolean writeStringToFile(String content, String filePath) {
        try {
            System.out.println("Writing file: " + filePath + " ...");
            File output = new File(filePath);
            FileWriter writer = new FileWriter(output);

            writer.write(content);
            writer.flush();
            writer.close();
        } catch (IOException ex) {
            return false;
        }

        return true;
    }

    /**
     * Left Zero pad an integer with five zeroes
     *
     * @param value
     * @return the number with left zero pad
     */
    public static String zeroPad(int value) {
        return String.format("%05d", value);
    }
}
