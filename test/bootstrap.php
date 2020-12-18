<?php

require_once __DIR__ . '../../vendor/autoload.php';
require_once __DIR__ . '../../api.php';

if (is_dir(__DIR__ . '/wp-tests-lib')) {
  require_once __DIR__ . '/wp-tests-lib/includes/functions.php';
  require_once __DIR__ . '/wp-tests-lib/includes/bootstrap.php';
}
