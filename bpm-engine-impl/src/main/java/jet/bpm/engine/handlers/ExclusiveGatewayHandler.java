package jet.bpm.engine.handlers;

import java.util.Iterator;
import java.util.List;
import jet.bpm.engine.api.ExecutionException;
import jet.bpm.engine.DefaultExecution;
import jet.bpm.engine.api.ExecutionContext;
import jet.bpm.engine.AbstractEngine;
import jet.bpm.engine.ProcessDefinitionUtils;
import jet.bpm.engine.commands.ProcessElementCommand;
import jet.bpm.engine.el.ExpressionManager;
import jet.bpm.engine.model.ExclusiveGateway;
import jet.bpm.engine.model.ProcessDefinition;
import jet.bpm.engine.model.SequenceFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Обработчик элемента 'exclusive gateway'.
 */
public class ExclusiveGatewayHandler extends AbstractElementHandler {

    private static final Logger log = LoggerFactory.getLogger(ExclusiveGatewayHandler.class);

    public ExclusiveGatewayHandler(AbstractEngine engine) {
        super(engine);
    }

    @Override
    public void handle(DefaultExecution s, ProcessElementCommand c) throws ExecutionException {
        s.pop();

        String nextId = null;

        ProcessDefinition pd = getProcessDefinition(c);

        // найдем все исходящие flow. Если на них были какие-то EL-выражения,
        // то вычислим их
        List<SequenceFlow> flows = ProcessDefinitionUtils.findOutgoingFlows(pd, c.getElementId());
        for (Iterator<SequenceFlow> i = flows.iterator(); i.hasNext();) {
            // вычислим значение
            SequenceFlow f = i.next();
            if (f.getExpression() != null) {
                i.remove();
                if (eval(c.getContext(), f)) {
                    // нашелся flow с выражением, которое вычислилось в true
                    nextId = f.getId();
                    break;
                }
            }
        }

        ExclusiveGateway element = (ExclusiveGateway) pd.getChild(c.getElementId());

        if (nextId == null && !flows.isEmpty()) {
            // остались только те flow, на которых не было EL-выражений
            String defaultFlow = element.getDefaultFlow();
            if (defaultFlow != null) {
                // задан flow по умолчанию, попробуем его
                for (SequenceFlow f : flows) {
                    if (f.getId().equals(defaultFlow)) {
                        nextId = f.getId();
                        break;
                    }
                }
            } else {
                // default flow не задан, возьмем первый из оставшихся
                nextId = flows.iterator().next().getId();
            }
        }

        if (nextId == null) {
            // ничего не найдено или ни один flow с EL-выражением не был
            // вычислен в true
            throw new ExecutionException("No valid outgoing flows for '%s' and no default flow", c.getElementId());
        }

        log.debug("'{}' was selected", nextId);
        s.push(new ProcessElementCommand(pd.getId(), nextId, c.getContext()));
    }

    private boolean eval(ExecutionContext ctx, SequenceFlow f) {
        String expr = f.getExpression();

        ExpressionManager em = getEngine().getExpressionManager();
        boolean b = em.eval(ctx, expr, Boolean.class);

        log.debug("eval ['{}', '{}'] -> {}", f.getId(), f.getExpression(), b);
        return b;
    }
}