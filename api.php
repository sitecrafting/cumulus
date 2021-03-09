<?php

/**
 * The public Cumulus API.
 *
 * @copyright 2021 SiteCrafting, Inc.
 * @author    Coby Tamayo <ctamayo@sitecrafting.com>
 */

namespace SiteCrafting\Cumulus;

use Cloudinary\Api\Exception\ApiError;
use Cloudinary\Api\Upload\UploadApi;
use WP_Post;


/**
 * Get the current Cumulus settings, taking constants and environment variables
 * into account as described here:
 *
 * https://github.com/sitecrafting/cumulus/#managing-cloudinary-settings
 * @return array an array with (at least) the following keys:
 * - cloud_name
 * - api_key
 * - api_secret
 */
function settings() : array {
  return apply_filters('cumulus/settings', []);
}

/**
 * Get the currently configured Cloudinary Cloud Name setting.
 *
 * @return string
 */
function cloud_name() : string {
  return apply_filters('cumulus/settings', [])['cloud_name'] ?? '';
}

/**
 * Get the currently configured Cloudinary API Key setting.
 *
 * @return string
 */
function api_key() : string {
  return apply_filters('cumulus/settings', [])['api_key'] ?? '';
}

/**
 * Get the currently configured Cloudinary API Secret setting.
 *
 * @return string
 */
function api_secret() : string {
  return apply_filters('cumulus/settings', [])['api_secret'] ?? '';
}

/**
 * Get the currently configured Cloudinary Folder setting.
 *
 * @return string
 */
function folder() : string {
  return apply_filters('cumulus/settings', [])['folder'] ?? '';
}

/**
 * Whether Cumulus should upload the given attachment to Cloudinary,
 * based on current settings.
 *
 * @param int $id the attachment ID to check
 */
function should_upload(int $id) : bool {
  $path = get_attached_file($id);
  if (!$path) {
    return false;
  }

  $supportedTypes = apply_filters(
    'cumulus/mime_types_to_upload',
    array_values(get_allowed_mime_types())
  );

  return in_array(mime_content_type($path), $supportedTypes);
}

/**
 * Compute the default (auto-scaled) URL of an attachment for the given size
 *
 * @param string $cloud the cloud name of your Cloudinary account
 * @param array $size the WP image size (array with width/height keys)
 * @param array $img the uploaded image as returned from the Cloudinary REST
 * API.
 * @return string the computed URL
 */
function default_url(string $cloud, array $size, array $img) : string {
  $width  = $size['width'] ?? null;
  $height = $size['height'] ?? null;

  $resize = implode(',', array_filter([
    ($width ? "w_{$width}" : ''),
    ($height ? "h_{$height}" : ''),
    'c_lfill',
  ]));

  return sprintf(
    'https://res.cloudinary.com/%s/image/upload/%s/%s.%s',
    $cloud,
    $resize,
    $img['public_id'],
    $img['format']
  );
}

/**
 * Compute a Retina URL of an attachment for the given size, at a given
 * device-pixel ratio (DPR)
 *
 * @param string $cloud the cloud name of your Cloudinary account
 * @param array $size the WP image size (array with width/height keys)
 * @param array $img the uploaded image as returned from the Cloudinary REST
 * API.
 * @param int $ratio
 * @return string the computed URL
 */
function retina_url(string $cloud, array $size, array $img, int $ratio) : string {
  if ($ratio === 1) {
    return default_url($cloud, $size, $img);
  }

  $width  = $size['width'] ?? null;
  $height = $size['height'] ?? null;

  $dpr = sprintf('dpr_%d', $ratio);

  $resize = implode(',', array_filter([
    ($width ? "w_{$width}" : ''),
    ($height ? "h_{$height}" : ''),
    'c_lfill',
    $dpr,
  ]));

  return sprintf(
    'https://res.cloudinary.com/%s/image/upload/%s/%s.%s',
    $cloud,
    $resize,
    $img['public_id'],
    $img['format']
  );
}

function retina_srcset($img, string $size) : string {
  if (is_int($img)) {
    $img = get_post_meta($img, 'cumulus_image', true) ?: [];
  } elseif ($img instanceof WP_Post) {
    $img = get_post_meta($img->ID, 'cumulus_image', true) ?: [];
  }

  if (!is_array($img) || empty($img['cloudinary_data'])) {
    return '';
  }

  $img_data = $img['cloudinary_data'];

  $dimensions = sizes()[$size] ?? null;
  if (empty($dimensions)) {
    return '';
  }

  $cloud = cloud_name();

  $srcset_sizes = array_map(
    function($ratio) use ($cloud, $dimensions, $img_data) {
      return sprintf(
        '%s %dx',
        retina_url($cloud, $dimensions, $img_data, $ratio),
        $ratio
      );
    },
    apply_filters('cumulus/retina_dprs', [1, 2])
  );

  return implode(',', $srcset_sizes);
}

/**
 * Get the image sizes that are relevant to Cumulus, as an array keyed by
 * size name (e.g. "thumbnail"); values are arrays as returned by
 * wp_get_registered_image_subsizes().
 *
 * @return array
 */
function sizes() : array {
  $sizes = wp_get_registered_image_subsizes();
  return array_intersect_key(
    $sizes,
    array_flip(apply_filters('cumulus/sizes', array_keys($sizes)))
  );
}

/**
 * Save a Cloudinary upload result to a specific attachment
 *
 * @param int $id the attachment ID
 * @param array $result the Cloudinary upload result from the REST API, as an
 * array
 */
function save_uploaded(int $id, array $result) : void {
  if (!$result) {
    return;
  }

  $registeredSizes = sizes();

  $urlsBySize = array_reduce(array_keys($registeredSizes),
    function($sizes, $size) use ($registeredSizes, $result) {
      // Compute the scale (lfill) URL for this size.
      $url = default_url(cloud_name(), $registeredSizes[$size], $result);

      $url = apply_filters("cumulus/crop_url/$size", $url, [
        'size'       => $size,
        'dimensions' => $registeredSizes[$size],
        'cloud_name' => cloud_name(),
        'response'   => $result,
      ]);

      return array_merge($sizes, [
        $size => $url,
      ]);
    }, []);

  // Ensure we also serve the full URL from Cloudinary
  $urlsBySize['full'] = $result['secure_url'];

  $paramsBySize = array_reduce(array_keys($registeredSizes),
    function($sizes, $size) use ($registeredSizes) {
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

/**
 * Upload an attachment to Cloudinary by its ID
 *
 * @param int $id the ID of the attachment post to upload
 */
function upload_attachment(int $id) : void {
  $path = get_attached_file($id);
  if (!$path) {
    return;
  }

  static $uploader;
  $result = [];

  try {
    $uploader = $uploader ?? new UploadApi();
    $result = $uploader->upload($path, apply_filters('cumulus/upload_options', [
      'public_id' => basename($path),
      'folder'    => folder(),
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

  save_uploaded($id, (array) $result);
}
