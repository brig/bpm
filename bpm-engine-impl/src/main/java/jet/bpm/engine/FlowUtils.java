package jet.bpm.engine;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import jet.bpm.engine.api.ExecutionException;
import jet.bpm.engine.commands.ProcessElementCommand;
import jet.bpm.engine.model.AbstractElement;
import jet.bpm.engine.model.ProcessDefinition;
import jet.bpm.engine.model.SequenceFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class FlowUtils {

    private static final Logger log = LoggerFactory.getLogger(FlowUtils.class);

    public static void followFlows(AbstractEngine engine, DefaultExecution execution, ProcessElementCommand c) throws ExecutionException {
        followFlows(engine, execution, c.getProcessDefinitionId(), c.getElementId(), c.getGroupId(), c.isExclusive());
    }

    public static void followFlows(AbstractEngine engine, DefaultExecution execution, ProcessElementCommand c, String elementId) throws ExecutionException {
        followFlows(engine, execution, c.getProcessDefinitionId(), elementId, c.getGroupId(), c.isExclusive());
    }

    public static void followFlows(AbstractEngine engine, DefaultExecution execution, ProcessElementCommand c, UUID groupId, boolean exclusive) throws ExecutionException {
        followFlows(engine, execution, c.getProcessDefinitionId(), c.getElementId(), groupId, exclusive);
    }

    public static void followFlows(AbstractEngine engine, DefaultExecution execution, String processDefinitionId, String elementId, UUID groupId, boolean exclusive) throws ExecutionException {
        ProcessDefinitionProvider provider = engine.getProcessDefinitionProvider();
        ProcessDefinition pd = provider.getById(processDefinitionId);
        List<SequenceFlow> flows = ProcessDefinitionUtils.findOutgoingFlows(pd, elementId);
        
        followFlows(execution, processDefinitionId, elementId, groupId, exclusive, flows);
    }
    
    public static void followFlows(DefaultExecution execution, ProcessElementCommand c, List<SequenceFlow> flows) {
        followFlows(execution, c.getProcessDefinitionId(), c.getElementId(), c.getGroupId(), c.isExclusive(), flows);
    }

    public static void followFlows(DefaultExecution execution, String processDefinitionId, String elementId, UUID groupId, boolean exclusive, List<SequenceFlow> flows) {
        // reverse the collection, to fill up the stack in correct order
        Collections.reverse(flows);
        for (SequenceFlow next : flows) {
            log.debug("followFlows ['{}'] -> continuing from '{}', '{}' to {}", execution.getId(), processDefinitionId, elementId, next.getId());
            execution.push(new ProcessElementCommand(processDefinitionId, next.getId(), groupId, exclusive));
        }
    }
    
    public static void activateFilteredFlows(DefaultExecution s, ProcessDefinition pd, String from, String ... filtered) throws ExecutionException {
        Collection<SequenceFlow> flows = ProcessDefinitionUtils.filterOutgoingFlows(pd, from, filtered);
        activateFlows(s, pd, flows);
    }
    
    public static void activateFlows(DefaultExecution s, ProcessDefinition pd, Collection<? extends AbstractElement> elements) throws ExecutionException {
        for (AbstractElement f : elements) {
            String gwId = ProcessDefinitionUtils.findNextGatewayId(pd, f.getId());
            if (gwId == null) {
                return;
            }
            
            log.debug("activateFlows ['{}', '{}'] -> activating '{}' via '{}'", s.getId(), pd.getId(), gwId, f.getId());
            s.inc(pd.getId(), gwId, 1);
        }
    }

    private FlowUtils() {
    }
}
