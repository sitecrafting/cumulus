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
 * Test central logic of how URLs are computed.
 */
class UrlTest extends IntegrationTest {
  public function test_src_with_false() {
    $this->assertFalse(apply_filters(
      'wp_get_attachment_image_src',
      false,
      123,
      'thumbnail'
    ));
  }

  public function test_src_with_missing_meta() {
    $id = $this->factory->post->create([
      'post_type' => 'attachment',
    ]);

    $this->assertEquals(
      ['/path/to/image.jpg', 500, 500, true],
      apply_filters(
        'wp_get_attachment_image_src',
        ['/path/to/image.jpg', 500, 500, true],
        $id,
        'thumbnail'
      )
    );
  }

  public function test_src_with_meta() {
    $id = $this->factory->post->create([
      'post_type' => 'attachment',
    ]);

    add_post_meta($id, 'cumulus_image', [
      'urls_by_size' => [
        'thumbnail'  => 'https://res.cloudinary.com/test/w_150,h_150/image.jpg',
        'full'       => 'https://res.cloudinary.com/test/image.jpg',
      ],
    ]);

    $this->assertEquals(
      ['https://res.cloudinary.com/test/w_150,h_150/image.jpg', 500, 500, true],
      apply_filters(
        'wp_get_attachment_image_src',
        ['/path/to/image.jpg', 500, 500, true],
        $id,
        'thumbnail'
      )
    );
  }

  public function test_src_with_array_size() {
    $id = $this->factory->post->create([
      'post_type' => 'attachment',
    ]);

    add_post_meta($id, 'cumulus_image', [
      'urls_by_size' => [
        '900x901'    => 'https://res.cloudinary.com/test/w_900,h_901/image.jpg',
        'full'       => 'https://res.cloudinary.com/test/image.jpg',
      ],
    ]);

    $this->assertEquals(
      ['https://res.cloudinary.com/test/w_900,h_901/image.jpg', 900, 901, true],
      apply_filters(
        'wp_get_attachment_image_src',
        ['/path/to/image.jpg', 900, 901, true],
        $id,
        [900, 901]
      )
    );
  }
}
