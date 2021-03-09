<?php

/**
 * WP-CLI integration
 *
 * @copyright 2021 SiteCrafting, Inc.
 * @author    Coby Tamayo <ctamayo@sitecrafting.com>
 */

namespace SiteCrafting\Cumulus\WpCli;

use WP_CLI;
use WP_CLI\Utils;
use WP_Post;
use WP_Query;

use SiteCrafting\Cumulus;

/**
 * Upload every Media Library item without a cloudinary_id meta entry to
 * Cloudinary. Note that this honors the cumulus/mime_types_to_upload filter,
 * so only items with accepted MIME types will be uploaded.
 *
 * ## OPTIONS
 *
 * [--count=<count>]
 * : Limit the number of attachments uploaded.
 * ---
 * default: -1
 * ---
 *
 * [--force]
 * : Re-upload items even if they have a cloudinary_id.
 * ---
 * default: false
 * ---
 *
 * [--dry-run]
 * : Only list items that would be uploaded; do not perform actual uploads.
 * ---
 * default: false
 * ---
 *
 * [--porcelain]
 * : Print only IDs (for machine-readable output)
 * ---
 * default: false
 * ---
 *
 * [--summarize]
 * : Print a count of uploaded files instead of each individual ID
 * ---
 * default: false
 * ---
 *
 * ## EXAMPLES
 *
 *     wp cumulus bulk-upload
 *     wp cumulus bulk-upload --dry-run
 *     wp cumulus bulk-upload --count=10
 *
 * @when after_wp_load
 */
function bulk_upload(array $_args, array $opts = []) {
  $count     = (int) Utils\get_flag_value($opts, 'count', -1);
  $force     = Utils\get_flag_value($opts, 'force', false);
  $dry_run   = Utils\get_flag_value($opts, 'dry-run', false);
  $porcelain = Utils\get_flag_value($opts, 'porcelain', false);
  $summarize = Utils\get_flag_value($opts, 'summarize', false);

  $ids = array_filter(get_bulk_upload_ids(), function($id) use ($force) {
    return check_should_upload($id, $force);
  });

  if ($count > -1 && $count < count($ids)) {
    WP_CLI::debug(sprintf(
      'Decreasing attachment count from %d to %d',
      count($ids),
      $count
    ));
    $ids = array_slice($ids, 0, $count);
  }

  foreach ($ids as $id) {
    if (!$dry_run) {
      Cumulus\upload_attachment($id);
    }

    if ($porcelain) {
      WP_CLI::log($id);
    } elseif (!$summarize) {
      WP_CLI::success(sprintf('Uploaded attachment %d', $id));
    }
  }

  if ($summarize) {
    WP_CLI::success(sprintf('Uploaded %d attachments', count($ids)));
  }
}

/**
 * Get attachment IDs to upload.
 *
 * @internal
 */
function get_bulk_upload_ids() : array {
  $query = new WP_Query([
    'post_type'      => 'attachment',
    'post_status'    => 'any',
    'posts_per_page' => -1,
    'fields'         => 'ids',
  ]);

  return $query->posts ?: [];
}

/**
 * Check whether the given attachment $id should be uploaded, honoring the
 * $force flag. Outputs debug info to stderr.
 *
 * @internal
 */
function check_should_upload(int $id, bool $force) : bool {
  if (!Cumulus\should_upload($id)) {
    WP_CLI::debug(sprintf(
      'Skipping upload of attachment %d due to MIME type',
      $id
    ));
    return false;
  }

  // Has this attachment been uploaded to Cloudinary already?
  $cloud_data = get_post_meta($id, 'cumulus_image', true);
  if ($cloud_data && !$force) {
    WP_CLI::debug(sprintf(
      'Attachment %d was already uploaded to Cloudinary.',
      $id
    ));
    return false;
  } elseif ($cloud_data) {
    WP_CLI::debug(sprintf(
      'Attachment %d will be re-uploaded.',
      $id
    ));
  }

  return true;
}
