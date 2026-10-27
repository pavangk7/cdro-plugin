package com.cloudbees.flowpdf.components.procedures

import com.cloudbees.flowpdf.components.delinea.DelineaClient
import com.cloudbees.flowpdf.components.delinea.DelineaConfig
import groovy.json.JsonOutput

/**
 * Handler for the SearchSecrets procedure.
 * Queries Delinea Secret Server and writes results to CDRO properties.
 */
class SearchSecrets {

    private final def context
    private final def efClient

    SearchSecrets(def context = null, def efClient = null) {
        this.context = context
        this.efClient = efClient
    }

    Map<String, Object> execute(Map<String, Object> params, DelineaConfig config) {
        println "=========================================================="
        println " Delinea Secret Server - Search Secrets"
        println " Configuration: ${config.configName}"
        println " Query:         ${params.searchText}"
        println "=========================================================="

        String searchText = params.searchText?.toString() ?: ''
        Integer folderId = params.folderId?.toString()?.trim() ? Integer.parseInt(params.folderId.toString().trim()) : null
        int maxResults = params.maxResults?.toString()?.trim() ? Integer.parseInt(params.maxResults.toString().trim()) : 25
        String resultPropertySheet = params.resultPropertySheet?.toString()?.trim() ?: '/myJob/delineaSearchResults'

        DelineaClient client = new DelineaClient(config)
        List<Map<String, Object>> results = client.searchSecrets(searchText, folderId, maxResults)

        println "Found ${results.size()} matching secret(s):"
        results.eachWithIndex { sec, idx ->
            println "  [${idx + 1}] ID: ${sec.id} | Name: '${sec.name}' | Template: ${sec.secretTemplateName ?: 'N/A'}"
            setProperty("${resultPropertySheet}/secrets/${idx}/id", sec.id.toString())
            setProperty("${resultPropertySheet}/secrets/${idx}/name", sec.name.toString())
            setProperty("${resultPropertySheet}/secrets/${idx}/template", sec.secretTemplateName ?: '')
        }

        setProperty("${resultPropertySheet}/count", results.size().toString())
        setProperty("${resultPropertySheet}/json", JsonOutput.toJson(results))

        String summary = "Found ${results.size()} secret(s) matching '${searchText}'. Saved to ${resultPropertySheet}."
        setStepSummary(summary)

        return [
            success: true,
            count: results.size(),
            results: results,
            summary: summary
        ]
    }

    protected void setProperty(String propertyName, String value) {
        if (efClient) {
            try {
                efClient.setProperty(propertyName: propertyName, value: value)
                return
            } catch (Exception ignored) {}
        }
        try {
            def commander = Class.forName("com.electriccloud.client.groovy.ElectricFlowClient").newInstance()
            commander.setProperty(propertyName: propertyName, value: value)
        } catch (Throwable ignored) {
            println "[INFO] Property [${propertyName}] set."
        }
    }

    protected void setStepSummary(String summary) {
        setProperty('/myJobStep/summary', summary)
    }
}
