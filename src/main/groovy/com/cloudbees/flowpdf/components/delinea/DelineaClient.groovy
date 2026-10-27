package com.cloudbees.flowpdf.components.delinea

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate

/**
 * REST API Client for Delinea Secret Server.
 * Supports OAuth2 token retrieval, secret lookups, field extraction, and search queries.
 */
class DelineaClient {

    private final DelineaConfig config
    private String cachedAccessToken
    private long tokenExpiresAtMillis = 0

    DelineaClient(DelineaConfig config) {
        if (!config) {
            throw new IllegalArgumentException("DelineaConfig cannot be null.")
        }
        this.config = config
    }

    /**
     * Obtains or returns a valid cached OAuth2 access token.
     */
    synchronized String getAccessToken() {
        long now = System.currentTimeMillis()
        if (cachedAccessToken && (now + 30000) < tokenExpiresAtMillis) {
            return cachedAccessToken
        }

        String tokenEndpoint = config.getTokenUrl()
        Map<String, String> formParams = [:]

        if ('oauth2_client_credentials'.equalsIgnoreCase(config.authType)) {
            formParams['grant_type'] = 'client_credentials'
            formParams['client_id'] = config.username ?: ''
            formParams['client_secret'] = config.password ?: ''
        } else {
            // Default: password grant
            formParams['grant_type'] = 'password'
            formParams['username'] = config.username ?: ''
            formParams['password'] = config.password ?: ''
            if (config.domain) {
                formParams['domain'] = config.domain
            }
        }

        String postBody = formParams.collect { k, v ->
            "${URLEncoder.encode(k, 'UTF-8')}=${URLEncoder.encode(v, 'UTF-8')}"
        }.join('&')

        Map<String, String> headers = [
            'Content-Type': 'application/x-www-form-urlencoded',
            'Accept': 'application/json'
        ]

        HttpResponse response = executeHttp('POST', tokenEndpoint, headers, postBody)
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new RuntimeException("Failed to authenticate with Delinea Secret Server at [${tokenEndpoint}]. " +
                    "HTTP ${response.statusCode}: ${response.body}")
        }

        def json = new JsonSlurper().parseText(response.body)
        if (!json.access_token) {
            throw new RuntimeException("Delinea Secret Server token response did not contain 'access_token': ${response.body}")
        }

        this.cachedAccessToken = json.access_token.toString()
        int expiresIn = json.expires_in ? (json.expires_in as int) : 1200
        this.tokenExpiresAtMillis = now + (expiresIn * 1000L)

        return this.cachedAccessToken
    }

    /**
     * Validates connection and credentials against Delinea Secret Server.
     */
    Map<String, Object> checkConnection() {
        String token = getAccessToken()
        String endpoint = config.getNormalizedEndpoint() + '/api/v1/users/current'

        Map<String, String> headers = [
            'Authorization': "Bearer ${token}",
            'Accept': 'application/json'
        ]

        HttpResponse response = executeHttp('GET', endpoint, headers, null)
        if (response.statusCode >= 200 && response.statusCode < 300) {
            def userObj = new JsonSlurper().parseText(response.body)
            return [
                success: true,
                statusCode: response.statusCode,
                userName: userObj.userName ?: userObj.displayName ?: config.username,
                userId: userObj.id,
                message: "Successfully connected to Delinea Secret Server at ${config.getNormalizedEndpoint()}."
            ]
        }

        // Fallback check to /api/v1/secrets if /users/current is restricted for the service account
        String fallbackEndpoint = config.getNormalizedEndpoint() + '/api/v1/secrets?take=1'
        HttpResponse fallbackResp = executeHttp('GET', fallbackEndpoint, headers, null)
        if (fallbackResp.statusCode >= 200 && fallbackResp.statusCode < 300) {
            return [
                success: true,
                statusCode: fallbackResp.statusCode,
                userName: config.username,
                message: "Successfully authenticated with Delinea Secret Server at ${config.getNormalizedEndpoint()}."
            ]
        }

        throw new RuntimeException("Connection check failed with HTTP ${response.statusCode}: ${response.body}")
    }

    /**
     * Fetches a secret by its numeric ID.
     * Supports all Delinea secret templates (SSH Keys, API Keys, Passwords, Certificates, Cloud Tokens, etc.)
     *
     * @param secretId Numeric secret ID
     * @param fetchFileAttachments Whether to automatically download file attachments if present (default true)
     */
    Map<String, Object> getSecret(int secretId, boolean fetchFileAttachments = true) {
        String token = getAccessToken()
        String endpoint = "${config.getNormalizedEndpoint()}/api/v1/secrets/${secretId}"

        Map<String, String> headers = [
            'Authorization': "Bearer ${token}",
            'Accept': 'application/json'
        ]

        HttpResponse response = executeHttp('GET', endpoint, headers, null)
        if (response.statusCode == 404) {
            throw new NoSuchElementException("Secret with ID [${secretId}] was not found in Delinea Secret Server.")
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new RuntimeException("Failed to retrieve secret ID [${secretId}]. HTTP ${response.statusCode}: ${response.body}")
        }

        def secretJson = new JsonSlurper().parseText(response.body)
        Map<String, Object> normalized = normalizeSecretResponse(secretJson as Map)

        // Automatically fetch file attachments if present and empty
        if (fetchFileAttachments) {
            normalized.fieldDetails.each { Map<String, Object> field ->
                if (field.isFile == true && (!field.itemValue || field.itemValue.toString().trim().isEmpty())) {
                    try {
                        byte[] bytes = downloadSecretFieldBytes(secretId, field.slug as String)
                        field.fileBytes = bytes
                        field.fileSize = bytes.length
                        if (isLikelyText(bytes)) {
                            field.itemValue = new String(bytes, 'UTF-8')
                        } else {
                            field.itemValue = Base64.encoder.encodeToString(bytes)
                        }
                        registerFieldAliases(normalized.fields as Map<String, String>, field.slug as String, field.fieldName as String, field.itemValue as String)
                    } catch (Exception e) {
                        println "[DEBUG] Could not auto-download file attachment for field '${field.slug}': ${e.message}"
                    }
                }
            }
        }

        return normalized
    }

    /**
     * Downloads raw bytes of a specific secret field or file attachment from Delinea Secret Server.
     * REST endpoint: GET /api/v1/secrets/{secretId}/fields/{slug}
     */
    byte[] downloadSecretFieldBytes(int secretId, String fieldSlug) {
        if (!fieldSlug) {
            throw new IllegalArgumentException("Field slug cannot be empty.")
        }
        String token = getAccessToken()
        String endpoint = "${config.getNormalizedEndpoint()}/api/v1/secrets/${secretId}/fields/${URLEncoder.encode(fieldSlug, 'UTF-8')}"
        Map<String, String> headers = [
            'Authorization': "Bearer ${token}",
            'Accept': '*/*'
        ]

        HttpResponse response = executeHttp('GET', endpoint, headers, null)
        if (response.statusCode == 404) {
            throw new NoSuchElementException("Field [${fieldSlug}] was not found on secret ID [${secretId}].")
        }
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new RuntimeException("Failed to download field [${fieldSlug}] from secret [${secretId}]. " +
                    "HTTP ${response.statusCode}: ${response.body}")
        }
        return response.bytes ?: (response.body ? response.body.getBytes('UTF-8') : new byte[0])
    }

    /**
     * Downloads a secret field or file attachment directly to a local target file.
     */
    void downloadSecretFieldToFile(int secretId, String fieldSlug, File targetFile) {
        byte[] bytes = downloadSecretFieldBytes(secretId, fieldSlug)
        targetFile.parentFile?.mkdirs()
        targetFile.bytes = bytes
    }

    /**
     * Fetches a secret by path or search text.
     */
    Map<String, Object> getSecretByPath(String path, boolean fetchFileAttachments = true) {
        if (!path) {
            throw new IllegalArgumentException("Secret path cannot be empty.")
        }
        String token = getAccessToken()

        // 1. Try path lookup endpoint if supported
        String lookupUrl = "${config.getNormalizedEndpoint()}/api/v1/secrets/lookup?path=${URLEncoder.encode(path, 'UTF-8')}"
        Map<String, String> headers = [
            'Authorization': "Bearer ${token}",
            'Accept': 'application/json'
        ]

        HttpResponse response = executeHttp('GET', lookupUrl, headers, null)
        if (response.statusCode >= 200 && response.statusCode < 300) {
            def lookupJson = new JsonSlurper().parseText(response.body)
            if (lookupJson.id) {
                return getSecret(lookupJson.id as int, fetchFileAttachments)
            }
        }

        // 2. Fallback to searching by exact or partial name
        String searchUrl = "${config.getNormalizedEndpoint()}/api/v1/secrets?filter.searchText=${URLEncoder.encode(path, 'UTF-8')}&take=5"
        HttpResponse searchResponse = executeHttp('GET', searchUrl, headers, null)
        if (searchResponse.statusCode >= 200 && searchResponse.statusCode < 300) {
            def searchJson = new JsonSlurper().parseText(searchResponse.body)
            def records = searchJson.records ?: searchJson.items ?: []
            if (records.isEmpty()) {
                throw new NoSuchElementException("No secret found matching path or query: [${path}]")
            }

            // Find best match by exact name or first result
            def bestMatch = records.find { it.name?.equalsIgnoreCase(path) } ?: records[0]
            if (bestMatch?.id) {
                return getSecret(bestMatch.id as int, fetchFileAttachments)
            }
        }

        throw new NoSuchElementException("Unable to resolve secret by path: [${path}]")
    }

    /**
     * Searches for secrets matching a query string and optional folder ID.
     */
    List<Map<String, Object>> searchSecrets(String searchText, Integer folderId = null, int maxResults = 25) {
        String token = getAccessToken()
        StringBuilder urlBuilder = new StringBuilder("${config.getNormalizedEndpoint()}/api/v1/secrets?")
        urlBuilder.append("filter.searchText=").append(URLEncoder.encode(searchText ?: '', 'UTF-8'))
        urlBuilder.append("&take=").append(maxResults > 0 ? maxResults : 25)
        if (folderId != null) {
            urlBuilder.append("&filter.folderId=").append(folderId)
        }

        Map<String, String> headers = [
            'Authorization': "Bearer ${token}",
            'Accept': 'application/json'
        ]

        HttpResponse response = executeHttp('GET', urlBuilder.toString(), headers, null)
        if (response.statusCode < 200 || response.statusCode >= 300) {
            throw new RuntimeException("Search failed. HTTP ${response.statusCode}: ${response.body}")
        }

        def searchJson = new JsonSlurper().parseText(response.body)
        def records = (searchJson.records ?: searchJson.items ?: []) as List
        return records.collect { rec ->
            [
                id: rec.id,
                name: rec.name,
                folderId: rec.folderId,
                secretTemplateName: rec.secretTemplateName,
                active: rec.active,
                lastAccessed: rec.lastAccessed
            ]
        }
    }

    /**
     * Normalizes Delinea Secret Server response structure into structured fields and key-values.
     * Supports multiple casing conventions (kebab-case, camelCase, snake_case) for easy pipeline access.
     */
    private Map<String, Object> normalizeSecretResponse(Map rawSecret) {
        Map<String, Object> normalized = [
            id: rawSecret.id,
            name: rawSecret.name,
            secretTemplateId: rawSecret.secretTemplateId,
            secretTemplateName: rawSecret.secretTemplateName,
            folderId: rawSecret.folderId,
            active: rawSecret.active,
            fields: [:] as Map<String, String>,
            fieldDetails: [] as List<Map<String, Object>>,
            rawJson: JsonOutput.toJson(rawSecret)
        ]

        def items = rawSecret.items ?: []
        items.each { item ->
            String slug = item.slug ?: item.fieldName ?: item.fieldSlug
            String name = item.fieldName ?: item.slug
            String val = item.itemValue != null ? item.itemValue.toString() : ''
            boolean isFile = item.isFile == true

            boolean isPassword = item.isPassword == true ||
                (!isFile && isSensitiveField(slug, name))

            DelineaClient.registerFieldAliases(normalized.fields, slug, name, val)

            normalized.fieldDetails << [
                slug: slug,
                fieldName: name,
                itemValue: val,
                isPassword: isPassword,
                isFile: isFile,
                filename: item.filename ?: (isFile ? "${slug}.dat" : null),
                fileAttachmentId: item.fileAttachmentId
            ]
        }

        return normalized
    }

    /**
     * Determines if a field name or slug represents sensitive data (passwords, private keys, API keys, tokens, etc.)
     */
    static boolean isSensitiveField(String slug, String name) {
        String s = (slug ?: '').toLowerCase()
        String n = (name ?: '').toLowerCase()
        List<String> sensitiveTerms = [
            'password', 'secret', 'key', 'token', 'passphrase', 'cert',
            'auth', 'credential', 'pin', 'private', 'apikey', 'api_key'
        ]
        return sensitiveTerms.any { term -> s.contains(term) || n.contains(term) }
    }

    /**
     * Registers field slug aliases (original, lowercase, camelCase, snake_case, kebab-case) in the map.
     */
    protected static void registerFieldAliases(Map<String, String> fieldsMap, String slug, String name, String val) {
        if (slug) {
            fieldsMap[slug] = val
            fieldsMap[slug.toLowerCase()] = val
            fieldsMap[toCamelCase(slug)] = val
            fieldsMap[toSnakeCase(slug)] = val
            fieldsMap[toKebabCase(slug)] = val
            fieldsMap[slug.replaceAll(/[-_]/, '').toLowerCase()] = val
        }
        if (name && name != slug) {
            fieldsMap[name] = val
            fieldsMap[name.toLowerCase()] = val
            fieldsMap[toCamelCase(name)] = val
            fieldsMap[toSnakeCase(name)] = val
            fieldsMap[toKebabCase(name)] = val
            fieldsMap[name.replaceAll(/[\s\-_]/, '').toLowerCase()] = val
        }
    }

    protected static String toCamelCase(String text) {
        if (!text) return ''
        String[] parts = text.split(/[\s\-_]+/)
        if (parts.length == 0) return ''
        StringBuilder sb = new StringBuilder(parts[0].toLowerCase())
        for (int i = 1; i < parts.length; i++) {
            if (parts[i].length() > 0) {
                sb.append(parts[i].substring(0, 1).toUpperCase())
                sb.append(parts[i].substring(1).toLowerCase())
            }
        }
        return sb.toString()
    }

    protected static String toSnakeCase(String text) {
        if (!text) return ''
        return text.replaceAll(/([a-z])([A-Z])/, '$1_$2')
                   .replaceAll(/[\s\-]+/, '_')
                   .toLowerCase()
    }

    protected static String toKebabCase(String text) {
        if (!text) return ''
        return text.replaceAll(/([a-z])([A-Z])/, '$1-$2')
                   .replaceAll(/[\s_]+/, '-')
                   .toLowerCase()
    }

    protected static boolean isLikelyText(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return true
        int checkLen = Math.min(bytes.length, 1024)
        for (int i = 0; i < checkLen; i++) {
            if (bytes[i] == 0) return false
        }
        return true
    }

    /**
     * Executes HTTP/HTTPS requests with optional SSL bypass and proxy.
     */
    protected HttpResponse executeHttp(String method, String urlStr, Map<String, String> headers, String body) {
        URL url = new URL(urlStr)
        HttpURLConnection conn

        if (config.httpProxy) {
            URI proxyUri = new URI(config.httpProxy)
            Proxy.Type type = Proxy.Type.HTTP
            InetSocketAddress sa = new InetSocketAddress(proxyUri.host, proxyUri.port > 0 ? proxyUri.port : 8080)
            conn = (HttpURLConnection) url.openConnection(new Proxy(type, sa))
        } else {
            conn = (HttpURLConnection) url.openConnection()
        }

        if (conn instanceof HttpsURLConnection && !config.sslVerify) {
            setupInsecureSsl(conn as HttpsURLConnection)
        }

        conn.requestMethod = method
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.useCaches = false

        headers?.each { k, v ->
            conn.setRequestProperty(k, v)
        }

        if (body != null) {
            conn.doOutput = true
            conn.outputStream.withWriter('UTF-8') { writer ->
                writer.write(body)
            }
        }

        int statusCode = conn.responseCode
        byte[] responseBytes = null
        String responseBody = ''
        InputStream is = null
        try {
            is = (statusCode >= 200 && statusCode < 400) ? conn.inputStream : conn.errorStream
            if (is != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream()
                byte[] buffer = new byte[8192]
                int readLen
                while ((readLen = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, readLen)
                }
                responseBytes = baos.toByteArray()
                responseBody = new String(responseBytes, 'UTF-8')
            }
        } finally {
            is?.close()
            conn.disconnect()
        }

        return new HttpResponse(
            statusCode: statusCode,
            body: responseBody,
            bytes: responseBytes,
            headerFields: conn.headerFields
        )
    }

    private static void setupInsecureSsl(HttpsURLConnection httpsConn) {
        TrustManager[] trustAllCerts = [
            new X509TrustManager() {
                X509Certificate[] getAcceptedIssuers() { return null }
                void checkClientTrusted(X509Certificate[] certs, String authType) {}
                void checkServerTrusted(X509Certificate[] certs, String authType) {}
            }
        ] as TrustManager[]

        SSLContext sc = SSLContext.getInstance('TLS')
        sc.init(null, trustAllCerts, new SecureRandom())
        httpsConn.setSSLSocketFactory(sc.socketFactory)
        httpsConn.setHostnameVerifier({ hostname, session -> true })
    }

    static class HttpResponse {
        int statusCode
        String body
        byte[] bytes
        Map<String, List<String>> headerFields

        String getHeader(String name) {
            if (!headerFields || !name) return null
            def entry = headerFields.find { it.key?.equalsIgnoreCase(name) }
            return entry?.value ? entry.value[0] : null
        }
    }
}
