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

  $queried_ids = get_bulk_upload_ids();
  $ids         = [];

  foreach($queried_ids as $id) {
    if (check_should_upload($id, $force)) {
      $ids[] = $id;
    }
    if ($count > -1 && count($ids) >= $count) {
      // We got what we needed
      WP_CLI::debug(sprintf(
        'Decreasing attachment count from %d to %d',
        count($queried_ids),
        $count
      ));
      break;
    }
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
 * Regenerate Cloudinary URLs for every Media Library item with a cumulus_image
 * meta entry.
 *
 * ## OPTIONS
 *
 * [--count=<count>]
 * : Limit the number of attachments updated.
 * ---
 * default: -1
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
 *     # This is a handy way to count the attachments with Cloudinary URLs:
 *     wp cumulus bulk-upload --summarize --dry-run
 *
 * @when after_wp_load
 */
function regenerate(array $_args, array $opts) {
  $count     = (int) Utils\get_flag_value($opts, 'count', -1);
  $dry_run   = Utils\get_flag_value($opts, 'dry-run', false);
  $porcelain = Utils\get_flag_value($opts, 'porcelain', false);
  $summarize = Utils\get_flag_value($opts, 'summarize', false);

  $ids = get_regenerate_ids();

  if ($count > -1 && count($ids) >= $count) {
    // We got what we needed
    WP_CLI::debug(sprintf(
      'Decreasing attachment count from %d to %d',
      count($ids),
      $count
    ));
    $ids = array_slice($ids, 0, $count);
  }

  foreach ($ids as $id) {
    if (!$dry_run) {
      $meta = get_post_meta($id, 'cumulus_image', true);
      Cumulus\save_uploaded($id, $meta['cloudinary_data'] ?? []);
    }

    if ($porcelain) {
      WP_CLI::log($id);
    } elseif (!$summarize) {
      WP_CLI::success(sprintf('Regenerated URLs for attachment %d', $id));
    }
  }

  if ($summarize) {
    WP_CLI::success(sprintf('Regenerated URLs for %d attachments', count($ids)));
  }
}



/* HELPER FUNCTIONS */


/**
 * Get attachment IDs to regenerate URLs for.
 *
 * @internal
 */
function get_regenerate_ids() : array {
  $query = new WP_Query([
    'post_type'      => 'attachment',
    'post_status'    => 'any',
    'posts_per_page' => -1,
    'fields'         => 'ids',
    'meta_query'     => [
      [
        'compare'    => 'EXISTS',
        'key'        => 'cumulus_image',
      ],
    ],
  ]);

  return $query->posts ?: [];
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
