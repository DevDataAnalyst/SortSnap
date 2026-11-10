#!/usr/bin/env python3
"""Draws the sample pictures used by the guide screenshots (app/src/test/.../GuideScreenshots.kt).

They are simple illustrations, not real photos, so the repository carries no personal pictures and
no third-party image licences. Re-run after changing a picture:

    python3 tools/generate_sample_images.py
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "test", "resources", "guide-samples")
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
SERIF_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSerif-Bold.ttf"
MONO = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"
W = H = 480


def font(path, size):
    return ImageFont.truetype(path, size)


def gradient(w, h, top, bottom):
    img = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(img)
    for y in range(h):
        t = y / max(1, h - 1)
        d.line([(0, y), (w, y)], fill=tuple(int(a + (b - a) * t) for a, b in zip(top, bottom)))
    return img


def grain(img, amount=10, seed=0):
    rnd = random.Random(seed)
    px = img.load()
    for _ in range(img.width * img.height // 6):
        x, y = rnd.randrange(img.width), rnd.randrange(img.height)
        r, g, b = px[x, y]
        n = rnd.randint(-amount, amount)
        px[x, y] = (max(0, min(255, r + n)), max(0, min(255, g + n)), max(0, min(255, b + n)))
    return img


def outlined(d, xy, text, f, fill="white", stroke="black", anchor="mm"):
    d.text(xy, text, font=f, fill=fill, stroke_width=max(2, f.size // 12), stroke_fill=stroke, anchor=anchor)


# ---------- photos ----------

def photo_sunset():
    img = gradient(W, H, (255, 140, 80), (120, 60, 140))
    d = ImageDraw.Draw(img)
    d.ellipse([170, 210, 310, 350], fill=(255, 220, 120))
    d.rectangle([0, 300, W, H], fill=(40, 60, 110))
    for i in range(12):
        y = 310 + i * 14
        d.line([(200 - i * 4, y), (280 + i * 4, y)], fill=(255, 200, 120), width=2)
    return grain(img.filter(ImageFilter.GaussianBlur(1.2)), seed=1)


def photo_mountains():
    img = gradient(W, H, (120, 180, 235), (220, 235, 250))
    d = ImageDraw.Draw(img)
    d.polygon([(0, 330), (130, 150), (230, 280), (330, 120), (480, 300), (480, 480), (0, 480)], fill=(90, 110, 140))
    d.polygon([(130, 150), (160, 190), (110, 185)], fill=(245, 245, 250))
    d.polygon([(330, 120), (365, 165), (300, 160)], fill=(245, 245, 250))
    d.polygon([(0, 380), (160, 300), (300, 360), (480, 320), (480, 480), (0, 480)], fill=(60, 120, 70))
    for x in range(10, 480, 34):
        h = 40 + (x * 7) % 30
        d.polygon([(x, 470), (x + 14, 470 - h), (x + 28, 470)], fill=(30, 80, 45))
    return grain(img.filter(ImageFilter.GaussianBlur(1)), seed=2)


def photo_beach():
    img = gradient(W, H, (110, 190, 240), (180, 225, 245))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 230, W, 330], fill=(30, 140, 180))
    for y in range(240, 330, 12):
        d.arc([-40, y - 6, 520, y + 10], 0, 180, fill=(200, 240, 250), width=2)
    d.rectangle([0, 320, W, H], fill=(235, 210, 160))
    d.line([(360, 470), (390, 300)], fill=(110, 80, 50), width=5)
    for a in range(0, 360, 45):
        r = math.radians(a)
        d.line([(390, 300), (390 + 70 * math.cos(r), 300 + 30 * math.sin(r) - 10)], fill=(40, 120, 60), width=6)
    d.ellipse([60, 40, 120, 100], fill=(255, 245, 200))
    return grain(img.filter(ImageFilter.GaussianBlur(1)), seed=3)


def photo_food():
    img = gradient(W, H, (120, 85, 60), (80, 55, 40))
    d = ImageDraw.Draw(img)
    for x in range(0, W, 60):
        d.line([(x, 0), (x, H)], fill=(100, 70, 50), width=2)
    d.ellipse([70, 70, 410, 410], fill=(245, 245, 240))
    d.ellipse([110, 110, 370, 370], fill=(230, 160, 60))
    rnd = random.Random(4)
    for _ in range(40):
        x, y = rnd.randint(140, 340), rnd.randint(140, 340)
        c = rnd.choice([(80, 150, 60), (200, 50, 40), (250, 230, 200)])
        d.ellipse([x, y, x + 18, y + 14], fill=c)
    return grain(img.filter(ImageFilter.GaussianBlur(0.8)), seed=4)


def photo_flowers():
    img = gradient(W, H, (90, 140, 70), (40, 80, 40))
    d = ImageDraw.Draw(img)
    rnd = random.Random(5)
    for _ in range(14):
        cx, cy, r = rnd.randint(30, 450), rnd.randint(30, 450), rnd.randint(25, 50)
        col = rnd.choice([(240, 90, 120), (250, 200, 60), (250, 250, 250), (200, 110, 220)])
        for a in range(0, 360, 60):
            rr = math.radians(a)
            px, py = cx + r * 0.6 * math.cos(rr), cy + r * 0.6 * math.sin(rr)
            d.ellipse([px - r / 2, py - r / 2, px + r / 2, py + r / 2], fill=col)
        d.ellipse([cx - r / 4, cy - r / 4, cx + r / 4, cy + r / 4], fill=(250, 210, 80))
    return grain(img.filter(ImageFilter.GaussianBlur(1.5)), seed=5)


def photo_city():
    img = gradient(W, H, (20, 25, 60), (70, 50, 110))
    d = ImageDraw.Draw(img)
    rnd = random.Random(6)
    x = 0
    while x < W:
        bw, bh = rnd.randint(40, 80), rnd.randint(120, 330)
        d.rectangle([x, H - bh, x + bw, H], fill=(25, 25, 40))
        for wy in range(H - bh + 10, H - 10, 18):
            for wx in range(x + 6, x + bw - 8, 14):
                if rnd.random() < 0.55:
                    d.rectangle([wx, wy, wx + 7, wy + 9], fill=(255, 220, 130))
        x += bw + 4
    d.ellipse([380, 40, 430, 90], fill=(240, 240, 220))
    return grain(img.filter(ImageFilter.GaussianBlur(0.6)), seed=6)


def photo_lake():
    img = gradient(W, H, (250, 190, 150), (150, 170, 210))
    d = ImageDraw.Draw(img)
    d.polygon([(0, 250), (90, 170), (190, 240), (300, 160), (480, 250)], fill=(70, 90, 110))
    reflect = gradient(W, 230, (120, 140, 180), (60, 80, 120))
    img.paste(reflect, (0, 250))
    d = ImageDraw.Draw(img)
    d.polygon([(0, 250), (90, 320), (190, 260), (300, 330), (480, 250)], fill=(85, 100, 125))
    d.polygon([(200, 380), (290, 380), (275, 395), (215, 395)], fill=(140, 70, 40))
    return grain(img.filter(ImageFilter.GaussianBlur(1)), seed=7)


def photo_dog():
    img = gradient(W, H, (150, 200, 120), (90, 150, 80))
    d = ImageDraw.Draw(img)
    d.ellipse([140, 230, 360, 420], fill=(200, 150, 90))   # body
    d.ellipse([170, 120, 320, 260], fill=(210, 160, 100))  # head
    d.ellipse([150, 130, 200, 230], fill=(150, 100, 60))   # ear
    d.ellipse([290, 130, 340, 230], fill=(150, 100, 60))   # ear
    d.ellipse([210, 170, 228, 188], fill=(30, 25, 20))
    d.ellipse([262, 170, 280, 188], fill=(30, 25, 20))
    d.ellipse([232, 200, 258, 220], fill=(40, 30, 25))
    return grain(img.filter(ImageFilter.GaussianBlur(1.3)), seed=8)


# ---------- screenshots ----------

def screenshot_chat():
    w, h = 360, 760
    img = Image.new("RGB", (w, h), (236, 229, 221))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, w, 24], fill=(7, 94, 84))
    d.text((10, 5), "9:41", font=font(BOLD, 12), fill="white")
    d.rectangle([0, 24, w, 80], fill=(7, 94, 84))
    d.ellipse([12, 34, 48, 70], fill=(200, 200, 200))
    d.text((60, 40), "Weekend plans", font=font(BOLD, 16), fill="white")
    y = 100
    for i, (mine, text) in enumerate([
        (False, "Are we still on for Saturday?"), (True, "Yes! 10am at the park"),
        (False, "Great, I'll bring snacks"), (True, "Perfect"), (False, "See you there"),
        (True, "Don't forget the ball"), (False, "Haha ok"),
    ]):
        tw = d.textlength(text, font=font(FONT, 14)) + 24
        x0 = w - tw - 12 if mine else 12
        d.rounded_rectangle([x0, y, x0 + tw, y + 36], 10, fill=(220, 248, 198) if mine else (255, 255, 255))
        d.text((x0 + 12, y + 9), text, font=font(FONT, 14), fill=(30, 30, 30))
        y += 52
    d.rectangle([0, h - 56, w, h], fill=(245, 245, 245))
    d.rounded_rectangle([10, h - 46, w - 60, h - 10], 18, fill="white")
    d.ellipse([w - 50, h - 48, w - 10, h - 8], fill=(7, 94, 84))
    return img


def screenshot_settings():
    w, h = 360, 760
    img = Image.new("RGB", (w, h), (250, 250, 252))
    d = ImageDraw.Draw(img)
    d.text((10, 5), "9:41", font=font(BOLD, 12), fill=(30, 30, 30))
    d.text((20, 50), "Settings", font=font(BOLD, 26), fill=(20, 20, 20))
    rows = ["Network & internet", "Connected devices", "Apps", "Notifications", "Battery", "Storage",
            "Sound & vibration", "Display", "Wallpaper", "Accessibility", "Security", "Privacy"]
    y = 110
    cols = [(66, 133, 244), (52, 168, 83), (251, 188, 5), (234, 67, 53)]
    for i, r in enumerate(rows):
        d.ellipse([20, y, 52, y + 32], fill=cols[i % 4])
        d.text((70, y + 6), r, font=font(FONT, 16), fill=(30, 30, 30))
        y += 52
    return img


def screenshot_map():
    w, h = 360, 760
    img = Image.new("RGB", (w, h), (232, 234, 237))
    d = ImageDraw.Draw(img)
    for x in range(-200, w, 70):
        d.line([(x, 0), (x + 300, h)], fill=(255, 255, 255), width=10)
    for y in range(0, h, 90):
        d.line([(0, y), (w, y + 40)], fill=(255, 255, 255), width=8)
    d.rectangle([40, 300, 140, 380], fill=(197, 232, 197))
    d.line([(60, 600), (150, 420), (260, 300), (300, 150)], fill=(66, 133, 244), width=8)
    d.rounded_rectangle([12, 30, w - 12, 76], 24, fill="white")
    d.text((30, 44), "Search here", font=font(FONT, 15), fill=(110, 110, 110))
    d.rounded_rectangle([0, h - 140, w, h], 18, fill="white")
    d.text((20, h - 120), "12 min (3.4 km)", font=font(BOLD, 18), fill=(24, 128, 56))
    return img


# ---------- memes ----------

def meme(top, bottom, bg_fn, seed):
    img = bg_fn()
    d = ImageDraw.Draw(img)
    f = font(BOLD, 40)
    outlined(d, (W // 2, 50), top, f)
    outlined(d, (W // 2, H - 50), bottom, f)
    return img


def meme_cat_bg():
    img = gradient(W, H, (180, 180, 190), (120, 120, 130))
    d = ImageDraw.Draw(img)
    d.ellipse([140, 170, 340, 400], fill=(90, 90, 95))
    d.polygon([(160, 200), (180, 120), (230, 180)], fill=(90, 90, 95))
    d.polygon([(320, 200), (300, 120), (250, 180)], fill=(90, 90, 95))
    d.ellipse([190, 240, 225, 275], fill=(230, 220, 90))
    d.ellipse([255, 240, 290, 275], fill=(230, 220, 90))
    return img.filter(ImageFilter.GaussianBlur(1))


def meme_desk_bg():
    img = gradient(W, H, (210, 200, 180), (150, 130, 110))
    d = ImageDraw.Draw(img)
    d.rectangle([60, 180, 420, 360], fill=(40, 40, 50))
    d.rectangle([80, 195, 400, 340], fill=(70, 130, 200))
    d.rectangle([0, 380, W, H], fill=(120, 90, 60))
    d.ellipse([360, 330, 420, 390], fill=(240, 240, 240))
    return img.filter(ImageFilter.GaussianBlur(1))


def meme_two_panel():
    img = Image.new("RGB", (W, H), "white")
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W // 2 - 2, H], fill=(250, 220, 120))
    d.rectangle([W // 2 + 2, 0, W, H], fill=(140, 200, 250))
    f = font(BOLD, 26)
    outlined(d, (W // 4, 120), "ME AT", f)
    outlined(d, (W // 4, 160), "9 AM", f)
    outlined(d, (3 * W // 4, 120), "ME AT", f)
    outlined(d, (3 * W // 4, 160), "9:05 AM", f)
    d.ellipse([60, 240, 180, 360], fill=(255, 200, 150))
    d.arc([90, 280, 150, 330], 20, 160, fill="black", width=4)
    d.ellipse([300, 240, 420, 360], fill=(255, 200, 150))
    d.arc([330, 300, 390, 350], 200, 340, fill="black", width=4)
    return img


# ---------- forwards ----------

def forward_good_morning():
    img = gradient(W, H, (255, 236, 179), (255, 183, 77))
    d = ImageDraw.Draw(img)
    d.ellipse([180, 60, 300, 180], fill=(255, 245, 200))
    for a in range(0, 360, 30):
        r = math.radians(a)
        d.line([(240 + 70 * math.cos(r), 120 + 70 * math.sin(r)), (240 + 100 * math.cos(r), 120 + 100 * math.sin(r))], fill=(255, 250, 220), width=5)
    d.text((W // 2, 260), "Good Morning", font=font(SERIF_BOLD, 52), fill=(180, 40, 60), anchor="mm")
    d.text((W // 2, 330), "Have a blessed day!", font=font(FONT, 26), fill=(120, 60, 30), anchor="mm")
    for x in range(30, 480, 70):
        d.ellipse([x, 400, x + 40, 440], fill=(240, 90, 120))
        d.ellipse([x + 14, 414, x + 26, 426], fill=(255, 220, 90))
    return img


def forward_quote():
    img = gradient(W, H, (40, 60, 110), (20, 25, 50))
    d = ImageDraw.Draw(img)
    d.text((60, 70), "“", font=font(SERIF_BOLD, 120), fill=(255, 210, 120))
    lines = ["Every day is a", "new beginning.", "Take a deep breath", "and start again."]
    for i, l in enumerate(lines):
        d.text((W // 2, 200 + i * 44), l, font=font(SERIF_BOLD, 30), fill="white", anchor="mm")
    d.text((W // 2, 420), "Share with your loved ones", font=font(FONT, 18), fill=(200, 200, 220), anchor="mm")
    return img


def forward_festival():
    img = gradient(W, H, (120, 20, 60), (60, 10, 40))
    d = ImageDraw.Draw(img)
    rnd = random.Random(9)
    for _ in range(80):
        x, y = rnd.randint(0, W), rnd.randint(0, H)
        d.ellipse([x, y, x + 4, y + 4], fill=(255, 220, 120))
    for x in (120, 240, 360):
        d.polygon([(x - 30, 400), (x + 30, 400), (x + 20, 430), (x - 20, 430)], fill=(200, 120, 40))
        d.ellipse([x - 8, 360, x + 8, 400], fill=(255, 200, 60))
    d.text((W // 2, 170), "Happy Diwali", font=font(SERIF_BOLD, 50), fill=(255, 215, 100), anchor="mm")
    d.text((W // 2, 240), "Wishing you light & joy", font=font(FONT, 24), fill="white", anchor="mm")
    return img


# ---------- documents ----------

def doc_receipt():
    img = Image.new("RGB", (W, H), (200, 195, 185))
    paper = Image.new("RGB", (300, 470), (252, 252, 248))
    d = ImageDraw.Draw(paper)
    m = font(MONO, 14)
    d.text((150, 26), "CORNER MARKET", font=font(BOLD, 18), fill=(20, 20, 20), anchor="mm")
    d.text((150, 50), "12 Main St  Tel 555-0100", font=m, fill=(60, 60, 60), anchor="mm")
    items = [("Milk 1L", "1.20"), ("Bread", "2.10"), ("Eggs x12", "3.40"), ("Apples", "2.75"),
             ("Coffee", "6.90"), ("Rice 2kg", "4.10"), ("Tomatoes", "1.85")]
    y = 90
    for name, price in items:
        d.text((20, y), name, font=m, fill=(30, 30, 30))
        d.text((280, y), price, font=m, fill=(30, 30, 30), anchor="ra")
        y += 26
    d.line([(20, y + 4), (280, y + 4)], fill=(80, 80, 80))
    d.text((20, y + 14), "TOTAL", font=font(BOLD, 16), fill=(20, 20, 20))
    d.text((280, y + 14), "22.30", font=font(BOLD, 16), fill=(20, 20, 20), anchor="ra")
    d.text((150, y + 70), "THANK YOU", font=m, fill=(60, 60, 60), anchor="mm")
    paper = paper.rotate(4, expand=True, fillcolor=(200, 195, 185))
    img.paste(paper, (80, -10))
    return grain(img, 6, seed=10)


def doc_notes():
    img = Image.new("RGB", (W, H), (255, 253, 235))
    d = ImageDraw.Draw(img)
    d.line([(60, 0), (60, H)], fill=(240, 120, 120), width=2)
    for y in range(60, H, 32):
        d.line([(0, y), (W, y)], fill=(170, 200, 230), width=1)
    lines = ["Meeting notes - Tue", "- Budget review Friday", "- Send slides to team", "- Book room for demo",
             "- Call supplier", "TODO: renew insurance", "- Pay electricity bill"]
    for i, l in enumerate(lines):
        d.text((72, 36 + i * 32 + 4), l, font=font(FONT, 20), fill=(30, 40, 120))
    return grain(img, 5, seed=11)


def doc_bill():
    img = Image.new("RGB", (W, H), (245, 245, 245))
    d = ImageDraw.Draw(img)
    d.rectangle([0, 0, W, 70], fill=(25, 90, 160))
    d.text((24, 22), "City Power & Light", font=font(BOLD, 22), fill="white")
    d.text((24, 92), "INVOICE  #2026-1042", font=font(BOLD, 18), fill=(30, 30, 30))
    d.text((24, 124), "Billing period: 01 Oct - 31 Oct", font=font(FONT, 15), fill=(70, 70, 70))
    rows = [("Energy charge", "48.20"), ("Fixed charge", "9.00"), ("Taxes", "5.72")]
    y = 180
    for a, b in rows:
        d.text((24, y), a, font=font(FONT, 17), fill=(30, 30, 30))
        d.text((456, y), b, font=font(FONT, 17), fill=(30, 30, 30), anchor="ra")
        d.line([(24, y + 30), (456, y + 30)], fill=(210, 210, 210))
        y += 44
    d.text((24, y + 20), "Amount due", font=font(BOLD, 20), fill=(30, 30, 30))
    d.text((456, y + 20), "62.92", font=font(BOLD, 20), fill=(25, 90, 160), anchor="ra")
    d.text((24, 430), "Due date: 15 Nov 2026", font=font(FONT, 15), fill=(180, 40, 40))
    return img


SAMPLES = {
    "photo_sunset": photo_sunset, "photo_mountains": photo_mountains, "photo_beach": photo_beach,
    "photo_food": photo_food, "photo_flowers": photo_flowers, "photo_city": photo_city,
    "photo_lake": photo_lake, "photo_dog": photo_dog,
    "screenshot_chat": screenshot_chat, "screenshot_settings": screenshot_settings, "screenshot_map": screenshot_map,
    "meme_cat": lambda: meme("WHEN THE WIFI", "GOES DOWN", meme_cat_bg, 1),
    "meme_desk": lambda: meme("IT WORKS", "DON'T TOUCH IT", meme_desk_bg, 2),
    "meme_panels": meme_two_panel,
    "forward_morning": forward_good_morning, "forward_quote": forward_quote, "forward_festival": forward_festival,
    "doc_receipt": doc_receipt, "doc_notes": doc_notes, "doc_bill": doc_bill,
}

if __name__ == "__main__":
    os.makedirs(OUT, exist_ok=True)
    for name, fn in SAMPLES.items():
        img = fn()
        img.save(os.path.join(OUT, name + ".jpg"), quality=82)
    print(f"wrote {len(SAMPLES)} images to {os.path.normpath(OUT)}")
