procedure 'CheckConnection', description: 'Tests connectivity and authentication with Delinea Secret Server.', {

    formalParameter 'config',
        type: 'entry',
        required: true,
        label: 'Configuration Name',
        description: 'Name of the Delinea Secret Server configuration to test.'

    step 'checkConnection',
        description: 'Verify connection and authentication',
        shell: 'ec-groovy',
        command: '''
import com.cloudbees.flowpdf.runner.ProcedureRunner

Map params = [
    config: '$[/myJobStep/config]'
]

new ProcedureRunner().run('CheckConnection', params)
'''
}
