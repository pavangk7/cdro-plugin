/**
 * Promotion script for EC-DelineaSecretServer.
 * Registers plugin procedures, metadata, and UI forms in CloudBees CD/RO.
 */
def pluginName = '@PLUGIN_NAME@'
def pluginKey = '@PLUGIN_KEY@'

project pluginName, {
    description = 'CloudBees CD/RO integration with Delinea Secret Server'

    property 'ec_visibility', value: 'all'
    property 'ec_plugin_category', value: 'Resource Management'
    property 'ec_plugin_icon', value: 'images/icon-delinea.png'

    // Load procedures
    load('dsl/procedures/CreateConfiguration.groovy')
    load('dsl/procedures/DeleteConfiguration.groovy')
    load('dsl/procedures/CheckConnection.groovy')
    load('dsl/procedures/GetSecret.groovy')
    load('dsl/procedures/DownloadSecretFile.groovy')
    load('dsl/procedures/RestorePemKey.groovy')
    load('dsl/procedures/SearchSecrets.groovy')
}
