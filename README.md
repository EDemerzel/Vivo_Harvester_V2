# Symplectic Elements Harvester for VIVO

This is a significatnly updated version of the VIVO Harvester that enables the use of Symplectic Elements as a data source for VIVO.
This version of the connector is based on the earlier open source versions, and leverages a similar XSLT pipeline to perform the main translations.
Nonetheless it has been significantly altered to support both delta based updates from the Elements API (to reduce load on the Elements server), 
transfer of Elements groups and group membership information, and address a variety of performance issues.

## Prerequisites

You must have the Java Development Kit and Maven 2+ installed.

NOTE: You may need to have an official Oracle JDK. The harvester has not been tested against OpenJDK.

IMPORTANT: It has been found that JDK 1.7.0 (runtime build 1.7.0-b147) has a problem with the concurrency methods used. Please ensure that a more recent JDK is used (1.7.0_03 and upwards have been tested with no apparent issues).

## Development Environment

Typically, we use IntelliJ IDEA for development (there is a free community edition as well as the commercial release).
Among the many nice features, is direct support for Maven projects. So, once you have installed the dependencies (above), all you need to do is open the pom.xml file from the elements-harvester directory.
This will create a project, providing access to all of the extension code, and setting up all of the dependencies as external libraries.

##Description

The solution fundamentally consists of two components:

1. The harvester (ElementsFetchAndTranslate)
2. The fragment loader (FragmentLoader)

###Harvester

The harvester performs several functions:

* Harvesting data from the elements API
* Translating the harvested data to a Vivo compatible RDF representation (triples)
* Loading those triples into a temporary triple store (Jena TDB)
* Creating changesets (diffs) by comparing the current temporary triple store to the temporary triple store from the previous run (if present)
* Turning those changesets into small fragment files (by default <2MB) suitable

The harvester is designed to minimize the load that is placed on the Elements API by making use of deltas when pulling data from the Elements API.
i.e. it will try to only pull data that has changed since the last time the harvester was run.
There are, however, various areas where this is not possible (e.g. Elements group/group membership information).
Furthermore, if you are connecting to an Elements API running an API Endpoint Specification earlier than v5.5 it cannot be guaranteed that the data being pulled by a delta will be 100% accurate.
A small number of items may be missed and these will never appear in Vivo until a full re-harvest of all the data in elements is completed.
(These type of issues can occur when data is being modified during a delta harvester run).

the harvester has multiple modes in which it can be run by passing an argument on the command line:
1) Default (No argument) : This mode will run either a full pull if it is the first run or a delta on subsequent runs
2) --full : forces the harvester to perform a full re-harvest, re-pulling all the data from Elements
3) --skipgroups : this will instruct a delta run NOT to re-process the Elements group/group membership structures and instead to rely on the structures from the previous run.
4) --reprocess : to reprocess the existing cached data against the current XSLT mappings without touching the Elements API (usefull when developing custom mappings).

It is expected that these different modes shuold be combined to create a harvest schedule using cron or a similar scheduling utility.
e.g:
* Run a --skipgroups delta every 3 hours.
* Run a normal delta every day at 4 am.
* Run a --full on the last Sunday of each month.

###FragmentLoader

The Fragment loader meanwhile has just one function. To load any fragments generated by the harvester in to Vivo via the Sparql update API.
The fragment files generated by the harvester are timestamped and indexed so effectively form a queue which the fragment loader works through one by one.
Note that the fragment loader is designed to be run as a daemon process and as such there are example files for integrating it with systemd in the systemd folder.

##Usage

### Running the Harvest from IntelliJ IDEA.

Typically you will be wanting to run the harvester when developing to query the Elements API, retrieve all of the objects, and translate them into VIVO model RDF files.

As such, it is useful to have this step setup for execution within the IDE.

In IntelliJ IDEA, what you should do is:

1. Open the drop-down next to the run / debug buttons in the tool bar.
2. Select 'edit configurations'
3. In the dialog, click the + icon at the top of the tree on the left. Choose 'Application'
4. On the right, change the name to 'ElementsFetchAndTranslate'
5. Set the main class to: uk.co.symplectic.vivoweb.harvester.app.ElementsFetchAndTranslate
7. Set working directory to: <project dir>/elements-harvester
8. Save this configuration (click OK).

When doing this you may additionally wish to set the Program Arguments value to make the code run with --full, --skipgroups or --reprocess.

You should now be able to run and/or debug the Elements harvester.
To achieve anything useful you will need to ensure that the "elementfetch.properties" file inside src/main/resources directory has been configured correctly,
At a minimum you will need to configure the harvester to know how to reach the Elements API being harvested.
This will involve setting at least the **Emphasize**apiEndpoint**Emphasize** and **Emphasize**apiVersion**Emphasize** parameters and secure APIs will need the **Emphasize**apiUsername**Emphasize** and **Emphasize**apiPassword**Emphasize** parameters too.

You will also need to specify the XSLT pipeline's entry point.
By default setting the **Emphasize**xslTemplate**Emphasize** parameter to **Emphasize**example-scripts/example-elements/elements-to-vivo.xsl**Emphasize** will allow you to use the example default scripts shipped with the harvester.

**Strong**You may additionally wish to set the **Emphasize**zipFiles**Emphasize** parameter to false if you are intending to develop custom XSLT mappings, although be aware that this will significantly increase the storage requirements of the harvester's intermediate output**Strong**

When you run the newly created IntelliJ configuration (assuming you made no other config changes), it will create a 'data' subdirectory within the '<project dir>/elements-harvester' directory.
Inside this will be 'data' directory will be:
* 'raw-records' (containing the data as it is retrieved from the API).
* 'translated-records' (containing the VIVO model RDF).
* 'harvestedImages' (containing any processed user photos pulled from Elements).
* 'tdb-output' (containing the temporary triple stores, change sets and the fragment files).
* 'other-data' (containing any cached group membership information).

Additionally a state.txt file will appear in the '<project dir>/elements-harvester' directory which is how the harvester tracks the current state of the system.

### Developing Translations of Elements to VIVO model

As each installation of Elements will capture data in slightly different ways, the key customization for anyone wanting to implement a VIVO instance with Symplectic Elements will be the translation of the Elements data to the VIVO model.

The Elements API returns records in an XML format, and XSLT is used to convert that to the RDF model.
With IntelliJ IDEA and its XSLT-Debugger plugin, it is possible to run the XSLT translations directly within the IDE, and even use a step-by-step debugger on the translation (if you have the commercial version).

In order to do so, you should first run an ElementsFetchAndTranslate (to obtain the data/raw-records directory) **Strong**with **Emphasize**zipFiles**Emphasize** set to false**Strong**.
Note that you can use the paramUserGroups and excludeUserGroups parameters (and other parameters) in the elementfetch.properties to restrict the amount of data being pulled from Elements if desired.

By default the harvester compresses its intermeditate output, including the contents of the data/raw-records directory. 
It can do this as the java layer passes the data into the XSLT pipeline directly and de-compresses the files on the fly.
When running the XSLT's directly within an IDE this on the fly de-compressing cannot be done, so it is important that your when you run your initial fetch of data that the zipFiles parameter in the "elementfetch.config" file is set to false.

Similarly the harvester often passes some extra parameters to the XSLT pipeline from the java layer
For example when processing certain types of Elements relationships (e.g. "activity-user-association" relationships) it passes in an extraObjects parameter containing Elements API XML representations of the objects involved in that relationship.
The example XSLT's have been designed to make it easy to work around this (provided the raw files are not zipped - see above) by passing in parameters to the XSLT pipeline from the Run Configuration in the IDE.

Once you have a cache of harvested data to work from then:

1. Open the drop-down next to the run / debug buttons in the tool bar.
2. Select 'Edit configurations'.
3. In the dialog, click the + icon at the top of the tree on the left. Choose 'XSLT'.
4. On the right, give this configuration a name.
5. Set XSLT script file to: <project dir>/example-scripts/example-elements/elements-to-vivo.xsl
6. Set Choose XML input file to: <project dir>/example-scripts/example-elements/data/raw-records/.... (choose a user / publication / relationship file, depending on the translation you are working on)
7. Uncheck the 'Make' checkbox (you don't need to rebuild the code when running the XSLT translation).
8. In the parameters section click the + icon, set the Name Column to "useRawDataFiles" and the Value column to "true".
9. In the parameters section click the + icon, set the Name Column to "recordDir" and the Value column to the path to your <project dir>/example-scripts/example-elements/data/raw-records/ directory (relative paths are acceptable).
9. Save this configuration (click OK).

You should now be able to run this translation, and the results will appear in a 'console' tab.

If there have issues with the XSLT's failing then it may be an issue where IntelliJ may default to using Xalan or an earlier version of SAXON which only support XSLT 1.0.
To rectify this try editing the Run configuration and on the Advanced tab, input "_-Dxslt.transformer.type=saxon9_" in the VM Arguments field to try and force the use of the XSLT 2.0 compatible SAXON 9.x XSLT processor. 


## Packaging and Deployment

When you are ready to move from your workstation to a server (either test or production), then you will need to package up the Elements harvester extensions, and install them on the server.

Start by opening a command prompt / terminal. Navigate to where you cloned the VIVO harvester project, and into the 'elements-harvester' subdirectory. From here, run:

	mvn clean package
	
Once it finishes executing, a .tar.gz file will be created in the 'target' directory.

To install the Elements harvester on a server, transfer and extract the elements-harvester.tar.gz folder at the location where you wish it to be installed.
You have free choice in this location, but we recommend installing it alongside the vivo "home" folder of the instance it will populate.
e.g at a location such as "/usr/local/vivo/harvester" if the vivo instance's home folder is at "/usr/local/vivo/home".

With this done you should run the init/initialise script suitable to your system. This will copy the example config files into the main installation folder and copy the example-scripts into a scripts folder.

**Strong**If you have any custom XSLT scripts you should deploy them within the scripts folder at this point (ideally in a separate directory)**Strong**

###Configuration

You should now edit the **Emphasize**elementsfetch.properties**Emphasize** file that is present in the main folder.
This will involve setting at least the **Emphasize**apiEndpoint**Emphasize** and **Emphasize**apiVersion**Emphasize** parameters and secure APIs will need the **Emphasize**apiUsername**Emphasize** and **Emphasize**apiPassword**Emphasize** parameters too.
There are a host of other configuration parameters that may be useful to you - please inspect the file.
If you have deployed custom XSLT scripts you should update the xslTemplate parameter accordingly.

Additionally you should edit the **Emphasize**fragmentloader.properties**Emphasize** file present in the main folder, to allow the fragment loader to be able to load data into Vivo.
Typically all you will need to edit here are the **Empasize**sparqlApiEndpoint**Empasize**, **Empasize**sparqlApiUsername**Empasize** and **Empasize**sparqlApiPassword**Empasize** values.
The **Empasize**sparqlApiEndpoint**Empasize** parameter should be set to the vivo server's URL (just the base URL is fine, although the exact URL of the sparqlUpdate endpoint is also valid) and the username and password values should be set appropriately.
**Strong**Note that by default only the Vivo root user has access to the sparql update API.**Strong**

##Running the harvester

Once you have completed the configuration of **Emphasize**elementsfetch.properties**Emphasize** file you can run the harvester by running the script elementsfetch.sh within the main installation folder.
You can pass the harvester options (--full, --skipgroups or --reprocess) to this script on the command line.

To transfer any fragments generated by running the harvester to Vivo you should run the ./fragmentloader.sh script once you have configured the **Emphasize**fragmentloader.properties**Emphasize** file.
Note that this process is designed to be a constantly running daemon process and the systemd folder contains information on how to integrate it with the systemd init process manager used by most linux distributions.

##Harvest advice

For the first run of elementsfetch.sh, your VIVO instance should be empty before you start.
Subsequent executions of elementsfetch.sh will perform differential updates - but ONLY if you retain the state.txt and 'tdb-output' directory created by the process
If either of these gets removed, you should ideally start again with a clean VIVO instance.

If you wish to clear down your VIVO instance and start again from scratch, you shuold remove the the state.txt file and the 'tdb-output' directory.

Note that when performing the initial load the re-inferencing and re-indexing processes within Vivo can be a significant bottleneck that increase the load time of the initial dataset by several days.
To work around this we reccomend the following order with regard to loading fragments for an initial load:

1. Disable inferencing in your vivo server (edit WEB-INF/resources/startup_listeners.txt within your deployed web app and comment out the line "edu.cornell.mannlib.vitro.webapp.servlet.setup.SimpleReasonerSetup".
2. Enable developer mode and disable indexing
3. Restart tomcat.
4. Start Fragment loader and let all fragments run in
5. Re-enable inferencing (reverse step 1)
6. Re-enable indexing (reverse step 2)
7. Log into vivo and recompute the inferences (this will also re-index all the data)

You should adopt a similar procedure if you deploy mapping changes that create a significanty large change set.

Typically you will want to schedule runs of elementsfetch.sh using cron. Typically we would reccomend running a differential update once a day.
If you are connecting to an Elements API using an Elements API Endpoint Specifictaion prior to v5.5 we reccomend running a full re-harvest (--full) fairly regularly (e.g. once a fortnight).
If your API is using an endpoint specification > v5.5 then technically you should not need to run a full re-harvest, but nonetheless we would reccomend doing so periodically (e.g. once every couple of months).

## Acknowledgements

The first version of the Elements-VIVO Harvester was developed by Ian Boston, and can be found at: https://github.com/ieb/symplectic-harvester
The second version was devloped by Graham Triggs, and can be found at https://github.com/Symplectic/VIVO
    Additional thanks to Daniel Grant from Emory University for his contribution of 4.6 API code and teaching activity XSLT via pull request.


