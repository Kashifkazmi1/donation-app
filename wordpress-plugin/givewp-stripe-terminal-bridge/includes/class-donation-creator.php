<?php
/**
 * Handles lookup/creation of the GiveWP donor + donation record for an incoming
 * "in person, already paid via Stripe Terminal" donation.
 *
 * @package GiveWP_Stripe_Terminal_Bridge
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * Class GSTB_Donation_Creator
 */
class GSTB_Donation_Creator {

	/**
	 * Payment meta key holding the Stripe PaymentIntent ID. Used both to store it on the
	 * donation and as the idempotency lookup key, per docs/API_CONTRACT.md section 4.
	 */
	const META_PAYMENT_INTENT_ID = '_give_stripe_terminal_payment_intent_id';

	/**
	 * Payment meta key holding the Stripe Charge ID.
	 */
	const META_CHARGE_ID = '_give_stripe_terminal_charge_id';

	/**
	 * GiveWP's standard "this donation is anonymous" meta flag.
	 */
	const META_ANONYMOUS = '_give_anonymous_donation';

	/**
	 * Create a donation from the given, already-sanitized request data, or return the
	 * existing one if a donation for the same stripePaymentIntentId already exists.
	 *
	 * @param array $data Sanitized request payload. See GSTB_REST_Controller::create_donation_args().
	 * @return array|WP_Error {
	 *     @type int    $donationId Give payment (donation) post ID.
	 *     @type int    $donorId    Give donor ID.
	 *     @type string $status     Resulting donation post status (e.g. 'publish').
	 *     @type bool   $created    True if a new donation was created, false if an existing one was returned.
	 * }
	 */
	public static function create_or_get( array $data ) {
		if ( ! gstb_is_givewp_active() ) {
			return new WP_Error(
				'givewp_not_active',
				__( 'GiveWP is not active on this site.', 'givewp-stripe-terminal-bridge' ),
				array( 'status' => 500 )
			);
		}

		$payment_intent_id = isset( $data['stripePaymentIntentId'] ) ? (string) $data['stripePaymentIntentId'] : '';

		if ( '' === $payment_intent_id ) {
			return new WP_Error(
				'missing_payment_intent_id',
				__( 'stripePaymentIntentId is required.', 'givewp-stripe-terminal-bridge' ),
				array( 'status' => 400 )
			);
		}

		// --- Idempotency: has this PaymentIntent already produced a donation? ---
		$existing = self::find_existing_donation( $payment_intent_id );
		if ( $existing ) {
			return $existing;
		}

		$donor_created = false;
		$payment_id    = 0;

		try {
			// --- Donor lookup / creation ---
			$donor = self::find_or_create_donor( $data, $donor_created );

			if ( is_wp_error( $donor ) ) {
				return $donor;
			}

			// --- Resolve the Give Form to attribute the donation to ---
			$form_id = self::resolve_form_id( $data );

			if ( empty( $form_id ) ) {
				self::cleanup( 0, $donor_created ? $donor : null );

				return new WP_Error(
					'no_give_form_available',
					__( 'No Give Form ID was supplied, no default Give Form is configured (Settings > Donation Terminal), and no published Give Form exists to fall back to.', 'givewp-stripe-terminal-bridge' ),
					array( 'status' => 500 )
				);
			}

			// --- Build and insert the payment ---
			$status = ! empty( $data['status'] ) ? sanitize_key( $data['status'] ) : 'publish';
			$amount = isset( $data['amount'] ) ? (float) $data['amount'] : 0.0;

			$date = ! empty( $data['date'] ) ? strtotime( $data['date'] ) : time();
			if ( false === $date ) {
				$date = time();
			}

			$payment_data = array(
				'price'           => $amount,
				'give_form_id'    => $form_id,
				'give_form_title' => get_the_title( $form_id ),
				'give_price_id'   => 'custom',
				'date'            => gmdate( 'Y-m-d H:i:s', $date ),
				'purchase_key'    => strtolower( md5( 'gstb_' . $payment_intent_id . '_' . wp_generate_password( 8, false ) ) ),
				'currency'        => ! empty( $data['currency'] ) ? sanitize_text_field( $data['currency'] ) : 'USD',
				'user_email'      => $donor->email,
				'user_info'       => array(
					'id'         => ! empty( $donor->user_id ) ? (int) $donor->user_id : 0,
					'email'      => $donor->email,
					'first_name' => isset( $data['firstName'] ) ? sanitize_text_field( $data['firstName'] ) : '',
					'last_name'  => isset( $data['lastName'] ) ? sanitize_text_field( $data['lastName'] ) : '',
					'address'    => array(),
				),
				'status'          => $status,
				'gateway'         => 'stripe_terminal',
				'give_donor_id'   => $donor->id,
			);

			$payment_id = give_insert_payment( $payment_data );

			if ( ! $payment_id ) {
				self::cleanup( 0, $donor_created ? $donor : null );

				return new WP_Error(
					'donation_insert_failed',
					__( 'give_insert_payment() failed to create the donation.', 'givewp-stripe-terminal-bridge' ),
					array( 'status' => 500 )
				);
			}

			// Link the payment to the donor and update the donor's donation stats (matches
			// what GiveWP's own gateways do after inserting a payment).
			if ( method_exists( $donor, 'attach_payment' ) ) {
				$donor->attach_payment( $payment_id, true );
			}

			// --- Payment meta: Stripe identifiers + transaction ID so it renders like a normal Stripe donation ---
			give_update_meta( $payment_id, self::META_PAYMENT_INTENT_ID, $payment_intent_id );

			if ( ! empty( $data['stripeChargeId'] ) ) {
				give_update_meta( $payment_id, self::META_CHARGE_ID, sanitize_text_field( $data['stripeChargeId'] ) );
			}

			$transaction_id = ! empty( $data['stripeTransactionId'] )
				? sanitize_text_field( $data['stripeTransactionId'] )
				: ( ! empty( $data['stripeChargeId'] ) ? sanitize_text_field( $data['stripeChargeId'] ) : $payment_intent_id );

			if ( function_exists( 'give_set_payment_transaction_id' ) ) {
				give_set_payment_transaction_id( $payment_id, $transaction_id );
			}

			// --- Anonymous flag ---
			if ( ! empty( $data['anonymous'] ) ) {
				give_update_meta( $payment_id, self::META_ANONYMOUS, 1 );
			}

			return array(
				'donationId' => (int) $payment_id,
				'donorId'    => (int) $donor->id,
				'status'     => get_post_status( $payment_id ) ? get_post_status( $payment_id ) : $status,
				'created'    => true,
			);
		} catch ( Exception $e ) {
			self::cleanup( $payment_id, $donor_created ? ( isset( $donor ) ? $donor : null ) : null );

			return new WP_Error(
				'donation_create_exception',
				sprintf(
					/* translators: %s: exception message */
					__( 'Unexpected error while creating the donation: %s', 'givewp-stripe-terminal-bridge' ),
					$e->getMessage()
				),
				array( 'status' => 500 )
			);
		}
	}

	/**
	 * Look for a donation already carrying the given Stripe PaymentIntent ID.
	 *
	 * GiveWP maintains full backward compatibility for `get_post_meta()`/`WP_Query`
	 * meta_query against the `give_payment` post type even on installs that have migrated
	 * payment meta storage to custom tables, so a plain get_posts() meta lookup is reliable
	 * across supported GiveWP versions.
	 *
	 * @param string $payment_intent_id Stripe PaymentIntent ID.
	 * @return array|null Response array (see create_or_get()) or null if not found.
	 */
	private static function find_existing_donation( $payment_intent_id ) {
		$existing = get_posts(
			array(
				'post_type'      => 'give_payment',
				'post_status'    => 'any',
				'posts_per_page' => 1,
				'fields'         => 'ids',
				'meta_key'       => self::META_PAYMENT_INTENT_ID, // phpcs:ignore WordPress.DB.SlowDBQuery.slow_db_query_meta_key
				'meta_value'     => $payment_intent_id, // phpcs:ignore WordPress.DB.SlowDBQuery.slow_db_query_meta_value
				'no_found_rows'  => true,
			)
		);

		if ( empty( $existing ) ) {
			return null;
		}

		$payment_id = (int) $existing[0];
		$donor_id   = (int) give_get_meta( $payment_id, '_give_payment_donor_id', true );

		return array(
			'donationId' => $payment_id,
			'donorId'    => $donor_id,
			'status'     => get_post_status( $payment_id ),
			'created'    => false,
		);
	}

	/**
	 * Find an existing GiveWP donor by email, or create one.
	 *
	 * @param array $data          Sanitized request payload.
	 * @param bool  $donor_created Out param, set true if a new donor row was created.
	 * @return Give_Donor|WP_Error
	 */
	private static function find_or_create_donor( array $data, &$donor_created ) {
		$email = isset( $data['email'] ) ? sanitize_email( $data['email'] ) : '';

		if ( '' === $email || ! is_email( $email ) ) {
			return new WP_Error(
				'invalid_email',
				__( 'A valid email address is required to create or match a donor.', 'givewp-stripe-terminal-bridge' ),
				array( 'status' => 400 )
			);
		}

		if ( ! class_exists( 'Give_Donor' ) ) {
			return new WP_Error(
				'givewp_not_active',
				__( 'GiveWP is not active on this site.', 'givewp-stripe-terminal-bridge' ),
				array( 'status' => 500 )
			);
		}

		$first_name = isset( $data['firstName'] ) ? sanitize_text_field( $data['firstName'] ) : '';
		$last_name  = isset( $data['lastName'] ) ? sanitize_text_field( $data['lastName'] ) : '';
		$phone      = isset( $data['phone'] ) ? sanitize_text_field( $data['phone'] ) : '';

		// Give_Donor( $email ) looks the donor up by email when a non-numeric value is given.
		$donor = new Give_Donor( $email );

		if ( empty( $donor->id ) ) {
			$name = trim( $first_name . ' ' . $last_name );

			if ( '' === $name ) {
				$name = $email;
			}

			$new_donor_id = $donor->create(
				array(
					'name'  => $name,
					'email' => $email,
				)
			);

			if ( ! $new_donor_id ) {
				return new WP_Error(
					'donor_create_failed',
					__( 'Failed to create a GiveWP donor for the given email.', 'givewp-stripe-terminal-bridge' ),
					array( 'status' => 500 )
				);
			}

			// Reload so all Give_Donor properties (id, purchase_value, etc.) are populated.
			$donor         = new Give_Donor( $new_donor_id );
			$donor_created = true;
		}

		if ( '' !== $phone ) {
			// GiveWP's donor object exposes a native `phone` meta property (surfaced in the
			// donor detail screen in wp-admin), so we use it directly rather than inventing
			// a custom meta key.
			$donor->update_meta( 'phone', $phone );
		}

		return $donor;
	}

	/**
	 * Resolve which Give Form the donation should be attributed to:
	 * request formId -> plugin's configured default -> earliest published give_forms post.
	 *
	 * @param array $data Sanitized request payload.
	 * @return int Form post ID, or 0 if none could be resolved.
	 */
	private static function resolve_form_id( array $data ) {
		if ( ! empty( $data['formId'] ) ) {
			$form_id = (int) $data['formId'];

			if ( $form_id > 0 && 'give_forms' === get_post_type( $form_id ) ) {
				return $form_id;
			}
		}

		$default_form_id = (int) get_option( GSTB_OPTION_DEFAULT_FORM_ID, 0 );

		if ( $default_form_id > 0 && 'give_forms' === get_post_type( $default_form_id ) ) {
			return $default_form_id;
		}

		$fallback = get_posts(
			array(
				'post_type'      => 'give_forms',
				'post_status'    => 'publish',
				'posts_per_page' => 1,
				'orderby'        => 'date',
				'order'          => 'ASC',
				'fields'         => 'ids',
				'no_found_rows'  => true,
			)
		);

		return ! empty( $fallback ) ? (int) $fallback[0] : 0;
	}

	/**
	 * Best-effort cleanup so a failed request never leaves a half-created donor/donation
	 * behind.
	 *
	 * @param int             $payment_id Payment post ID to delete (0 to skip).
	 * @param Give_Donor|null $donor      Donor to delete IF it was newly created by this
	 *                                    request and has no other donations attached.
	 */
	private static function cleanup( $payment_id, $donor ) {
		if ( $payment_id ) {
			wp_delete_post( $payment_id, true );
		}

		if ( $donor && ! empty( $donor->id ) && function_exists( 'Give' ) ) {
			// Only remove the donor we just created, and only if nothing else got attached
			// to it in the meantime (e.g. a race with another request).
			$donation_count = isset( $donor->purchase_count ) ? (int) $donor->purchase_count : 0;

			if ( 0 === $donation_count && isset( Give()->donors ) ) {
				Give()->donors->delete( $donor->id );
			}
		}
	}
}
