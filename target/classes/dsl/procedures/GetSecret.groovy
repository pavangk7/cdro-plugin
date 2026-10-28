procedure 'GetSecret', description: 'Retrieves secret fields from Delinea Secret Server and writes them to CDRO property sheets and/or job credentials.', {

    formalParameter 'config',
        type: 'entry',
        required: true,
        label: 'Configuration Name',
        description: 'Name of the Delinea Secret Server configuration to use.'

    formalParameter 'secretId',
        type: 'entry',
        required: false,
        label: 'Secret ID',
        description: 'Numeric ID of the secret in Delinea Secret Server (e.g. 2048 for SSH Key, 1042 for DB/API credentials).'

    formalParameter 'secretPath',
        type: 'entry',
        required: false,
        label: 'Secret Path',
        description: 'Path or search expression for secret lookup if Secret ID is omitted.'

    formalParameter 'fieldNames',
        type: 'entry',
        required: false,
        label: 'Fields to Extract',
        description: 'Comma-separated list of field slugs (e.g., username,password). Leave empty for all fields.'

    formalParameter 'resultPropertySheet',
        type: 'entry',
        required: false,
        defaultValue: '/myJob/delineaSecret',
        label: 'Output Property Sheet',
        description: 'CDRO property sheet where extracted secrets will be saved.'

    formalParameter 'exportToPipeline',
        type: 'checkbox',
        required: false,
        defaultValue: 'true',
        label: 'Export to Pipeline Runtime',
        description: 'Publish all secret values and .pem key to /myPipelineRuntime for cross-stage pipeline access.'

    formalParameter 'pipelinePropertySheet',
        type: 'entry',
        required: false,
        defaultValue: '/myPipelineRuntime/delineaSecret',
        label: 'Pipeline Property Sheet',
        description: 'Target path on /myPipelineRuntime where secret properties and .pem key will be stored.'

    formalParameter 'destinationFilePath',
        type: 'entry',
        required: false,
        label: 'Destination File Path',
        description: 'Optional file path on the CDRO agent to write a secret field (e.g. ~/.ssh/id_rsa or /tmp/cert.pem).'

    formalParameter 'fileFieldName',
        type: 'entry',
        required: false,
        label: 'File Field Slug',
        description: 'Field slug to write to the Destination File Path (e.g. private-key, certificate-file, apiKey). Auto-detected if omitted.'

    formalParameter 'filePermissions',
        type: 'entry',
        required: false,
        defaultValue: '0600',
        label: 'File Permissions',
        description: 'POSIX permissions for the saved file (e.g., 0600 for SSH keys, 0644 for general files).'

    formalParameter 'fetchFileContent',
        type: 'checkbox',
        required: false,
        defaultValue: 'true',
        label: 'Fetch File Attachments',
        description: 'Whether to automatically download binary/text file attachment contents from Delinea.'

    formalParameter 'createJobCredential',
        type: 'checkbox',
        required: false,
        defaultValue: 'false',
        label: 'Create Job Credential',
        description: 'Whether to register a dynamic runtime job credential with extracted user and password.'

    formalParameter 'credentialName',
        type: 'entry',
        required: false,
        label: 'Job Credential Name',
        description: 'Name of the credential to attach to the job.'

    formalParameter 'credentialUserField',
        type: 'entry',
        required: false,
        defaultValue: 'username',
        label: 'Username Field Slug',
        description: 'Field slug to map to the username.'

    formalParameter 'credentialPasswordField',
        type: 'entry',
        required: false,
        defaultValue: 'password',
        label: 'Password Field Slug',
        description: 'Field slug to map to the password.'

    formalParameter 'maskPasswords',
        type: 'checkbox',
        required: false,
        defaultValue: 'true',
        label: 'Mask Passwords in Output',
        description: 'Mask sensitive values in logs and summaries.'

    step 'getSecret',
        description: 'Execute Delinea secret retrieval',
        shell: 'ec-groovy',
        command: '''
import com.cloudbees.flowpdf.runner.ProcedureRunner

Map params = [
    config: '$[/myJobStep/config]',
    secretId: '$[/myJobStep/secretId]',
    secretPath: '$[/myJobStep/secretPath]',
    fieldNames: '$[/myJobStep/fieldNames]',
    resultPropertySheet: '$[/myJobStep/resultPropertySheet]',
    exportToPipeline: '$[/myJobStep/exportToPipeline]',
    pipelinePropertySheet: '$[/myJobStep/pipelinePropertySheet]',
    destinationFilePath: '$[/myJobStep/destinationFilePath]',
    fileFieldName: '$[/myJobStep/fileFieldName]',
    filePermissions: '$[/myJobStep/filePermissions]',
    fetchFileContent: '$[/myJobStep/fetchFileContent]',
    createJobCredential: '$[/myJobStep/createJobCredential]',
    credentialName: '$[/myJobStep/credentialName]',
    credentialUserField: '$[/myJobStep/credentialUserField]',
    credentialPasswordField: '$[/myJobStep/credentialPasswordField]',
    maskPasswords: '$[/myJobStep/maskPasswords]'
]

new ProcedureRunner().run('GetSecret', params)
'''
}
