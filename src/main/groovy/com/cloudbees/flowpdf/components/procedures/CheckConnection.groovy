package com.cloudbees.flowpdf.components.procedures

import com.cloudbees.flowpdf.components.delinea.DelineaClient
import com.cloudbees.flowpdf.components.delinea.DelineaConfig

/**
 * Handler for the CheckConnection procedure.
 * Validates credentials and endpoint connectivity against Delinea Secret Server.
 */
class CheckConnection {

    private final def context
    private final def efClient

    CheckConnection(def context = null, def efClient = null) {
        this.context = context
        this.efClient = efClient
    }

    Map<String, Object> execute(Map<String, Object> params, DelineaConfig config) {
        println "=========================================================="
        println " Delinea Secret Server - Check Connection"
        println " Configuration: ${config.configName}"
        println " Endpoint:      ${config.getNormalizedEndpoint()}"
        println " Auth Method:   ${config.authType}"
        println " SSL Verify:    ${config.sslVerify}"
        println "=========================================================="

        DelineaClient client = new DelineaClient(config)
        Map<String, Object> result = client.checkConnection()

        String summary = "Successfully connected to Delinea Secret Server at [${config.getNormalizedEndpoint()}]."
        if (result.userName) {
            summary += " Authenticated as: ${result.userName}"
        }

        println summary
        setStepSummary(summary)

        return [
            success: true,
            summary: summary,
            userName: result.userName
        ]
    }

    protected void setStepSummary(String summary) {
        if (efClient) {
            try {
                efClient.setProperty(propertyName: '/myJobStep/summary', value: summary)
                return
            } catch (Exception ignored) {}
        }
        try {
            def commander = Class.forName("com.electriccloud.client.groovy.ElectricFlowClient").newInstance()
            commander.setProperty(propertyName: '/myJobStep/summary', value: summary)
        } catch (Throwable ignored) {
            println "[INFO] Summary: ${summary}"
        }
    }
}
