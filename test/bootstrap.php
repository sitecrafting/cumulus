<?php

require_once __DIR__ . '../../vendor/autoload.php';

if (is_dir(__DIR__ . '/wp-tests-lib')) {
  require_once __DIR__ . '/wp-tests-lib/includes/functions.php';
  require_once __DIR__ . '/wp-tests-lib/includes/bootstrap.php';
  require_once __DIR__ . '../../cumulus.php';
}
