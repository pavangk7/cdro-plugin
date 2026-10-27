package com.cloudbees.flowpdf.components.delinea

import spock.lang.Specification

class DelineaConfigSpec extends Specification {

    def "should correctly normalize endpoint URLs"() {
        expect:
        new DelineaConfig(endpoint: input).getNormalizedEndpoint() == expected

        where:
        input                                             | expected
        'https://tenant.secretservercloud.com/'           | 'https://tenant.secretservercloud.com'
        'https://secrets.internal.corp/SecretServer///'   | 'https://secrets.internal.corp/SecretServer'
        'http://secrets.internal.corp:8080/SecretServer'  | 'http://secrets.internal.corp:8080/SecretServer'
        'tenant.secretservercloud.com'                    | 'https://tenant.secretservercloud.com'
    }

    def "should throw exception when endpoint is missing"() {
        when:
        new DelineaConfig(endpoint: '').getNormalizedEndpoint()

        then:
        thrown(IllegalArgumentException)
    }

    def "should populate config from property map"() {
        given:
        Map<String, Object> props = [
            endpoint: 'https://mytenant.secretservercloud.com',
            authType: 'oauth2_client_credentials',
            userName: 'my-client-id',
            password: 'my-client-secret',
            domain: 'CORP',
            sslVerify: 'false',
            httpProxy: 'http://proxy.corp:8080',
            debugLevel: '2'
        ]

        when:
        DelineaConfig config = DelineaConfig.fromMap('prod-delinea', props)

        then:
        config.configName == 'prod-delinea'
        config.endpoint == 'https://mytenant.secretservercloud.com'
        config.authType == 'oauth2_client_credentials'
        config.username == 'my-client-id'
        config.password == 'my-client-secret'
        config.domain == 'CORP'
        config.sslVerify == false
        config.httpProxy == 'http://proxy.corp:8080'
        config.debugLevel == 2
    }
}
