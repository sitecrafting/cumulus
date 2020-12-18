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


/**
 * Get the currently configured Cloudinary Cloud Name setting.
 */
function cloud_name() : string {
  return apply_filters('cumulus/settings', [])['cloud_name'] ?? '';
}

/**
 * Get the currently configured Cloudinary API Key setting.
 */
function api_key() : string {
  return apply_filters('cumulus/settings', [])['api_key'] ?? '';
}

/**
 * Get the currently configured Cloudinary API Secret setting.
 */
function api_secret() : string {
  return apply_filters('cumulus/settings', [])['api_secret'] ?? '';
}

/**
 * Get the currently configured Cloudinary Folder setting.
 */
function folder() : string {
  return apply_filters('cumulus/settings', [])['folder'] ?? '';
}

/**
 * Whether Cumulus should upload the given attachment to Cloudinary,
 * based on current settings.
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

  if ($result) {
    $result = (array) $result;
    $registeredSizes = wp_get_registered_image_subsizes();

    // TODO farm most of this out to a filter
    $urlsBySize = array_reduce(array_keys($registeredSizes), function($sizes, $size) use ($registeredSizes, $result) {
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
}