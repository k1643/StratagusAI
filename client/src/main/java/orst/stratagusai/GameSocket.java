package orst.stratagusai;

import java.io.*;
import java.net.*;
import org.apache.log4j.Logger;

public class GameSocket {
    private static final Logger log = Logger.getLogger(GameSocket.class);

    /** log all messages.  This can be enabled in log4j.properties */
    private static final Logger msglog = Logger.getLogger("msglog");

    final private int portAddress = 4870;
    private Socket socket;
    private BufferedReader in;
    private BufferedWriter out;

    /** connect to game server on localhost */
    public GameSocket() throws IOException {
        this("localhost");
    }

    public GameSocket(String hostname) throws IOException {
        socket = new Socket(hostname, portAddress);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        log.debug("Connected");
    }

    public void close() {
        try {
            socket.close();
            log.debug("Socket Connection Closed");
        } catch (IOException e) {
            log.warn(e.toString() + " >> Error in Closing Socket Connection");
        }
    }

    public String readFromSocket() {
        StringBuffer response = new StringBuffer("");
        try {
            int openBraces = 0;
            int closeBraces = 0;
            while (true) {
                // BufferedReader, InputStreamReader,
                String line = in.readLine();
                int len = line.length();
                for (int i = 0; i < len; i++) {
                    char c = line.charAt(i);
                    if (c == '(') {
                        openBraces++;
                    } else if (c == ')') {
                        closeBraces++;
                    }
                }
                response.append(line);
                if (openBraces == closeBraces) {
                    break;
                }
            }
            String s = response.toString();
            msglog.info(s);
            return response.toString();
        } catch (IOException e) {
            log.error(e.toString() + " >> Socket Read Error");
            throw new RuntimeException(e); // no reason to continue.
        }
    }

    public void writeToSocket(String command) {
        msglog.info(command);
        try {
            out.write(command);
            out.write('\n');
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
