package com.cloudbees.flowpdf.components.procedures

import com.cloudbees.flowpdf.components.delinea.DelineaConfig

/**
 * Handler for the RestorePemKey procedure.
 * Enables any pipeline stage to restore and materialize a .pem private key onto the local agent filesystem
 * with strict 0600 POSIX permissions from pipeline runtime properties (e.g. /myPipelineRuntime/delineaSecret/pem).
 */
class RestorePemKey {

    private final def context
    private final def efClient

    RestorePemKey(def context = null, def efClient = null) {
        this.context = context
        this.efClient = efClient
    }

    /**
     * Executes .pem key materialization on the current agent workspace.
     *
     * @param params Map containing runtime procedure parameters:
     *        - pemKeyProperty: property path containing the PEM key (default: /myPipelineRuntime/delineaSecret/pem)
     *        - destinationFilePath: path where .pem file will be written on current agent (e.g. ~/.ssh/id_rsa or /tmp/key.pem)
     *        - filePermissions: POSIX permissions (default: 0600)
     *        - overwrite: true/false (default: true)
     *        - resultPropertySheet: property sheet path (default: /myJob/pemFile)
     * @param config DelineaConfig instance (optional)
     */
    Map<String, Object> execute(Map<String, Object> params, DelineaConfig config = null) {
        println "=========================================================="
        println " Delinea Secret Server - Restore .pem Key Procedure"
        println "=========================================================="

        String pemKeyProp = params.pemKeyProperty?.toString()?.trim() ?: '/myPipelineRuntime/delineaSecret/pem'
        String destinationFilePath = params.destinationFilePath?.toString()?.trim()

        if (!destinationFilePath) {
            throw new IllegalArgumentException("Parameter 'destinationFilePath' is required (e.g., ~/.ssh/id_rsa or /tmp/delinea.pem).")
        }

        String filePermissions = params.filePermissions?.toString()?.trim() ?: '0600'
        boolean overwrite = params.overwrite == null || Boolean.parseBoolean(params.overwrite.toString())
        String resultPropertySheet = params.resultPropertySheet?.toString()?.trim() ?: '/myJob/pemFile'

        println "Reading .pem private key from property [${pemKeyProp}]..."
        String pemContent = readProperty(pemKeyProp)

        if (!pemContent) {
            // Check fallback standard pipeline property locations
            List<String> fallbacks = [
                '/myPipelineRuntime/delineaSecret/pemKey',
                '/myPipelineRuntime/delineaSecret/private-key',
                '/myPipelineRuntime/delineaSecret/privateKey',
                '/myJob/delineaSecret/pem',
                '/myJob/delineaSecret/private-key'
            ]
            for (String fallback : fallbacks) {
                if (fallback != pemKeyProp) {
                    pemContent = readProperty(fallback)
                    if (pemContent) {
                        println "Found .pem key in fallback property [${fallback}]."
                        break
                    }
                }
            }
        }

        if (!pemContent || pemContent.trim().isEmpty()) {
            throw new IllegalStateException("No .pem private key content found at property '${pemKeyProp}'. " +
                    "Ensure GetSecret has run in an earlier pipeline stage with exportToPipeline enabled.")
        }

        // Expand home directory ~ if present
        String expandedPath = destinationFilePath
        if (expandedPath.startsWith('~')) {
            String userHome = System.getProperty("user.home")
            expandedPath = expandedPath.replaceFirst('^~', userHome)
        }

        File targetFile = new File(expandedPath).absoluteFile
        targetFile.parentFile?.mkdirs()

        if (targetFile.exists() && !overwrite) {
            throw new IllegalStateException("Destination file already exists and 'overwrite' is false: ${targetFile.absolutePath}")
        }

        targetFile.setText(pemContent, 'UTF-8')
        GetSecret.applyFilePermissions(targetFile, filePermissions)

        println "Successfully restored .pem key (${pemContent.length()} chars) to '${targetFile.absolutePath}' with permissions '${filePermissions}'."

        // Export output properties
        setProperty("${resultPropertySheet}/filePath", targetFile.absolutePath)
        setProperty("${resultPropertySheet}/fileName", targetFile.name)
        setProperty("${resultPropertySheet}/fileSize", targetFile.length().toString())
        setProperty("${resultPropertySheet}/permissions", filePermissions)

        String summary = "Restored .pem key to '${targetFile.absolutePath}' with ${filePermissions} permissions."
        setStepSummary(summary)

        return [
            success: true,
            filePath: targetFile.absolutePath,
            fileName: targetFile.name,
            fileSize: targetFile.length(),
            permissions: filePermissions,
            summary: summary
        ]
    }

    protected String readProperty(String propertyName) {
        if (efClient) {
            try {
                def res = efClient.getProperty(propertyName: propertyName)
                return res?.property?.value
            } catch (Exception ignored) {}
        }
        try {
            def commander = Class.forName("com.electriccloud.client.groovy.ElectricFlowClient").newInstance()
            def res = commander.getProperty(propertyName: propertyName)
            return res?.property?.value
        } catch (Throwable ignored) {
            return null
        }
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
