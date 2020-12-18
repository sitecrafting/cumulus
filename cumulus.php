<?php

/**
 * Plugin Name: Cumulus: Cloudinary Image Crops
 * Description: Serve your custom image crops from Cloudinary Image CDN
 * Version: 0.0.2
 * Author: SiteCrafting, Inc.
 * Author URI: https://www.sitecrafting.com
 *
 * TODO (MC3-186):
 * - Compute default crop on load?
 * - Flip horizontal/vertical transforms
 * - add click listeners to WP attachment arrow buttons
 * - phpunit tests (future)
 * - SVG icons (nice-to-have)
 * - Push all AJAX requests down into CLJS (nice-to-have)
 * - nicer transitions between crop sizes (nice-to-have)
 * - Nicer styles (nice-to-have)
 */


require_once __DIR__ . '/vendor/autoload.php';

require_once __DIR__ . '/api.php';

use Cloudinary\Configuration\Configuration;

use SiteCrafting\Cumulus;


define('CUMULUS_JS_DIR', __DIR__ . '/dist/js/');
define('CUMULUS_CSS_DIR', __DIR__ . '/dist/css/');
define('CUMULUS_VIEW_DIR', __DIR__ . '/views/');


/**
 * Configure global Cloudinary settings.
 *
 * TODO implement a settings panel where admins can do this on their own.
 */
add_action('init', function() {
  $settings = apply_filters('cumulus/settings', [
    'cloud_name' => get_option('cumulus_cloud_name'),
    'api_key'    => get_option('cumulus_api_key'),
    'api_secret' => get_option('cumulus_api_secret'),
  ]);

  Configuration::instance([
    'account'      => [
      'cloud_name' => Cumulus\cloud_name(),
      'api_key'    => $settings['api_key'],
      'api_secret' => $settings['api_secret'],
    ],
    'url'          => [
      'secure'     => true,
    ],
  ]);
});


/**
 * Configure fallbacks for global settings.
 * If option does not exist in the database, look for a constant or an
 * environment variable, in that order.
 */
add_filter('cumulus/settings', function($settings = []) {
  $settings = wp_parse_args(
    $settings,
    apply_filters('cumulus/settings/defaults', [])
  );

  if (empty($settings['cloud_name']) && defined('CUMULUS_CLOUD_NAME')) {
    $settings['cloud_name'] = CUMULUS_CLOUD_NAME;
  }
  if (empty($settings['cloud_name']) && !empty($_ENV['CUMULUS_CLOUD_NAME'])) {
    $settings['cloud_name'] = $_ENV['CUMULUS_CLOUD_NAME'];
  }

  if (empty($settings['api_key']) && defined('CUMULUS_API_KEY')) {
    $settings['api_key'] = CUMULUS_API_KEY;
  }
  if (empty($settings['api_key']) && !empty($_ENV['CUMULUS_API_KEY'])) {
    $settings['api_key'] = $_ENV['CUMULUS_API_KEY'];
  }

  if (empty($settings['api_secret']) && defined('CUMULUS_API_SECRET')) {
    $settings['api_secret'] = CUMULUS_API_SECRET;
  }
  if (empty($settings['api_secret']) && !empty($_ENV['CUMULUS_API_SECRET'])) {
    $settings['api_secret'] = $_ENV['CUMULUS_API_SECRET'];
  }

  if (empty($settings['folder']) && defined('CUMULUS_FOLDER')) {
    $settings['folder'] = CUMULUS_API_SECRET;
  }
  if (empty($settings['folder']) && !empty($_ENV['CUMULUS_FOLDER'])) {
    $settings['folder'] = $_ENV['CUMULUS_FOLDER'];
  }

  return $settings;
});

add_filter('cumulus/settings/defaults', function() {
  // Load Cumulus options from the database once and cache them for the
  // request lifetime.
  static $settings;
  $settings = $settings ?? [
    'cloud_name' => get_option('cumulus_cloud_name'),
    'api_key'    => get_option('cumulus_api_key'),
    'api_secret' => get_option('cumulus_api_secret'),
  ];

  return $settings;
});


/**
 * The main event. Hook into the attachment src for a given size and
 * return the Cloudinary-rendered cropped/scaled image URL instead.
 *
 * Look in e.g. cumulus_image['sizes']['my_image_size'] for the Cloudinary
 * URL to serve, where `cumulus_image` is the array stored in the post
 * meta field of the same name, and "my_image_size" is any registered
 * image size.
 *
 * @see https://developer.wordpress.org/reference/functions/add_image_size/
 */
add_filter('wp_get_attachment_image_src', function($src, $id, $size) {
  // Persist a mapping of attachment IDs to crop URLs, to save
  // database calls
  static $configsById;
  if (!isset($configsById)) {
    $configsById = [];
  }

  if (!isset($configsById[$id])) {
    // Remember sizes for this attachment, if found.
    $configsById[$id] = get_post_meta($id, 'cumulus_image', true) ?: [];
  }

  // Render the customized crop src, if there is one for this size.
  $src[0] = $configsById[$id]['urls_by_size'][$size] ?? $src[0];
  // TODO remove
  $src[0] = $configsById[$id]['sizes'][$size] ?? $src[0];

  return $src;
}, 10, 3);


add_action('rest_api_init', function() {
  /**
   * When the Attachment Detail modal opens, we need to fetch additional
   * data for the attachment being cropped. To do that, we call this endpoint.
   */
  register_rest_route('cumulus/v1', '/attachment/(?P<id>\d++)', [
    'methods'  => 'GET',
    'callback' => function($req) {
      $id = $req->get_param('id');

      $meta = get_post_meta($id, 'cumulus_image', true);

      if (!$meta) {
        return [
          'attachment_id' => $id,
          'uploaded'      => false,
        ];
      }

      $cloudinaryData = $meta['cloudinary_data'];

      return [
        'attachment_id'  => $id,
        'uploaded'       => true,
        'nonce'          => wp_create_nonce("cumulus_attachment_$id"),
        'version'        => $cloudinaryData['version'],
        // NOTE: public_id also includes the folder to which the image was uploaded, if any.
        'filename'       => $cloudinaryData['public_id'] . '.' . $cloudinaryData['format'],
        'full_url'       => $meta['cloudinary_data']['secure_url'],
        'full_width'     => $meta['cloudinary_data']['width'],
        'full_height'    => $meta['cloudinary_data']['height'],
        // `params_by_size.my_image_size` contains the saved transform params for a given crop
        'params_by_size' => $meta['params_by_size'] ?? [],
        'urls_by_size'   => $meta['urls_by_size'] ?? [],
        // NOTE: global sizes come through via wp_localize_script
      ];
    },
  ]);

  /**
   * Saving changes in the Customize Image Crops modal sends a POST request
   * to this endpoint.
   */
  register_rest_route('cumulus/v1', '/attachment/(?P<id>\d++)', [
    'methods'  => 'POST',
    'callback' => function($req) {
      $id    = $req->get_param('id');
      $nonce = $req->get_header('x-nonce');

      if (!wp_verify_nonce($nonce, "cumulus_attachment_$id")) {
        return [
          'success' => false,
        ];
      }

      $meta   = get_post_meta($id, 'cumulus_image', true) ?: [];
      $config = array_merge($meta, $req->get_json_params());

      update_post_meta($id, 'cumulus_image', array_merge($meta, $config));

      return [
        'success'        => true,
        'cumulus_config' => $config,
      ];
    },
  ]);
});


/**
 * Upload to Cloudinary backend when a new Attachment is added.
 */
add_action('add_attachment', function(int $id) {
  if (!Cumulus\should_upload($id)) {
    return;
  }

  Cumulus\upload_attachment($id);
}, 10);

/**
 * Dynamically set Cloudinary upload params when a new Attachment is uploaded.
 */
add_filter('cumulus/upload_options', function(array $options) {
  // TODO Deal with extensions in a less hacky way
  $extensions = ['/\.jpeg$/', '/\.jpg$/', '/\.png$/'];
  foreach ($extensions as $pattern) {
    $options['public_id'] = preg_replace($pattern, '', $options['public_id']);
  }

  $folder = Cumulus\folder();
  if ($folder) {
    $options['public_id'] = $folder . '/' . $options['public_id'];
  }

  return $options;
});

/**
 * Generic filter for rendering content partials.
 */
add_action('cumulus/render', function($file, $data) {
  ob_start();
  include CUMULUS_VIEW_DIR . $file;
  return ob_get_clean();
}, 10, 2);

/**
 * Hook into the WordPress attachment media modal and inject the code we
 * need on the frontend to bootstrap the Crop UI.
 */
add_action('print_media_templates', function() {
  echo apply_filters('cumulus/render', 'media-templates.php', [
    'customize_crops' => __('Customize Image Crops'),
    'back_to_details' => __('Back to Attachment Details'),
  ]);
});

/**
 * Enqueue Crop UI JS.
 */
add_action('admin_enqueue_scripts', function() {
  $file = CUMULUS_JS_DIR . 'main.js';
  wp_enqueue_script(
    'cumulus-crop-ui-js',
    plugin_dir_url(__FILE__) . 'dist/js/main.js',
    [],
    filemtime($file)
  );

  $registeredSizes = wp_get_registered_image_subsizes();
  $supportedSizes  = array_intersect_key(
    $registeredSizes,
    array_flip(apply_filters('cumulus/sizes', array_keys($registeredSizes)))
  );

  $sizes = array_map(function($key, $dimensions) {
    return [
      'size_name' => $key,
      'width'     => $dimensions['width'],
      'height'    => $dimensions['height'],
    ];
  }, array_keys($supportedSizes), array_values($supportedSizes));

  wp_localize_script('cumulus-crop-ui-js', 'CUMULUS_CONFIG', [
    // TODO remove in favor of "cloud"
    'bucket'   => Cumulus\cloud_name(),
    'cloud'    => Cumulus\cloud_name(),
    'sizes'    => $sizes,
    'WP_DEBUG' => WP_DEBUG,
  ]);

  wp_enqueue_style('cropper-css', plugin_dir_url(__FILE__) . 'dist/css/react-image-crop.css', [], '1.5.7');

  $css = CUMULUS_CSS_DIR . 'main.css';
  wp_enqueue_style('cumulus-css', plugin_dir_url(__FILE__) . 'dist/css/main.css', ['cropper-css'], filemtime($css));
});
