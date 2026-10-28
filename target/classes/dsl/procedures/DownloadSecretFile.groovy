procedure 'DownloadSecretFile', description: 'Downloads a file attachment, certificate, or private key from Delinea Secret Server directly to the local agent filesystem.', {

    formalParameter 'config',
        type: 'entry',
        required: true,
        label: 'Configuration Name',
        description: 'Name of the Delinea Secret Server configuration to use.'

    formalParameter 'secretId',
        type: 'entry',
        required: false,
        label: 'Secret ID',
        description: 'Numeric ID of the secret in Delinea Secret Server (e.g. 2048 for SSH key, 1042 for certificates/files).'

    formalParameter 'secretPath',
        type: 'entry',
        required: false,
        label: 'Secret Path',
        description: 'Path or search expression for secret lookup if Secret ID is omitted.'

    formalParameter 'fieldSlug',
        type: 'entry',
        required: false,
        label: 'Field Slug',
        description: 'Slug of the file attachment or private key field (e.g. private-key, certificate-file). Leave blank to auto-detect.'

    formalParameter 'destinationPath',
        type: 'entry',
        required: true,
        label: 'Destination Path',
        description: 'Local file path or directory on the CDRO agent where the file will be saved (e.g., ~/.ssh/id_rsa or /tmp/certs).'

    formalParameter 'filePermissions',
        type: 'entry',
        required: false,
        defaultValue: '0600',
        label: 'File Permissions',
        description: 'POSIX file permissions (e.g., 0600 for SSH keys, 0644 for general files).'

    formalParameter 'overwrite',
        type: 'checkbox',
        required: false,
        defaultValue: 'true',
        label: 'Overwrite Existing File',
        description: 'Whether to overwrite destination file if it already exists.'

    formalParameter 'resultPropertySheet',
        type: 'entry',
        required: false,
        defaultValue: '/myJob/delineaDownloadedFile',
        label: 'Output Property Sheet',
        description: 'CDRO property sheet where file download metadata (filePath, fileName, fileSize) will be saved.'

    step 'downloadSecretFile',
        description: 'Download file from Delinea Secret Server',
        shell: 'ec-groovy',
        command: '''
import com.cloudbees.flowpdf.runner.ProcedureRunner

Map params = [
    config: '$[/myJobStep/config]',
    secretId: '$[/myJobStep/secretId]',
    secretPath: '$[/myJobStep/secretPath]',
    fieldSlug: '$[/myJobStep/fieldSlug]',
    destinationPath: '$[/myJobStep/destinationPath]',
    filePermissions: '$[/myJobStep/filePermissions]',
    overwrite: '$[/myJobStep/overwrite]',
    resultPropertySheet: '$[/myJobStep/resultPropertySheet]'
]

new ProcedureRunner().run('DownloadSecretFile', params)
'''
}
