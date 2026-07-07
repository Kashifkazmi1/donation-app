'use strict';

/**
 * Converts an integer amount in the smallest currency unit (e.g. cents) to
 * the decimal major-unit string GiveWP expects (e.g. 2500 -> "25.00").
 *
 * Assumes 2-decimal currencies, which covers every currency this system is
 * expected to process (USD and similar). If 0-decimal or 3-decimal
 * currencies are ever needed, this is the place to branch on currency code.
 */
function minorUnitsToDecimalString(amount) {
  const sign = amount < 0 ? '-' : '';
  const abs = Math.abs(Math.trunc(amount));
  const major = Math.floor(abs / 100);
  const minor = String(abs % 100).padStart(2, '0');
  return `${sign}${major}.${minor}`;
}

function toUpperCurrency(currency) {
  return String(currency || '').toUpperCase();
}

module.exports = { minorUnitsToDecimalString, toUpperCurrency };
