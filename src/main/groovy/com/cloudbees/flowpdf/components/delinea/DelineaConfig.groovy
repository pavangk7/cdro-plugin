package com.cloudbees.flowpdf.components.delinea

import groovy.transform.ToString

/**
 * Data model and configuration resolver for Delinea Secret Server.
 */
@ToString(includeNames = true, excludes = ['password'])
class DelineaConfig {

    String configName
    String endpoint
    String tokenUrl
    String authType = 'oauth2_password'
    String username
    String password
    String domain
    boolean sslVerify = true
    String httpProxy
    int debugLevel = 1

    /**
     * Parses and normalizes endpoint URL.
     * Ensures scheme exists and trailing slash is removed.
     * Strips accidental /oauth2/token or /api/v1 suffixes from base endpoint.
     */
    String getNormalizedEndpoint() {
        if (!endpoint) {
            throw new IllegalArgumentException("Delinea Secret Server endpoint URL is required.")
        }
        String cleanUrl = endpoint.trim().replaceAll('/+$', '')
        if (!cleanUrl.startsWith('http://') && !cleanUrl.startsWith('https://')) {
            cleanUrl = 'https://' + cleanUrl
        }
        // Normalize if the user provided the full oauth2 token URL or api path in endpoint
        cleanUrl = cleanUrl.replaceAll('(?i)/oauth2/token/?$', '')
        cleanUrl = cleanUrl.replaceAll('(?i)/api/v1/?$', '')
        cleanUrl = cleanUrl.replaceAll('/+$', '')
        return cleanUrl
    }

    /**
     * Resolves the OAuth2 token URL.
     * Uses explicit tokenUrl if configured, otherwise computes <normalizedEndpoint>/oauth2/token.
     */
    String getTokenUrl() {
        if (tokenUrl?.trim()) {
            String cleanToken = tokenUrl.trim()
            if (!cleanToken.startsWith('http://') && !cleanToken.startsWith('https://')) {
                cleanToken = 'https://' + cleanToken
            }
            return cleanToken
        }
        return getNormalizedEndpoint() + '/oauth2/token'
    }

    /**
     * Factory method to load configuration from CDRO properties map or commander client.
     */
    static DelineaConfig fromMap(String name, Map<String, Object> props) {
        DelineaConfig config = new DelineaConfig()
        config.configName = name
        config.endpoint = props.endpoint?.toString()?.trim()
        config.tokenUrl = props.tokenUrl?.toString()?.trim()
        config.authType = props.authType?.toString()?.trim() ?: 'oauth2_password'
        config.username = props.userName?.toString() ?: props.username?.toString()
        config.password = props.password?.toString()
        config.domain = props.domain?.toString()?.trim()
        config.httpProxy = props.httpProxy?.toString()?.trim()

        if (props.sslVerify != null) {
            if (props.sslVerify instanceof Boolean) {
                config.sslVerify = (Boolean) props.sslVerify
            } else {
                config.sslVerify = !('false'.equalsIgnoreCase(props.sslVerify.toString()) ||
                                     '0'.equals(props.sslVerify.toString()))
            }
        }

        if (props.debugLevel != null) {
            try {
                config.debugLevel = Integer.parseInt(props.debugLevel.toString())
            } catch (Exception ignored) {
                config.debugLevel = 1
            }
        }

        return config
    }
}
