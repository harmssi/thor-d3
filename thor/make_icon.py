# Thor Edition launcher icon (own art, no game or Blizzard artwork): dark iron disc, red ember glow, gold "III" over "THOR".
# Run: python3 thor/make_icon.py dm/android/d3/icon.png   (needs Pillow; macOS Copperplate font)
import math, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont
S = 512
img = Image.new('RGB', (S, S), (8, 4, 4))
glow = Image.new('L', (S, S), 0); g = ImageDraw.Draw(glow)
for r in range(260, 0, -4): g.ellipse((S/2-r, S/2+40-r, S/2+r, S/2+40+r), fill=int(150*(1-r/260)**1.6))
img.paste((170, 28, 18), (0, 0), glow.filter(ImageFilter.GaussianBlur(18)))
d = ImageDraw.Draw(img)
gold, bronze = (214, 172, 104), (110, 70, 42)
d.ellipse((34, 34, S-34, S-34), outline=bronze, width=14); d.ellipse((52, 52, S-52, S-52), outline=gold, width=4)
for i in range(24):
    t = math.radians(i * 15); r0, r1 = (196, 222) if i % 2 else (190, 228)
    d.line((S/2 + r0*math.cos(t), S/2 + r0*math.sin(t), S/2 + r1*math.cos(t), S/2 + r1*math.sin(t)), fill=gold, width=3)
big = ImageFont.truetype('/System/Library/Fonts/Supplemental/Copperplate.ttc', 210, index=1)
small = ImageFont.truetype('/System/Library/Fonts/Supplemental/Copperplate.ttc', 66, index=1)
shadow = Image.new('L', (S, S), 0); sd = ImageDraw.Draw(shadow)
sd.text((S/2, 238), 'III', font=big, anchor='mm', fill=255); sd.text((S/2, 372), 'THOR', font=small, anchor='mm', fill=255)
img.paste((0, 0, 0), (0, 0), shadow.filter(ImageFilter.GaussianBlur(8)))
d.text((S/2, 238), 'III', font=big, anchor='mm', fill=gold); d.text((S/2, 372), 'THOR', font=small, anchor='mm', fill=(232, 80, 56))
img.save(sys.argv[1])
