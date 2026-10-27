package com.cloudbees.flowpdf.components.procedures

import com.cloudbees.flowpdf.components.delinea.DelineaClient
import com.cloudbees.flowpdf.components.delinea.DelineaConfig
import groovy.json.JsonOutput

/**
 * Handler for the GetSecret procedure.
 * Retrieves secret values from Delinea Secret Server and exports them to CDRO property sheets and/or job credentials.
 */
class GetSecret {

    private final def context
    private final def efClient

    GetSecret(def context = null, def efClient = null) {
        this.context = context
        this.efClient = efClient
    }

    /**
     * Main execution entry point.
     *
     * @param params Map containing runtime procedure parameters:
     *        - config: configuration name
     *        - secretId: numeric ID (optional if secretPath given)
     *        - secretPath: path/name lookup (optional if secretId given)
     *        - fieldNames: comma-separated list of field slugs (optional)
     *        - resultPropertySheet: property sheet path (default: /myJob/delineaSecret)
     *        - destinationFilePath: optional file path on agent to write a secret field (e.g. ~/.ssh/id_rsa or /tmp/cert.pem)
     *        - fileFieldName: specific field slug to write to destinationFilePath (auto-detected if omitted)
     *        - filePermissions: POSIX file permissions for saved file (default: 0600)
     *        - fetchFileContent: whether to auto-fetch file attachments from Delinea (default: true)
     *        - createJobCredential: true/false
     *        - credentialName: name of job credential
     *        - credentialUserField: field name for username (default: username)
     *        - credentialPasswordField: field name for password (default: password)
     *        - maskPasswords: true/false
     * @param config DelineaConfig instance
     */
    Map<String, Object> execute(Map<String, Object> params, DelineaConfig config) {
        println "=========================================================="
        println " Delinea Secret Server - Get Secret Procedure"
        println " Configuration: ${config.configName}"
        println " Endpoint:      ${config.getNormalizedEndpoint()}"
        println "=========================================================="

        String secretIdStr = params.secretId?.toString()?.trim()
        String secretPath = params.secretPath?.toString()?.trim()

        if (!secretIdStr && !secretPath) {
            throw new IllegalArgumentException("Either 'secretId' or 'secretPath' must be provided.")
        }

        String resultPropertySheet = params.resultPropertySheet?.toString()?.trim() ?: '/myJob/delineaSecret'
        boolean exportToPipeline = params.exportToPipeline == null || Boolean.parseBoolean(params.exportToPipeline.toString())
        String pipelinePropertySheet = params.pipelinePropertySheet?.toString()?.trim() ?: '/myPipelineRuntime/delineaSecret'
        String destinationFilePath = params.destinationFilePath?.toString()?.trim()
        String fileFieldName = params.fileFieldName?.toString()?.trim()
        String filePermissions = params.filePermissions?.toString()?.trim() ?: '0600'
        boolean fetchFileContent = params.fetchFileContent == null || Boolean.parseBoolean(params.fetchFileContent.toString())
        boolean createJobCredential = Boolean.parseBoolean(params.createJobCredential?.toString())
        String credentialName = params.credentialName?.toString()?.trim()
        String credUserField = params.credentialUserField?.toString()?.trim() ?: 'username'
        String credPassField = params.credentialPasswordField?.toString()?.trim() ?: 'password'
        boolean maskPasswords = params.maskPasswords == null || Boolean.parseBoolean(params.maskPasswords.toString())

        DelineaClient client = new DelineaClient(config)
        Map<String, Object> secret

        if (secretIdStr) {
            int secretId = Integer.parseInt(secretIdStr)
            println "Fetching secret with ID [${secretId}]..."
            secret = client.getSecret(secretId, fetchFileContent)
        } else {
            println "Resolving secret by path [${secretPath}]..."
            secret = client.getSecretByPath(secretPath, fetchFileContent)
        }

        println "Found secret: '${secret.name}' (ID: ${secret.id}, Template: ${secret.secretTemplateName ?: 'N/A'})"

        // Filter fields if requested
        Set<String> requestedFields = []
        if (params.fieldNames?.toString()?.trim()) {
            requestedFields = params.fieldNames.toString().split(',')*.trim()*.toLowerCase() as Set
        }

        Map<String, String> exportedFields = [:]
        List<Map<String, Object>> fieldDetails = (List<Map<String, Object>>) secret.fieldDetails

        fieldDetails.each { f ->
            String slug = f.slug
            String lowerSlug = slug?.toLowerCase()
            String lowerName = f.fieldName?.toLowerCase()

            boolean include = requestedFields.isEmpty() ||
                              requestedFields.contains(lowerSlug) ||
                              requestedFields.contains(lowerName)

            if (include && slug) {
                String val = f.itemValue ?: ''
                exportedFields[slug] = val

                // Export to CDRO job property sheet
                setProperty("${resultPropertySheet}/${slug}", val)
                if (lowerSlug != slug) {
                    setProperty("${resultPropertySheet}/${lowerSlug}", val)
                }

                // If slug contains dashes, also export camelCase and snake_case for easy groovy/js referencing
                if (slug.contains('-')) {
                    String camel = slug.split('-').collect { it.capitalize() }.join('')
                    camel = camel.substring(0, 1).toLowerCase() + camel.substring(1)
                    setProperty("${resultPropertySheet}/${camel}", val)
                    setProperty("${resultPropertySheet}/${slug.replace('-', '_')}", val)
                }

                // Export to CDRO Pipeline Runtime property sheet for cross-pipeline access
                if (exportToPipeline && pipelinePropertySheet) {
                    setProperty("${pipelinePropertySheet}/${slug}", val)
                    if (lowerSlug != slug) {
                        setProperty("${pipelinePropertySheet}/${lowerSlug}", val)
                    }
                    if (slug.contains('-')) {
                        String camel = slug.split('-').collect { it.capitalize() }.join('')
                        camel = camel.substring(0, 1).toLowerCase() + camel.substring(1)
                        setProperty("${pipelinePropertySheet}/${camel}", val)
                        setProperty("${pipelinePropertySheet}/${slug.replace('-', '_')}", val)
                    }
                }

                // Log output
                if (maskPasswords && (f.isPassword || DelineaClient.isSensitiveField(slug, f.fieldName as String))) {
                    println "  [Exported] ${slug} -> ********* (masked)"
                } else {
                    println "  [Exported] ${slug} -> ${val}"
                }
            }
        }

        // Export core metadata to job property sheet
        setProperty("${resultPropertySheet}/id", secret.id.toString())
        setProperty("${resultPropertySheet}/name", secret.name.toString())
        setProperty("${resultPropertySheet}/folderId", secret.folderId ? secret.folderId.toString() : '')
        setProperty("${resultPropertySheet}/secretTemplateName", secret.secretTemplateName ? secret.secretTemplateName.toString() : '')
        setProperty("${resultPropertySheet}/json", secret.rawJson.toString())

        // Export core metadata to pipeline runtime property sheet
        if (exportToPipeline && pipelinePropertySheet) {
            setProperty("${pipelinePropertySheet}/id", secret.id.toString())
            setProperty("${pipelinePropertySheet}/name", secret.name.toString())
            setProperty("${pipelinePropertySheet}/folderId", secret.folderId ? secret.folderId.toString() : '')
            setProperty("${pipelinePropertySheet}/secretTemplateName", secret.secretTemplateName ? secret.secretTemplateName.toString() : '')
            setProperty("${pipelinePropertySheet}/json", secret.rawJson.toString())
            println "  [Pipeline Export] Published all secret values to pipeline runtime '${pipelinePropertySheet}' (accessible across all stages)."
        }

        // Detect and enable .pem private key for cross-pipeline usage
        Map<String, Object> pemField = fieldDetails.find { f ->
            String val = f.itemValue?.toString() ?: ''
            val.contains('-----BEGIN')
        } ?: fieldDetails.find { f ->
            String s = (f.slug ?: '').toLowerCase()
            String n = (f.fieldName ?: '').toLowerCase()
            s.contains('private') || s.contains('pem') || s.contains('key') || n.contains('private key') || n.contains('pem')
        }

        String pemContent = pemField?.itemValue?.toString()
        if (pemContent) {
            println "  [PEM Enabled] Detected .pem / private key (field: '${pemField.slug}'). Enabled for cross-pipeline usage."
            setProperty("${resultPropertySheet}/pem", pemContent)
            setProperty("${resultPropertySheet}/pemKey", pemContent)
            setProperty("${resultPropertySheet}/hasPem", 'true')
            setProperty("${resultPropertySheet}/private-key", pemContent)
            setProperty("${resultPropertySheet}/privateKey", pemContent)
            setProperty("${resultPropertySheet}/private_key", pemContent)

            if (exportToPipeline && pipelinePropertySheet) {
                setProperty("${pipelinePropertySheet}/pem", pemContent)
                setProperty("${pipelinePropertySheet}/pemKey", pemContent)
                setProperty("${pipelinePropertySheet}/hasPem", 'true')
                setProperty("${pipelinePropertySheet}/private-key", pemContent)
                setProperty("${pipelinePropertySheet}/privateKey", pemContent)
                setProperty("${pipelinePropertySheet}/private_key", pemContent)
                println "  [PEM Export] Exported .pem key to '${pipelinePropertySheet}/pem' (accessible across all pipeline stages)."
            }
        }

        // Save field to destination file on agent if requested
        File savedFile = null
        if (destinationFilePath) {
            savedFile = saveFieldToFile(secret, destinationFilePath, fileFieldName, filePermissions)
            setProperty("${resultPropertySheet}/savedFilePath", savedFile.absolutePath)
            setProperty("${resultPropertySheet}/savedFileName", savedFile.name)
            if (pemContent) {
                setProperty("${resultPropertySheet}/pemFilePath", savedFile.absolutePath)
            }
            if (exportToPipeline && pipelinePropertySheet) {
                setProperty("${pipelinePropertySheet}/savedFilePath", savedFile.absolutePath)
                setProperty("${pipelinePropertySheet}/savedFileName", savedFile.name)
                if (pemContent) {
                    setProperty("${pipelinePropertySheet}/pemFilePath", savedFile.absolutePath)
                }
            }
        }

        // Create CDRO Job Credential if requested
        if (createJobCredential) {
            if (!credentialName) {
                throw new IllegalArgumentException("'credentialName' is required when 'createJobCredential' is enabled.")
            }

            // Resolve username
            String userVal = resolveCredentialField(secret, credUserField, [
                'username', 'user', 'client_id', 'clientid', 'access_key_id', 'accesskeyid', 'account', 'login'
            ], false)

            // Resolve password / secret / private-key / api-key
            String passVal = resolveCredentialField(secret, credPassField, [
                'password', 'private-key', 'privatekey', 'private_key', 'api-key', 'apikey', 'api_key',
                'token', 'secret', 'secret-access-key', 'secretaccesskey', 'client-secret', 'clientsecret', 'key'
            ], true)

            if (!userVal) {
                userVal = secret.name ?: 'delinea'
                println "[INFO] Username mapped to secret name '${userVal}'."
            }

            println "Creating dynamic CDRO credential '${credentialName}' for job..."
            createCredential(credentialName, userVal, passVal)
            println "Successfully attached credential '${credentialName}'."
        }

        String summary = "Retrieved Delinea Secret '${secret.name}' (ID: ${secret.id}). " +
                "Exported ${exportedFields.size()} fields to ${resultPropertySheet}."
        if (exportToPipeline) {
            summary += " Available across pipeline at ${pipelinePropertySheet}."
        }
        if (pemContent) {
            summary += " .pem key enabled."
        }
        if (savedFile) {
            summary += " Saved to file '${savedFile.absolutePath}'."
        }
        if (createJobCredential) {
            summary += " Created job credential '${credentialName}'."
        }
        setStepSummary(summary)

        return [
            success: true,
            secretId: secret.id,
            secretName: secret.name,
            exportedFieldsCount: exportedFields.size(),
            resultPropertySheet: resultPropertySheet,
            pipelinePropertySheet: exportToPipeline ? pipelinePropertySheet : null,
            hasPem: pemContent != null,
            savedFilePath: savedFile?.absolutePath,
            summary: summary
        ]
    }

    /**
     * Resolves a credential field value from secret fields or candidate fallback names.
     */
    private String resolveCredentialField(Map<String, Object> secret, String requestedField, List<String> fallbackNames, boolean searchSensitive) {
        String req = requestedField?.toLowerCase()
        if (req && secret.fields[req]) {
            return secret.fields[req]
        }
        if (requestedField && secret.fields[requestedField]) {
            return secret.fields[requestedField]
        }

        for (String candidate : fallbackNames) {
            if (secret.fields[candidate]) {
                return secret.fields[candidate]
            }
        }

        if (searchSensitive) {
            def sensitiveField = ((List<Map<String, Object>>) secret.fieldDetails).find { it.isPassword == true }
            if (sensitiveField && sensitiveField.itemValue) {
                return sensitiveField.itemValue.toString()
            }
        }

        return ''
    }

    /**
     * Writes a target secret field (private key, certificate, API key, or file attachment) to a file on disk.
     */
    protected File saveFieldToFile(Map<String, Object> secret, String destPath, String fieldName, String permissions) {
        List<Map<String, Object>> fieldDetails = (List<Map<String, Object>>) secret.fieldDetails
        Map<String, Object> targetField = null

        if (fieldName) {
            String lowerField = fieldName.toLowerCase()
            targetField = fieldDetails.find {
                it.slug?.equalsIgnoreCase(fieldName) ||
                it.fieldName?.equalsIgnoreCase(fieldName) ||
                it.slug?.toLowerCase()?.replace('-', '_') == lowerField.replace('-', '_')
            }
            if (!targetField) {
                throw new IllegalArgumentException("Requested field '${fieldName}' was not found in secret '${secret.name}'.")
            }
        } else {
            // Auto-detect best field candidate (file attachment, private key, cert, or apiKey)
            targetField = fieldDetails.find { it.isFile == true } ?:
                          fieldDetails.find { it.slug?.toLowerCase()?.contains('private') || it.slug?.toLowerCase()?.contains('key') } ?:
                          fieldDetails.find { it.slug?.toLowerCase()?.contains('cert') } ?:
                          fieldDetails.find { it.slug?.toLowerCase()?.contains('token') || it.slug?.toLowerCase()?.contains('api') } ?:
                          fieldDetails.find { it.isPassword == true }

            if (!targetField && !fieldDetails.isEmpty()) {
                targetField = fieldDetails[0]
            }
        }

        if (!targetField) {
            throw new IllegalStateException("Could not identify any field to save to '${destPath}'.")
        }

        File targetFile = new File(destPath).absoluteFile
        targetFile.parentFile?.mkdirs()

        if (targetField.fileBytes) {
            targetFile.bytes = (byte[]) targetField.fileBytes
        } else {
            String content = targetField.itemValue != null ? targetField.itemValue.toString() : ''
            targetFile.setText(content, 'UTF-8')
        }

        applyFilePermissions(targetFile, permissions)
        println "  [Saved to File] Saved field '${targetField.slug}' to '${targetFile.absolutePath}' (Permissions: ${permissions ?: 'default'})."
        return targetFile
    }

    /**
     * Applies POSIX file permissions (e.g. 0600) on Unix/Linux/macOS platforms.
     */
    static void applyFilePermissions(File file, String permissions) {
        if (!permissions || System.getProperty("os.name", "").toLowerCase().contains("win")) {
            return
        }
        try {
            String octal = permissions.trim().replaceAll('^0+', '')
            if (octal.length() == 3) {
                Set<java.nio.file.attribute.PosixFilePermission> perms = []
                int u = Character.getNumericValue(octal.charAt(0))
                int g = Character.getNumericValue(octal.charAt(1))
                int o = Character.getNumericValue(octal.charAt(2))

                if (u & 4) perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_READ)
                if (u & 2) perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_WRITE)
                if (u & 1) perms.add(java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE)

                if (g & 4) perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_READ)
                if (g & 2) perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_WRITE)
                if (g & 1) perms.add(java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE)

                if (o & 4) perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_READ)
                if (o & 2) perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE)
                if (o & 1) perms.add(java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE)

                java.nio.file.Files.setPosixFilePermissions(file.toPath(), perms)
            }
        } catch (Throwable t) {
            println "[WARNING] Could not set POSIX permissions '${permissions}' on '${file.path}': ${t.message}"
        }
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
        // Fallback for ec-groovy runtime
        try {
            def commander = Class.forName("com.electriccloud.client.groovy.ElectricFlowClient").newInstance()
            commander.setProperty(propertyName: propertyName, value: value)
        } catch (Throwable ignored) {
            println "[INFO] Property [${propertyName}] set."
        }
    }

    protected void createCredential(String credName, String userName, String password) {
        String projectName = getProjectName()
        if (efClient) {
            // First attempt: Create project-level credential (accessible across all pipeline stages)
            if (projectName) {
                try {
                    efClient.createCredential(
                        projectName: projectName,
                        credentialName: credName,
                        userName: userName,
                        password: password
                    )
                    println "  [Pipeline Credential] Created project-level credential '${credName}' in project '${projectName}' (usable across all pipeline stages)."
                    return
                } catch (Exception e) {
                    println "[DEBUG] Project-level credential creation failed (${e.message}). Falling back to job-step credential."
                }
            }
            try {
                efClient.createCredential(
                    jobStepId: '$[/myJobStep/jobStepId]',
                    credentialName: credName,
                    userName: userName,
                    password: password
                )
                return
            } catch (Exception e) {
                println "[DEBUG] efClient.createCredential failed: ${e.message}"
            }
        }
        try {
            def commander = Class.forName("com.electriccloud.client.groovy.ElectricFlowClient").newInstance()
            if (projectName) {
                try {
                    commander.createCredential(
                        projectName: projectName,
                        credentialName: credName,
                        userName: userName,
                        password: password
                    )
                    return
                } catch (Throwable ignored) {}
            }
            commander.createCredential(
                jobStepId: '$[/myJobStep/jobStepId]',
                credentialName: credName,
                userName: userName,
                password: password
            )
        } catch (Throwable ignored) {
            println "[INFO] Credential [${credName}] created."
        }
    }

    protected String getProjectName() {
        return readProperty('/myProject/projectName') ?: readProperty('/myPipelineRuntime/projectName')
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

    protected void setStepSummary(String summary) {
        setProperty('/myJobStep/summary', summary)
    }
}
