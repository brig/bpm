package jet.scdp.bpm.engine;

import jet.scdp.bpm.api.ActivationListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import jet.scdp.bpm.api.Execution;
import jet.scdp.bpm.engine.event.EventManager;
import jet.scdp.bpm.engine.event.EventManagerImpl;
import jet.scdp.bpm.api.JavaDelegate;
import jet.scdp.bpm.engine.task.ServiceTaskRegistry;
import jet.scdp.bpm.engine.task.ServiceTaskRegistryImpl;
import jet.scdp.bpm.model.ProcessDefinition;
import static org.junit.Assert.*;
import org.junit.Before;
import static org.mockito.Mockito.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractEngineTest implements ActivationListener {

    private static final Logger log = LoggerFactory.getLogger(AbstractEngineTest.class);

    private ProcessDefinitionProvider processDefinitionProvider;
    private ServiceTaskRegistry serviceTaskRegistry;
    protected EventManager eventManager;
    private AbstractEngine engine;
    private Map<String, List<String>> activations;

    @Before
    public void init() {
        processDefinitionProvider = new ProcessDefinitionProviderImpl();
        serviceTaskRegistry = new ServiceTaskRegistryImpl();
        eventManager = spy(new EventManagerImpl());

        engine = new DefaultEngine(processDefinitionProvider, serviceTaskRegistry, eventManager);

        activations = new HashMap<>();
        engine.addListener(this);
    }

    protected void deploy(ProcessDefinition pd) {
        ((ProcessDefinitionProviderImpl) processDefinitionProvider).add(pd);
    }

    protected AbstractEngine getEngine() {
        return engine;
    }

    protected void register(String key, JavaDelegate d) {
        serviceTaskRegistry.register(key, d);
    }

    @Override
    public void onActivation(Execution e, String processDefinitionId, String elementId) {
        String k = e.getProcessBusinessKey() + "/" + processDefinitionId;
        List<String> l = activations.get(k);
        if (l == null) {
            l = new ArrayList<>();
            activations.put(k, l);
        }

        l.add(elementId);
    }

    protected void assertActivation(String processBusinessKey, String processDefinitionId, String elementId) {
        String k = processBusinessKey + "/" + processDefinitionId;
        List<String> l = activations.get(k);
        assertNotNull("No activations for " + k, l);
        assertFalse("No more activations for " + k + ", element " + elementId, l.isEmpty());

        String s = l.remove(0);
        assertTrue("Unexpected activation: '" + s + "' instead of '" + elementId + "'", elementId.equals(s));
    }

    protected void assertActivations(String processBusinessKey, String processDefinitionId, String ... elementIds) {
        for (String eid : elementIds) {
            assertActivation(processBusinessKey, processDefinitionId, eid);
        }
    }

    protected void assertNoMoreActivations() {
        int s = 0;
        for (List<String> l : activations.values()) {
            s += l.size();
        }
        assertTrue("We have " + s + " more activations", s == 0);
    }

    protected void dumpActivations(String processBusinessKey, String processDefinitionId) {
        String k = processBusinessKey + "/" + processDefinitionId;
        List<String> l = activations.get(k);
        log.info("dumpActivations ['{}', '{}'] -> done: {}", processBusinessKey, processDefinitionId,
                Arrays.asList(l.toArray(new String[l.size()])));
    }
}
