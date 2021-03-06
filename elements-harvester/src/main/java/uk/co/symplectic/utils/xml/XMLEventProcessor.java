/*
 * ******************************************************************************
 *   Copyright (c) 2019 Symplectic. All rights reserved.
 *   This Source Code Form is subject to the terms of the Mozilla Public
 *   License, v. 2.0. If a copy of the MPL was not distributed with this
 *   file, You can obtain one at http://mozilla.org/MPL/2.0/.
 * ******************************************************************************
 *   Version :  ${git.branch}:${git.commit.id}
 * ******************************************************************************
 */

package uk.co.symplectic.utils.xml;

import org.apache.commons.lang.NullArgumentException;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLEventReader;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.*;
import java.text.MessageFormat;
import java.util.*;

/**
 * Processor class for dealing with XMLEventStreams.
 * Filters registered either on construction or using the addFilter method are invoked if the document location specified in the filter is encountered.
 * Filters then receive all events from the stream until that location scope is exited.
 **/
public class XMLEventProcessor {

    /*
    Storage for filters that will be invoked by this processor when parsing an XML stream.
    The Hash map linking a given Filter document location (expressed as a QName list) to the set of filters that should be activated if that location is found in the document.
    This makes it much easier to retrieve the relevant filters when needed.
     */
    private HashMap<List<QName>, List<EventFilter>> filters = new HashMap<List<QName>, List<EventFilter>>();

    /*
    Constructor allowing the easy addition of filters to run on this processor
     */
    public XMLEventProcessor(EventFilter... filters) {
        for (EventFilter filter : filters) {
            addFilter(filter);
        }
    }

    /*
    Method to add a filter to this processor
     */
    public void addFilter(EventFilter filter) {
        if (filter != null) {
            List<QName> key = filter.getFilterLocation();
            if (this.filters.get(key) == null) this.filters.put(key, new ArrayList<EventFilter>());
            this.filters.get(key).add(filter);
        }
    }

    /*
    Helper method used by "process" - Tests that the incoming reader is in a sensible place to start processing and errors out if not.
     */
    private void checkInitialState(ReaderProxy proxy) {
        XMLEvent initialEvent = proxy.peek();
        if (!initialEvent.isStartDocument() && !initialEvent.isStartElement())
            throw new IllegalStateException("Must enter process with the XMLEventReader currently on a StartElements or StartDocument event");
    }

    /*
    Main processing method - does the work of advancing the stream, tracking scope,
    invoking filters when relevant and handing out events to any filters in scope
    Note : ONLY this method must ever advance the stream - which is why it only ever hands out events and proxies when invoking external events
     */
    public void process(XMLEventReader reader) throws XMLStreamException{
        if (reader != null) {
            //Proxied access to the underlying stream,
            //Allows us to grant access to the next-event to filters, etc without allowing them to advance the stream.
            ReaderProxy proxy = new ReaderProxy(reader);

            //Test that incoming reader is in a sensible state to start processing
            checkInitialState(proxy);

            //initialise scope tracking stack
            Stack<ProcessScope> stack = new Stack<ProcessScope>();

            //MAIN LOOP this is the only thing that should ever advance the reader stream.
            while (reader.hasNext()) {
                XMLEvent event = reader.nextEvent();
                WrappedXmlEvent wrappedEvent = new WrappedXmlEvent(event, proxy);

                //work out the new scope's document location based on the existing scope stack and the name of the element we just encountered
                List<QName> currentDocumentLocation = new ArrayList<QName>();
                for (ProcessScope scope : stack) {
                    currentDocumentLocation.add(scope.getName());
                }

                //if we have encountered a StartElement we have a new scope
                if (event.isStartElement()) {
                    StartElement eventAsStartElement = event.asStartElement();
                    //get the new scope's name
                    QName eventQName = eventAsStartElement.getName();

                    //update the current location
                    currentDocumentLocation.add(eventQName);

                    //retrieve any filters associated with the new scope's document location  and create a ProcessScope object to track the new scope.
                    ProcessScope newScope = new ProcessScope(eventQName, filters.get(currentDocumentLocation));

                    //Inform all the filters activated in our new scope that we are about to start sending them events
                    for (EventFilter filter : newScope.getFiltersInScope()) {
                        filter.itemStart(wrappedEvent);
                    }

                    //put the ProcessScope object representing our new scope into the tracking stack
                    stack.push(newScope);
                }

                //for all events work through all the filters in all the currently active scopes and pass them the details of the current event
                //Note: this will include the "StartElement" from a scope that has only just been activated and the EndElements from a scope that is about to close
                for (ProcessScope scope : stack) {
                    for (EventFilter filter : scope.getFiltersInScope()) {
                        List<QName> relativeLocation = currentDocumentLocation.subList(filter.getFilterLocation().size(), currentDocumentLocation.size());
                        filter.processEvent(wrappedEvent, relativeLocation);
                    }
                }

                //if we have encountered an EndElement then we are exiting a scope.
                if (event.isEndElement()) {
                    EndElement eventAsEndElement = event.asEndElement();
                    //get the name of the scope that is closing.
                    QName eventQName = eventAsEndElement.getName();

                    //Get the name of the scope that we thing we are currently in from our scope tracking stack.
                    ProcessScope currentScope = stack.empty() ? null : stack.peek();

                    //Test that the scope we are exiting in the stream is what we were expecting from our tracking.
                    //Error out if there is a discrepancy
                    if (currentScope == null) throw new XMLStreamException("Invalid XML structure detected");
                    QName expectedName = currentScope.getName();
                    if (!eventQName.equals(expectedName))
                        throw new XMLStreamException("Invalid XML structure detected");

                    //Inform all the filters in the scope we are about to exit that we are about to stop sending them events.
                    for (EventFilter filter : currentScope.getFiltersInScope()) {
                        filter.itemEnd(wrappedEvent);
                    }

                    //remove the current scope from the tracking stack.
                    stack.pop();
                }
            }
        }
    }

    /*
        Immutable Helper class to track XML document scopes as we process the file.
        Each represents the Scope associated with processing an Element of name "name"
        And any filters that were newly activated at this scope
     */
    private class ProcessScope {
        private final QName name;
        private final List<EventFilter> filtersInScope = new ArrayList<EventFilter>();

        ProcessScope(QName name, List<EventFilter> filters) {
            this.name = name;
            if (filters != null) {
                for (EventFilter filter : filters) {
                    if (filter != null) this.filtersInScope.add(filter);
                }
            }
        }

        QName getName() { return name; }
        List<EventFilter> getFiltersInScope() { return filtersInScope; }
    }

    /**
     * A wrapping class around a raw XMLEvent which exposes methods to ease extraction of values and attributes from the
     * underlying events.
     *
     * The XMLEventProcessor only ever passes WrappedXmlEvent objects out (e.g. to EventFilters, etc), so you can rely on
     * having one of these if you are using this framework to parse XML.
     */
    @SuppressWarnings({"WeakerAccess", "unused"})
    public static class WrappedXmlEvent{
        private final XMLEvent innerEvent;
        private final ReaderProxy reader;

        //package access as should only be created by these processes..
        WrappedXmlEvent(XMLEvent event, ReaderProxy reader){
            if(event == null) throw new NullArgumentException("event");
            if(reader == null) throw new NullArgumentException("reader");
            this.innerEvent = event;
            this.reader = reader;
        }

        public XMLEvent getRawEvent(){ return innerEvent; }
        private XMLEvent getNextEvent() throws XMLStreamException { return reader.peek(); }

        public boolean isRelevantForExtraction(){
            return innerEvent.isStartElement();
        }

        //if the wrapped event is a start or end Element we respond with the name, otherwise we respond with null
        public QName getName(){
            if(innerEvent.isStartElement()) return innerEvent.asStartElement().getName();
            if(innerEvent.isEndElement()) return innerEvent.asEndElement().getName();
            return null;
        }

        public boolean hasAttribute(QName name){
            if(isRelevantForExtraction()){
                Attribute att = innerEvent.asStartElement().getAttributeByName(name);
                return att != null;
            }
            return false;
        }

        public boolean hasAttribute(String name){
            return hasAttribute(new QName(name));
        }

        private  String innerGetAttribute(QName name, boolean required){
            if(isRelevantForExtraction()){
                Attribute att = innerEvent.asStartElement().getAttributeByName(name);
                if(att != null) return att.getValue();
                if(required) throw new IllegalStateException(MessageFormat.format("Missing attribute ({0}) attempting extraction at [{1}]", name.toString(), getName().toString()));
                return null;
            }
            throw new IllegalStateException(MessageFormat.format("Illegal site to attempt attribute ({0}) extraction [{1}]", name.toString(), getEventTypeString()));
        }

        public String getAttribute(String name) {
            return getAttribute(new QName(name));
        }

        public String getAttribute(QName name){
            return innerGetAttribute(name, true);
        }

        public String getAttributeValueOrNull(String name){
            return getAttributeValueOrNull(new QName(name));
        }

        public String getAttributeValueOrNull(QName name){
            return innerGetAttribute(name, false);
        }

        //This is naive extraction logic. It should really be calculated across the entire "scope" of the current Element.
        //this works for all our use cases though...
        public boolean hasValue() throws XMLStreamException{
            if(isRelevantForExtraction()){
                XMLEvent nextEvent = getNextEvent();
                if (nextEvent != null && nextEvent.isCharacters()){
                    return true;
                }
            }
            return false;
        }

        //This is naive extraction logic. It should really be calculated across the entire "scope" of the current Element.
        //this works for all our use cases though...
        private String innerGetValue(boolean required) throws XMLStreamException{
            if(isRelevantForExtraction()){
                XMLEvent nextEvent = getNextEvent();
                if (nextEvent != null && nextEvent.isCharacters()){
                    return nextEvent.asCharacters().getData();
                }
                if(required) throw new IllegalStateException(MessageFormat.format("Missing value attempting extraction at [{0}]", getName().toString()));
                return null;
            }
            throw new IllegalStateException(MessageFormat.format("Illegal site to attempt value extraction [{0}]", getEventTypeString()));
        }

        public String getValueOrNull() throws XMLStreamException {
            return innerGetValue(false);
        }
        public String getRequiredValue() throws XMLStreamException {
            return innerGetValue(true);
        }

        //helper method to expose underlying event type as a string... for logging/exceptions
        private String getEventTypeString() {
            int eventType = getRawEvent().getEventType();
            switch (eventType) {
                case XMLEvent.START_ELEMENT: return "START_ELEMENT";
                case XMLEvent.END_ELEMENT: return "END_ELEMENT";
                case XMLEvent.PROCESSING_INSTRUCTION: return "PROCESSING_INSTRUCTION";
                case XMLEvent.CHARACTERS: return "CHARACTERS";
                case XMLEvent.COMMENT: return "COMMENT";
                case XMLEvent.START_DOCUMENT: return "START_DOCUMENT";
                case XMLEvent.END_DOCUMENT: return "END_DOCUMENT";
                case XMLEvent.ENTITY_REFERENCE: return "ENTITY_REFERENCE";
                case XMLEvent.ATTRIBUTE: return "ATTRIBUTE";
                case XMLEvent.DTD: return "DTD";
                case XMLEvent.CDATA: return "CDATA";
                case XMLEvent.SPACE:return "SPACE";
            }
            return "UNKNOWN_EVENT_TYPE," + eventType;
        }

    }

    /*
    Helper class to wrap an XMLEventReader to only provide access to the underlying peek method
    Exists to allow access to the stream to be exposed to invoked filters without granting access to methods that advance the stream.
     */
    class ReaderProxy {
        private final XMLEventReader reader;

        ReaderProxy(XMLEventReader reader) { this.reader = reader; }

        XMLEvent peek() {
            try {
                return this.reader.peek();
            } catch (XMLStreamException e) {
                //deliberately don't worry in here - just return null, let the wrapping processor complain when it actually processes the event.
                return null;
            }
        }
    }

    /**
     * EventFilter objects are used to register an interest in sections of the Events being handled by an XmlEventProcessor.
     * You define a filter by passing in a DocumentLocation to express the "path" within an XML document that you are
     * interested in.
     * For every location in the document being processed by an XmlEventProcessor that matches the location defined by "names".
     * The processor ensures that itemStart is called on the filter, which then receives every event that is passed in from the system
     * until the filter goes out of scope at which point the processor invokes itemEnd
     *
     * This class represents a common base from which any Filters defined for use with an XmlEventProcessor must inherit.
     */
    @SuppressWarnings("WeakerAccess")
    public abstract static class EventFilter {

        /**
         * Class to represent the location of an Element in an XML document as a list of "path" fragments
         */
        public static class DocumentLocation{
            private final List<QName> location = new ArrayList<QName>();

            public DocumentLocation(QName...documentLocation){
                for (QName name : documentLocation) {
                    if(name == null) throw new IllegalArgumentException("Must not supply null as one of the QNames in a Document Location");
                    this.location.add(name);
                }
            }
            List<QName> getLocationAsList(){ return Collections.unmodifiableList(location); }

            @Override
            public final boolean equals(Object objectToTest) {
                if (objectToTest == this) {
                    return true;
                }

                if (objectToTest == null || !(objectToTest instanceof DocumentLocation)) {
                    return false;
                }

                DocumentLocation otherLocation = (DocumentLocation) objectToTest;
                return this.getLocationAsList().equals(otherLocation.getLocationAsList());
            }

            public boolean matches(List<QName> objectToTest){
                //noinspection SimplifiableIfStatement
                if (objectToTest == null) return false;
                return this.getLocationAsList().equals(objectToTest);
            }
        }

        //The document location this filter is active at (expressed as a QName array)
        final DocumentLocation documentLocation;

        //Constructor allowing easy specification of the document location this filter is active at.
        protected EventFilter(DocumentLocation location) {
            if(location == null) throw new NullArgumentException("location");
            this.documentLocation = location;
        }

        //Get the document Location where this filter is active (as a QName array)
        List<QName> getFilterLocation() {
            return documentLocation.location;
        }

        //default methods to do "nothing" on item start and end only override if you need to do something
        protected void itemStart(WrappedXmlEvent initialEvent) throws XMLStreamException { }
        protected void itemEnd(WrappedXmlEvent finalEvent) throws XMLStreamException { }
        //abstract stub for process event - ensure users have to do something to create a concrete filter.
        protected abstract void processEvent(WrappedXmlEvent event, List<QName> relativeLocation) throws XMLStreamException;
    }

    /**
     * A base class for a Filter that will "count" the number of items that have been processed from the XML document
     * Where each item is a "scope" that matches the DocumentLocation provided in the constructor.
     */
    public static class ItemCountingFilter extends EventFilter{
        int itemCount = 0;

        public int getItemCount(){ return itemCount; }

        public ItemCountingFilter(DocumentLocation location) { super(location); }

        @Override
        protected void itemStart(WrappedXmlEvent initialEvent) throws XMLStreamException {
            itemCount++;
        }

        @Override
        protected void processEvent(WrappedXmlEvent event, List<QName> relativeLocation) throws XMLStreamException {}
    }

    /**
     * A (still abstract) specialization of an EventFilter that is designed to extract a Java object of Type T from
     * the set of underlying events that come from each scope matching the DocumentLocation provided in the constructor.
     *
     * Users are expected to override  initialiseItemExtraction, processEvent and finaliseItemExtraction as appropriate
     * to populate an item T corresponding to the set of data from a given "scope" of the underlying XML document.
     * @param <T> the type of item that is going to be extracted
     */
    @SuppressWarnings("unused")
    public abstract static class ItemExtractingFilter<T> extends EventFilter{
        final private int maximumAmountExpected;
        private boolean extractionAttempted = false;
        private boolean itemReady = false;
        private T previouslyExtractedItem = null;
        private T extractedItem = null;
        private int countOfExtractedItems = 0;

        protected ItemExtractingFilter(DocumentLocation location) {
            this(location, 0);
        }

        public T getExtractedItem(){
            if(extractionAttempted && !itemReady) throw new IllegalAccessError("Must not call getExtractedItem when item is in middle of being processed");
            return extractedItem;
        }

        protected ItemExtractingFilter(DocumentLocation location, int maximumAmountExpected) {
            super(location);
            this.maximumAmountExpected = maximumAmountExpected;
        }

        @Override
        final protected void itemStart(WrappedXmlEvent initialEvent) throws XMLStreamException {
            if(maximumAmountExpected > 0 && countOfExtractedItems >= maximumAmountExpected) throw new IllegalStateException("More items detected in XML than expected");
            itemReady = false;
            extractionAttempted = true;
            initialiseItemExtraction(initialEvent);
        }

        protected abstract void initialiseItemExtraction(WrappedXmlEvent initialEvent) throws XMLStreamException;

        @Override
        final protected void itemEnd(WrappedXmlEvent finalEvent) throws XMLStreamException {
            previouslyExtractedItem = extractedItem;
            extractedItem = finaliseItemExtraction(finalEvent);
            //careful with == we really do want to check if is the same reference here..
            if(previouslyExtractedItem == extractedItem) throw new IllegalStateException("Must create a new object for each extracted item when implementing an extractor");
            countOfExtractedItems++;
            itemReady = true;
        }

        abstract protected T finaliseItemExtraction(WrappedXmlEvent finalEvent);
    }

    /**
     * A wrapper for an EventFilter object which is itself an EventFilter.
     * Which exposes hook points to allow this wrapper to perform additional processing of the WrappedXMLEvent's
     * before and after (pre and post) the wrapped filter's itemStart itemEnd and processEvent methods being called.
     * @param <T>
     */
    @SuppressWarnings({"unused", "WeakerAccess", "EmptyMethod"})
    public abstract static class EventFilterWrapper<T extends EventFilter> extends EventFilter {
        final protected T innerFilter;

        public EventFilterWrapper(T innerFilter){
            //slightly dodgy will fail with a null reference if innerFilter is null - can't easily avoid though
            super(innerFilter.documentLocation);
            this.innerFilter = innerFilter;
        }

        @Override
        final protected void itemStart(WrappedXmlEvent initialEvent) throws XMLStreamException {
            preInnerItemStart(initialEvent);
            innerFilter.itemStart(initialEvent);
            postInnerItemStart(initialEvent);
        }
        protected void preInnerItemStart(WrappedXmlEvent initialEvent) throws XMLStreamException {}
        protected void postInnerItemStart(WrappedXmlEvent initialEvent) throws XMLStreamException {}

        @Override
        final protected void itemEnd(WrappedXmlEvent finalEvent) throws XMLStreamException {
            preInnerItemEnd(finalEvent);
            innerFilter.itemEnd(finalEvent);
            postInnerItemEnd(finalEvent);
        }
        protected void preInnerItemEnd(WrappedXmlEvent finalEvent) throws XMLStreamException {}
        protected void postInnerItemEnd(WrappedXmlEvent finalEvent) throws XMLStreamException {}

        @Override
        final protected void processEvent(WrappedXmlEvent event, List<QName> relativeLocation) throws XMLStreamException {
            preInnerProcessEvent(event, relativeLocation);
            innerFilter.processEvent(event, relativeLocation);
            postInnerProcessEvent(event, relativeLocation);
        }
        protected void preInnerProcessEvent(WrappedXmlEvent event, List<QName> relativeLocation) throws XMLStreamException {}
        protected void postInnerProcessEvent(WrappedXmlEvent event, List<QName> relativeLocation) throws XMLStreamException {}
    }
}
