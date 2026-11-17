from PIL import Image,ImageDraw
from pathlib import Path
for n in [72,96]:
 for pressed in [False,True]:
  s=4;im=Image.new('RGB',(72*s,72*s),'black');d=ImageDraw.Draw(im)
  d.rounded_rectangle((4,4,284,284),22*s,fill='#343a40' if pressed else '#111315',outline='#d2d8df' if pressed else '#33383e',width=4)
  def line(points):d.line([(x*s,y*s)for x,y in points],fill='white',width=3*s,joint='curve')
  line([(29,20),(54,20),(54,46)]);line([(23,27),(47,27),(47,53)]);line([(17,34),(40,34),(40,58),(17,58),(17,34)])
  im.resize((n,n),Image.Resampling.LANCZOS).save(Path(__file__).parent/'assets/square390'/('multishot'+('96'if n==96 else'')+('-pressed'if pressed else'')+'.png'))
