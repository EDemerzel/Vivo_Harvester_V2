/*******************************************************************************
 * Copyright (c) 2012 Symplectic Ltd. All rights reserved.
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 ******************************************************************************/
package uk.co.symplectic.vivoweb.harvester.model;

import uk.co.symplectic.utils.xml.XMLEventProcessor;
import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.events.EndElement;
import javax.xml.stream.events.StartElement;
import javax.xml.stream.events.XMLEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static uk.co.symplectic.elements.api.ElementsAPI.apiNS;
import static uk.co.symplectic.elements.api.ElementsAPI.atomNS;

public class ElementsRelationshipInfo extends ElementsItemInfo{

    public static class Extractor extends XMLEventProcessor.ItemExtractingFilter<ElementsItemInfo>{

        private static DocumentLocation fileEntryLocation = new DocumentLocation(new QName(atomNS, "entry"), new QName(apiNS, "relationship"));
        private static DocumentLocation feedEntryLocation = new DocumentLocation(new QName(atomNS, "feed"), new QName(atomNS, "entry"), new QName(apiNS, "relationship"));
        private static DocumentLocation feedDeletedEntryLocation = new DocumentLocation(new QName(atomNS, "feed"), new QName(atomNS, "entry"), new QName(apiNS, "deleted-relationship"));

        public static Extractor getExtractor(ElementsItemInfo.ExtractionSource source, int maximumExpected){
            switch(source) {
                case FEED : return new Extractor(feedEntryLocation, maximumExpected);
                case DELETED_FEED : return new Extractor(feedDeletedEntryLocation, maximumExpected);
                case FILE : return new Extractor(fileEntryLocation, maximumExpected);
                default : throw new IllegalStateException("invalid extractor source type requested");
            }
        }

        private ElementsRelationshipInfo workspace = null;

        private Extractor(DocumentLocation location, int maximumAmountExpected){
            super(location, maximumAmountExpected);
        }

        @Override
        protected void initialiseItemExtraction(StartElement initialElement, XMLEventProcessor.ReaderProxy readerProxy) throws XMLStreamException {
            String id = initialElement.getAttributeByName(new QName("id")).getValue();
            workspace = ElementsItemInfo.createRelationshipItem(Integer.parseInt(id));
        }

        @Override
        protected void processEvent(XMLEvent event, XMLEventProcessor.ReaderProxy readerProxy) throws XMLStreamException {
            if (event.isStartElement()) {
                StartElement startElement = event.asStartElement();
                QName name = startElement.getName();
                //only pull type id for "relationship" not "deleted-relationship" where it is not present.
                if(name.equals(new QName(apiNS, "relationship"))){
                    workspace.setType(startElement.getAttributeByName(new QName("type")).getValue());
                }
                else if (name.equals(new QName(apiNS, "object"))) {
                    try {
                        ElementsObjectCategory objectCategory = ElementsObjectCategory.valueOf(startElement.getAttributeByName(new QName("category")).getValue());
                        int objectID = Integer.parseInt(startElement.getAttributeByName(new QName("id")).getValue());
                        workspace.addObjectId(ElementsItemId.createObjectId(objectCategory, objectID));
                    }
                    catch(IndexOutOfBoundsException e){
                        //do nothing - this is just a relationship to an object type we don't know how to handle yet..
                        //will result in an "incomplete" relationship
                    }
                }
                else if(name.equals(new QName(apiNS, "is-visible"))){
                    XMLEvent nextEvent = readerProxy.peek();
                    if (nextEvent.isCharacters())
                        workspace.setIsVisible(Boolean.parseBoolean(nextEvent.asCharacters().getData()));
                }
            }
        }

        @Override
        protected ElementsRelationshipInfo finaliseItemExtraction(EndElement finalElement, XMLEventProcessor.ReaderProxy readerProxy){
            return workspace;
        }
    }

    //default visible to "true" so that relationships that are not marked as visible at all (e.g those between a grant and a publication) are definitely included.
    private boolean isVisible = true;
    private String type = null;
    private final List<ElementsItemId.ObjectId> objectIds = new ArrayList<ElementsItemId.ObjectId>();

    //package private as should only ever be constructed by create calls into superclass
    ElementsRelationshipInfo(int id) { super(ElementsItemId.createRelationshipId(id)); }

    public String getType() {
        if(type == null) throw new IllegalAccessError("typeId has not been initialised");
        return type;
    }
    private void setType(String type) {
        this.type = type;
    }

    public boolean getIsVisible() {
        return isVisible;
    }

    public boolean getIsComplete() {
        return objectIds.size() == 2;
    }

    private void setIsVisible(boolean isVisible) {
        this.isVisible = isVisible;
    }

    public List<ElementsItemId.ObjectId> getUserIds() {
        List<ElementsItemId.ObjectId> userIds = new ArrayList<ElementsItemId.ObjectId>();
        for(ElementsItemId.ObjectId id : objectIds){
            if(id.getItemSubType() == ElementsObjectCategory.USER) userIds.add(id);
        }
        return userIds;
    }

    public List<ElementsItemId.ObjectId> getNonUserIds() {
        List<ElementsItemId.ObjectId> nonUserIds = new ArrayList<ElementsItemId.ObjectId>();
        for(ElementsItemId.ObjectId id : objectIds){
            if(id.getItemSubType() != ElementsObjectCategory.USER) nonUserIds.add(id);
        }
        return nonUserIds;
    }

    private void addObjectId(ElementsItemId.ObjectId id) { objectIds.add(id); }

    public List<ElementsItemId.ObjectId> getObjectIds() { return Collections.unmodifiableList(objectIds); }
}
