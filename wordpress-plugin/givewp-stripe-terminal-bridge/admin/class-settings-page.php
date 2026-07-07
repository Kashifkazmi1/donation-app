<?php
/**
 * Settings > Donation Terminal admin page: API key management + default Give Form picker.
 *
 * @package GiveWP_Stripe_Terminal_Bridge
 */

if ( ! defined( 'ABSPATH' ) ) {
	exit;
}

/**
 * Class GSTB_Settings_Page
 */
class GSTB_Settings_Page {

	/**
	 * Nonce action/name used for all form submissions on this page.
	 */
	const NONCE_ACTION = 'gstb_settings_save';

	/**
	 * Slug the page is registered under.
	 */
	const PAGE_SLUG = 'givewp-stripe-terminal-bridge';

	/**
	 * Hook everything up. Called from the main plugin file on `plugins_loaded`.
	 */
	public static function init() {
		$page = new self();

		add_action( 'admin_menu', array( $page, 'register_page' ) );
		add_action( 'admin_init', array( $page, 'maybe_handle_post' ) );
	}

	/**
	 * Register the "Donation Terminal" item under Settings.
	 */
	public function register_page() {
		add_options_page(
			__( 'Donation Terminal', 'givewp-stripe-terminal-bridge' ),
			__( 'Donation Terminal', 'givewp-stripe-terminal-bridge' ),
			'manage_options',
			self::PAGE_SLUG,
			array( $this, 'render_page' )
		);
	}

	/**
	 * Handle form submissions (regenerate key, save key, save default form). Runs on
	 * admin_init so redirects can happen before any HTML is output.
	 */
	public function maybe_handle_post() {
		if ( empty( $_POST['gstb_action'] ) ) {
			return;
		}

		if ( ! current_user_can( 'manage_options' ) ) {
			wp_die( esc_html__( 'You do not have permission to do this.', 'givewp-stripe-terminal-bridge' ) );
		}

		check_admin_referer( self::NONCE_ACTION, 'gstb_nonce' );

		$action  = sanitize_key( wp_unslash( $_POST['gstb_action'] ) );
		$updated = false;

		switch ( $action ) {
			case 'regenerate_api_key':
				update_option( GSTB_OPTION_API_KEY, gstb_generate_api_key(), false );
				$updated = true;
				break;

			case 'save_api_key':
				if ( isset( $_POST['gstb_api_key'] ) ) {
					$key = sanitize_text_field( wp_unslash( $_POST['gstb_api_key'] ) );

					if ( '' !== $key ) {
						update_option( GSTB_OPTION_API_KEY, $key, false );
						$updated = true;
					}
				}
				break;

			case 'save_default_form':
				$form_id = isset( $_POST['gstb_default_form_id'] ) ? absint( wp_unslash( $_POST['gstb_default_form_id'] ) ) : 0;
				update_option( GSTB_OPTION_DEFAULT_FORM_ID, $form_id, false );
				$updated = true;
				break;
		}

		$redirect_args = array(
			'page'         => self::PAGE_SLUG,
			'gstb_updated' => $updated ? '1' : '0',
		);

		wp_safe_redirect( add_query_arg( $redirect_args, admin_url( 'options-general.php' ) ) );
		exit;
	}

	/**
	 * Render the settings page.
	 */
	public function render_page() {
		if ( ! current_user_can( 'manage_options' ) ) {
			return;
		}

		$api_key          = get_option( GSTB_OPTION_API_KEY, '' );
		$default_form_id  = (int) get_option( GSTB_OPTION_DEFAULT_FORM_ID, 0 );
		$givewp_active    = gstb_is_givewp_active();
		$rest_url         = rest_url( GSTB_REST_NAMESPACE . '/donations' );
		$forms            = $givewp_active ? get_posts(
			array(
				'post_type'      => 'give_forms',
				'post_status'    => 'publish',
				'posts_per_page' => -1,
				'orderby'        => 'title',
				'order'          => 'ASC',
			)
		) : array();
		?>
		<div class="wrap">
			<h1><?php esc_html_e( 'Donation Terminal', 'givewp-stripe-terminal-bridge' ); ?></h1>
			<p>
				<?php esc_html_e( 'Configure the shared API key and default Give Form used by the Donation Terminal Node.js backend when it creates GiveWP donations for in-person Stripe Terminal payments.', 'givewp-stripe-terminal-bridge' ); ?>
			</p>

			<?php if ( ! $givewp_active ) : ?>
				<div class="notice notice-error">
					<p><?php esc_html_e( 'GiveWP is not currently active. The REST API is disabled until GiveWP is active.', 'givewp-stripe-terminal-bridge' ); ?></p>
				</div>
			<?php endif; ?>

			<?php $updated_flag = isset( $_GET['gstb_updated'] ) ? sanitize_text_field( wp_unslash( $_GET['gstb_updated'] ) ) : ''; ?>
			<?php if ( '' !== $updated_flag ) : ?>
				<?php if ( '1' === $updated_flag ) : ?>
					<div class="notice notice-success is-dismissible">
						<p><?php esc_html_e( 'Settings saved.', 'givewp-stripe-terminal-bridge' ); ?></p>
					</div>
				<?php else : ?>
					<div class="notice notice-warning is-dismissible">
						<p><?php esc_html_e( 'Nothing was saved -- the submitted value was empty.', 'givewp-stripe-terminal-bridge' ); ?></p>
					</div>
				<?php endif; ?>
			<?php endif; ?>

			<h2><?php esc_html_e( 'API Key', 'givewp-stripe-terminal-bridge' ); ?></h2>
			<p><?php esc_html_e( 'The Node.js backend must send this exact value in the X-API-Key header on every request to the Donation Terminal REST API.', 'givewp-stripe-terminal-bridge' ); ?></p>

			<table class="form-table" role="presentation">
				<tr>
					<th scope="row"><label for="gstb-api-key-display"><?php esc_html_e( 'Current key', 'givewp-stripe-terminal-bridge' ); ?></label></th>
					<td>
						<input
							type="password"
							id="gstb-api-key-display"
							value="<?php echo esc_attr( $api_key ); ?>"
							readonly="readonly"
							class="regular-text"
							style="font-family:monospace; width:28em;"
						/>
						<button type="button" class="button" id="gstb-toggle-reveal"><?php esc_html_e( 'Reveal', 'givewp-stripe-terminal-bridge' ); ?></button>
						<button type="button" class="button" id="gstb-copy-key"><?php esc_html_e( 'Copy', 'givewp-stripe-terminal-bridge' ); ?></button>
						<p class="description"><?php esc_html_e( 'Stored in wp_options; never logged or transmitted anywhere except this page.', 'givewp-stripe-terminal-bridge' ); ?></p>
					</td>
				</tr>
			</table>

			<form method="post" style="margin-bottom:2em;">
				<?php wp_nonce_field( self::NONCE_ACTION, 'gstb_nonce' ); ?>
				<input type="hidden" name="gstb_action" value="regenerate_api_key" />
				<?php submit_button( __( 'Regenerate API Key', 'givewp-stripe-terminal-bridge' ), 'secondary', 'submit', false ); ?>
				<p class="description"><?php esc_html_e( 'Generates a brand new random key and immediately invalidates the old one. You will need to update the backend\'s GIVEWP_API_KEY value afterward.', 'givewp-stripe-terminal-bridge' ); ?></p>
			</form>

			<details style="margin-bottom:2em;">
				<summary style="cursor:pointer;"><?php esc_html_e( 'Set a specific API key manually', 'givewp-stripe-terminal-bridge' ); ?></summary>
				<form method="post" style="margin-top:1em;">
					<?php wp_nonce_field( self::NONCE_ACTION, 'gstb_nonce' ); ?>
					<input type="hidden" name="gstb_action" value="save_api_key" />
					<input type="text" name="gstb_api_key" class="regular-text" style="font-family:monospace; width:28em;" placeholder="<?php esc_attr_e( 'Paste an API key value', 'givewp-stripe-terminal-bridge' ); ?>" />
					<?php submit_button( __( 'Save Key', 'givewp-stripe-terminal-bridge' ), 'secondary', 'submit', false ); ?>
				</form>
			</details>

			<h2><?php esc_html_e( 'Default Give Form', 'givewp-stripe-terminal-bridge' ); ?></h2>
			<p><?php esc_html_e( 'Used when the incoming donation request does not specify a formId. If left unset, the earliest published Give Form is used instead.', 'givewp-stripe-terminal-bridge' ); ?></p>

			<form method="post">
				<?php wp_nonce_field( self::NONCE_ACTION, 'gstb_nonce' ); ?>
				<input type="hidden" name="gstb_action" value="save_default_form" />
				<table class="form-table" role="presentation">
					<tr>
						<th scope="row"><label for="gstb-default-form-id"><?php esc_html_e( 'Default form', 'givewp-stripe-terminal-bridge' ); ?></label></th>
						<td>
							<select name="gstb_default_form_id" id="gstb-default-form-id">
								<option value="0"><?php esc_html_e( '— None (use earliest published form) —', 'givewp-stripe-terminal-bridge' ); ?></option>
								<?php foreach ( $forms as $form ) : ?>
									<option value="<?php echo esc_attr( $form->ID ); ?>" <?php selected( $default_form_id, $form->ID ); ?>>
										<?php echo esc_html( $form->post_title ); ?> (#<?php echo esc_html( $form->ID ); ?>)
									</option>
								<?php endforeach; ?>
							</select>
							<?php if ( $givewp_active && empty( $forms ) ) : ?>
								<p class="description"><?php esc_html_e( 'No published Give Forms were found yet. Create at least one so a fallback form is available.', 'givewp-stripe-terminal-bridge' ); ?></p>
							<?php endif; ?>
						</td>
					</tr>
				</table>
				<?php submit_button( __( 'Save Default Form', 'givewp-stripe-terminal-bridge' ) ); ?>
			</form>

			<h2><?php esc_html_e( 'Backend configuration reference', 'givewp-stripe-terminal-bridge' ); ?></h2>
			<p><?php esc_html_e( 'Give these two values to whoever configures the Node.js backend:', 'givewp-stripe-terminal-bridge' ); ?></p>
			<table class="widefat" style="max-width:60em;">
				<tbody>
					<tr>
						<th style="width:16em;"><?php esc_html_e( 'GIVEWP_API_BASE_URL', 'givewp-stripe-terminal-bridge' ); ?></th>
						<td><code><?php echo esc_html( rest_url( GSTB_REST_NAMESPACE ) ); ?></code></td>
					</tr>
					<tr>
						<th><?php esc_html_e( 'GIVEWP_API_KEY', 'givewp-stripe-terminal-bridge' ); ?></th>
						<td><code><?php echo esc_html__( '(the value shown above, revealed)', 'givewp-stripe-terminal-bridge' ); ?></code></td>
					</tr>
					<tr>
						<th><?php esc_html_e( 'Full donations endpoint', 'givewp-stripe-terminal-bridge' ); ?></th>
						<td><code><?php echo esc_html( $rest_url ); ?></code></td>
					</tr>
				</tbody>
			</table>
		</div>
		<script>
		(function () {
			var input  = document.getElementById( 'gstb-api-key-display' );
			var toggle = document.getElementById( 'gstb-toggle-reveal' );
			var copy   = document.getElementById( 'gstb-copy-key' );

			if ( toggle && input ) {
				toggle.addEventListener( 'click', function () {
					var revealing = input.type === 'password';
					input.type = revealing ? 'text' : 'password';
					toggle.textContent = revealing ? <?php echo wp_json_encode( __( 'Hide', 'givewp-stripe-terminal-bridge' ) ); ?> : <?php echo wp_json_encode( __( 'Reveal', 'givewp-stripe-terminal-bridge' ) ); ?>;
				} );
			}

			if ( copy && input ) {
				copy.addEventListener( 'click', function () {
					var restoreType = input.type;
					input.type = 'text';
					input.select();
					input.setSelectionRange( 0, 99999 );

					try {
						if ( navigator.clipboard && navigator.clipboard.writeText ) {
							navigator.clipboard.writeText( input.value );
						} else {
							document.execCommand( 'copy' );
						}
						copy.textContent = <?php echo wp_json_encode( __( 'Copied!', 'givewp-stripe-terminal-bridge' ) ); ?>;
						setTimeout( function () {
							copy.textContent = <?php echo wp_json_encode( __( 'Copy', 'givewp-stripe-terminal-bridge' ) ); ?>;
						}, 1500 );
					} catch ( e ) {
						// Clipboard API unavailable; the value is at least selected for manual copy.
					}

					input.type = restoreType;
					window.getSelection().removeAllRanges();
				} );
			}
		})();
		</script>
		<?php
	}
}
