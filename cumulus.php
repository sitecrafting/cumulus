<?php

/**
 * Plugin Name: Cumulus: Cloudinary Image Crops
 * Description: Serve your custom image crops from Cloudinary Image CDN
 * Version: 0.1.0
 * Author: SiteCrafting, Inc.
 * Author URI: https://www.sitecrafting.com
 *
 * TODO (MC3-186):
 * - set crop on load
 * - Flip horizontal/vertical transforms
 * - add click listeners to WP attachment arrow buttons
 * - hook for MIME type
 * - hook for crop sizes to manage
 * - phpunit tests
 * - WHERE DO THE EXTRA Y PIXELS COME FROM?? (later)
 * - SVG icons (nice-to-have)
 * - Push all AJAX requests down into CLJS (nice-to-have)
 * - nicer transitions between crop sizes (nice-to-have)
 * - Nicer styles (nice-to-have)
 */


require_once __DIR__ . '/vendor/autoload.php';

use Cloudinary\Api\Exception\ApiError;
use Cloudinary\Api\Upload\UploadApi;
use Cloudinary\Configuration\Configuration;


define('CUMULUS_JS_DIR', __DIR__ . '/dist/js/');
define('CUMULUS_CSS_DIR', __DIR__ . '/dist/css/');
define('CUMULUS_VIEW_DIR', __DIR__ . '/views/');


/**
 * Configure global Cloudinary settings.
 *
 * TODO implement a settings panel where admins can do this on their own.
 */
add_action('init', function() {
  $cloud  = get_option('cumulus_cloud_name');
  $key    = get_option('cumulus_api_key');
  $secret = get_option('cumulus_api_secret');

  Configuration::instance([
    'account'      => [
      'cloud_name' => $cloud,
      'api_key'    => $key,
      'api_secret' => $secret,
    ],
    'url'          => [
      'secure'     => true,
    ],
  ]);
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
  static $sizesById;
  if (!isset($sizesById)) {
    $sizesById = [];
  }

  if (!isset($sizesById[$id])) {
    // Remember sizes for this attachment, if found.
    $sizesById[$id] = get_post_meta($id, 'cumulus_image', true) ?: [];
  }

  // Render the customized crop src, if there is one for this size.
  $src[0] = $sizesById[$id]['urls_by_size'][$size] ?? $src[0];
  // TODO remove
  $src[0] = $sizesById[$id]['sizes'][$size] ?? $src[0];

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
        return [];
      }

      $cloudinaryData = $meta['cloudinary_data'];

      return [
        'attachment_id'  => $id,
        'version'        => $cloudinaryData['version'],
        // NOTE: public_id also includes the folder to which the image was uploaded, if any.
        'filename'       => $cloudinaryData['public_id'] . '.' . $cloudinaryData['format'],
        'full_url'       => $meta['cloudinary_data']['secure_url'],
        'full_width'     => $meta['cloudinary_data']['width'],
        'full_height'    => $meta['cloudinary_data']['height'],
        // `params_by_size.my_image_size` contains the saved transform params for a given crop
        'params_by_size' => $meta['params_by_size'] ?? [],
        // `detail.sizes` is where the Cloudinary URLs will show up
        'detail'         => $meta,
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
      $id     = $req->get_param('id');
      $meta   = get_post_meta($id, 'cumulus_image', true) ?: [];
      $params = $req->get_json_params();

      update_post_meta($id, 'cumulus_image', array_merge($meta, [
        'params_by_size' => $params,
      ]));

      return [
        'success'        => true,
        'params_by_size' => $params,
      ];
    },
  ]);
});


/**
 * Upload to Cloudinary backend when a new Attachment is added.
 */
add_action('add_attachment', function(int $id) {
  $path = get_attached_file($id);
  if (!$path) {
    return;
  }

  $supportedTypes = apply_filters(
    'cumulus/mime_types_to_upload',
    array_values(get_allowed_mime_types())
  );
  if (!in_array(mime_content_type($path), $supportedTypes)) {
    // Not a MIME type the user wants to support
    return;
  }

  $uploadFolder = get_option('cumulus_upload_folder') ?: null;

  static $uploader;
  $result = [];

  try {
    $uploader = $uploader ?? new UploadApi();
    $result = $uploader->upload($path, apply_filters('cumulus/upload_options', [
      'public_id' => basename($path),
      'folder'    => $uploadFolder,
    ]));
  } catch (ApiError $err) {
    do_action('cumulus/api_error', $err->getMessage(), [
      'exception' => $err,
      'context'   => 'upload',
    ]);
  } catch (InvalidArgumentException $err) {
    do_action('cumulus/api_error', $err->getMessage(), [
      'exception' => $err,
      'context'   => 'upload',
    ]);
  }

  if ($result) {
    $registeredSizes = wp_get_registered_image_subsizes();

    $cloud = get_option('cumulus_cloud_name');

    // TODO farm most of this out to a filter
    $urlsBySize = array_reduce(array_keys($registeredSizes), function($sizes, $size) use ($registeredSizes, $result, $cloud) {
      // Avoid crops of zero width or height.
      $width  = $registeredSizes[$size]['width'];
      $height = $registeredSizes[$size]['height'];
      $dimensionParams = implode(',', array_filter([
        ($width ? "w_{$width}" : ''),
        ($height ? "h_{$height}" : ''),
        'c_lfill',
      ]));

      // Compute the scale (lfill) URL for this size.
      $url = sprintf(
        'https://res.cloudinary.com/%s/image/upload/%s/%s.%s',
        $cloud,
        $dimensionParams,
        $result['public_id'], // filename
        $result['format'] // extension
      );

      $url = apply_filters("cumulus/crop_url/$size", $url, [
        'size'       => $size,
        'dimensions' => $registeredSizes[$size],
        'cloud_name' => $cloud,
        'response'   => $result,
      ]);

      return array_merge($sizes, [
        $size => $url,
      ]);
    }, []);

    $paramsBySize = array_reduce(array_keys($registeredSizes), function($sizes, $size) use ($registeredSizes) {
      return array_merge($sizes, [
        $size => [
          'edit_mode' => 'scale',
        ],
      ]);
    }, []);

    update_post_meta($id, 'cumulus_image', [
      'cloudinary_id'   => $result['public_id'],
      'urls_by_size'    => $urlsBySize,
      'params_by_size'  => $paramsBySize,
      'cloudinary_data' => $result,
    ]);
  }
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

  $folder = get_option('cumulus_folder');
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
    'cloud'   => get_option('cumulus_cloud_name'),
    'sizes'   => $sizes,
    // TODO remove in favor of "cloud"
    'bucket'  => get_option('cumulus_cloud_name'),
    'WP_DEBUG' => WP_DEBUG,
  ]);

  $css = CUMULUS_JS_DIR . 'main.js';
  wp_enqueue_style('cropper-css', plugin_dir_url(__FILE__) . 'dist/css/react-image-crop.css', [], '1.5.7');
  wp_enqueue_style('cumulus-css', plugin_dir_url(__FILE__) . 'dist/css/main.css', ['cropper-css'], filemtime($css));
});
