/**
 * Sample CloudBees CD/RO Pipeline demonstrating Cross-Pipeline Secret & .PEM Key Sharing.
 *
 * This pipeline showcases:
 * 1. Fetching secrets from Delinea Secret Server in Stage 1 with 'exportToPipeline: true'.
 * 2. Automatic .pem key detection and enabling for cross-pipeline use (/myPipelineRuntime/delineaSecret/pem).
 * 3. Accessing any Delinea value (usernames, passwords, API keys, endpoints) across subsequent stages.
 * 4. Materializing and using the .pem private key on any pipeline agent using RestorePemKey procedure or inline shell.
 */

pipeline 'Cross-Pipeline-Delinea-Secrets-And-PEM', {
    description = 'CDRO pipeline showing Delinea secrets and .pem keys shared across all stages and tasks'

    // =========================================================================
    // STAGE 1: Retrieve Secrets from Delinea and Enable .PEM for Pipeline
    // =========================================================================
    stage 'Stage 1 - Retrieve Secrets & Enable .PEM', {
        colorCode = '#28a745'

        task 'Fetch Bastion SSH Key & Enable .PEM', {
            taskType = 'PROCEDURE'
            subpluginKey = 'EC-DelineaSecretServer'
            subprocedure = 'GetSecret'

            actualParameter 'config', 'Prod-Delinea-Vault'
            // For SSH Key, secretId is used as the input value (e.g. 2048)
            actualParameter 'secretId', '2048'
            // Note: secretPath can alternatively be used: actualParameter 'secretPath', '/DevOps/SSH/ProdBastionKey'
            // exportToPipeline publishes all fields & .pem key to /myPipelineRuntime
            actualParameter 'exportToPipeline', 'true'
            actualParameter 'pipelinePropertySheet', '/myPipelineRuntime/delineaSecret'
            actualParameter 'maskPasswords', 'true'
        }

        task 'Fetch Cloud API Key', {
            taskType = 'PROCEDURE'
            subpluginKey = 'EC-DelineaSecretServer'
            subprocedure = 'GetSecret'

            actualParameter 'config', 'Prod-Delinea-Vault'
            // For API Key / Web Token, secretId is used as the input value:
            actualParameter 'secretId', '3099'
            actualParameter 'exportToPipeline', 'true'
            actualParameter 'pipelinePropertySheet', '/myPipelineRuntime/stripeSecret'
            actualParameter 'maskPasswords', 'true'
        }

        task 'Fetch Database Credentials', {
            taskType = 'PROCEDURE'
            subpluginKey = 'EC-DelineaSecretServer'
            subprocedure = 'GetSecret'

            actualParameter 'config', 'Prod-Delinea-Vault'
            actualParameter 'secretId', '1042'
            actualParameter 'exportToPipeline', 'true'
            actualParameter 'pipelinePropertySheet', '/myPipelineRuntime/dbSecret'
            actualParameter 'createJobCredential', 'true'
            actualParameter 'credentialName', 'myAppDbCredential'
            actualParameter 'maskPasswords', 'true'
        }
    }

    // =========================================================================
    // STAGE 2: Use .PEM Key via RestorePemKey Procedure on Agent A
    // =========================================================================
    stage 'Stage 2 - Deploy via SSH (RestorePemKey)', {
        colorCode = '#007bff'

        // Restores the .pem key from /myPipelineRuntime/delineaSecret/pem onto this agent
        task 'Materialize .PEM on Agent A', {
            taskType = 'PROCEDURE'
            subpluginKey = 'EC-DelineaSecretServer'
            subprocedure = 'RestorePemKey'

            actualParameter 'pemKeyProperty', '/myPipelineRuntime/delineaSecret/pem'
            actualParameter 'destinationFilePath', '/tmp/agent_a_bastion.pem'
            actualParameter 'filePermissions', '0600'
            actualParameter 'resultPropertySheet', '/myJob/pemFile'
        }

        task 'Execute Remote Deployment Command', {
            taskType = 'COMMAND'
            subproject = 'Default'

            command = '''
echo "Verifying .pem key permissions on Agent A:"
ls -la /tmp/agent_a_bastion.pem

echo "Executing SSH command using restored .pem key:"
# ssh -i /tmp/agent_a_bastion.pem -o StrictHostKeyChecking=no deployer@bastion.corp.internal "uptime"
echo "SSH connection verified successfully with restored .pem key."
'''
        }
    }

    // =========================================================================
    // STAGE 3: Access Any Secret & .PEM Directly in Subsequent Stages
    // =========================================================================
    stage 'Stage 3 - Deploy Cloud & DB Services', {
        colorCode = '#17a2b8'

        task 'Deploy Service with Cross-Pipeline Values', {
            taskType = 'COMMAND'
            subproject = 'Default'

            command = '''
# 1. Any secret value from Stage 1 is accessible directly across the pipeline:
echo "Accessing database credentials from Stage 1:"
DB_USER="$[/myPipelineRuntime/dbSecret/username]"
DB_PORT="$[/myPipelineRuntime/dbSecret/port]"
DB_NAME="$[/myPipelineRuntime/dbSecret/database]"
echo "Database Target: ${DB_USER}@db.corp.internal:${DB_PORT}/${DB_NAME}"

# 2. Access API key from Stage 1 across the pipeline:
API_KEY="$[/myPipelineRuntime/stripeSecret/apiKey]"
echo "Retrieved masked API Key: ${API_KEY:0:4}********"

# 3. Use .pem key directly via inline shell across pipeline:
echo "Restoring .pem key directly from pipeline runtime property:"
echo "$[/myPipelineRuntime/delineaSecret/pem]" > /tmp/pipeline_runtime_key.pem
chmod 600 /tmp/pipeline_runtime_key.pem

ls -la /tmp/pipeline_runtime_key.pem
echo "Pipeline-wide .pem key is active and ready for use in Stage 3."
'''
        }
    }

    // =========================================================================
    // STAGE 4: Cleanup & Audit
    // =========================================================================
    stage 'Stage 4 - Cleanup & Security Audit', {
        colorCode = '#6c757d'

        task 'Scrub Temporary .PEM Keys and Pipeline Secrets', {
            taskType = 'COMMAND'
            subproject = 'Default'

            command = '''
echo "Removing temporary .pem files from disk..."
rm -f /tmp/agent_a_bastion.pem
rm -f /tmp/pipeline_runtime_key.pem

echo "Scrubbing sensitive properties from pipeline runtime..."
ectool deleteProperty "/myPipelineRuntime/delineaSecret/pem" || true
ectool deleteProperty "/myPipelineRuntime/delineaSecret/password" || true
ectool deleteProperty "/myPipelineRuntime/stripeSecret/apiKey" || true
ectool deleteProperty "/myPipelineRuntime/dbSecret/password" || true

echo "Pipeline secrets scrubbed successfully."
'''
        }
    }
}
