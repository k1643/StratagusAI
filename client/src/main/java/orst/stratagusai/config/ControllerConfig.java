package orst.stratagusai.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Brian
 */
public class ControllerConfig {

    private String controllerClassName;

    /**  */
    private Map<String,Object> params = new LinkedHashMap<String,Object>();
    private List<ParamList> paramLists = new ArrayList<ParamList>();

    public String getControllerClassName() {
        return controllerClassName;
    }

    public void setControllerClassName(String agentClassName) {
        this.controllerClassName = agentClassName;
    }

    public Object getParam(String name) {
        return params.get(name);
    }

    public void setParams(Map<String, Object> params) {
        this.params = params;
    }

    public void setParam(String name, String value) {
        params.put(name, value);
    }

    public List<ParamList> getParamLists() {
        return paramLists;
    }

    public void setParamLists(List<ParamList> paramLists) {
        this.paramLists = paramLists;
    }

    public void addParamList(ParamList l) {
        paramLists.add(l);
    }
}
