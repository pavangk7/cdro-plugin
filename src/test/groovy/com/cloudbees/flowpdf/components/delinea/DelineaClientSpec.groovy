package com.cloudbees.flowpdf.components.delinea

import spock.lang.Specification

class DelineaClientSpec extends Specification {

    def "should authenticate and parse OAuth2 access token"() {
        given:
        DelineaConfig config = new DelineaConfig(
            configName: 'testCfg',
            endpoint: 'https://tenant.secretservercloud.com',
            authType: 'oauth2_password',
            username: 'admin',
            password: 'secretPassword'
        )

        String mockTokenJson = '{"access_token": "mock-jwt-token-12345", "token_type": "Bearer", "expires_in": 1200}'

        DelineaClient client = new DelineaClient(config) {
            @Override
            protected HttpResponse executeHttp(String method, String url, Map<String, String> headers, String body) {
                if (url.endsWith('/oauth2/token')) {
                    assert method == 'POST'
                    assert body.contains('grant_type=password')
                    assert body.contains('username=admin')
                    return new HttpResponse(statusCode: 200, body: mockTokenJson)
                }
                throw new UnsupportedOperationException("Unexpected URL: " + url)
            }
        }

        when:
        String token = client.getAccessToken()

        then:
        token == 'mock-jwt-token-12345'
    }

    def "should retrieve secret and normalize fields properly"() {
        given:
        DelineaConfig config = new DelineaConfig(
            configName: 'testCfg',
            endpoint: 'https://tenant.secretservercloud.com',
            authType: 'oauth2_client_credentials',
            username: 'client-id',
            password: 'client-secret'
        )

        String mockSecretJson = '''
        {
            "id": 1042,
            "name": "Prod-Postgres-Credentials",
            "secretTemplateId": 6001,
            "secretTemplateName": "Database Account",
            "folderId": 12,
            "active": true,
            "items": [
                {
                    "slug": "username",
                    "fieldName": "Username",
                    "itemValue": "db_deployer",
                    "isPassword": false,
                    "isFile": false
                },
                {
                    "slug": "password",
                    "fieldName": "Password",
                    "itemValue": "P@ssw0rd123!",
                    "isPassword": true,
                    "isFile": false
                },
                {
                    "slug": "database",
                    "fieldName": "Database",
                    "itemValue": "production_app",
                    "isPassword": false,
                    "isFile": false
                }
            ]
        }
        '''

        DelineaClient client = new DelineaClient(config) {
            @Override
            protected HttpResponse executeHttp(String method, String url, Map<String, String> headers, String body) {
                if (url.endsWith('/oauth2/token')) {
                    return new HttpResponse(statusCode: 200, body: '{"access_token": "mock-token", "expires_in": 1200}')
                }
                if (url.endsWith('/api/v1/secrets/1042')) {
                    assert headers['Authorization'] == 'Bearer mock-token'
                    return new HttpResponse(statusCode: 200, body: mockSecretJson)
                }
                throw new UnsupportedOperationException("Unexpected URL: " + url)
            }
        }

        when:
        Map<String, Object> secret = client.getSecret(1042)

        then:
        secret.id == 1042
        secret.name == 'Prod-Postgres-Credentials'
        secret.secretTemplateName == 'Database Account'
        secret.fields['username'] == 'db_deployer'
        secret.fields['password'] == 'P@ssw0rd123!'
        secret.fields['database'] == 'production_app'
        secret.fieldDetails.size() == 3

        def passField = secret.fieldDetails.find { it.slug == 'password' }
        passField.isPassword == true
        passField.itemValue == 'P@ssw0rd123!'
    }

    def "should fetch SSH Key / Private Key secret and alias all slug formats"() {
        given:
        DelineaConfig config = new DelineaConfig(
            configName: 'sshCfg',
            endpoint: 'https://tenant.secretservercloud.com',
            username: 'admin',
            password: 'pwd'
        )

        String mockSshJson = '''
        {
            "id": 2048,
            "name": "Production-Bastion-SSH",
            "secretTemplateId": 60,
            "secretTemplateName": "SSH Key",
            "items": [
                {
                    "slug": "private-key",
                    "fieldName": "Private Key",
                    "itemValue": "-----BEGIN OPENSSH PRIVATE KEY-----\\nmockKeyContent\\n-----END OPENSSH PRIVATE KEY-----",
                    "isPassword": true,
                    "isFile": false
                },
                {
                    "slug": "public-key",
                    "fieldName": "Public Key",
                    "itemValue": "ssh-rsa AAAAB3NzaC1yc2E...",
                    "isPassword": false,
                    "isFile": false
                },
                {
                    "slug": "passphrase",
                    "fieldName": "Passphrase",
                    "itemValue": "bastionSecret123",
                    "isPassword": true,
                    "isFile": false
                },
                {
                    "slug": "username",
                    "fieldName": "Username",
                    "itemValue": "ec2-user",
                    "isPassword": false,
                    "isFile": false
                }
            ]
        }
        '''

        DelineaClient client = new DelineaClient(config) {
            @Override
            protected HttpResponse executeHttp(String method, String url, Map<String, String> headers, String body) {
                if (url.endsWith('/oauth2/token')) {
                    return new HttpResponse(statusCode: 200, body: '{"access_token": "token", "expires_in": 1200}')
                }
                if (url.endsWith('/api/v1/secrets/2048')) {
                    return new HttpResponse(statusCode: 200, body: mockSshJson)
                }
                throw new UnsupportedOperationException("Unexpected URL: " + url)
            }
        }

        when:
        Map<String, Object> secret = client.getSecret(2048)

        then:
        secret.id == 2048
        secret.name == 'Production-Bastion-SSH'
        secret.secretTemplateName == 'SSH Key'
        // Verify all slug variations are accessible
        secret.fields['private-key'].contains('BEGIN OPENSSH PRIVATE KEY')
        secret.fields['privateKey'].contains('BEGIN OPENSSH PRIVATE KEY')
        secret.fields['private_key'].contains('BEGIN OPENSSH PRIVATE KEY')
        secret.fields['privatekey'].contains('BEGIN OPENSSH PRIVATE KEY')
        secret.fields['public-key'] == 'ssh-rsa AAAAB3NzaC1yc2E...'
        secret.fields['publicKey'] == 'ssh-rsa AAAAB3NzaC1yc2E...'
        secret.fields['passphrase'] == 'bastionSecret123'
        secret.fields['username'] == 'ec2-user'

        and: "Sensitive detection should identify private key and passphrase as sensitive"
        DelineaClient.isSensitiveField('private-key', 'Private Key') == true
        DelineaClient.isSensitiveField('passphrase', 'Passphrase') == true
        DelineaClient.isSensitiveField('username', 'Username') == false
    }

    def "should fetch API Key secret and normalize token fields"() {
        given:
        DelineaConfig config = new DelineaConfig(
            configName: 'apiCfg',
            endpoint: 'https://tenant.secretservercloud.com',
            username: 'admin',
            password: 'pwd'
        )

        String mockApiKeyJson = '''
        {
            "id": 3050,
            "name": "Stripe-Payment-Gateway-Key",
            "secretTemplateId": 85,
            "secretTemplateName": "Web API Key",
            "items": [
                {
                    "slug": "api-key",
                    "fieldName": "API Key",
                    "itemValue": "sk_live_51AbcDef123456789",
                    "isPassword": true,
                    "isFile": false
                },
                {
                    "slug": "endpoint-url",
                    "fieldName": "Endpoint URL",
                    "itemValue": "https://api.stripe.com/v1",
                    "isPassword": false,
                    "isFile": false
                }
            ]
        }
        '''

        DelineaClient client = new DelineaClient(config) {
            @Override
            protected HttpResponse executeHttp(String method, String url, Map<String, String> headers, String body) {
                if (url.endsWith('/oauth2/token')) {
                    return new HttpResponse(statusCode: 200, body: '{"access_token": "token", "expires_in": 1200}')
                }
                if (url.endsWith('/api/v1/secrets/3050')) {
                    return new HttpResponse(statusCode: 200, body: mockApiKeyJson)
                }
                throw new UnsupportedOperationException("Unexpected URL: " + url)
            }
        }

        when:
        Map<String, Object> secret = client.getSecret(3050)

        then:
        secret.id == 3050
        secret.fields['api-key'] == 'sk_live_51AbcDef123456789'
        secret.fields['apiKey'] == 'sk_live_51AbcDef123456789'
        secret.fields['api_key'] == 'sk_live_51AbcDef123456789'
        secret.fields['endpointUrl'] == 'https://api.stripe.com/v1'
        DelineaClient.isSensitiveField('api-key', 'API Key') == true
    }

    def "should download file attachment bytes from field endpoint"() {
        given:
        DelineaConfig config = new DelineaConfig(
            configName: 'certCfg',
            endpoint: 'https://tenant.secretservercloud.com',
            username: 'admin',
            password: 'pwd'
        )

        byte[] fakeCertBytes = "MOCK_CERTIFICATE_PFX_CONTENT".getBytes('UTF-8')

        DelineaClient client = new DelineaClient(config) {
            @Override
            protected HttpResponse executeHttp(String method, String url, Map<String, String> headers, String body) {
                if (url.endsWith('/oauth2/token')) {
                    return new HttpResponse(statusCode: 200, body: '{"access_token": "token", "expires_in": 1200}')
                }
                if (url.contains('/api/v1/secrets/4000/fields/certificate-file')) {
                    return new HttpResponse(statusCode: 200, body: "MOCK_CERTIFICATE_PFX_CONTENT", bytes: fakeCertBytes)
                }
                throw new UnsupportedOperationException("Unexpected URL: " + url)
            }
        }

        when:
        byte[] downloaded = client.downloadSecretFieldBytes(4000, 'certificate-file')

        then:
        downloaded == fakeCertBytes
        new String(downloaded, 'UTF-8') == "MOCK_CERTIFICATE_PFX_CONTENT"
    }

    def "should throw NoSuchElementException when secret ID is not found (404)"() {
        given:
        DelineaConfig config = new DelineaConfig(
            configName: 'testCfg',
            endpoint: 'https://tenant.secretservercloud.com',
            username: 'admin',
            password: 'pwd'
        )

        DelineaClient client = new DelineaClient(config) {
            @Override
            protected HttpResponse executeHttp(String method, String url, Map<String, String> headers, String body) {
                if (url.endsWith('/oauth2/token')) {
                    return new HttpResponse(statusCode: 200, body: '{"access_token": "mock-token", "expires_in": 1200}')
                }
                return new HttpResponse(statusCode: 404, body: '{"message": "Secret not found"}')
            }
        }

        when:
        client.getSecret(9999)

        then:
        thrown(NoSuchElementException)
    }

    def "should correctly resolve default and custom tokenUrl and normalize endpoint"() {
        given:
        DelineaConfig cfgDefault = new DelineaConfig(
            endpoint: 'https://tenant.secretservercloud.com/'
        )
        DelineaConfig cfgWithTokenUrlInEndpoint = new DelineaConfig(
            endpoint: 'https://tenant.secretservercloud.com/oauth2/token'
        )
        DelineaConfig cfgCustom = new DelineaConfig(
            endpoint: 'https://tenant.secretservercloud.com',
            tokenUrl: 'https://idp.company.internal/oauth2/token'
        )

        expect:
        cfgDefault.getNormalizedEndpoint() == 'https://tenant.secretservercloud.com'
        cfgDefault.getTokenUrl() == 'https://tenant.secretservercloud.com/oauth2/token'

        cfgWithTokenUrlInEndpoint.getNormalizedEndpoint() == 'https://tenant.secretservercloud.com'
        cfgWithTokenUrlInEndpoint.getTokenUrl() == 'https://tenant.secretservercloud.com/oauth2/token'

        cfgCustom.getNormalizedEndpoint() == 'https://tenant.secretservercloud.com'
        cfgCustom.getTokenUrl() == 'https://idp.company.internal/oauth2/token'
    }
}

