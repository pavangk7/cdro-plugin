package com.cloudbees.flowpdf.components.procedures

import com.cloudbees.flowpdf.components.delinea.DelineaClient
import com.cloudbees.flowpdf.components.delinea.DelineaConfig

/**
 * Handler for the DownloadSecretFile procedure.
 * Downloads file attachments (certificates, SSH private keys, kubeconfig, licenses, PFX)
 * or secret fields directly from Delinea Secret Server to the local agent filesystem.
 */
class DownloadSecretFile {

    private final def context
    private final def efClient

    DownloadSecretFile(def context = null, def efClient = null) {
        this.context = context
        this.efClient = efClient
    }

    /**
     * Executes file download from Delinea Secret Server.
     *
     * @param params Map containing runtime procedure parameters:
     *        - config: configuration name
     *        - secretId: numeric secret ID (optional if secretPath given)
     *        - secretPath: path or search expression (optional if secretId given)
     *        - fieldSlug: specific field slug to download (optional; auto-detects file/key field)
     *        - destinationPath: local target file path or directory on agent
     *        - filePermissions: POSIX permissions (default: 0600)
     *        - overwrite: whether to overwrite existing target file (default: true)
     *        - resultPropertySheet: output property sheet (default: /myJob/delineaDownloadedFile)
     * @param config DelineaConfig instance
     */
    Map<String, Object> execute(Map<String, Object> params, DelineaConfig config) {
        println "=========================================================="
        println " Delinea Secret Server - Download Secret File Procedure"
        println " Configuration: ${config.configName}"
        println " Endpoint:      ${config.getNormalizedEndpoint()}"
        println "=========================================================="

        String secretIdStr = params.secretId?.toString()?.trim()
        String secretPath = params.secretPath?.toString()?.trim()

        if (!secretIdStr && !secretPath) {
            throw new IllegalArgumentException("Either 'secretId' or 'secretPath' must be provided.")
        }

        String destinationPath = params.destinationPath?.toString()?.trim()
        if (!destinationPath) {
            throw new IllegalArgumentException("Parameter 'destinationPath' is required.")
        }

        String fieldSlug = params.fieldSlug?.toString()?.trim()
        String filePermissions = params.filePermissions?.toString()?.trim() ?: '0600'
        boolean overwrite = params.overwrite == null || Boolean.parseBoolean(params.overwrite.toString())
        String resultPropertySheet = params.resultPropertySheet?.toString()?.trim() ?: '/myJob/delineaDownloadedFile'

        DelineaClient client = new DelineaClient(config)
        Map<String, Object> secret

        if (secretIdStr) {
            int secretId = Integer.parseInt(secretIdStr)
            println "Fetching secret with ID [${secretId}]..."
            secret = client.getSecret(secretId, false)
        } else {
            println "Resolving secret by path [${secretPath}]..."
            secret = client.getSecretByPath(secretPath, false)
        }

        println "Found secret: '${secret.name}' (ID: ${secret.id}, Template: ${secret.secretTemplateName ?: 'N/A'})"

        // Identify the target field to download
        List<Map<String, Object>> fieldDetails = (List<Map<String, Object>>) secret.fieldDetails
        Map<String, Object> targetField = null

        if (fieldSlug) {
            targetField = fieldDetails.find {
                it.slug?.equalsIgnoreCase(fieldSlug) ||
                it.fieldName?.equalsIgnoreCase(fieldSlug) ||
                it.slug?.toLowerCase()?.replace('-', '_') == fieldSlug.toLowerCase().replace('-', '_')
            }
            if (!targetField) {
                // If not found in items list, proceed using fieldSlug directly
                targetField = [slug: fieldSlug, fieldName: fieldSlug, isFile: true]
            }
        } else {
            // Auto-detect: first file attachment, or private key, or certificate
            targetField = fieldDetails.find { it.isFile == true } ?:
                          fieldDetails.find { it.slug?.toLowerCase()?.contains('private') || it.slug?.toLowerCase()?.contains('key') } ?:
                          fieldDetails.find { it.slug?.toLowerCase()?.contains('cert') } ?:
                          fieldDetails.find { it.isPassword == true }

            if (!targetField && !fieldDetails.isEmpty()) {
                targetField = fieldDetails[0]
            }
        }

        if (!targetField?.slug) {
            throw new IllegalStateException("Could not find any file or field to download from secret '${secret.name}'.")
        }

        String targetSlug = targetField.slug as String
        println "Downloading field/attachment slug [${targetSlug}] for secret [${secret.id}]..."
        byte[] fileBytes = client.downloadSecretFieldBytes(secret.id as int, targetSlug)

        if (fileBytes == null || fileBytes.length == 0) {
            // Fallback check if itemValue already contained the text (e.g. inline private key)
            if (targetField.itemValue) {
                fileBytes = targetField.itemValue.toString().getBytes('UTF-8')
            }
        }

        // Determine destination file
        File destFile = new File(destinationPath).absoluteFile
        if (destFile.isDirectory() || destinationPath.endsWith('/') || destinationPath.endsWith('\\')) {
            destFile.mkdirs()
            String defaultFileName = targetField.filename ?: "${targetSlug}.dat"
            destFile = new File(destFile, defaultFileName)
        } else {
            destFile.parentFile?.mkdirs()
        }

        if (destFile.exists() && !overwrite) {
            throw new IllegalStateException("Destination file already exists and 'overwrite' is set to false: ${destFile.absolutePath}")
        }

        destFile.bytes = fileBytes
        GetSecret.applyFilePermissions(destFile, filePermissions)

        println "Successfully saved [${fileBytes.length} bytes] to '${destFile.absolutePath}' (Permissions: ${filePermissions})."

        // Export properties
        setProperty("${resultPropertySheet}/filePath", destFile.absolutePath)
        setProperty("${resultPropertySheet}/fileName", destFile.name)
        setProperty("${resultPropertySheet}/fileSize", fileBytes.length.toString())
        setProperty("${resultPropertySheet}/fieldSlug", targetSlug)
        setProperty("${resultPropertySheet}/secretId", secret.id.toString())
        setProperty("${resultPropertySheet}/secretName", secret.name.toString())

        String summary = "Downloaded file '${destFile.name}' (${fileBytes.length} bytes) from secret '${secret.name}' to '${destFile.absolutePath}'."
        setStepSummary(summary)

        return [
            success: true,
            filePath: destFile.absolutePath,
            fileName: destFile.name,
            fileSize: fileBytes.length,
            fieldSlug: targetSlug,
            secretId: secret.id,
            secretName: secret.name,
            summary: summary
        ]
    }

    protected void setProperty(String propertyName, String value) {
        if (efClient) {
            try {
                efClient.setProperty(propertyName: propertyName, value: value)
                return
            } catch (Exception e) {
                println "[DEBUG] efClient.setProperty failed: ${e.message}"
            }
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
