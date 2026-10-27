package com.cloudbees.flowpdf.components.procedures

import spock.lang.Specification

import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions

class RestorePemKeySpec extends Specification {

    def "should read .pem key from pipeline property and save to disk with 0600 permissions"() {
        given:
        File tempDir = File.createTempDir("restore-pem-test", "")
        File destKeyFile = new File(tempDir, "bastion.pem")

        String mockPemKey = "-----BEGIN OPENSSH PRIVATE KEY-----\nMOCK_PEM_PRIVATE_KEY_BODY\n-----END OPENSSH PRIVATE KEY-----"

        Map<String, String> propertiesStore = [
            '/myPipelineRuntime/delineaSecret/pem': mockPemKey
        ]
        Map<String, String> exportedProps = [:]

        def mockEfClient = [
            getProperty: { Map args ->
                String val = propertiesStore[args.propertyName]
                return val ? [property: [value: val]] : null
            },
            setProperty: { Map args ->
                exportedProps[args.propertyName] = args.value
            }
        ]

        RestorePemKey procedure = new RestorePemKey(null, mockEfClient) {
            @Override
            protected void setStepSummary(String summary) {}
        }

        Map params = [
            pemKeyProperty: '/myPipelineRuntime/delineaSecret/pem',
            destinationFilePath: destKeyFile.absolutePath,
            filePermissions: '0600',
            overwrite: 'true',
            resultPropertySheet: '/myJob/pemFile'
        ]

        when:
        Map result = procedure.execute(params, null)

        then:
        result.success == true
        destKeyFile.exists()
        destKeyFile.text == mockPemKey

        if (!System.getProperty("os.name").toLowerCase().contains("win")) {
            String perms = PosixFilePermissions.toString(Files.getPosixFilePermissions(destKeyFile.toPath()))
            perms == 'rw-------'
        }

        exportedProps['/myJob/pemFile/filePath'] == destKeyFile.absolutePath
        exportedProps['/myJob/pemFile/fileName'] == 'bastion.pem'
        exportedProps['/myJob/pemFile/permissions'] == '0600'

        cleanup:
        destKeyFile.delete()
        tempDir.delete()
    }

    def "should fallback to alternative property locations if primary property is empty"() {
        given:
        File tempDir = File.createTempDir("restore-fallback", "")
        File destKeyFile = new File(tempDir, "fallback.pem")

        String mockPemKey = "-----BEGIN RSA PRIVATE KEY-----\nFALLBACK_KEY\n-----END RSA PRIVATE KEY-----"

        Map<String, String> propertiesStore = [
            '/myPipelineRuntime/delineaSecret/private-key': mockPemKey
        ]

        def mockEfClient = [
            getProperty: { Map args ->
                String val = propertiesStore[args.propertyName]
                return val ? [property: [value: val]] : null
            },
            setProperty: { Map args -> }
        ]

        RestorePemKey procedure = new RestorePemKey(null, mockEfClient) {
            @Override
            protected void setStepSummary(String summary) {}
        }

        Map params = [
            pemKeyProperty: '/myPipelineRuntime/delineaSecret/pem',
            destinationFilePath: destKeyFile.absolutePath,
            filePermissions: '0600',
            overwrite: 'true'
        ]

        when:
        Map result = procedure.execute(params, null)

        then:
        result.success == true
        destKeyFile.exists()
        destKeyFile.text == mockPemKey

        cleanup:
        destKeyFile.delete()
        tempDir.delete()
    }

    def "should throw IllegalStateException if no .pem content is found"() {
        given:
        def mockEfClient = [
            getProperty: { Map args -> null },
            setProperty: { Map args -> }
        ]

        RestorePemKey procedure = new RestorePemKey(null, mockEfClient)

        Map params = [
            pemKeyProperty: '/myPipelineRuntime/delineaSecret/pem',
            destinationFilePath: '/tmp/test.pem'
        ]

        when:
        procedure.execute(params, null)

        then:
        thrown(IllegalStateException)
    }
}
