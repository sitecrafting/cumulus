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

// Only allow custom crops for these specific sizes
add_filter('cumulus/sizes', function() {
  return [
    'thumbnail',
    'custom_small',
    'medium',
    'custom_medium',
    'large',
    'custom_large'
  ];
});
