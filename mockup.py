import math
from PIL import Image, ImageDraw

def render_slimmer(thickness_dp, sweep_deg, start_offset):
    im = Image.new('RGBA', (480, 480), (10, 15, 29, 255))
    draw = ImageDraw.Draw(im)

    # 1. Circular Watch Bezel (for reference)
    draw.ellipse([0, 0, 479, 479], outline=(40, 50, 70, 255), width=2)

    cx, cy = 240, 240
    outer_r = 115 * 2.0
    th = thickness_dp * 2.0
    r_mid = outer_r - (th / 2.0)
    r_cap = th / 2.0
    r_outer = r_mid + r_cap
    r_inner = r_mid - r_cap

    def draw_curved_pill(start_deg, sweep_deg, fill):
        steps = int(sweep_deg * 4)
        points = []
        for i in range(steps + 1):
            a = math.radians(start_deg + sweep_deg * (i / steps))
            points.append((cx + r_outer * math.cos(a), cy + r_outer * math.sin(a)))
        for i in range(steps, -1, -1):
            a = math.radians(start_deg + sweep_deg * (i / steps))
            points.append((cx + r_inner * math.cos(a), cy + r_inner * math.sin(a)))
        draw.polygon(points, fill=fill)

        a_start = math.radians(start_deg)
        cap_start_x = cx + r_mid * math.cos(a_start)
        cap_start_y = cy + r_mid * math.sin(a_start)
        draw.ellipse([cap_start_x - r_cap, cap_start_y - r_cap, cap_start_x + r_cap, cap_start_y + r_cap], fill=fill)

        a_end = math.radians(start_deg + sweep_deg)
        cap_end_x = cx + r_mid * math.cos(a_end)
        cap_end_y = cy + r_mid * math.sin(a_end)
        draw.ellipse([cap_end_x - r_cap, cap_end_y - r_cap, cap_end_x + r_cap, cap_end_y + r_cap], fill=fill)

    # Equal (Amber)
    start_eq = 230 + start_offset
    draw_curved_pill(start_eq, sweep_deg, (217, 119, 6, 255))
    # Backspace (Slate)
    start_del = 286 - start_offset
    draw_curved_pill(start_del, sweep_deg, (51, 65, 85, 255))

    # Precision pill
    draw.rounded_rectangle([205, 18, 275, 42], radius=12, fill=(15, 23, 42, 255), outline=(56, 189, 248, 255), width=2)

    # Divider line at y = 114 (57dp)
    draw.line([0, 114, 480, 114], fill=(30, 41, 59, 255), width=2)

    # Keypad background
    draw.rectangle([0, 116, 480, 480], fill=(0, 0, 0, 255))

    im.save(f'slimmer_{thickness_dp}_{sweep_deg}.png')
    print(f'slimmer_{thickness_dp}_{sweep_deg}.png saved')

if __name__ == '__main__':
    render_slimmer(20, 20, 0)
    render_slimmer(22, 22, -1)
    render_slimmer(18, 18, 2)
