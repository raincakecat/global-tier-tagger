import urllib.request,json,concurrent.futures
from pathlib import Path
main=['crystal','sword','axe','smp','pot','mace','uhc','nethpot','spear-mace']
sets=[['1529032989248585778','1510869328793440286','1389199593799028747','1529034975729418360','1559425945016860702','1391859118892912752','1559428125891371089','1476510781607510067','1547107112495095849'],['1565462440408252426','1552740204631293992','1557745505818386573','1476510940294938665','1552741121858473995','1557746029246418964','1557747136576233512','1411267823870939197','1569942731210358874']]
fixed=dict(zip(['minecart','og-vanilla','debuff','elytra','manhunt','trident','speed','diamond-vanilla','bed','creeper','diamond-smp','bow'],['1491790929982656673','1570205072221012108','1368570447578533908','1346304893576675338','1250488848304308308','1346304943509995603','1273644050561503374','1536440635341672469','1546636583241916527','1273825972436598848','1471685837099106537','1522235676987228340']))
root=Path('client/src/main/resources/assets/multi_tier_tagger');dest=root/'textures/icons';dest.mkdir(parents=True,exist_ok=True)
providers=[];manifest=[];jobs=[]
for si,ids in enumerate(sets):
 for i,(mode,id) in enumerate(zip(main,ids)):
  name=f'{mode}-set{si+1}';code=0xe000+si*9+i;jobs.append((name,id,code))
for i,(mode,id) in enumerate(fixed.items()):jobs.append((mode,id,0xe020+i))
def fetch(row):
 name,id,code=row;url=f'https://cdn.discordapp.com/emojis/{id}.png';path=dest/(name+'.png')
 if not path.exists():
  try: raw=urllib.request.urlopen(urllib.request.Request(url,headers={"User-Agent":"GlobalTierTagger/0.3"}),timeout=15).read()
  except Exception as e: raise RuntimeError(name+": "+str(e))
  if not raw.startswith(b'\x89PNG'):raise ValueError(name+' is not PNG')
  path.write_bytes(raw)
 return {'type':'bitmap','file':f'multi_tier_tagger:icons/{name}.png','ascent':7,'height':8,'chars':[chr(code)]},{'name':name,'discordId':id,'glyph':code,'source':url}
with concurrent.futures.ThreadPoolExecutor(max_workers=6) as ex:
 for p,m in ex.map(fetch,jobs):providers.append(p);manifest.append(m)
(root/'font').mkdir(exist_ok=True);(root/'font/icons.json').write_text(json.dumps({'providers':providers},indent=2));(root/'icons-manifest.json').write_text(json.dumps(manifest,indent=2));print('Saved',len(manifest),'icons')

