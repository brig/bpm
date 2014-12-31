package jet.scdp.bpm.engine;

import jet.scdp.bpm.api.ActivationListener;
import java.util.ArrayList;
import java.util.List;
import jet.scdp.bpm.engine.persistence.PersistenceManager;
import jet.scdp.bpm.api.Engine;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import jet.scdp.bpm.api.Execution;
import jet.scdp.bpm.api.ExecutionException;
import jet.scdp.bpm.engine.commands.ExecutionCommand;
import jet.scdp.bpm.engine.commands.ProcessElementCommand;
import jet.scdp.bpm.engine.el.ExpressionManager;
import jet.scdp.bpm.engine.event.Event;
import jet.scdp.bpm.engine.event.EventManager;
import jet.scdp.bpm.engine.handlers.ElementHandler;
import jet.scdp.bpm.engine.lock.LockManager;
import jet.scdp.bpm.engine.task.ServiceTaskRegistry;
import jet.scdp.bpm.model.ProcessDefinition;
import jet.scdp.bpm.model.StartEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class AbstractEngine implements Engine {

    private static final Logger log = LoggerFactory.getLogger(AbstractEngine.class);
    
    private final ActivationListenerHolder listenerHolder = new ActivationListenerHolder();
    
    public abstract ProcessDefinitionProvider getProcessDefinitionProvider();

    public abstract ElementHandler getElementHandler();

    public abstract EventManager getEventManager();

    public abstract PersistenceManager getPersistenceManager();

    public abstract ServiceTaskRegistry getServiceTaskRegistry();
    
    public abstract ExpressionManager getExpressionManager();
    
    public abstract LockManager getLockManager();
    
    public abstract IdGenerator getIdGenerator();
    
    @Override
    public void addListener(ActivationListener l) {
        listenerHolder.addListener(l);
    }
    
    public void fireOnElementActivation(DefaultExecution e, String processDefinitionId, String elementId) {
        listenerHolder.fireOnElementActivation(e, processDefinitionId, elementId);
    }

    @Override
    public void run(String processBusinessKey, String processDefinitionId, Map<String, Object> variables) throws ExecutionException {
        ProcessDefinitionProvider pdp = getProcessDefinitionProvider();

        ProcessDefinition pd = pdp.getById(processDefinitionId);
        StartEvent start = ProcessDefinitionUtils.findStartEvent(pd);

        ExecutionContext ctx = new ExecutionContextImpl();
        applyVariables(ctx, variables);

        IdGenerator idg = getIdGenerator();
        
        DefaultExecution s = new DefaultExecution(idg.create(), processBusinessKey);
        s.push(new ProcessElementCommand(processDefinitionId, start.getId(), ctx));
        
        LockManager lm = getLockManager();
        lm.lock(processBusinessKey);
        try {
            run(s);
        } finally {
            lm.unlock(processBusinessKey);
        }
    }

    @Override
    public void resume(String processBusinessKey, String eventId, Map<String, Object> variables) throws ExecutionException {
        EventManager em = getEventManager();
        LockManager lm = getLockManager();
        
        lm.lock(processBusinessKey);
        
        try {
            Event e = em.find(processBusinessKey, eventId);
            if (e == null) {
                throw new ExecutionException("No event '" + eventId + "' found for process '" + processBusinessKey + "'");
            }
            
            if (e.isExclusive()) {
                // эксклюзивное событие означает, что может возникнуть только одно
                // событие из группы. А значит все остальные события надо удалить
                em.clearGroup(processBusinessKey, e.getGroupId());
            }
            
            String eid = e.getExecutionId();
            log.debug("wakeUp ['{}', '{}'] -> got execution id {}", processBusinessKey, eventId, eid);
            
            PersistenceManager pm = getPersistenceManager();
            DefaultExecution s = pm.remove(eid);
            if (s == null) {
                throw new ExecutionException("Execution not found: " + eid);
            }
            
            s.setSuspended(false);
            
            ExecutionCommand c = s.peek();
            applyVariables(c.getContext(), variables);
            
            run(s);
        } finally {
            lm.unlock(processBusinessKey);
        }
    }
        
    private void applyVariables(ExecutionContext ctx, Map<String, Object> m) {
        if (m == null) {
            return;
        }

        for (Map.Entry<String, Object> e : m.entrySet()) {
            ctx.setVariable(e.getKey(), e.getValue());
        }
    }

    private void run(DefaultExecution s) throws ExecutionException {
        while (!s.isSuspended()) {
            if (s.isDone()) {
                if (s.getParentId() != null) {
                    log.debug("run ['{}'] -> switching to {}", s.getId(), s.getParentId());
                    PersistenceManager pm = getPersistenceManager();
                    
                    String pid = s.getParentId();
                    s = pm.remove(pid);
                    if (s == null) {
                        throw new ExecutionException("Parent execution not found: " + pid);
                    }
                    
                    s.setSuspended(false);
                } else {
                    log.debug("run ['{}'] -> no parent execution, breaking", s.getId());
                    break;
                }
            }

            ExecutionCommand c = s.peek();
            if (c != null) {
                s = c.exec(this, s);
            }
        }
        
        log.debug("run ['{}'] -> (done: {}, suspended: {})", s.getId(), s.isDone(), s.isSuspended());
    }

    private static final class ActivationListenerHolder {

        private final List<ActivationListener> listeners = new ArrayList<>();
        private final ReadWriteLock lock = new ReentrantReadWriteLock();

        public void addListener(ActivationListener l) {
            try {
                lock.writeLock().lock();
                listeners.add(l);
            } finally {
                lock.writeLock().unlock();
            }
        }

        public void fireOnElementActivation(Execution e, String processDefinitionId, String elementId) {
            try {
                lock.readLock().lock();
                for (ActivationListener l : listeners) {
                    l.onActivation(e, processDefinitionId, elementId);
                }
            } finally {
                lock.readLock().unlock();
            }
        }
    }
}