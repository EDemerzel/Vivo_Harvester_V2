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

package uk.co.symplectic.vivoweb.harvester.store;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.NullArgumentException;
import uk.co.symplectic.vivoweb.harvester.model.*;
import uk.co.symplectic.utils.xml.StAXUtils;
import uk.co.symplectic.utils.xml.XMLEventProcessor;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.text.MessageFormat;

/**
 * A class to represent data about an Elements item held in a store.
 * Extends a BasicElementsStoredItem (which this holds internally) with an ElementsItemInfo providing richer detail
 * about the underlying Elements item.
 *
 * Provides information about the itemInfo, the resourceType, access to the underlying data as a StoredData object
 * and information about the "address" (e.g. filename) where the underlying file can be found.
 *
 * Additionally provides static "loadStoredResource" methods to create one of these from:
 * BasicElementsStoredItem, StoredData/ResourceType pairs
 * These load methods will only work for "RAW-xxx" storable resources types and leverage the relevant Extractors to
 * create the ElementsItemInfo from the underlying data.
 */

@SuppressWarnings("unused")
public class ElementsStoredItemInfo {
    private final ElementsItemInfo itemInfo;
    private final BasicElementsStoredItem innerItem;

    public ElementsStoredItemInfo(ElementsItemInfo itemInfo, StorableResourceType resourceType, StoredData data) {
        if (itemInfo == null) throw new NullArgumentException("itemInfo");
        if (resourceType == null) throw new NullArgumentException("resourceType");
        if (data == null) throw new NullArgumentException("data");

        innerItem = new BasicElementsStoredItem(itemInfo.getItemId(), resourceType, data);
        this.itemInfo = itemInfo;
    }

    public ElementsItemInfo getItemInfo() {
        return itemInfo;
    }

    public StorableResourceType getResourceType() {
        return innerItem.getResourceType();
    }

    public InputStream getInputStream() throws IOException{
        return innerItem.getStoredData().getInputStream();
    }

    public String getAddress(){
        return innerItem.getStoredData().getAddress();
    }

    private synchronized static <T> T loadFromStoredData(StoredData data, XMLEventProcessor.ItemExtractingFilter<T> extractor) {
        if (data == null) throw new NullArgumentException("data");
        InputStream inputStream = null;
        try {
            //TODO: check UTF-8 behaviour here.
            inputStream = data.getInputStream();
            XMLInputFactory xmlInputFactory = StAXUtils.getXMLInputFactory();
            XMLEventProcessor processor = new XMLEventProcessor(extractor);
            processor.process(xmlInputFactory.createXMLEventReader(inputStream));

            return extractor.getExtractedItem();
        } catch (FileNotFoundException fileNotFoundException) {
            throw new IllegalStateException("Catastrophic failure reading files - abandoning", fileNotFoundException);
        } catch (IOException ioException) {
            throw new IllegalStateException("Catastrophic failure reading files - abandoning", ioException);
        } catch (XMLStreamException xmlStreamException) {
            throw new IllegalStateException("Catastrophic failure reading files - abandoning", xmlStreamException);
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } catch (IOException ignored) {
                // no sensible way to report this without hiding things - all sensible paths exit
            }
        }
    }

    private static void checkItem(ElementsItemId loadedId, ElementsItemId idToCompareTo, StoredData source, String typeLogName){
        if (idToCompareTo != null && !idToCompareTo.equals(loadedId)) {
            String message = MessageFormat.format("Elements {0} loaded from \"{1}\" ({2}) does not match supplied check value ({3})",
                    typeLogName, source.getAddress(), loadedId, idToCompareTo);
            throw new IllegalStateException(message);
        }
    }

    public synchronized static ElementsStoredItemInfo loadStoredResource(BasicElementsStoredItem item){
        return loadStoredResource(item.getStoredData(), item.getResourceType(), item.getItemId());
    }

    public synchronized static ElementsStoredItemInfo loadStoredResource(StoredData data, StorableResourceType type){
        return loadStoredResource(data, type, null);
    }

    @SuppressWarnings("WeakerAccess")
    public synchronized static ElementsStoredItemInfo loadStoredResource(StoredData data, StorableResourceType type, ElementsItemId idToCompareTo){
        if(data == null) throw new NullArgumentException("data");
        if(type == null) throw new NullArgumentException("type");
        StorableResourceType[] validTypes = {StorableResourceType.RAW_OBJECT, StorableResourceType.RAW_RELATIONSHIP, StorableResourceType.RAW_GROUP};
        if(!ArrayUtils.contains(validTypes, type)) throw new IllegalStateException("Invalid storable resource type passed to loadStoredResource");

        XMLEventProcessor.ItemExtractingFilter<ElementsItemInfo> extractor = ElementsItemInfo.getExtractor(type.getKeyItemType(), ElementsItemInfo.ExtractionSource.FILE, 1);
        ElementsItemInfo itemInfo = loadFromStoredData(data, extractor);
        checkItem(itemInfo.getItemId(), idToCompareTo, data, type.getKeyItemType().getName());
        return new ElementsStoredItemInfo(itemInfo, type, data);
    }
}







