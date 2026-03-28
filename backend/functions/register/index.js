'use strict';

/**
 * functions/register/index.js
 * POST /auth/register  — public, no JWT required
 *
 * Body: { email, password }
 * Response: { token, runnerId }
 *
 * Credentials stored in DynamoDB:
 *   PK: AUTH#{email}  SK: CREDENTIALS
 */

const { DynamoDBClient } = require('@aws-sdk/client-dynamodb');
const { DynamoDBDocumentClient, GetCommand, PutCommand } = require('@aws-sdk/lib-dynamodb');
const crypto = require('crypto');

const { wrap, parseBody, errors, created, require: requireFields } = require('/opt/nodejs/shared/utils/response');
const { generateToken } = require('/opt/nodejs/shared/utils/auth');
const { hashPassword } = require('/opt/nodejs/shared/utils/password');

const dynamo = DynamoDBDocumentClient.from(new DynamoDBClient({}));
const TABLE  = process.env.DYNAMODB_TABLE_NAME;

exports.handler = wrap(async (event) => {
  const body = parseBody(event);
  requireFields(body, ['email', 'password']);

  const email    = body.email.toLowerCase().trim();
  const password = body.password;

  if (password.length < 8) {
    throw { isValidationError: true, message: 'Password must be at least 8 characters' };
  }

  // Check for existing account
  const existing = await dynamo.send(new GetCommand({
    TableName: TABLE,
    Key: { PK: `AUTH#${email}`, SK: 'CREDENTIALS' },
  }));
  if (existing.Item) {
    return errors.conflict('An account with this email already exists');
  }

  const runnerId     = crypto.randomUUID();
  const passwordHash = await hashPassword(password);

  await dynamo.send(new PutCommand({
    TableName: TABLE,
    Item: {
      PK:           `AUTH#${email}`,
      SK:           'CREDENTIALS',
      email,
      runnerId,
      passwordHash,
      createdAt:    new Date().toISOString(),
      updatedAt:    new Date().toISOString(),
    },
  }));

  const token = await generateToken(runnerId);

  console.log(JSON.stringify({ type: 'REGISTER', runnerId }));
  return created({ token, runnerId });
});
