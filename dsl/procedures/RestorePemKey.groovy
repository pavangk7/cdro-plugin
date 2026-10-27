procedure 'RestorePemKey', description: 'Restores a .pem private key from pipeline runtime properties and saves it to a file with 0600 permissions on the current agent.', {

    formalParameter 'pemKeyProperty',
        type: 'entry',
        required: false,
        defaultValue: '/myPipelineRuntime/delineaSecret/pem',
        label: '.PEM Key Property Path',
        description: 'Property path containing the .pem key content (default: /myPipelineRuntime/delineaSecret/pem).'

    formalParameter 'destinationFilePath',
        type: 'entry',
        required: true,
        label: 'Destination File Path',
        description: 'File path on the agent where the .pem key will be written (e.g. ~/.ssh/id_rsa or /tmp/delinea.pem).'

    formalParameter 'filePermissions',
        type: 'entry',
        required: false,
        defaultValue: '0600',
        label: 'File Permissions',
        description: 'POSIX permissions for the written file (default: 0600 for SSH private keys).'

    formalParameter 'overwrite',
        type: 'checkbox',
        required: false,
        defaultValue: 'true',
        label: 'Overwrite Existing File',
        description: 'Whether to overwrite destination file if it already exists.'

    formalParameter 'resultPropertySheet',
        type: 'entry',
        required: false,
        defaultValue: '/myJob/pemFile',
        label: 'Output Property Sheet',
        description: 'CDRO property sheet where output metadata (filePath, fileSize, permissions) will be saved.'

    step 'restorePemKey',
        description: 'Restore .pem key to agent filesystem',
        shell: 'ec-groovy',
        command: '''
import com.cloudbees.flowpdf.runner.ProcedureRunner

Map params = [
    pemKeyProperty: '$[/myJobStep/pemKeyProperty]',
    destinationFilePath: '$[/myJobStep/destinationFilePath]',
    filePermissions: '$[/myJobStep/filePermissions]',
    overwrite: '$[/myJobStep/overwrite]',
    resultPropertySheet: '$[/myJobStep/resultPropertySheet]'
]

new ProcedureRunner().run('RestorePemKey', params)
'''
}
