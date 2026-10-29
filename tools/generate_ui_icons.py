#!/usr/bin/env python3
"""Generate the OmniIsle 16-px UI icon atlas with the Python standard library only.

Atlas: 8 columns x 3 rows of 16x16 cells (128x48 RGBA), pure white pixels so the
client can tint them with any theme colour. Run from the repository root:

    python3 tools/generate_ui_icons.py
"""

import os
import struct
import zlib

CELL = 16
COLUMNS = 8
ROWS = 3
WIDTH = CELL * COLUMNS
HEIGHT = CELL * ROWS


def blank():
    return [[(0, 0, 0, 0) for _ in range(WIDTH)] for _ in range(HEIGHT)]


def plot(img, x, y, a=255):
    if 0 <= x < WIDTH and 0 <= y < HEIGHT:
        img[y][x] = (255, 255, 255, a)


def rect(img, x0, y0, x1, y1, filled=False):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            if filled or x in (x0, x1) or y in (y0, y1):
                plot(img, x, y)


def line(img, x0, y0, x1, y1, width=1):
    dx = abs(x1 - x0)
    dy = -abs(y1 - y0)
    sx = 1 if x0 < x1 else -1
    sy = 1 if y0 < y1 else -1
    err = dx + dy
    while True:
        if width <= 1:
            plot(img, x0, y0)
        else:
            for oy in range(width):
                for ox in range(width):
                    plot(img, x0 + ox, y0 + oy)
        if x0 == x1 and y0 == y1:
            break
        e2 = 2 * err
        if e2 >= dy:
            err += dy
            x0 += sx
        if e2 <= dx:
            err += dx
            y0 += sy


def circle(img, cx, cy, r, filled=False, width=1):
    for y in range(cy - r, cy + r + 1):
        for x in range(cx - r, cx + r + 1):
            d2 = (x - cx) ** 2 + (y - cy) ** 2
            if filled:
                if d2 <= r * r:
                    plot(img, x, y)
            else:
                inner = (r - width) ** 2
                if inner <= d2 <= r * r:
                    plot(img, x, y)


def dot(img, x, y):
    for oy in range(2):
        for ox in range(2):
            plot(img, x + ox, y + oy)


def cell_origin(index):
    return (index % COLUMNS) * CELL, (index // COLUMNS) * CELL


def icon_brush(img, ox, oy):
    line(img, ox + 3, oy + 12, ox + 9, oy + 6, width=2)
    for i in range(5):
        for j in range(5 - i):
            plot(img, ox + 9 + j, oy + 2 + i)
    plot(img, ox + 2, oy + 13)
    plot(img, ox + 3, oy + 13)


def icon_eraser(img, ox, oy):
    for i in range(8):
        line(img, ox + 3 + i, oy + 3, ox + 6 + i, oy + 12, width=1)
        line(img, ox + 4 + i, oy + 3, ox + 7 + i, oy + 12, width=1)
    rect(img, ox + 9, oy + 10, ox + 13, oy + 12, filled=True)
    for x in range(ox + 3, ox + 8):
        plot(img, x, oy + 4)
        plot(img, x, oy + 5)


def icon_smooth(img, ox, oy):
    for base in (4, 8, 12):
        for x in range(2, 14):
            y = base + (1 if x % 4 in (1, 2) else -1)
            plot(img, ox + x, oy + y)


def icon_fill(img, ox, oy):
    line(img, ox + 3, oy + 6, ox + 8, oy + 11, width=1)
    line(img, ox + 8, oy + 11, ox + 12, oy + 7, width=1)
    line(img, ox + 3, oy + 6, ox + 6, oy + 3, width=1)
    line(img, ox + 12, oy + 7, ox + 9, oy + 3, width=1)
    line(img, ox + 6, oy + 3, ox + 9, oy + 3, width=1)
    for y in range(7, 10):
        for x in range(6, 11):
            plot(img, ox + x, oy + y)
    dot(img, ox + 12, oy + 11)
    plot(img, ox + 12, oy + 10)


def icon_line(img, ox, oy):
    line(img, ox + 3, oy + 12, ox + 12, oy + 3, width=2)
    rect(img, ox + 2, oy + 11, ox + 5, oy + 14)
    rect(img, ox + 11, oy + 2, ox + 14, oy + 5)


def icon_ellipse(img, ox, oy):
    circle(img, ox + 8, oy + 8, 5, width=2)


def icon_rectangle(img, ox, oy):
    rect(img, ox + 2, oy + 3, ox + 13, oy + 12)
    plot(img, ox + 2, oy + 3)
    plot(img, ox + 13, oy + 12)


def icon_spawn(img, ox, oy):
    circle(img, ox + 8, oy + 6, 4, width=2)
    line(img, ox + 6, oy + 9, ox + 8, oy + 13, width=2)
    line(img, ox + 10, oy + 9, ox + 8, oy + 13, width=2)
    plot(img, ox + 8, oy + 6)


def icon_symmetry(img, ox, oy):
    for y in range(1, 15, 2):
        plot(img, ox + 8, oy + y)
    for i in range(5):
        plot(img, ox + 2 + i, oy + 4 + i)
        plot(img, ox + 2 + i, oy + 12 - i)
        plot(img, ox + 13 - i, oy + 4 + i)
        plot(img, ox + 13 - i, oy + 12 - i)
    for y in range(5, 12):
        plot(img, ox + 5, oy + y)
        plot(img, ox + 10, oy + y)


def icon_zoom(img, ox, oy):
    circle(img, ox + 7, oy + 7, 4, width=2)
    line(img, ox + 10, oy + 10, ox + 14, oy + 14, width=2)
    line(img, ox + 5, oy + 7, ox + 9, oy + 7)


def icon_grid(img, ox, oy):
    rect(img, ox + 2, oy + 2, ox + 13, oy + 13)
    line(img, ox + 6, oy + 2, ox + 6, oy + 13)
    line(img, ox + 10, oy + 2, ox + 10, oy + 13)
    line(img, ox + 2, oy + 6, ox + 13, oy + 6)
    line(img, ox + 2, oy + 10, ox + 13, oy + 10)


def icon_undo(img, ox, oy):
    line(img, ox + 4, oy + 6, ox + 11, oy + 6, width=2)
    line(img, ox + 4, oy + 6, ox + 4, oy + 11, width=2)
    line(img, ox + 4, oy + 11, ox + 10, oy + 11, width=2)
    line(img, ox + 11, oy + 6, ox + 11, oy + 8, width=2)
    line(img, ox + 4, oy + 6, ox + 7, oy + 3)
    line(img, ox + 4, oy + 6, ox + 7, oy + 9)


def icon_redo(img, ox, oy):
    line(img, ox + 5, oy + 6, ox + 12, oy + 6, width=2)
    line(img, ox + 12, oy + 6, ox + 12, oy + 11, width=2)
    line(img, ox + 6, oy + 11, ox + 12, oy + 11, width=2)
    line(img, ox + 5, oy + 6, ox + 5, oy + 8, width=2)
    line(img, ox + 12, oy + 6, ox + 9, oy + 3)
    line(img, ox + 12, oy + 6, ox + 9, oy + 9)


def icon_dice(img, ox, oy):
    rect(img, ox + 3, oy + 3, ox + 12, oy + 12)
    for cx, cy in ((5, 5), (10, 5), (8, 8), (5, 10), (10, 10)):
        dot(img, ox + cx, oy + cy)


def icon_copy(img, ox, oy):
    rect(img, ox + 3, oy + 3, ox + 9, oy + 9)
    rect(img, ox + 6, oy + 6, ox + 12, oy + 12)
    line(img, ox + 6, oy + 6, ox + 9, oy + 6)
    line(img, ox + 6, oy + 6, ox + 6, oy + 9)


def icon_import(img, ox, oy):
    line(img, ox + 8, oy + 2, ox + 8, oy + 9, width=2)
    line(img, ox + 5, oy + 7, ox + 8, oy + 10, width=2)
    line(img, ox + 11, oy + 7, ox + 8, oy + 10, width=2)
    line(img, ox + 3, oy + 13, ox + 13, oy + 13, width=2)
    line(img, ox + 3, oy + 10, ox + 3, oy + 13)


def icon_export(img, ox, oy):
    line(img, ox + 8, oy + 10, ox + 8, oy + 3, width=2)
    line(img, ox + 5, oy + 6, ox + 8, oy + 3, width=2)
    line(img, ox + 11, oy + 6, ox + 8, oy + 3, width=2)
    line(img, ox + 3, oy + 13, ox + 13, oy + 13, width=2)
    line(img, ox + 3, oy + 10, ox + 3, oy + 13)


def icon_preset(img, ox, oy):
    rect(img, ox + 2, oy + 3, ox + 9, oy + 9)
    rect(img, ox + 5, oy + 6, ox + 13, oy + 13)
    line(img, ox + 2, oy + 3, ox + 5, oy + 6)
    line(img, ox + 9, oy + 3, ox + 13, oy + 6)


def icon_settings(img, ox, oy):
    circle(img, ox + 8, oy + 8, 4, width=2)
    for angle in ((8, 1), (8, 14), (1, 8), (14, 8), (3, 3), (12, 3), (3, 12), (12, 12)):
        plot(img, ox + angle[0], oy + angle[1])
        plot(img, ox + angle[0], oy + angle[1] + (1 if angle[1] == 1 else -1))
    circle(img, ox + 8, oy + 8, 1, filled=True)


def icon_close(img, ox, oy):
    line(img, ox + 4, oy + 4, ox + 11, oy + 11, width=2)
    line(img, ox + 11, oy + 4, ox + 4, oy + 11, width=2)


ICONS = [
    icon_brush, icon_eraser, icon_smooth, icon_fill,
    icon_line, icon_ellipse, icon_rectangle, icon_spawn,
    icon_symmetry, icon_zoom, icon_grid, icon_undo,
    icon_redo, icon_dice, icon_copy, icon_import,
    icon_export, icon_preset, icon_settings, icon_close,
]


def write_png(path, img):
    raw = bytearray()
    for row in img:
        raw.append(0)
        for r, g, b, a in row:
            raw.extend((r, g, b, a))

    def chunk(tag, data):
        payload = tag + data
        return struct.pack(">I", len(data)) + payload + struct.pack(">I", zlib.crc32(payload) & 0xFFFFFFFF)

    header = struct.pack(">IIBBBBB", WIDTH, HEIGHT, 8, 6, 0, 0, 0)
    png = b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b"")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as handle:
        handle.write(png)


def main():
    img = blank()
    for index, draw in enumerate(ICONS):
        ox, oy = cell_origin(index)
        draw(img, ox, oy)
        # Guarantee every declared cell is non-empty.
        if not any(img[y][x][3] for y in range(oy, oy + CELL) for x in range(ox, ox + CELL)):
            plot(img, ox + 8, oy + 8)
    path = os.path.join("src", "main", "resources", "assets", "omniisle", "textures", "gui", "icons.png")
    write_png(path, img)
    print(f"wrote {path} ({WIDTH}x{HEIGHT}, {len(ICONS)} icons)")


if __name__ == "__main__":
    main()
