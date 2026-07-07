<?php
/**
 * Registers the donation-terminal/v1 REST routes and shapes every response (success or
 * error) into the envelope defined by docs/API_CONTRACT.md section 1:
 *   Success: { "success": true, "data": { ... } }
 *   Error:   { "success": false, "error": { "code": "...", "message": "...", "details": {...} } }
 *
 * @package GiveWP_Stripe_Terminal_Bridge
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * Class GSTB_REST_Controller
 */
class GSTB_REST_Controller {

	/**
	 * Singleton instance.
	 *
	 * @var GSTB_REST_Controller|null
	 */
	private static $instance = null;

	/**
	 * Get (and lazily create) the singleton instance, wiring up its hooks.
	 *
	 * @return GSTB_REST_Controller
	 */
	public static function instance() {
		if ( null === self::$instance ) {
			self::$instance = new self();
			self::$instance->hooks();
		}

		return self::$instance;
	}

	/**
	 * Wire up WordPress hooks.
	 */
	private function hooks() {
		add_action( 'rest_api_init', array( $this, 'register_routes' ) );

		// Reshape any WP_Error produced anywhere in the request lifecycle (permission
		// check, arg validation, or the route callback itself) for our routes into the
		// contract's error envelope, instead of WordPress's default {code,message,data} shape.
		add_filter( 'rest_request_after_callbacks', array( $this, 'format_error_response' ), 10, 3 );
	}

	/**
	 * Register the donation-terminal/v1 REST routes.
	 */
	public function register_routes() {
		register_rest_route(
			GSTB_REST_NAMESPACE,
			'/health',
			array(
				'methods'             => WP_REST_Server::READABLE,
				'callback'            => array( $this, 'get_health' ),
				'permission_callback' => array( $this, 'check_api_key' ),
			)
		);

		register_rest_route(
			GSTB_REST_NAMESPACE,
			'/donations',
			array(
				'methods'             => WP_REST_Server::CREATABLE,
				'callback'            => array( $this, 'create_donation' ),
				'permission_callback' => array( $this, 'check_api_key' ),
				'args'                => $this->create_donation_args(),
			)
		);
	}

	/**
	 * permission_callback for both routes: validates the X-API-Key header against the
	 * value stored in wp_options, using hash_equals() to avoid timing attacks.
	 *
	 * @param WP_REST_Request $request Request.
	 * @return true|WP_Error
	 */
	public function check_api_key( WP_REST_Request $request ) {
		$provided = $request->get_header( 'x-api-key' );
		$stored   = get_option( GSTB_OPTION_API_KEY, '' );

		if ( empty( $stored ) ) {
			return new WP_Error(
				'api_key_not_configured',
				__( 'No API key has been configured for this site yet. Set one under Settings > Donation Terminal.', 'givewp-stripe-terminal-bridge' ),
				array( 'status' => 401 )
			);
		}

		if ( empty( $provided ) || ! is_string( $provided ) || ! hash_equals( (string) $stored, (string) $provided ) ) {
			return new WP_Error(
				'unauthorized',
				__( 'Missing or invalid X-API-Key header.', 'givewp-stripe-terminal-bridge' ),
				array( 'status' => 401 )
			);
		}

		return true;
	}

	/**
	 * GET /donation-terminal/v1/health
	 *
	 * @return WP_REST_Response
	 */
	public function get_health() {
		return new WP_REST_Response(
			array(
				'success' => true,
				'data'    => array(
					'givewpActive' => gstb_is_givewp_active(),
					'version'      => GSTB_VERSION,
				),
			),
			200
		);
	}

	/**
	 * POST /donation-terminal/v1/donations
	 *
	 * @param WP_REST_Request $request Request, already validated/sanitized per create_donation_args().
	 * @return WP_REST_Response|WP_Error
	 */
	public function create_donation( WP_REST_Request $request ) {
		if ( ! gstb_is_givewp_active() ) {
			return new WP_Error(
				'givewp_not_active',
				__( 'GiveWP is not active on this site; the donation could not be created.', 'givewp-stripe-terminal-bridge' ),
				array( 'status' => 500 )
			);
		}

		$data = array(
			'amount'                => $request->get_param( 'amount' ),
			'currency'              => $request->get_param( 'currency' ),
			'firstName'             => $request->get_param( 'firstName' ),
			'lastName'              => $request->get_param( 'lastName' ),
			'email'                 => $request->get_param( 'email' ),
			'phone'                 => $request->get_param( 'phone' ),
			'anonymous'             => $request->get_param( 'anonymous' ),
			'gateway'               => $request->get_param( 'gateway' ),
			'status'                => $request->get_param( 'status' ),
			'stripePaymentIntentId' => $request->get_param( 'stripePaymentIntentId' ),
			'stripeChargeId'        => $request->get_param( 'stripeChargeId' ),
			'stripeTransactionId'   => $request->get_param( 'stripeTransactionId' ),
			'formId'                => $request->get_param( 'formId' ),
			'date'                  => $request->get_param( 'date' ),
		);

		$result = GSTB_Donation_Creator::create_or_get( $data );

		if ( is_wp_error( $result ) ) {
			return $result;
		}

		$status_code = ! empty( $result['created'] ) ? 201 : 200;

		return new WP_REST_Response(
			array(
				'success' => true,
				'data'    => array(
					'donationId' => $result['donationId'],
					'donorId'    => $result['donorId'],
					'status'     => $result['status'],
				),
			),
			$status_code
		);
	}

	/**
	 * Arg schema for POST /donations: required fields, sanitize/validate callbacks. This is
	 * the "Input validation/sanitization on all REST input" layer -- WordPress runs these
	 * before create_donation() is ever invoked.
	 *
	 * @return array
	 */
	private function create_donation_args() {
		return array(
			'amount'                => array(
				'required'          => true,
				'type'              => 'string',
				'sanitize_callback' => 'sanitize_text_field',
				'validate_callback' => function ( $value ) {
					return is_numeric( $value ) && (float) $value > 0;
				},
			),
			'currency'              => array(
				'required'          => false,
				'type'              => 'string',
				'default'           => 'USD',
				'sanitize_callback' => function ( $value ) {
					return strtoupper( sanitize_text_field( $value ) );
				},
			),
			'firstName'             => array(
				'required'          => false,
				'type'              => 'string',
				'default'           => '',
				'sanitize_callback' => 'sanitize_text_field',
			),
			'lastName'              => array(
				'required'          => false,
				'type'              => 'string',
				'default'           => '',
				'sanitize_callback' => 'sanitize_text_field',
			),
			'email'                 => array(
				'required'          => true,
				'type'              => 'string',
				'format'            => 'email',
				'sanitize_callback' => 'sanitize_email',
				'validate_callback' => function ( $value ) {
					return is_email( $value );
				},
			),
			'phone'                 => array(
				'required'          => false,
				'type'              => 'string',
				'default'           => '',
				'sanitize_callback' => 'sanitize_text_field',
			),
			'anonymous'             => array(
				'required'          => false,
				'type'              => 'boolean',
				'default'           => false,
			),
			'gateway'               => array(
				'required'          => false,
				'type'              => 'string',
				'default'           => 'stripe_terminal',
				'sanitize_callback' => 'sanitize_key',
			),
			'status'                => array(
				'required'          => false,
				'type'              => 'string',
				'default'           => 'publish',
				'sanitize_callback' => 'sanitize_key',
			),
			'stripePaymentIntentId' => array(
				'required'          => true,
				'type'              => 'string',
				'sanitize_callback' => 'sanitize_text_field',
			),
			'stripeChargeId'        => array(
				'required'          => false,
				'type'              => 'string',
				'default'           => '',
				'sanitize_callback' => 'sanitize_text_field',
			),
			'stripeTransactionId'   => array(
				'required'          => false,
				'type'              => 'string',
				'default'           => '',
				'sanitize_callback' => 'sanitize_text_field',
			),
			'formId'                => array(
				'required'          => false,
				'type'              => array( 'integer', 'null' ),
				'default'           => null,
				'sanitize_callback' => function ( $value ) {
					return null === $value ? null : absint( $value );
				},
			),
			'date'                  => array(
				'required'          => false,
				'type'              => 'string',
				'default'           => '',
				'sanitize_callback' => 'sanitize_text_field',
			),
		);
	}

	/**
	 * Reshape WP_Error responses from our namespace into the contract's error envelope.
	 *
	 * @param WP_REST_Response|WP_Error $response Response so far.
	 * @param array                     $handler  Route handler config (unused).
	 * @param WP_REST_Request           $request  Request.
	 * @return WP_REST_Response|WP_Error
	 */
	public function format_error_response( $response, $handler, $request ) {
		if ( ! is_wp_error( $response ) ) {
			return $response;
		}

		if ( 0 !== strpos( $request->get_route(), '/' . GSTB_REST_NAMESPACE . '/' ) ) {
			return $response;
		}

		$error_data = $response->get_error_data();
		$status     = ( is_array( $error_data ) && ! empty( $error_data['status'] ) ) ? (int) $error_data['status'] : 500;

		$error = array(
			'code'    => $response->get_error_code(),
			'message' => $response->get_error_message(),
		);

		if ( is_array( $error_data ) && ! empty( $error_data['details'] ) ) {
			$error['details'] = $error_data['details'];
		}

		return new WP_REST_Response(
			array(
				'success' => false,
				'error'   => $error,
			),
			$status
		);
	}
}
