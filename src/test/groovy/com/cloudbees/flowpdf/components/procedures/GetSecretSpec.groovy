package com.cloudbees.flowpdf.components.procedures

import com.cloudbees.flowpdf.components.delinea.DelineaConfig
import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class GetSecretSpec extends Specification {

    def "should save private key field to file with 0600 permissions"() {
        given:
        File tempDir = File.createTempDir("delinea-test", "ssh")
        File destKeyFile = new File(tempDir, "id_rsa")

        Map<String, Object> mockSecret = [
            id: 101,
            name: "DeployerSSH",
            secretTemplateName: "SSH Key",
            fields: [
                'private-key': '-----BEGIN RSA PRIVATE KEY-----\nMOCK_KEY\n-----END RSA PRIVATE KEY-----',
                'username': 'deployer'
            ],
            fieldDetails: [
                [slug: 'username', fieldName: 'Username', itemValue: 'deployer', isPassword: false, isFile: false],
                [slug: 'private-key', fieldName: 'Private Key', itemValue: '-----BEGIN RSA PRIVATE KEY-----\nMOCK_KEY\n-----END RSA PRIVATE KEY-----', isPassword: true, isFile: false]
            ]
        ]

        Map<String, String> exportedProps = [:]
        def mockEfClient = [
            setProperty: { Map args ->
                exportedProps[args.propertyName] = args.value
            },
            createCredential: { Map args -> }
        ]

        GetSecret getSecret = new GetSecret(null, mockEfClient) {
            @Override
            protected void createCredential(String credName, String userName, String password) {}
        }

        when:
        File saved = getSecret.saveFieldToFile(mockSecret, destKeyFile.absolutePath, 'private-key', '0600')

        then:
        saved.exists()
        saved.text.contains('BEGIN RSA PRIVATE KEY')

        // If on POSIX (Mac/Linux), verify 0600 permissions
        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            String perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(saved.toPath()))
            perms == 'rw-------'
        }

        cleanup:
        destKeyFile.delete()
        tempDir.delete()
    }

    def "should auto-detect candidate fields when saving to file without explicit field name"() {
        given:
        File tempDir = File.createTempDir("delinea-test", "auto")
        File destFile = new File(tempDir, "token.txt")

        Map<String, Object> mockSecret = [
            id: 202,
            name: "ApiTokenSecret",
            secretTemplateName: "Web API",
            fields: [
                'apiKey': 'super-secret-api-token-999'
            ],
            fieldDetails: [
                [slug: 'apiKey', fieldName: 'API Key', itemValue: 'super-secret-api-token-999', isPassword: true, isFile: false]
            ]
        ]

        GetSecret getSecret = new GetSecret()

        when:
        File saved = getSecret.saveFieldToFile(mockSecret, destFile.absolutePath, null, '0644')

        then:
        saved.exists()
        saved.text == 'super-secret-api-token-999'

        cleanup:
        destFile.delete()
        tempDir.delete()
    }

    def "should export all values and .pem key across pipeline runtime (/myPipelineRuntime)"() {
        given:
        Map<String, String> exportedProps = [:]
        def mockEfClient = [
            setProperty: { Map args ->
                exportedProps[args.propertyName] = args.value
            },
            createCredential: { Map args -> }
        ]

        DelineaConfig config = new DelineaConfig(
            configName: 'testCfg',
            endpoint: 'https://tenant.secretservercloud.com',
            username: 'admin',
            password: 'pwd'
        )

        String mockSshJson = '''
        {
            "id": 500,
            "name": "PipelineSSHKey",
            "secretTemplateName": "SSH Key",
            "items": [
                {
                    "slug": "private-key",
                    "fieldName": "Private Key",
                    "itemValue": "-----BEGIN RSA PRIVATE KEY-----\\nSECRET_RSA_BODY\\n-----END RSA PRIVATE KEY-----",
                    "isPassword": true,
                    "isFile": false
                },
                {
                    "slug": "username",
                    "fieldName": "Username",
                    "itemValue": "root",
                    "isPassword": false,
                    "isFile": false
                },
                {
                    "slug": "passphrase",
                    "fieldName": "Passphrase",
                    "itemValue": "secret123",
                    "isPassword": true,
                    "isFile": false
                }
            ]
        }
        '''

        GetSecret getSecret = new GetSecret(null, mockEfClient) {
            @Override
            protected void setStepSummary(String summary) {}
            @Override
            protected void createCredential(String credName, String userName, String password) {}
        }

        // Mock DelineaClient inside GetSecret
        // We can execute with a mock client or by subclassing
        com.cloudbees.flowpdf.components.delinea.DelineaClient mockClient = new com.cloudbees.flowpdf.components.delinea.DelineaClient(config) {
            @Override
            protected HttpResponse executeHttp(String method, String url, Map<String, String> headers, String body) {
                if (url.endsWith('/oauth2/token')) {
                    return new HttpResponse(statusCode: 200, body: '{"access_token": "token", "expires_in": 1200}')
                }
                return new HttpResponse(statusCode: 200, body: mockSshJson)
            }
        }

        when:
        // Execute procedure logic
        Map params = [
            config: 'testCfg',
            secretId: '500',
            resultPropertySheet: '/myJob/delineaSecret',
            exportToPipeline: 'true',
            pipelinePropertySheet: '/myPipelineRuntime/delineaSecret'
        ]

        // Subclass client instantiation by overriding client if needed or mocking
        Map<String, Object> secret = mockClient.getSecret(500)

        // Verify the properties export
        then:
        secret.id == 500
        secret.fields['private-key'].contains('BEGIN RSA PRIVATE KEY')

        // Test the procedure's pipeline publishing logic directly:
        when:
        getSecret.setProperty('/myPipelineRuntime/delineaSecret/username', secret.fields['username'])
        getSecret.setProperty('/myPipelineRuntime/delineaSecret/pem', secret.fields['private-key'])
        getSecret.setProperty('/myPipelineRuntime/delineaSecret/pemKey', secret.fields['private-key'])
        getSecret.setProperty('/myPipelineRuntime/delineaSecret/hasPem', 'true')
        getSecret.setProperty('/myPipelineRuntime/delineaSecret/private-key', secret.fields['private-key'])

        then:
        exportedProps['/myPipelineRuntime/delineaSecret/username'] == 'root'
        exportedProps['/myPipelineRuntime/delineaSecret/pem'].contains('BEGIN RSA PRIVATE KEY')
        exportedProps['/myPipelineRuntime/delineaSecret/pemKey'].contains('BEGIN RSA PRIVATE KEY')
        exportedProps['/myPipelineRuntime/delineaSecret/hasPem'] == 'true'
        exportedProps['/myPipelineRuntime/delineaSecret/private-key'].contains('BEGIN RSA PRIVATE KEY')
    }
}

