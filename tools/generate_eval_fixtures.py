#!/usr/bin/env python3
"""Generates 60 synthetic evaluation fixtures for the classifier harness.

Deterministic (fixed seed). Replace or extend core/src/test/resources/eval/fixtures.csv with your own
labelled rows; the harness picks up every row.
"""
import csv
import random
import sys

random.seed(42)
HEADER = ["id", "label", "relative_path", "bucket", "display_name", "mime", "width", "height", "size",
          "exif_make", "exif_model", "focal", "gps", "screen_w", "screen_h", "brightness", "uniformity",
          "text", "line_count", "text_coverage", "top_band", "bottom_band", "labels"]
WA = ("Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Images/", "WhatsApp Images")
TG = ("Pictures/Telegram/", "Telegram")
DL = ("Download/", "Download")
CAM = ("DCIM/Camera/", "Camera")
SS = ("Pictures/Screenshots/", "Screenshots")
IG = ("Pictures/Instagram/", "Instagram")

rows = []


def add(label, folder, name, mime, w, h, size, make="", model="", focal=0, gps=0, bright="", unif="",
        text="", lines="", cov="", top=0, bottom=0, labels=""):
    rows.append([f"{label.lower()}_{len(rows):03d}", label, folder[0], folder[1], name, mime, w, h, size,
                 make, model, focal, gps, 1080, 2400, bright, unif, text, lines, cov, top, bottom, labels])


people = ["Person:0.93|Smile:0.81", "Food:0.88|Tableware:0.7", "Dog:0.91|Pet:0.86", "Sky:0.9|Mountain:0.8",
          "Flower:0.85|Plant:0.8", "Crowd:0.77|Event:0.7", "Selfie:0.9|Person:0.88", "Beach:0.86|Sea:0.84"]
for i in range(12):
    folder = CAM if i < 9 else (WA if i < 11 else TG)  # a few real photos shared over chat
    exif = ("Google", "Pixel 9") if folder == CAM and i % 4 != 3 else ("", "")
    add("PHOTO", folder, f"PXL_2026{i:04d}.jpg" if folder == CAM else f"IMG-2026-WA{i:04d}.jpg", "image/jpeg",
        4080 if folder == CAM else 1600, 3072 if folder == CAM else 1200, random.randint(1_500_000, 4_500_000),
        exif[0], exif[1], 1 if exif[0] else 0, 1 if (exif[0] and i % 2 == 0) else 0,
        round(random.uniform(0.3, 0.6), 2), round(random.uniform(0.1, 0.3), 2),
        "" if i % 5 else "EXIT", 0 if i % 5 else 1, 0.0 if i % 5 else 0.01, 0, 0, people[i % len(people)])

chrome = ["10:42 85% Reply Like Share", "9:41 Search Home Settings", "Type a message last seen today 18:05",
          "Follow Message 2,341 followers", "4:20 PM 62% Notifications View all"]
for i in range(12):
    if i < 8:
        add("SCREENSHOT", SS, f"Screenshot_2026110{i}-0930{i:02d}_Chrome.png", "image/png", 1080, 2400,
            random.randint(300_000, 1_200_000), bright=0.8, unif=0.5, text=chrome[i % 5], lines=6, cov=0.3)
    elif i < 10:  # screenshots shared from another phone: wrong size, JPEG, in WhatsApp
        add("SCREENSHOT", WA, f"IMG-2026-WA{i:04d}.jpg", "image/jpeg", 1170, 2532, 400_000,
            bright=0.85, unif=0.5, text=chrome[i % 5], lines=7, cov=0.35)
    else:  # screenshot saved without ML text (no text found)
        add("SCREENSHOT", ("DCIM/Screenshots/", "Screenshots"), f"Screenshot_{i}.jpg", "image/jpeg", 1080, 2400, 500_000)

memes = ["WHEN YOU SAY ONE MORE EPISODE\nAND IT'S 4AM", "NOBODY:\nME AT 3AM", "ME BEFORE EXAMS\nME AFTER EXAMS",
         "THAT FEELING WHEN\nWIFI CONNECTS", "MONDAY BE LIKE", "HOW IT STARTED\nHOW IT'S GOING"]
for i in range(12):
    folder = [WA, TG, DL, IG][i % 4]
    two = i % 3 != 2
    add("MEME", folder, f"meme_{i}.jpg", "image/jpeg", 720, 720 + 40 * (i % 3), random.randint(60_000, 200_000),
        bright=round(random.uniform(0.3, 0.7), 2), unif=0.25, text=memes[i % len(memes)],
        lines=2 if two else 1, cov=0.12, top=1, bottom=1 if two else 0,
        labels="Cartoon:0.72" if i % 2 == 0 else ("Poster:0.6" if i % 4 == 1 else "Person:0.7"))

greetings = ["Good Morning\nHave a blessed day", "Happy Diwali\nWishing you joy", "Good Night\nSweet dreams",
             "Shubho Sokal", "Suprabhat\nJai Shri Krishna", "Thought of the day\nLife is beautiful"]
for i in range(12):
    folder = WA if i < 9 else DL
    add("FORWARD", folder, f"IMG-2026-WA{100 + i:04d}.jpg", "image/jpeg", 1080, 1350, random.randint(80_000, 250_000),
        bright=round(random.uniform(0.4, 0.8), 2), unif=0.3, text=greetings[i % len(greetings)],
        lines=2, cov=round(random.uniform(0.05, 0.25), 2), top=1 if i % 4 == 0 else 0, bottom=0,
        labels="Flower:0.82" if i % 2 == 0 else "Sky:0.75")

receipts = ["Invoice No 4411\nSubtotal 450.00\nGST 18%\nTotal 531.00", "SUPERMART\nQty Amount\nTotal Rs. 882",
            "Electricity Bill\nDue date 12/11/2026\nAmount 1,240", "Payment successful\nUPI Txn ID 22331\n₹ 499",
            "Meeting notes\n- launch plan\n- budget review\n- hiring", "Boarding pass PNR X7Y2Z1 12/12/2026"]
for i in range(12):
    folder = CAM if i < 6 else (DL if i < 9 else WA)
    exif = ("Samsung", "SM-S928B") if folder == CAM else ("", "")
    add("DOCUMENT", folder, f"doc_{i}.jpg", "image/jpeg", 3000 if folder == CAM else 1240, 4000 if folder == CAM else 1754,
        random.randint(400_000, 2_000_000), exif[0], exif[1], 1 if exif[0] else 0, 0,
        round(random.uniform(0.65, 0.9), 2), round(random.uniform(0.45, 0.8), 2), receipts[i % len(receipts)],
        4, round(random.uniform(0.2, 0.5), 2), 0, 0, "Paper:0.8|Font:0.7")

out = sys.argv[1] if len(sys.argv) > 1 else "core/src/test/resources/eval/fixtures.csv"
with open(out, "w", newline="") as f:
    w = csv.writer(f)
    w.writerow(HEADER)
    w.writerows(rows)
print(f"wrote {len(rows)} fixtures to {out}")
