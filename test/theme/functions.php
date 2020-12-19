<?php

require ABSPATH . '../vendor/autoload.php';

use Symfony\Component\Dotenv\Dotenv;
use Timber\Timber;

// Grab our Cumulus settings from .env as a backstop
$dotenv = new Dotenv();
$dotenv->load(ABSPATH . '../.env');

new Timber();


add_image_size('custom_small', 300, 185);
add_image_size('custom_medium', 500, 310);
add_image_size('custom_large', 1000, 618);

add_image_size('soft_crop', 300, 9999, false);

// Only allow custom crops for these specific sizes
add_filter('cumulus/sizes', function() {
  return [
    'thumbnail',
    'custom_small',
    'medium',
    'custom_medium',
    'large',
    'custom_large',
    'soft_crop',
  ];
});


/**
 * Log stuff to wp-content/debug.log
 * Assumes you have WP debugging configured like so in wp-config.php:
 * ```php
 *  define('WP_DEBUG',              true);
 *  define('WP_DEBUG_DISPLAY',      false);
 *  define('WP_DEBUG_LOG',          true);
 * ```
 * @param  mixed $message the message or other value to log. If not a string, gets var_export'ed
 */
function debug($message, string $prefix = '') {
  if(!is_string($message)) {
    $message = var_export($message,true);
  }

  error_log($message);
}

function select_keys(array $assoc, array $keys) : array {
  return array_intersect_key($assoc, array_flip($keys));
}
