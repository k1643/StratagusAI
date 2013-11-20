package orst.stratagusai.stratplan.mgr;

/**
 * Exception that is thrown when a manager does not hold up its side of the
 * contract for task termination.
 * @author sean
 */
public class TaskTerminationException extends Exception {

    public TaskTerminationException() {
        super();
    }
    
    public TaskTerminationException(String msg) {
        super(msg);
    }

    public TaskTerminationException(String msg, Throwable t) {
        super(msg, t);
    }
}
