'use strict';

const { z } = require('zod');

const loginSchema = z.object({
  username: z.string().trim().min(1, 'username is required'),
  password: z.string().min(1, 'password is required'),
});

const donorSchema = z
  .object({
    firstName: z.string().trim().min(1).optional(),
    lastName: z.string().trim().min(1).optional(),
    email: z.string().trim().email('email must be a valid email address').optional(),
    phone: z.string().trim().min(1).optional(),
  })
  .partial();

const paymentIntentSchema = z.object({
  amount: z
    .number({ invalid_type_error: 'amount must be a number' })
    .int('amount must be an integer (smallest currency unit)')
    .positive('amount must be greater than 0'),
  currency: z.string().trim().min(3).max(3).toLowerCase().optional(),
  anonymous: z.boolean().optional().default(false),
  donor: donorSchema.optional(),
  idempotencyKey: z
    .string()
    .uuid('idempotencyKey must be a UUID v4'),
});

const donationCompleteSchema = z.object({
  paymentIntentId: z.string().trim().min(1, 'paymentIntentId is required'),
  idempotencyKey: z
    .string()
    .uuid('idempotencyKey must be a UUID v4'),
});

const transactionsQuerySchema = z.object({
  page: z
    .string()
    .optional()
    .transform((v) => (v === undefined ? 1 : parseInt(v, 10)))
    .refine((v) => Number.isInteger(v) && v >= 1, 'page must be a positive integer'),
  pageSize: z
    .string()
    .optional()
    .transform((v) => (v === undefined ? 20 : parseInt(v, 10)))
    .refine(
      (v) => Number.isInteger(v) && v >= 1 && v <= 100,
      'pageSize must be an integer between 1 and 100'
    ),
  status: z.string().trim().min(1).optional(),
});

module.exports = {
  loginSchema,
  paymentIntentSchema,
  donationCompleteSchema,
  transactionsQuerySchema,
};
