/**
 * Demotion script for EC-DelineaSecretServer.
 * Executed when the plugin is demoted in CloudBees CD/RO.
 */
def pluginName = '@PLUGIN_NAME@'

// Preserve configuration data by default while disabling the plugin in UI catalog
println "Demoting plugin ${pluginName}..."
