<?php

/**
 * Tests for the core API functions
 *
 * @copyright 2020 SiteCrafting, Inc.
 * @author    Coby Tamayo <ctamayo@sitecrafting.com>
 */

namespace Cumulus\Integration;

use SiteCrafting\Cumulus;

/**
 * Stuff
 */
class ApiTest extends IntegrationTest {
  public function test_default_url() {
    $result = [
      'public_id' => 'test/cat',
      'format'    => 'jpg',
    ];
    $size = [
      'width'     => 150,
      'height'    => 150,
    ];

    $this->assertEquals(
      "https://res.cloudinary.com/my-cloud/image/upload/w_150,h_150,c_lfill/test/cat.jpg",
      Cumulus\default_url('my-cloud', $size, $result)
    );
  }
}
