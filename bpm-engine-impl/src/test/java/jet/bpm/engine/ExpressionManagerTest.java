package jet.bpm.engine;

import jet.bpm.engine.api.ExecutionContext;
import jet.bpm.engine.el.DefaultExpressionManager;
import jet.bpm.engine.el.ExpressionManager;
import jet.bpm.engine.task.ServiceTaskRegistry;
import static org.junit.Assert.*;
import org.junit.Test;
import static org.mockito.Mockito.*;

public class ExpressionManagerTest {

    @Test
    public void testEvalString() throws Exception {
        ExpressionManager em = new DefaultExpressionManager(mock(ServiceTaskRegistry.class));
        String s1 = "PT30S";
        String s2 = em.eval(mock(ExecutionContext.class), s1, String.class);
        assertEquals(s1, s2);
    }
}
