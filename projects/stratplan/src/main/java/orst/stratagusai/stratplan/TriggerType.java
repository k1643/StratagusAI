package orst.stratagusai.stratplan;

/**
 * Declares the trigger types allowed in the strategy plan graph.
 * EndStart - trigger from end port of a task to start port of another task.
 * StartStart - trigger from start port of a task to start port of another task.
 * EndEnd - trigger from end port of a task to end port of another task.
 * StartEnd - trigger from start port of a task to end port of another task.
 * @author Brian, Sean
 */
public enum TriggerType {
    EndStart, StartStart, EndEnd, StartEnd
}
