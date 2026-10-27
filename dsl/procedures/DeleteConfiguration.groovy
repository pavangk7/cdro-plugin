procedure 'DeleteConfiguration', description: 'Deletes a Delinea Secret Server configuration from CDRO.', {

    formalParameter 'config',
        type: 'entry',
        required: true,
        label: 'Configuration Name',
        description: 'Name of the Delinea Secret Server configuration to delete.'

    step 'deleteConfiguration',
        description: 'Remove Delinea configuration property sheet and attached credentials',
        shell: 'ec-groovy',
        command: '''
def ef = new com.electriccloud.client.groovy.ElectricFlowClient()

String configName = '$[/myJobStep/config]'.trim()
String pluginProjectName = '$[/myProject/projectName]'
String cfgRoot = "/plugins/@PLUGIN_NAME@/project/ec_plugin_cfgs/${configName}"
String attachedCredName = "${configName}-credential"

println "Removing configuration property sheet: ${cfgRoot}..."
try {
    ef.deleteProperty(propertyName: cfgRoot)
} catch (Exception e) {
    println "[WARN] Could not delete property sheet ${cfgRoot}: ${e.message}"
}

println "Removing configuration credential: ${attachedCredName}..."
try {
    ef.deleteCredential(projectName: pluginProjectName, credentialName: attachedCredName)
} catch (Exception e) {
    println "[WARN] Could not delete credential ${attachedCredName}: ${e.message}"
}

ef.setProperty(propertyName: '/myJobStep/summary', value: "Configuration '${configName}' deleted successfully.")
println "Configuration '${configName}' removed."
'''
}
