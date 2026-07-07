<?php
/**
 * Plugin Name:       GiveWP Stripe Terminal Bridge
 * Plugin URI:        https://example.org/donation-terminal
 * Description:       REST bridge that lets the Donation Terminal Node.js backend create completed GiveWP donations after a Stripe Terminal (card-present) payment succeeds. Exposes donation-terminal/v1 REST routes secured by a shared API key.
 * Version:           1.0.0
 * Requires at least: 5.6
 * Requires PHP:      7.2
 * Author:            Donation Terminal Project
 * License:           GPL v2 or later
 * License URI:       https://www.gnu.org/licenses/gpl-2.0.html
 * Text Domain:       givewp-stripe-terminal-bridge
 * Domain Path:       /languages
 *
 * Requires Plugins:  give
 */

// Exit if accessed directly.
if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * Core plugin constants.
 */
define( 'GSTB_VERSION', '1.0.0' );
define( 'GSTB_PLUGIN_FILE', __FILE__ );
define( 'GSTB_PLUGIN_DIR', plugin_dir_path( __FILE__ ) );
define( 'GSTB_PLUGIN_URL', plugin_dir_url( __FILE__ ) );
define( 'GSTB_PLUGIN_BASENAME', plugin_basename( __FILE__ ) );

/**
 * Option names used in wp_options. Kept as constants so every file agrees on the key.
 */
define( 'GSTB_OPTION_API_KEY', 'givewp_stripe_terminal_api_key' );
define( 'GSTB_OPTION_DEFAULT_FORM_ID', 'givewp_stripe_terminal_default_form_id' );

/**
 * REST namespace, matches docs/API_CONTRACT.md section 4 exactly.
 */
define( 'GSTB_REST_NAMESPACE', 'donation-terminal/v1' );

/**
 * Check whether GiveWP is active and usable.
 *
 * GiveWP's core plugin class is `Give`; the `Give()` accessor function is only defined
 * once GiveWP has bootstrapped. We check both so we behave correctly regardless of load order.
 *
 * @return bool
 */
function gstb_is_givewp_active() {
	return class_exists( 'Give' ) && function_exists( 'give_insert_payment' );
}

/**
 * Activation hook: nothing GiveWP-specific has to exist yet (activation can happen before
 * GiveWP loads), but we do make sure an API key exists so the settings page has something
 * sensible to show on first visit.
 */
function gstb_activate_plugin() {
	if ( false === get_option( GSTB_OPTION_API_KEY, false ) ) {
		update_option( GSTB_OPTION_API_KEY, gstb_generate_api_key(), false );
	}
}
register_activation_hook( GSTB_PLUGIN_FILE, 'gstb_activate_plugin' );

/**
 * Deactivation hook. We intentionally do NOT delete the stored API key or default form ID on
 * deactivation -- only on uninstall would that be appropriate, and this plugin has no
 * uninstall.php by design so a reactivation (e.g. after a temporary plugin conflict) doesn't
 * force the Node backend to be reconfigured with a new key.
 */
function gstb_deactivate_plugin() {
	flush_rewrite_rules();
}
register_deactivation_hook( GSTB_PLUGIN_FILE, 'gstb_deactivate_plugin' );

/**
 * Generate a new random API key.
 *
 * @return string
 */
function gstb_generate_api_key() {
	if ( function_exists( 'wp_generate_password' ) ) {
		return wp_generate_password( 48, false, false );
	}

	return substr( bin2hex( random_bytes( 32 ) ), 0, 48 );
}

/**
 * Show an admin notice when GiveWP is missing/inactive. REST routes are not registered in
 * this case (see includes/class-rest-controller.php).
 */
function gstb_givewp_missing_notice() {
	if ( ! current_user_can( 'manage_options' ) ) {
		return;
	}
	?>
	<div class="notice notice-error">
		<p>
			<?php
			echo wp_kses_post(
				sprintf(
					/* translators: %s: plugin name */
					__( '%s requires GiveWP to be installed and active. The Donation Terminal REST API has NOT been registered until GiveWP is active.', 'givewp-stripe-terminal-bridge' ),
					'<strong>GiveWP Stripe Terminal Bridge</strong>'
				)
			);
			?>
		</p>
	</div>
	<?php
}

/**
 * Bootstrap the plugin once all plugins have loaded, so we can reliably detect GiveWP.
 */
function gstb_bootstrap() {
	if ( ! gstb_is_givewp_active() ) {
		add_action( 'admin_notices', 'gstb_givewp_missing_notice' );
		return;
	}

	require_once GSTB_PLUGIN_DIR . 'includes/class-donation-creator.php';
	require_once GSTB_PLUGIN_DIR . 'includes/class-rest-controller.php';

	GSTB_REST_Controller::instance();

	// Register the "stripe_terminal" gateway label so GiveWP's admin UI shows a friendly
	// name instead of "Unknown Gateway" for donations created by this plugin. This is
	// display-only; no checkout gateway class is implemented because payment already
	// happened externally via the Stripe Terminal SDK.
	add_filter( 'give_payment_gateways', 'gstb_register_gateway_label' );
}
add_action( 'plugins_loaded', 'gstb_bootstrap', 20 );

/**
 * Register the stripe_terminal gateway label with GiveWP.
 *
 * @param array $gateways Existing gateways keyed by gateway ID.
 * @return array
 */
function gstb_register_gateway_label( $gateways ) {
	$gateways['stripe_terminal'] = array(
		'admin_label'    => __( 'Stripe Terminal (In Person)', 'givewp-stripe-terminal-bridge' ),
		'checkout_label' => __( 'Stripe Terminal (In Person)', 'givewp-stripe-terminal-bridge' ),
	);

	return $gateways;
}

/**
 * Admin-only includes (settings page).
 */
if ( is_admin() ) {
	require_once GSTB_PLUGIN_DIR . 'admin/class-settings-page.php';
	add_action( 'plugins_loaded', array( 'GSTB_Settings_Page', 'init' ) );
}
