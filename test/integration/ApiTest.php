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

  public function test_sizes() {
    $this->assertEquals(wp_get_registered_image_subsizes(), Cumulus\sizes());
  }

  public function test_sizes_with_filter() {
    $this->add_filter_temporarily('cumulus/sizes', function() {
      return ['thumbnail', 'medium'];
    });

    $this->assertEquals([
      'thumbnail' => [
        'width'   => 150,
        'height'  => 150,
        'crop'    => true,
      ],
      'medium'    => [
        'width'   => 300,
        'height'  => 300,
        'crop'    => false,
      ],
    ], Cumulus\sizes());
  }
}
