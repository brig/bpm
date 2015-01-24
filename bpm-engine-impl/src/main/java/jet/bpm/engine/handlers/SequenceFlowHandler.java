package jet.bpm.engine.handlers;

import jet.bpm.engine.api.ExecutionException;
import jet.bpm.engine.DefaultExecution;
import jet.bpm.engine.api.ExecutionContext;
import jet.bpm.engine.api.ExecutionListener;
import jet.bpm.engine.AbstractEngine;
import jet.bpm.engine.ProcessDefinitionUtils;
import jet.bpm.engine.commands.ProcessElementCommand;
import jet.bpm.engine.el.ExpressionManager;
import jet.bpm.engine.model.ExpressionType;
import jet.bpm.engine.model.ProcessDefinition;
import jet.bpm.engine.model.SequenceFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Обработчик элемента 'sequence flow'.
 */
public class SequenceFlowHandler extends AbstractElementHandler {

    private static final Logger log = LoggerFactory.getLogger(SequenceFlowHandler.class);

    public SequenceFlowHandler(AbstractEngine engine) {
        super(engine);
    }

    @Override
    public void handle(DefaultExecution s, ProcessElementCommand c) throws ExecutionException {
        s.pop();

        ProcessDefinition pd = getProcessDefinition(c);

        SequenceFlow flow = (SequenceFlow) ProcessDefinitionUtils.findElement(pd, c.getElementId());
        processListeners(c.getContext(), flow);

        // помещаем на стек команду обработки элемента, на который показал flow.
        // cохраняем ID и признак экслюзивности группы
        s.push(new ProcessElementCommand(c.getProcessDefinitionId(), flow.getTo(), c.getGroupId(), c.isExclusive(), c.getContext()));
    }

    /**
     * Обработка слушателей события перехода по flow. Осложняется тем, что
     * в некоторых случаях слушатель получается путем вычисления некоторого
     * EL-выражения.
     * @param ctx контекст текущего процесса
     * @param f обрабатываемый flow
     * @throws ExecutionException
     */
    private void processListeners(ExecutionContext ctx, SequenceFlow f) throws ExecutionException {
        if (f.getListeners() == null) {
            return;
        }

        ExpressionManager em = getEngine().getExpressionManager();
        for (SequenceFlow.ExecutionListener l : f.getListeners()) {
            ExpressionType type = l.getType();
            String expr = l.getExpression();
            if (expr == null) {
                continue;
            }

            try {
                switch (type) {
                    case SIMPLE:
                        em.eval(ctx, expr, Object.class);
                        break;

                    case DELEGATE:
                        ExecutionListener d = em.eval(ctx, expr, ExecutionListener.class);
                        d.notify(ctx);
                }
            } catch (Exception e) {
                log.error("processListeners ['{}'] -> error", f.getId(), e);
                throw new ExecutionException("Unhandled listener exception", e);
            }
        }
    }
}