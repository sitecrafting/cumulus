<?php

/**
 * Plugin Name: Cumulus: Cloudinary Image Crops
 * Description: Serve your custom image crops from Cloudinary Image CDN
 * Version: 0.1.0
 * Author: SiteCrafting, Inc.
 * Author URI: https://www.sitecrafting.com
 */


require_once __DIR__ . '/vendor/autoload.php';

use Cloudinary\Api\Exception\ApiError;
use Cloudinary\Api\Upload\UploadApi;
use Cloudinary\Configuration\Configuration;


define('CUMULUS_JS_DIR', __DIR__ . '/cljs/dist/');
define('CUMULUS_VIEW_DIR', __DIR__ . '/views/');


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
  $src[0] = $sizesById[$id]['sizes'][$size] ?? $src[0];

  return $src;
}, 10, 3);


add_action('add_attachment', function(int $id) {
  $path = get_attached_file($id);
  if (!$path) {
    return;
  }
  debug(wp_get_attachment_metadata($id));

  static $uploader;
  $uploader = $uploader ?? new UploadApi();

  try {
    $result = $uploader->upload($path, apply_filters('cumulus/upload_options', [
      'public_id' => basename($path),
    ]));
  } catch (ApiError $err) {
    do_action('cumulus/api_error', $err->getMessage(), [
      'exception' => $err,
      'context'   => 'upload',
    ]);
  }

  update_post_meta($id, 'cumulus_image', [
    'cloudinary_id' => $result['public_id'],
    'sizes'  => [
      'full' => $result['secure_url'],
      'home-hero' => $result['secure_url'],
    ],
    'cloudinary_data' => $result,
  ]);
}, 10);

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

add_action('cumulus/render', function($file, $data) {
  ob_start();
  include CUMULUS_VIEW_DIR . $file;
  return ob_get_clean();
}, 10, 2);

add_action('print_media_templates', function() {
  echo apply_filters('cumulus/render', 'media-templates.php', [
    'customize_crops' => __('Customize Image Crops'),
    'back_to_details' => __('Back to Attachment Details'),
  ]);
});

add_action('admin_enqueue_scripts', function() {
  wp_enqueue_script('cumulus-crop-ui-js', CUMULUS_JS_DIR . 'main.js');
});