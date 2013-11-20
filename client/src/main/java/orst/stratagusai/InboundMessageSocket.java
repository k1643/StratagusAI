package orst.stratagusai;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.concurrent.ConcurrentLinkedQueue;

import org.apache.log4j.Logger;

public class InboundMessageSocket {
	private static final Logger log = Logger.getLogger(InboundMessageSocket.class);
	   /** log all messages.  This can be enabled in log4j.properties */
    private static final Logger cmdlog = Logger.getLogger("cmdlog");

    private ConcurrentLinkedQueue<String> inboundCommandQueue = new ConcurrentLinkedQueue<String>();
    final private int portAddress = 4871;
    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private InboundMessageThread inboundMessageThread;

    /** connect to game server on localhost */
    public InboundMessageSocket() throws IOException {
        this("localhost");
        inboundMessageThread = new InboundMessageThread();
        inboundMessageThread.setPriority(Thread.NORM_PRIORITY);
        inboundMessageThread.start();
    }

    public InboundMessageSocket(String hostname) throws IOException {
        socket = new Socket(hostname, portAddress);
        in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        out = new PrintWriter(socket.getOutputStream(),true);
        log.debug("InboundCommandSocket Connected");
    }

    public void close() {
        try {
            socket.close();
            log.debug("InboundCommandSocket Connection Closed");
        } catch (IOException e) {
            log.warn(e.toString() + " >> Error in Closing InboundCommandSocket Connection");
        }
    }

    public String readFromSocket() {
        StringBuffer inboundMessage = new StringBuffer("");
        try {
            int openBraces = 0;
            int closeBraces = 0;
            //while (in.ready() == false); // continue until the buffer is ready to be read 1st time
            while (true) {
                String line = in.readLine();
                /*int len = line.length();
                for (int i = 0; i < len; i++) {
                    if (line.charAt(i) == '(') {
                        openBraces++;
                    }
                    if (line.charAt(i) == ')') {
                        closeBraces++;
                    }
                }*/
                inboundMessage.append(line);
                if (openBraces == closeBraces) {
                    break;
                }
            }
            String s = inboundMessage.toString();
            cmdlog.info(s);
            return inboundMessage.toString();
        } catch (IOException e) {
            log.debug(e.toString() + " >> Socket Read Error");
            return null;
        }
    }

    public void writeToSocket(String command) {
        cmdlog.info("Send " + command);
        out.println(command);
        out.flush();
    }
    private class InboundMessageThread extends Thread {
    	
    	public InboundMessageThread(){
    		super();
    	}
    	public void run(){
    		while (true){
    			String inboundMessage = readFromSocket();
    			inboundCommandQueue.add(inboundMessage);
    		}
    	}
    }
    
    public String getNextInboundMessage(){
    	String result = null;
    	if (!inboundCommandQueue.isEmpty()){
    		result = inboundCommandQueue.poll();
    	}
    	return result;
    }
}
