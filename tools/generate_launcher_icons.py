"""Derive launcher resources from the approved PNG; run with desktop Pillow/NumPy.

No app/build dependency. The reference is read-only. Stone pixels are extracted,
never traced or redrawn; only uniform scaling and Android mask padding are applied.
"""
from pathlib import Path
import hashlib
import json

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "docs/design/brdcrmbs-launcher-icon-approved.png"
APPROVED_SHA256 = "21ae691ce1a8551d5b5daa9bf8a650d10b9f16bb21b15813e92648a0440fa3a4"
assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == APPROVED_SHA256
source = Image.open(SOURCE).convert("RGB")
pixels = np.asarray(source).astype(np.int16)
height, width = pixels.shape[:2]
assert width == height
# The artwork's charcoal has blue >= green; green excess separates the four stones.
stone_mask = pixels[:, :, 1] - np.maximum(pixels[:, :, 0], pixels[:, :, 2]) > 6
ys, xs = np.nonzero(stone_mask)
assert len(xs) > 0
center = (width - 1) / 2
radius = np.hypot(xs - center, ys - center).max()
rgba = np.zeros((height, width, 4), dtype=np.uint8)
rgba[:, :, :3] = pixels.astype(np.uint8)
rgba[:, :, 3] = stone_mask.astype(np.uint8) * 255
# 108dp layers at xxxhdpi. All important pixels fit a 62dp circle, inside 66dp safe zone.
side = 432
scaled_side = round(width * (31 * 4) / radius)
stones = Image.fromarray(rgba).resize((scaled_side, scaled_side), Image.Resampling.LANCZOS)
foreground = Image.new("RGBA", (side, side))
offset = (side - scaled_side) // 2
foreground.alpha_composite(stones, (offset, offset))
fy, fx = np.nonzero(np.asarray(foreground)[:, :, 3] > 8)
safe_radius = float(np.hypot(fx - 215.5, fy - 215.5).max())
assert safe_radius < 33 * 4
bounds = foreground.getbbox()
symbol_dp = ((bounds[2] - bounds[0]) / 4, (bounds[3] - bounds[1]) / 4)
assert min(symbol_dp) >= 48

# Reconstruct only the charcoal occluded by stones/shadows, using surrounding source
# colors. Harmonic interpolation avoids residual silhouettes/banding during parallax.
small_side = 158
hole_image = Image.fromarray(stone_mask.astype(np.uint8) * 255).resize((small_side, small_side), Image.Resampling.NEAREST).filter(ImageFilter.MaxFilter(11))
holes = np.asarray(hole_image) > 0
shading = np.asarray(source.resize((small_side, small_side), Image.Resampling.BOX)).astype(np.float32)
axis = np.arange(small_side)
for y in range(small_side):
    for channel in range(3):
        shading[y, holes[y], channel] = np.interp(axis[holes[y]], axis[~holes[y]], shading[y, ~holes[y], channel])
interior = holes[1:-1, 1:-1]
for _ in range(2500):
    average = (shading[:-2, 1:-1] + shading[2:, 1:-1] + shading[1:-1, :-2] + shading[1:-1, 2:]) / 4
    shading[1:-1, 1:-1][interior] = average[interior]
reconstructed = Image.fromarray(np.rint(shading).astype(np.uint8)).resize(source.size, Image.Resampling.BICUBIC)
blend = hole_image.resize(source.size, Image.Resampling.BILINEAR).filter(ImageFilter.GaussianBlur(8))
backdrop = Image.composite(reconstructed, source, blend)
inset = round(width * 0.15)
background_viewport = backdrop.crop((inset, inset, width - inset, height - inset)).resize((288, 288), Image.Resampling.LANCZOS)
# Extend charcoal to the 18dp bleed rather than shrinking its visible shading twice.
background = Image.fromarray(np.pad(np.asarray(background_viewport), ((72, 72), (72, 72), (0, 0)), mode="edge"))
assert np.asarray(background).shape == (432, 432, 3)
bg = np.asarray(background).astype(np.int16)
assert np.max(bg[:, :, 1] - np.maximum(bg[:, :, 0], bg[:, :, 2])) <= 6

resources = ROOT / "app/src/main/res"
layers = resources / "drawable-xxxhdpi"
layers.mkdir(exist_ok=True)
foreground.save(layers / "ic_launcher_foreground.png", optimize=True)
background.save(layers / "ic_launcher_background.png", optimize=True)

# Android exposes the central 72dp of its 108dp layers before optional motion.
viewport = (72, 72, 360, 360)
composite = Image.alpha_composite(background.convert("RGBA"), foreground).crop(viewport)
visible_stones = np.asarray(foreground.crop(viewport))[:, :, 3]

def mask(kind):
    size = 288
    large = Image.new("L", (size * 4, size * 4))
    draw = ImageDraw.Draw(large)
    if kind == "circle":
        draw.ellipse((0, 0, size * 4 - 1, size * 4 - 1), fill=255)
    elif kind == "rounded square":
        draw.rounded_rectangle((0, 0, size * 4 - 1, size * 4 - 1), radius=round(size * 4 * 0.22), fill=255)
    else:
        coord = (np.arange(size * 4) + 0.5) / (size * 2) - 1
        inside = coord[:, None] ** 4 + coord[None, :] ** 4 <= 1
        large = Image.fromarray(inside.astype(np.uint8) * 255)
    return large.resize((size, size), Image.Resampling.LANCZOS)

masked = {}
for kind in ("circle", "rounded square", "squircle"):
    alpha = mask(kind)
    assert not np.any((visible_stones > 0) & (np.asarray(alpha) < 255)), f"Clipped stones: {kind}"
    image = composite.copy()
    image.putalpha(alpha)
    masked[kind] = image

for density, size in (("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)):
    directory = resources / f"mipmap-{density}"
    for name, kind in (("ic_launcher", "rounded square"), ("ic_launcher_round", "circle")):
        masked[kind].resize((size, size), Image.Resampling.LANCZOS).save(directory / f"{name}.webp", lossless=True, exact=True)

review = ROOT / "app/build/launcher-review"
review.mkdir(parents=True, exist_ok=True)
montage = Image.new("RGB", (4 * 224, 256), "#34393c")
draw = ImageDraw.Draw(montage)
for index, (label, image) in enumerate((("Approved reference", source), *masked.items())):
    draw.text((index * 224 + 16, 12), label, fill="white")
    tile = image.resize((192, 192), Image.Resampling.LANCZOS).convert("RGBA")
    montage.paste(tile, (index * 224 + 16, 44), tile)
montage.save(review / "mask-comparison.png")
facts = {"source_sha256": APPROVED_SHA256, "source_size": source.size,
         "layer_px": side, "layer_dp": 108, "symbol_dp": symbol_dp, "symbol_radius_dp": safe_radius / 4,
         "safe_radius_dp": 33, "unclipped_masks": list(masked), "legacy_sizes_px": [48, 72, 96, 144, 192]}
(review / "checks.json").write_text(json.dumps(facts, indent=2) + "\n", encoding="utf-8")
assert hashlib.sha256(SOURCE.read_bytes()).hexdigest() == APPROVED_SHA256
print(json.dumps(facts))
