<?php

/**
 * Base class for Cumulus integration test cases
 *
 * @copyright 2020 SiteCrafting, Inc.
 * @author    Coby Tamayo <ctamayo@sitecrafting.com>
 */

namespace Cumulus\Integration;

use WP_UnitTestCase;

/**
 * Base test class for the plugin. Declared abstract so that PHPUnit doesn't
 * complain about a lack of tests defined here.
 */
abstract class IntegrationTest extends WP_UnitTestCase {
  /**
   * Maintain a list of action/filter hook removals to perform at the
   * end of each test.
   *
   * @var array
   */
  private $temporary_hook_removals = [];

  public function tearDown() {
    parent::tearDown();

    foreach ($this->temporary_hook_removals as $remove) {
      $remove();
    }

    $this->temporary_hook_removals = [];
  }

  public function add_filter_temporarily(
    string $hook,
    callable $func,
    int $pri = 10,
    int $count = 1
  ) : void {
    add_filter($hook, $func, $pri, $count);
    $this->temporary_hook_removals[] = function() use($hook, $func, $pri, $count) {
      remove_filter($hook, $func, $pri, $count);
    };
  }
}
