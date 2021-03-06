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

import uk.co.symplectic.vivoweb.harvester.config.Configuration;

/**
 * Class to construct singleton instances of ElementsItemFileStore to represent stores for:
 * RAW data and TRANSLATED data
 */
public class ElementsStoreFactory {

    private static ElementsItemFileStore objectStore = null;
    private static ElementsRdfStore rdfStore = null;

    public static ElementsItemFileStore getObjectStore() {
        if (objectStore != null) {
            return objectStore;
        } else {
            synchronized (ElementsStoreFactory.class) {
                if (objectStore == null)
                    objectStore = new ElementsRawDataStore(Configuration.getRawOutputDir(), false, Configuration.getZipFiles());
                return objectStore;
            }
        }
    }

    public static ElementsRdfStore getRdfStore() {
        if (rdfStore != null) {
            return rdfStore;
        } else {
            synchronized (ElementsStoreFactory.class) {
                if(rdfStore == null)
                    rdfStore = new ElementsRdfStore(Configuration.getRdfOutputDir(), false, Configuration.getZipFiles());
                return rdfStore;
            }
        }
    }
}
