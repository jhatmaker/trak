# Trak Bastion — DB Access via SSM Port Forwarding

Use this when you need direct PostgreSQL access to Aurora (e.g. running migrations).
No SSH key, no public IP — uses AWS Systems Manager Session Manager.

Deploy it, run your queries, tear it down. The whole thing takes ~5 minutes.

---

## Prerequisites

- AWS CLI configured with admin credentials
- [Session Manager plugin for AWS CLI](https://docs.aws.amazon.com/systems-manager/latest/userguide/session-manager-working-with-install-plugin.html) installed locally
- `psql` installed locally (`brew install libpq && brew link --force libpq` on macOS)

---

## One-time SSM parameter setup

The bastion template needs a single subnet ID (not the comma-separated list).
Use either of the two subnets already stored for Lambda/Aurora:

```bash
# See your existing subnets
aws ssm get-parameter --name /trak/dev/db-subnet-ids \
  --region us-east-1 --query 'Parameter.Value' --output text

# Store the first one as a separate parameter
aws ssm put-parameter --region us-east-1 \
  --name /trak/dev/db-subnet-id-1 \
  --value "subnet-FIRST_ONE_FROM_ABOVE" \
  --type String
```

---

## Step 1 — Deploy the bastion (~2 min)

```bash
aws cloudformation deploy \
  --template-file scripts/bastion.yaml \
  --stack-name trak-bastion-dev \
  --capabilities CAPABILITY_NAMED_IAM \
  --region us-east-1
```

Wait for `CREATE_COMPLETE`, then get the instance ID and Aurora endpoint:

```bash
aws cloudformation describe-stacks --stack-name trak-bastion-dev \
  --region us-east-1 \
  --query 'Stacks[0].Outputs' --output table
```

Note the `InstanceId` and `AuroraEndpoint` values.

---

## Step 2 — Wait for SSM agent to register (~60 sec)

```bash
aws ssm describe-instance-information \
  --region us-east-1 \
  --query 'InstanceInformationList[?InstanceId==`<InstanceId>`].PingStatus' \
  --output text
```

Wait until it returns `Online`.

---

## Step 3 — Open the tunnel (keep this terminal open)

```bash
aws ssm start-session \
  --target <InstanceId> \
  --document-name AWS-StartPortForwardingSessionToRemoteHost \
  --parameters '{"host":["<AuroraEndpoint>"],"portNumber":["5432"],"localPortNumber":["5432"]}'
```

The tunnel is open as long as this process runs.

---

## Step 4 — Get the DB password

In a new terminal:

```bash
aws secretsmanager get-secret-value \
  --region us-east-1 \
  --secret-id trak/aurora-dev \
  --query 'SecretString' --output text \
  | python3 -c "import sys,json; print(json.load(sys.stdin)['password'])"
```

---

## Step 5 — Connect and run migrations

```bash
# Test connection
psql -h localhost -p 5432 -U trakadmin -d trak -c "SELECT version();"

# Run schema migration
psql -h localhost -p 5432 -U trakadmin -d trak \
  -f backend/shared/db/migrations/001_initial.sql

# Run seed data
psql -h localhost -p 5432 -U trakadmin -d trak \
  -f backend/shared/db/seed/sources.sql

# Verify
psql -h localhost -p 5432 -U trakadmin -d trak \
  -c "SELECT canonical_slug, name FROM race_source ORDER BY name;"
```

When prompted for a password, use the value from Step 4.

---

## Step 6 — Tear down when done

Close the tunnel terminal (Ctrl+C), then:

```bash
aws cloudformation delete-stack \
  --stack-name trak-bastion-dev \
  --region us-east-1
```

This removes the EC2 instance, instance profile, security group, and the Aurora
ingress rule that was opened for the bastion.

---

## Troubleshooting

**SSM session fails immediately**
The instance may not have outbound HTTPS (port 443) to the SSM endpoints.
This requires either a NAT Gateway or SSM VPC endpoints in your VPC.
Check that your private subnets have a route to a NAT Gateway.

**`psql: error: connection to server at "localhost" failed`**
The tunnel is not open — go back to Step 3.

**`FATAL: password authentication failed`**
Double-check the password from Step 4. Note it may contain special characters —
wrap it in single quotes if pasting directly into a psql prompt.

**`ERROR: relation "race_source" already exists`**
Migrations are idempotent — this is fine. The `IF NOT EXISTS` clauses mean
re-running is safe; the error just means the table was already created.
