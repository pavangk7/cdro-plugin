procedure 'CreateConfiguration', description: 'Creates and tests a Delinea Secret Server configuration in CDRO.', {

    formalParameter 'config',
        type: 'entry',
        required: true,
        label: 'Configuration Name',
        description: 'Unique name for this Delinea Secret Server configuration.'

    formalParameter 'desc',
        type: 'entry',
        required: false,
        label: 'Description',
        description: 'Optional description of this configuration.'

    formalParameter 'endpoint',
        type: 'entry',
        required: true,
        label: 'Secret Server URL',
        description: 'Base URL of Delinea Secret Server (e.g. https://mytenant.secretservercloud.com).'

    formalParameter 'tokenUrl',
        type: 'entry',
        required: false,
        label: 'OAuth2 Token URL',
        description: 'Optional custom OAuth2 token URL. If omitted, defaults to <endpoint>/oauth2/token.'

    formalParameter 'authType',
        type: 'select',
        required: true,
        defaultValue: 'oauth2_password',
        label: 'Authentication Method',
        description: 'OAuth2 authentication method.'

    formalParameter 'credential',
        type: 'credential',
        required: true,
        label: 'Credential',
        description: 'User credentials or OAuth2 client credentials.'

    formalParameter 'domain',
        type: 'entry',
        required: false,
        label: 'Domain',
        description: 'Active Directory / Local domain (for OAuth2 password grant).'

    formalParameter 'sslVerify',
        type: 'checkbox',
        required: false,
        defaultValue: 'true',
        label: 'Verify SSL',
        description: 'Whether to verify SSL/TLS certificates.'

    formalParameter 'httpProxy',
        type: 'entry',
        required: false,
        label: 'HTTP Proxy',
        description: 'Outbound HTTP proxy URL.'

    formalParameter 'checkConnection',
        type: 'checkbox',
        required: false,
        defaultValue: 'true',
        label: 'Check Connection',
        description: 'Validate connectivity to Delinea Secret Server before finalizing configuration.'

    step 'createConfiguration',
        description: 'Save and validate Delinea configuration',
        shell: 'ec-groovy',
        command: '''
import com.cloudbees.flowpdf.components.delinea.DelineaConfig
import com.cloudbees.flowpdf.components.procedures.CheckConnection

def ef = new com.electriccloud.client.groovy.ElectricFlowClient()

String configName = '$[/myJobStep/config]'.trim()
String endpoint = '$[/myJobStep/endpoint]'.trim()
String tokenUrl = '$[/myJobStep/tokenUrl]'.trim()
String authType = '$[/myJobStep/authType]'.trim() ?: 'oauth2_password'
String domain = '$[/myJobStep/domain]'.trim()
String sslVerify = '$[/myJobStep/sslVerify]'.trim()
String httpProxy = '$[/myJobStep/httpProxy]'.trim()
String desc = '$[/myJobStep/desc]'.trim()
boolean doCheck = Boolean.parseBoolean('$[/myJobStep/checkConnection]'.trim() ?: 'true')

// Extract credential
def cred = ef.getFullCredential(
    credentialName: 'credential',
    jobStepId: '$[/myJobStep/jobStepId]'
)
String userName = cred?.credential?.userName
String password = cred?.credential?.password

// Get plugin project
String pluginProjectName = '$[/myProject/projectName]'
String cfgRoot = "/plugins/@PLUGIN_NAME@/project/ec_plugin_cfgs/${configName}"

// Build config object for healthcheck
DelineaConfig delineaCfg = DelineaConfig.fromMap(configName, [
    endpoint: endpoint,
    tokenUrl: tokenUrl,
    authType: authType,
    userName: userName,
    password: password,
    domain: domain,
    sslVerify: sslVerify,
    httpProxy: httpProxy
])

if (doCheck) {
    println "Testing connection to Delinea Secret Server..."
    new CheckConnection(null, ef).execute([:], delineaCfg)
}

println "Persisting configuration to [${cfgRoot}]..."
ef.setProperty(propertyName: "${cfgRoot}/endpoint", value: endpoint)
if (tokenUrl) {
    ef.setProperty(propertyName: "${cfgRoot}/tokenUrl", value: tokenUrl)
}
ef.setProperty(propertyName: "${cfgRoot}/authType", value: authType)
ef.setProperty(propertyName: "${cfgRoot}/domain", value: domain)
ef.setProperty(propertyName: "${cfgRoot}/sslVerify", value: sslVerify)
ef.setProperty(propertyName: "${cfgRoot}/httpProxy", value: httpProxy)
ef.setProperty(propertyName: "${cfgRoot}/desc", value: desc)

// Attach credential
String attachedCredName = "${configName}-credential"
try {
    ef.deleteCredential(projectName: pluginProjectName, credentialName: attachedCredName)
} catch (Exception ignored) {}

ef.createCredential(
    projectName: pluginProjectName,
    credentialName: attachedCredName,
    userName: userName,
    password: password
)
ef.attachCredential(
    projectName: pluginProjectName,
    credentialName: attachedCredName,
    propertySheet: cfgRoot
)

ef.setProperty(propertyName: '/myJobStep/summary', value: "Configuration '${configName}' created successfully.")
println "Configuration '${configName}' saved successfully."
'''
}
