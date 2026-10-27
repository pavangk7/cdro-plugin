procedure 'SearchSecrets', description: 'Searches for secrets in Delinea Secret Server matching a text query.', {

    formalParameter 'config',
        type: 'entry',
        required: true,
        label: 'Configuration Name',
        description: 'Name of the Delinea Secret Server configuration to use.'

    formalParameter 'searchText',
        type: 'entry',
        required: true,
        label: 'Search Query',
        description: 'Keyword to search for in secret names and descriptions.'

    formalParameter 'folderId',
        type: 'entry',
        required: false,
        label: 'Folder ID',
        description: 'Optional numeric folder ID to scope the search.'

    formalParameter 'maxResults',
        type: 'entry',
        required: false,
        defaultValue: '25',
        label: 'Max Results',
        description: 'Maximum number of results to return (default 25).'

    formalParameter 'resultPropertySheet',
        type: 'entry',
        required: false,
        defaultValue: '/myJob/delineaSearchResults',
        label: 'Output Property Sheet',
        description: 'CDRO property sheet where search results will be saved.'

    step 'searchSecrets',
        description: 'Execute secret search query',
        shell: 'ec-groovy',
        command: '''
import com.cloudbees.flowpdf.runner.ProcedureRunner

Map params = [
    config: '$[/myJobStep/config]',
    searchText: '$[/myJobStep/searchText]',
    folderId: '$[/myJobStep/folderId]',
    maxResults: '$[/myJobStep/maxResults]',
    resultPropertySheet: '$[/myJobStep/resultPropertySheet]'
]

new ProcedureRunner().run('SearchSecrets', params)
'''
}
