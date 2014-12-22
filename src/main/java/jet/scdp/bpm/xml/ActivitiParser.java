package jet.scdp.bpm.xml;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import jet.scdp.bpm.model.AbstractElement;
import jet.scdp.bpm.model.BoundaryEvent;
import jet.scdp.bpm.model.CallActivity;
import jet.scdp.bpm.model.EndEvent;
import jet.scdp.bpm.model.EventBasedGateway;
import jet.scdp.bpm.model.ExclusiveGateway;
import jet.scdp.bpm.model.ProcessDefinition;
import jet.scdp.bpm.model.SequenceFlow;
import jet.scdp.bpm.model.ServiceTask;
import jet.scdp.bpm.model.ExpressionType;
import jet.scdp.bpm.model.IntermediateCatchEvent;
import jet.scdp.bpm.model.SequenceFlow.ExecutionListener;
import jet.scdp.bpm.model.StartEvent;
import jet.scdp.bpm.model.VariableMapping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class ActivitiParser implements Parser {

    private static final Logger log = LoggerFactory.getLogger(ActivitiParser.class);

    @Override
    public ProcessDefinition parse(InputStream in) throws ParserException {
        if (in == null) {
            throw new NullPointerException("Input cannot be null");
        }

        try {
            SAXParserFactory spf = SAXParserFactory.newInstance();
            SAXParser p = spf.newSAXParser();

            Handler h = new Handler();
            p.parse(in, h);

            return h.process;
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new ParserException("Parsing error", e);
        }
    }

    private static final class Handler extends DefaultHandler {

        private String id;
        private String processId;
        private String attachedToRef;
        private String errorRef;
        private String sourceRef;
        private String targetRef;
        private String messageRef;
        private String timeDate;
        private String timeDuration;
        private String calledElement;
        private StringBuilder text;

        private ProcessDefinition process;
        private Collection<AbstractElement> children;
        private Collection<ExecutionListener> listeners;
        private Set<VariableMapping> in;
        private Set<VariableMapping> out;

        @Override
        public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
            log.debug("startElement ['{}']", qName);

            switch (qName) {
                case "process":
                    processId = attributes.getValue("id");
                    children = new ArrayList<>();
                    break;

                case "startEvent":
                    id = attributes.getValue("id");
                    StartEvent ev = new StartEvent(id);
                    children.add(ev);
                    break;

                case "callActivity":
                    id = attributes.getValue("id");
                    calledElement = attributes.getValue("calledElement");
                    break;

                case "boundaryEvent":
                    id = attributes.getValue("id");
                    attachedToRef = attributes.getValue("attachedToRef");
                    break;

                case "errorEventDefinition":
                    errorRef = attributes.getValue("errorRef");
                    break;

                case "endEvent":
                    id = attributes.getValue("id");

                    break;

                case "sequenceFlow":
                    id = attributes.getValue("id");
                    sourceRef = attributes.getValue("sourceRef");
                    targetRef = attributes.getValue("targetRef");
                    break;

                case "conditionExpression":
                    text = new StringBuilder();
                    break;

                case "exclusiveGateway":
                    id = attributes.getValue("id");
                    ExclusiveGateway eg = new ExclusiveGateway(id, attributes.getValue("default"));
                    children.add(eg);
                    break;

                case "serviceTask":
                    id = attributes.getValue("id");

                    String simple = attributes.getValue("activiti:expression");
                    String delegate = attributes.getValue("activiti:delegateExpression");

                    ExpressionType type = ExpressionType.NONE;
                    String expr = null;

                    if (simple != null) {
                        type = ExpressionType.SIMPLE;
                        expr = simple;
                    } else if (delegate != null) {
                        type = ExpressionType.DELEGATE;
                        expr = delegate;
                    }

                    ServiceTask st = new ServiceTask(id, type, expr);
                    children.add(st);
                    break;

                case "activiti:executionListener":
                    if (listeners == null) {
                        listeners = new ArrayList<>();
                    }

                    String event = attributes.getValue("event");
                    
                    String s = null;
                    ExpressionType t = ExpressionType.NONE;
                    
                    String expression = attributes.getValue("expression");
                    String delegateExpression = attributes.getValue("delegateExpression");
                    if (expression != null) {
                        s = expression;
                        t = ExpressionType.SIMPLE;
                    } else if (delegateExpression != null) {
                        s = delegateExpression;
                        t = ExpressionType.DELEGATE;
                    }

                    ExecutionListener sel = new ExecutionListener(event, t, s);
                    listeners.add(sel);
                    break;

                case "activiti:in":
                    if (in == null) {
                        in = new HashSet<>();
                    }
                    in.add(parseVariableMapping(attributes));
                    break;
                    
                case "activiti:out":
                    if (out == null) {
                        out = new HashSet<>();
                    }
                    out.add(parseVariableMapping(attributes));
                    break;
                    
                case "eventBasedGateway":
                    id = attributes.getValue("id");
                    
                    EventBasedGateway ebg = new EventBasedGateway(id);
                    children.add(ebg);
                    break;
                    
                case "messageEventDefinition":
                    messageRef = attributes.getValue("messageRef");
                    break;

                case "intermediateCatchEvent":
                    id = attributes.getValue("id");
                    break;
                    
                case "timeDate":
                    text = new StringBuilder();
                    break;
                    
                case "timeDuration":
                    text = new StringBuilder();
                    break;
            }
        }

        private VariableMapping parseVariableMapping(Attributes attributes) {
            String source = attributes.getValue("source");
            String sourceExpression = attributes.getValue("sourceExpression");
            String target = attributes.getValue("target");
            return new VariableMapping(source, sourceExpression, target);
        }

        @Override
        public void characters(char[] ch, int start, int length) throws SAXException {
            if (text != null) {
                text.append(ch, start, length);
            }
        }

        @Override
        public void endElement(String uri, String localName, String qName) throws SAXException {
            log.debug("endElement ['{}']", qName);

            switch (qName) {
                case "process":
                    process = new ProcessDefinition(processId, children);
                    children = null;
                    break;

                case "boundaryEvent":
                    BoundaryEvent be = new BoundaryEvent(id, attachedToRef, errorRef);
                    children.add(be);

                    attachedToRef = null;
                    errorRef = null;
                    break;

                case "sequenceFlow":
                    String expr = text != null ? text.toString().trim() : null;

                    ExecutionListener[] l = null;
                    if (listeners != null) {
                        l = listeners.toArray(new ExecutionListener[listeners.size()]);
                    }

                    SequenceFlow sf = new SequenceFlow(id, sourceRef, targetRef, expr, l);
                    children.add(sf);

                    sourceRef = null;
                    targetRef = null;
                    text = null;
                    listeners = null;
                    break;

                case "endEvent":
                    EndEvent ee = new EndEvent(id, errorRef);
                    children.add(ee);

                    errorRef = null;
                    break;
                    
                case "callActivity":
                    CallActivity ca = new CallActivity(id, calledElement, in, out);
                    children.add(ca);
                    
                    calledElement = null;
                    in = null;
                    out = null;
                    break;
                
                case "intermediateCatchEvent":
                    IntermediateCatchEvent ice = new IntermediateCatchEvent(id, messageRef, timeDate, timeDuration);
                    children.add(ice);
                    
                    messageRef = null;
                    timeDate = null;
                    timeDuration = null;
                    break;
                    
                case "timeDate":
                    timeDate = text.toString();
                    text = null;
                    break;
                    
                case "timeDuration":
                    timeDuration = text.toString();
                    text = null;
                    break;
            }
        }
    }
}
