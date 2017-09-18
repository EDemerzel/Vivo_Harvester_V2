<?xml version="1.0" encoding="UTF-8"?>
<!--
  ~ *******************************************************************************
  ~   Copyright (c) 2017 Symplectic. All rights reserved.
  ~   This Source Code Form is subject to the terms of the Mozilla Public
  ~   License, v. 2.0. If a copy of the MPL was not distributed with this
  ~   file, You can obtain one at http://mozilla.org/MPL/2.0/.
  ~ *******************************************************************************
  -->
<xsl:stylesheet version="2.0"
                xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
                xmlns:xs="http://www.w3.org/2001/XMLSchema"
                xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#"
                xmlns:rdfs="http://www.w3.org/2000/01/rdf-schema#"
                xmlns:bibo="http://purl.org/ontology/bibo/"
                xmlns:vivo="http://vivoweb.org/ontology/core#"
                xmlns:foaf="http://xmlns.com/foaf/0.1/"
                xmlns:obo="http://purl.obolibrary.org/obo/"
                xmlns:score="http://vivoweb.org/ontology/score#"
                xmlns:ufVivo="http://vivo.ufl.edu/ontology/vivo-ufl/"
                xmlns:vitro="http://vitro.mannlib.cornell.edu/ns/vitro/0.7#"
                xmlns:api="http://www.symplectic.co.uk/publications/api"
                xmlns:symp="http://www.symplectic.co.uk/vivo/"
                xmlns:svfn="http://www.symplectic.co.uk/vivo/namespaces/functions"
                xmlns:config="http://www.symplectic.co.uk/vivo/namespaces/config"
                exclude-result-prefixes="rdf rdfs bibo vivo foaf obo score ufVivo vitro api symp svfn config xs"
        >

    <!-- Import XSLT files that are used -->
    <xsl:import href="elements-to-vivo-utils.xsl" />

    <!--
        Output as part of relationship - Supports publication
        <vivo:supportedInformationResource rdf:resource="http://vivo.mydomain.edu/individual/n4893"/>
    -->
    <xsl:template match="api:relationship[@type='user-grant-primary-investigation' or @type='user-grant-secondary-investigation' or @type='grant-user-funding']">
        <xsl:variable name="investigatorURI" select="svfn:relationshipURI(.,'investigator')" />

        <!-- Get the user object reference from the relationship -->
        <xsl:variable name="user" select="api:related/api:object[@category='user']" />

        <!-- Get the user object reference from the relationship -->
        <xsl:variable name="grant" select="api:related/api:object[@category='grant']" />

        <!-- Create a Role -->
        <xsl:call-template name="render_rdf_object">
            <xsl:with-param name="objectURI" select="$investigatorURI" />
            <xsl:with-param name="rdfNodes">
                <xsl:choose>
                    <xsl:when test="./@type='user-grant-primary-investigation' or ./@type='user-grant-principal-investigation'">
                        <rdf:type rdf:resource="http://vivoweb.org/ontology/core#PrincipalInvestigatorRole" />
                    </xsl:when>
                    <xsl:when test="./@type='user-grant-co-primary-investigation' or ./@type='user-grant-co-principal-investigation' or ./@type='user-grant-multi-pi'">
                        <rdf:type rdf:resource="http://vivoweb.org/ontology/core#CoPrincipalInvestigatorRole" />
                    </xsl:when>
                    <xsl:when test="./@type='user-grant-secondary-investigation' or ./@type='user-grant-co-investigation'">
                        <rdf:type rdf:resource="http://vivoweb.org/ontology/core#InvestigatorRole"/>
                    </xsl:when>
                    <xsl:otherwise>
                        <rdf:type rdf:resource="http://vivoweb.org/ontology/core#ResearcherRole"/>
                        <rdfs:label>
                            <xsl:choose>
                                <xsl:when test="./@type='grant-user-funding' or ./@type='user-grant-sponsorship'">
                                    <xsl:text>Funded by</xsl:text>
                                </xsl:when>
                                <xsl:when test="./@type='user-grant-primary-investigation-sub-project'">
                                    <xsl:text>Sub Project Principal Investigator</xsl:text>
                                </xsl:when>
                                <xsl:when test="./@type='user-grant-secondary-investigation-sub-project'">
                                    <xsl:text>Sub Project Investigator</xsl:text>
                                </xsl:when>
                                <xsl:when test="./@type='user-grant-senior-key-personnel'">
                                    <xsl:text>Senior Personnel</xsl:text>
                                </xsl:when>
                                <xsl:when test="./@type='user-grant-personnel'">
                                    <xsl:text>Personnel</xsl:text>
                                </xsl:when>
                                <xsl:when test="./@type='user-grant-project-co-leadership'">
                                    <xsl:text>Sub Project Co-Leader</xsl:text>
                                </xsl:when>
                                <xsl:when test="./@type='user-grant-site-pi-investigation'">
                                    <xsl:text>Site Principal Investigator</xsl:text>
                                </xsl:when>
                                <xsl:when test="./@type='user-grant-site-investigation'">
                                    <xsl:text>Site Investigator</xsl:text>
                                </xsl:when>
                                <xsl:when test="./@type='user-grant-consulting'">
                                    <xsl:text>Consultant</xsl:text>
                                </xsl:when>
                                <xsl:when test="./@type='user-grant-collaboration'">
                                    <xsl:text>Collaborator</xsl:text>
                                </xsl:when>
                                <xsl:when test="./@type='user-grant-clinical-evaluation'">
                                    <xsl:text>Clinical Evaluator</xsl:text>
                                </xsl:when>
                                <xsl:when test="./@type='user-grant-mentoring'">
                                    <xsl:text>Mentor</xsl:text>
                                </xsl:when>
                                <xsl:when test="./@type='user-grant-program-coordination'">
                                    <xsl:text>Project Co-ordinator</xsl:text>
                                </xsl:when>
                                <xsl:when test="./@type='user-grant-project-leadership' or ./@type='user-grant-program-direction'">
                                    <xsl:text>Project Leader/Director</xsl:text>
                                </xsl:when>
                                <xsl:when test="./@type='user-grant-research'">
                                    <xsl:text>Researcher</xsl:text>
                                </xsl:when>
                                <xsl:when test="./@type='user-grant-statistics'">
                                    <xsl:text>Statistician</xsl:text>
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:text>Other Contribution</xsl:text>
                                </xsl:otherwise>
                            </xsl:choose>
                        </rdfs:label>
                    </xsl:otherwise>
                </xsl:choose>
                <vivo:relatedBy rdf:resource="{svfn:objectURI($grant)}"/><!-- link to grant -->
                <xsl:if test="./@type='user-grant-primary-investigation' or @type='user-grant-secondary-investigation'">
                    <obo:RO_0000052 rdf:resource="{svfn:userURI($user)}"/><!-- link to user -->
                </xsl:if>
                <!-- vivo:dateTimeInterval rdf:resource="http://vivo.mydomain.edu/individual/n6127" / - link to date / time -->
                <xsl:if test="api:is-visible='false'">
                    <vivo:hideFromDisplay>true</vivo:hideFromDisplay>
                </xsl:if>
            </xsl:with-param>
        </xsl:call-template>

        <!-- Add a reference to the role object to the grant object -->
        <xsl:call-template name="render_rdf_object">
            <xsl:with-param name="objectURI" select="svfn:objectURI($grant)" />
            <xsl:with-param name="rdfNodes">
                <vivo:relates rdf:resource="{$investigatorURI}"/><!-- link to role -->
                <vivo:relates rdf:resource="{svfn:userURI($user)}" /><!-- link to user -->
            </xsl:with-param>
        </xsl:call-template>

        <!-- Add a reference to the role object to the user object -->
        <xsl:call-template name="render_rdf_object">
            <xsl:with-param name="objectURI" select="svfn:userURI($user)" />
            <xsl:with-param name="rdfNodes">
                <obo:RO_0000053 rdf:resource="{$investigatorURI}"/><!-- link to role -->
                <xsl:if test="./@type='user-grant-primary-investigation' or @type='user-grant-secondary-investigation'">
                    <vivo:relatedBy rdf:resource="{svfn:objectURI($grant)}" /><!-- link to grant -->
                </xsl:if>
            </xsl:with-param>
        </xsl:call-template>
    </xsl:template>
</xsl:stylesheet>
