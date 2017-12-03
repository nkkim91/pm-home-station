package pmstation.serial;

import com.fazecast.jSerialComm.SerialPort;

import org.apache.commons.lang3.SystemUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pmstation.core.serial.ISerialUART;
import pmstation.core.serial.SerialUARTUtils;

public class SerialUART implements ISerialUART {
    private static final Logger logger = LoggerFactory.getLogger(SerialUART.class);

    //TODO: read values from config file
    private static final int TIMEOUT_READ = 2000; //[ms]
    private static final int TIMEOUT_WRITE = 2000; //[ms]
    private static final int BAUD_RATE = 9600;

    private SerialPort comPort;

    public boolean openPort() {
        SerialPort[] ports = SerialPort.getCommPorts();
        if (ports.length == 0) {
            logger.warn("No serial ports available!");
            return false;
        }
        logger.debug("Got {} serial ports available", ports.length);
        int portToUse = SystemUtils.IS_OS_LINUX ? 0 : -1;

        for (int i = 0; !SystemUtils.IS_OS_LINUX && i < ports.length; i++) {
            SerialPort sp = ports[i];
            logger.debug("\t- {}, {}", sp.getSystemPortName(), sp.getDescriptivePortName());
            if (isSerialPort(sp)) {
                portToUse = i;
            }
        }
        if (portToUse < 0) {
            logger.warn("No relevant serial usb found on this system!");
            return false;
        }
        comPort = ports[portToUse];
        logger.info("Going to use the following port: {}", comPort.getSystemPortName());

        comPort.setFlowControl(SerialPort.FLOW_CONTROL_DISABLED);
        comPort.setComPortParameters(BAUD_RATE, 8,
                                     SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
        comPort.setComPortTimeouts(
                SerialPort.TIMEOUT_READ_BLOCKING | SerialPort.TIMEOUT_WRITE_BLOCKING,
                TIMEOUT_READ,
                TIMEOUT_WRITE
                                  );

        logger.debug("Going to open the port...");
        boolean result = comPort.openPort();
        logger.debug("Port opened? {}", result);
        return result;
    }

    public void closePort() {
        if (comPort != null) {
            // ???
            // serialPort.closePort();
            comPort.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);
            comPort.removeDataListener();
            logger.debug("Going to close the port...");
            boolean result = comPort.closePort();
            logger.debug("Port closed? {}", result);
        }
    }

    public byte[] readBytes(int dataLenght) {
        byte[] readBuffer = new byte[dataLenght];
        comPort.readBytes(readBuffer, readBuffer.length);
        logger.debug("ReadBuffer:\n{}", SerialUARTUtils.bytesToHexString(readBuffer));

        return readBuffer;
    }

    public void writeBytes(byte[] writeBuffer) {
        logger.debug("ReadBuffer:\n{}", SerialUARTUtils.bytesToHexString(writeBuffer));
        comPort.writeBytes(writeBuffer, writeBuffer.length);
    }

    private boolean isSerialPort(SerialPort sp) {
        // TODO auto-discovery for linux
        return ((SystemUtils.IS_OS_MAC_OSX && sp.getSystemPortName().startsWith("cu") && sp.getSystemPortName().toLowerCase().contains("usbserial")) ||
                (SystemUtils.IS_OS_WINDOWS && sp.getDescriptivePortName().toLowerCase().contains("serial")) // ||
                //(isLinux)
        );
    }
}