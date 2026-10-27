package com.cloudbees.flowpdf.runner

import com.cloudbees.flowpdf.components.delinea.DelineaConfig
import com.cloudbees.flowpdf.components.procedures.CheckConnection
import com.cloudbees.flowpdf.components.procedures.DownloadSecretFile
import com.cloudbees.flowpdf.components.procedures.GetSecret
import com.cloudbees.flowpdf.components.procedures.RestorePemKey
import com.cloudbees.flowpdf.components.procedures.SearchSecrets

/**
 * Universal runtime runner for EC-DelineaSecretServer procedures in CloudBees CD/RO.
 * Bridges CDRO server properties, credentials, and parameters into FlowPDF procedure handlers.
 */
class ProcedureRunner {

    private final def ef

    ProcedureRunner(def electricFlowClient = null) {
        if (electricFlowClient) {
            this.ef = electricFlowClient
        } else {
            this.ef = createDefaultEfClient()
        }
    }

    /**
     * Entry point called from procedure step groovy scripts.
     *
     * @param procedureName Name of procedure: 'GetSecret', 'CheckConnection', 'SearchSecrets', 'RestorePemKey'
     * @param params Map of procedure parameters passed from ec-groovy script
     */
    void run(String procedureName, Map<String, Object> params) {
        try {
            if ('RestorePemKey'.equalsIgnoreCase(procedureName)) {
                new RestorePemKey(null, ef).execute(params, null)
                setOutcome('success')
                return
            }

            String configName = params.config?.toString()?.trim()
            if (!configName) {
                throw new IllegalArgumentException("Procedure parameter 'config' (Configuration Name) is required.")
            }

            DelineaConfig config = loadConfiguration(configName)

            switch (procedureName) {
                case 'GetSecret':
                    new GetSecret(null, ef).execute(params, config)
                    break
                case 'DownloadSecretFile':
                    new DownloadSecretFile(null, ef).execute(params, config)
                    break
                case 'CheckConnection':
                    new CheckConnection(null, ef).execute(params, config)
                    break
                case 'SearchSecrets':
                    new SearchSecrets(null, ef).execute(params, config)
                    break
                default:
                    throw new IllegalArgumentException("Unknown procedure name: '${procedureName}'")
            }

            setOutcome('success')
        } catch (Throwable t) {
            println "----------------------------------------------------------"
            println " [ERROR] Procedure failed: ${t.message}"
            println "----------------------------------------------------------"
            t.printStackTrace(System.out)

            setOutcome('error')
            setSummary("Error: ${t.message}")
            throw t
        }
    }

    /**
     * Resolves plugin configuration and credentials from CDRO property hierarchy.
     */
    DelineaConfig loadConfiguration(String configName) {
        String pluginName = getPluginName()
        String cfgPath = "/plugins/${pluginName}/project/ec_plugin_cfgs/${configName}"

        Map<String, Object> props = [:]
        props.endpoint = getProperty("${cfgPath}/endpoint")
        props.tokenUrl = getProperty("${cfgPath}/tokenUrl")
        props.authType = getProperty("${cfgPath}/authType") ?: 'oauth2_password'
        props.domain = getProperty("${cfgPath}/domain")
        props.sslVerify = getProperty("${cfgPath}/sslVerify")
        props.httpProxy = getProperty("${cfgPath}/httpProxy")
        props.debugLevel = getProperty("${cfgPath}/debugLevel")

        // Retrieve credential
        try {
            String credName = "${configName}-credential"
            String projectName = "/plugins/${pluginName}/project"

            def credResponse = ef?.getFullCredential(credentialName: credName, projectName: projectName)
            if (credResponse) {
                props.userName = credResponse.credential?.userName
                props.password = credResponse.credential?.password
            }
        } catch (Exception e) {
            println "[DEBUG] Note: Could not fetch credential via getFullCredential (${e.message}). Falling back to property sheet."
            props.userName = getProperty("${cfgPath}/credential/userName")
            props.password = getProperty("${cfgPath}/credential/password")
        }

        return DelineaConfig.fromMap(configName, props)
    }

    protected String getProperty(String path) {
        try {
            def res = ef?.getProperty(propertyName: path)
            return res?.property?.value
        } catch (Exception ignored) {
            return null
        }
    }

    protected void setProperty(String path, String value) {
        try {
            ef?.setProperty(propertyName: path, value: value)
        } catch (Exception ignored) {}
    }

    protected void setOutcome(String outcome) {
        setProperty('/myJobStep/outcome', outcome)
    }

    protected void setSummary(String summary) {
        setProperty('/myJobStep/summary', summary)
    }

    protected String getPluginName() {
        String pName = getProperty('/myJob/pluginName')
        if (pName) return pName

        // Fallback default
        return 'EC-DelineaSecretServer'
    }

    private static Object createDefaultEfClient() {
        try {
            Class<?> clazz = Class.forName("com.electriccloud.client.groovy.ElectricFlowClient")
            return clazz.newInstance()
        } catch (Throwable t) {
            return null
        }
    }
}
