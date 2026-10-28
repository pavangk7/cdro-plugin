# CloudBees CD/RO Plugin for Delinea Secret Server (`EC-DelineaSecretServer`)

[![CloudBees CD/RO](https://img.shields.io/badge/CloudBees%20CD%2FRO-Plugin-blue.svg)](https://www.cloudbees.com/)
[![Delinea](https://img.shields.io/badge/Delinea-Secret%20Server-purple.svg)](https://delinea.com/products/secret-server)
[![Language](https://img.shields.io/badge/Language-Groovy%20%2F%20FlowPDF-orange.svg)](https://groovy-lang.org/)

The **EC-DelineaSecretServer** plugin integrates **CloudBees Continuous Delivery / Release Orchestration (CD/RO)** with **Delinea Secret Server** (both Delinea Secret Server Cloud and On-Premises deployments). 

It enables release engineers and pipeline authors to dynamically retrieve secrets, share any Delinea value across all pipeline stages, enable and materialize `.pem` private keys across different pipeline agents, and eliminate hardcoded credentials while maintaining strict audit trails and masking sensitive values.

---

## Key Features

- **Cross-Pipeline Secret Sharing (`/myPipelineRuntime`)**:
  - Any secret value retrieved from Delinea is automatically published to `/myPipelineRuntime/delineaSecret/<field>` when `exportToPipeline` is enabled (default: `true`).
  - Subsequent pipeline stages and tasks on any agent can immediately reference any secret field: `$[/myPipelineRuntime/delineaSecret/username]`, `$[/myPipelineRuntime/delineaSecret/password]`, `$[/myPipelineRuntime/delineaSecret/apiKey]`, etc.
- **Pipeline-Wide `.pem` Key Enabling**:
  - Automatically identifies SSH keys, certificates, or PEM blocks (`-----BEGIN RSA/OPENSSH PRIVATE KEY-----`).
  - Publishes standard properties across the pipeline:
    - `/myPipelineRuntime/delineaSecret/pem`
    - `/myPipelineRuntime/delineaSecret/pemKey`
    - `/myPipelineRuntime/delineaSecret/private-key`
    - `/myPipelineRuntime/delineaSecret/hasPem`
  - Any subsequent stage on any agent can use the `.pem` key:
    - **Option A (Dedicated Procedure)**: Call `RestorePemKey` to materialize the `.pem` file directly onto the agent filesystem with strict `0600` POSIX permissions.
    - **Option B (Inline Command)**: In any shell step, simply run:
      `echo "$[/myPipelineRuntime/delineaSecret/pem]" > /tmp/key.pem && chmod 600 /tmp/key.pem`
- **Universal Multi-Type Secret Retrieval**:
  - **SSH / Private Keys**: Fetches private keys (PEM, OpenSSH, PKCS8) and writes them to local agent files with enforced POSIX `0600` permissions.
  - **API Keys & Tokens**: Fetches API keys, Bearer tokens, and Web API secrets with automatic slug normalization (`apiKey`, `api_key`, `api-key`).
  - **File Attachments & Certificates**: Automatically retrieves binary or text file attachments (`.pfx`, `.crt`, `.pem`, `kubeconfig.yaml`, license files) via Delinea's `/api/v1/secrets/{id}/fields/{slug}` endpoint.
  - **Cloud Credentials**: Handles AWS IAM keys (`access-key-id`, `secret-access-key`), Azure Service Principals, and GCP Service Accounts.
  - **Standard User / Password Credentials**: Extracts database, OS, and service account passwords.
- **Project-Level Dynamic Credentials**: Registers retrieved credentials as project-level or job-level CDRO credentials for zero-footprint pipeline security.
- **OAuth 2.0 Authentication**: Supports both **OAuth2 Client Credentials Grant** (API Client ID & Secret) and **OAuth2 Password Grant** (User credentials + optional Active Directory domain).
- **Log Masking**: Automatically detects sensitive fields (passwords, private keys, API keys, tokens, passphrases, secrets) and masks them in CDRO execution logs and summaries.
- **Enterprise Network Support**: Configurable SSL/TLS certificate verification bypass (for internal labs) and outbound HTTP/HTTPS proxy support.

---

## Directory Structure

```
EC-DelineaSecretServer/
├── config/
│   ├── pluginspec.yaml               # FlowPDF plugin specification (metadata, config, procedures)
│   └── flowpdf.yaml                  # Framework version and runtime descriptor
├── dsl/
│   ├── promote.groovy                # Promotion script registered when plugin is installed
│   ├── demote.groovy                 # Cleanup script executed upon demotion
│   └── procedures/
│       ├── CreateConfiguration.groovy# Configuration setup & validation
│       ├── DeleteConfiguration.groovy# Configuration cleanup
│       ├── CheckConnection.groovy    # Connectivity and authentication health check
│       ├── GetSecret.groovy          # Secret retrieval & pipeline runtime export
│       ├── DownloadSecretFile.groovy # Dedicated file attachment & key downloader
│       ├── RestorePemKey.groovy      # Cross-pipeline .pem key materializer
│       └── SearchSecrets.groovy      # Query secrets matching criteria
├── forms/
│   ├── CreateConfiguration.xml       # UI configuration form (HTML/XML)
│   ├── CheckConnection.xml           # UI form for connection testing
│   ├── GetSecret.xml                 # UI parameter form for secret retrieval
│   ├── DownloadSecretFile.xml        # UI parameter form for file downloading
│   ├── RestorePemKey.xml             # UI parameter form for .pem key restoration
│   └── SearchSecrets.xml             # UI parameter form for secret queries
├── src/main/groovy/com/cloudbees/flowpdf/
│   ├── components/
│   │   ├── delinea/
│   │   │   ├── DelineaConfig.groovy  # Configuration entity & endpoint normalizer
│   │   │   └── DelineaClient.groovy  # OAuth2 REST client (token cache, HTTP engine)
│   │   └── procedures/
│   │       ├── GetSecret.groovy      # Business logic for GetSecret procedure
│   │       ├── DownloadSecretFile.groovy # Business logic for DownloadSecretFile procedure
│   │       ├── RestorePemKey.groovy  # Business logic for RestorePemKey procedure
│   │       ├── CheckConnection.groovy# Business logic for CheckConnection procedure
│   │       └── SearchSecrets.groovy  # Business logic for SearchSecrets procedure
│   └── runner/
│       └── ProcedureRunner.groovy    # Runtime bridge between ec-groovy and plugin handlers
├── src/test/groovy/                  # Spock unit test suite
├── samples/
│   └── pipeline.groovy               # Production-ready CloudBees CD/RO Pipeline DSL example
├── pom.xml                       # Maven build descriptor (compile, test, package plugin jar)
├── buildPlugin.sh                # Universal build script (auto-detects Maven or Gradle)
├── build.gradle                  # Gradle build and packaging specification
└── settings.gradle
```

---

## Building and Packaging the Plugin

### Option 1: Using Maven (Recommended if using Maven)
To compile, run the Spock test suite, and package the CDRO plugin:
```bash
# Run unit tests
mvn clean test

# Package the CDRO plugin jar
mvn clean package
```
This generates the plugin archive in both:
- `target/EC-DelineaSecretServer-1.0.0.jar`
- `build/plugins/EC-DelineaSecretServer-1.0.0.jar`

### Option 2: Using the Universal Build Script
```bash
chmod +x buildPlugin.sh
./buildPlugin.sh
```
The build script automatically detects Maven (`mvn`) or Gradle (`./gradlew`) and packages the plugin.

### Option 3: Using Gradle
```bash
./gradlew test
./gradlew packagePlugin
```

---

## Installing into CloudBees CD/RO

### Method 1: Using the CloudBees CD/RO Web UI
1. Log in to the CloudBees CD/RO console as an administrator.
2. Navigate to **Administration > Plugins**.
3. Click **Install from File...**
4. Select `build/plugins/EC-DelineaSecretServer-1.0.0.jar` and click **Upload**.
5. Locate **EC-DelineaSecretServer** in the plugin list and click **Promote**.

### Method 2: Using the `ectool` CLI
```bash
ectool login admin <password>
ectool installPlugin /path/to/EC-DelineaSecretServer/build/plugins/EC-DelineaSecretServer-1.0.0.jar
ectool promotePlugin EC-DelineaSecretServer-1.0.0
```

---

## Plugin Procedures

### 1. `GetSecret`
Retrieves secrets of any type (SSH Key, API Key, Password, Token, Certificate) and exports fields into CDRO property sheets, writes keys/files to disk, and publishes them across the pipeline runtime.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `config` | Entry | Yes | Configuration name (e.g., `Prod-Delinea-Vault`). |
| `secretId` | Entry | Optional | Numeric Secret ID (e.g., `1042`). Provide either `secretId` or `secretPath`. |
| `secretPath` | Entry | Optional | Path or lookup name (e.g., `/DevOps/SSH/BastionKey`). |
| `exportToPipeline` | Checkbox | Optional | Publish all fields and `.pem` key to `/myPipelineRuntime` (default: `true`). |
| `pipelinePropertySheet`| Entry | Optional | Path on `/myPipelineRuntime` (default: `/myPipelineRuntime/delineaSecret`). |
| `fieldNames` | Entry | Optional | Comma-separated list of field slugs to extract. Leave blank for all fields. |
| `resultPropertySheet` | Entry | Optional | Target CDRO property sheet (default: `/myJob/delineaSecret`). |
| `destinationFilePath` | Entry | Optional | Path on agent to write a secret field (e.g. `~/.ssh/id_rsa` or `/tmp/cert.pem`). |
| `fileFieldName` | Entry | Optional | Field slug to write to `destinationFilePath` (auto-detected if omitted). |
| `filePermissions` | Entry | Optional | POSIX permissions for saved file (defaults to `0600`). |
| `fetchFileContent` | Checkbox | Optional | Auto-download file attachments from Delinea (default: `true`). |
| `createJobCredential` | Checkbox | Optional | If checked, creates a dynamic runtime CDRO credential. |
| `credentialName` | Entry | Optional | Name of the credential to create. |
| `credentialUserField` | Entry | Optional | Field slug for username (default: `username`). |
| `credentialPasswordField`| Entry | Optional | Field slug for password/key (default: `password`). |
| `maskPasswords` | Checkbox | Optional | Masks passwords and secret fields in logs (default: `true`). |

#### Cross-Pipeline Exported Properties
When `exportToPipeline` is enabled (default), properties are set on `/myPipelineRuntime/delineaSecret`:
- `/myPipelineRuntime/delineaSecret/<fieldSlug>`: Value of each extracted secret field.
- `/myPipelineRuntime/delineaSecret/pem`: Full `.pem` private key content.
- `/myPipelineRuntime/delineaSecret/pemKey`: Full `.pem` private key content.
- `/myPipelineRuntime/delineaSecret/hasPem`: `'true'` if a PEM key was detected.
- `/myPipelineRuntime/delineaSecret/private-key`: Private key content.
- `/myPipelineRuntime/delineaSecret/privateKey`: Normalized camelCase private key alias.
- `/myPipelineRuntime/delineaSecret/apiKey`: Normalized API key alias.
- `/myPipelineRuntime/delineaSecret/id`: Secret ID.
- `/myPipelineRuntime/delineaSecret/name`: Secret display name.
- `/myPipelineRuntime/delineaSecret/secretTemplateName`: Name of the Delinea template.

---

### 2. `RestorePemKey`
Restores a `.pem` private key from pipeline runtime properties (`/myPipelineRuntime/delineaSecret/pem`) and writes it to a file with strict `0600` permissions on the local agent filesystem.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `pemKeyProperty` | Entry | Optional | Property path containing the `.pem` key (default: `/myPipelineRuntime/delineaSecret/pem`). |
| `destinationFilePath`| Entry | Yes | Path on agent where file will be written (e.g. `~/.ssh/id_rsa` or `/tmp/delinea.pem`). |
| `filePermissions` | Entry | Optional | POSIX permissions (default: `0600`). |
| `overwrite` | Checkbox | Optional | Overwrite if destination file exists (default: `true`). |
| `resultPropertySheet` | Entry | Optional | Defaults to `/myJob/pemFile`. |

#### Exported Properties
- `/myJob/pemFile/filePath`: Full absolute path of saved `.pem` file.
- `/myJob/pemFile/fileName`: Saved filename.
- `/myJob/pemFile/fileSize`: File size in bytes.
- `/myJob/pemFile/permissions`: Permissions applied (e.g. `0600`).

---

### 3. `DownloadSecretFile`
Downloads a file attachment (such as an SSL Certificate `.pfx`, `.crt`, SSH private key file, or `kubeconfig.yaml`) directly to the agent filesystem.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `config` | Entry | Yes | Configuration name. |
| `secretId` | Entry | Optional | Numeric Secret ID. |
| `secretPath` | Entry | Optional | Path or lookup name. |
| `fieldSlug` | Entry | Optional | Field slug of attachment. Auto-detected if omitted. |
| `destinationPath` | Entry | Yes | Target directory or file path on the CDRO agent. |
| `filePermissions` | Entry | Optional | POSIX permissions (default: `0600`). |
| `overwrite` | Checkbox | Optional | Overwrite if destination exists (default: `true`). |
| `resultPropertySheet` | Entry | Optional | Defaults to `/myJob/delineaDownloadedFile`. |

---

### 4. `SearchSecrets`
Searches for secrets matching a query keyword or folder ID.

| Parameter | Type | Required | Description |
|---|---|---|---|
| `config` | Entry | Yes | Configuration name. |
| `searchText` | Entry | Yes | Keyword to match in secret names or descriptions. |
| `folderId` | Entry | Optional | Numeric folder ID to scope the search. |
| `maxResults` | Entry | Optional | Max records to return (defaults to 25). |
| `resultPropertySheet` | Entry | Optional | Defaults to `/myJob/delineaSearchResults`. |

---

### 5. `CheckConnection`
Validates connectivity and authentication against the Delinea Secret Server instance.

---

## Cross-Pipeline Usage Examples

### Stage 1: Retrieve Secrets & Enable .PEM for Pipeline
```groovy
stage 'Stage 1 - Retrieve Secrets', {
    task 'Fetch Bastion SSH Key & Enable .PEM', {
        taskType = 'PROCEDURE'
        subpluginKey = 'EC-DelineaSecretServer'
        subprocedure = 'GetSecret'

        actualParameter 'config', 'Prod-Delinea-Vault'
        // For SSH key, secretId is provided directly as the input value:
        actualParameter 'secretId', '2048'
        // Note: secretPath can also be used: actualParameter 'secretPath', '/DevOps/SSH/ProdBastionKey'
        actualParameter 'exportToPipeline', 'true'
        actualParameter 'pipelinePropertySheet', '/myPipelineRuntime/delineaSecret'
    }
}
```

### Stage 2: Materialize and Use .PEM Key on Agent A (RestorePemKey)
```groovy
stage 'Stage 2 - Remote Deploy', {
    task 'Restore .PEM on Agent', {
        taskType = 'PROCEDURE'
        subpluginKey = 'EC-DelineaSecretServer'
        subprocedure = 'RestorePemKey'

        actualParameter 'pemKeyProperty', '/myPipelineRuntime/delineaSecret/pem'
        actualParameter 'destinationFilePath', '/tmp/id_rsa.pem'
        actualParameter 'filePermissions', '0600'
    }

    task 'Run SSH Command', {
        taskType = 'COMMAND'
        command = 'ssh -i /tmp/id_rsa.pem -o StrictHostKeyChecking=no deployer@bastion.corp.internal "uptime"'
    }
}
```

### Stage 3: Use Delinea Values & Inline .PEM Key in Another Stage
```groovy
stage 'Stage 3 - Deploy Services', {
    task 'Deploy Container', {
        taskType = 'COMMAND'
        command = '''
        echo "Using secret values from Stage 1:"
        echo "DB Username: $[/myPipelineRuntime/delineaSecret/username]"
        
        # Restore .pem key inline via shell:
        echo "$[/myPipelineRuntime/delineaSecret/pem]" > /tmp/deploy.pem
        chmod 600 /tmp/deploy.pem
        
        ssh -i /tmp/deploy.pem deployer@host "docker-compose up -d"
        '''
    }
}
```

See [samples/pipeline.groovy](samples/pipeline.groovy) for the complete multi-stage pipeline example.

---

## Security Best Practices

1. **Principle of Least Privilege**: Create Delinea API clients with read-only access restricted solely to the specific folders or secrets needed by the pipeline.
2. **Ephemeral Property Scrubbing**: Clean up sensitive properties at the end of the pipeline execution using `ectool deleteProperty "/myPipelineRuntime/delineaSecret/pem"`.
3. **Audit Logging**: Secret Server logs every access event with timestamp, IP, and caller identity under **Secret Audit** in Delinea UI.

---

## License

Apache License 2.0.
# cdro-plugin
