"""Render the original geometric phone status glyph, matching watch line icons."""
from PIL import Image,ImageDraw
from pathlib import Path
im=Image.new('RGB',(384,384),'black');d=ImageDraw.Draw(im)
d.rounded_rectangle((96,24,288,360),radius=32,outline='#f0f3f7',width=16)
d.rounded_rectangle((160,304,224,320),radius=8,fill='#f0f3f7')
im.resize((96,96),Image.Resampling.LANCZOS).save(Path(__file__).parent/'assets/square390/phone96.png')
