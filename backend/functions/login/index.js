'use strict';

/**
 * functions/login/index.js
 * POST /auth/login  — public, no JWT required
 *
 * Body: { email, password }
 * Response: { token, runnerId }
 *
 * Same error message for "not found" and "wrong password" to prevent email enumeration.
 */

const { DynamoDBClient } = require('@aws-sdk/client-dynamodb');
const { DynamoDBDocumentClient, GetCommand } = require('@aws-sdk/lib-dynamodb');
const bcrypt = require('bcryptjs');

const { wrap, parseBody, errors, ok, require: requireFields } = require('/opt/nodejs/shared/utils/response');
const { generateToken } = require('/opt/nodejs/shared/utils/auth');

const dynamo = DynamoDBDocumentClient.from(new DynamoDBClient({}));
const TABLE  = process.env.DYNAMODB_TABLE_NAME;

const INVALID_MSG = 'Invalid email or password';

exports.handler = wrap(async (event) => {
  const body = parseBody(event);
  requireFields(body, ['email', 'password']);

  const email    = body.email.toLowerCase().trim();
  const password = body.password;

  const result = await dynamo.send(new GetCommand({
    TableName: TABLE,
    Key: { PK: `AUTH#${email}`, SK: 'CREDENTIALS' },
  }));

  if (!result.Item) {
    return errors.unauthorized(INVALID_MSG);
  }

  const valid = await bcrypt.compare(password, result.Item.passwordHash);
  if (!valid) {
    return errors.unauthorized(INVALID_MSG);
  }

  const token = await generateToken(result.Item.runnerId);

  console.log(JSON.stringify({ type: 'LOGIN', runnerId: result.Item.runnerId }));
  return ok({ token, runnerId: result.Item.runnerId });
});
