import copy
import unittest
from verify_cohort import evaluate

def fixture():
    d={"schema":1,"code_sha":"a"*40,"natural_scene":True,"npc_ai":True,
       "cohort_ids":[1,2,3,4],"primary_ids":[1,2],"samples":[]}
    for s in range(0,361,10):
        c=s//50
        actors=[]
        for pid in d["cohort_ids"]:
            actors.append(dict(profile=pid,object=100+pid,epoch=500+pid,damage=c*3,
                kills=c,rewards=c,cycles=c,targets=c,exp=1000+c*70,sp=100+c*2,
                loot=c,overflow=False,incident=""))
        d["samples"].append(dict(elapsed=s,actors=actors))
    return d

class Tests(unittest.TestCase):
    def test_good_aggregate(self): self.assertTrue(evaluate(fixture())["pass"])
    def test_missing_actor(self):
        d=fixture(); d["samples"][4]["actors"].pop(); self.assertFalse(evaluate(d)["pass"])
    def test_epoch(self):
        d=fixture(); d["samples"][-1]["actors"][0]["epoch"]+=1; self.assertFalse(evaluate(d)["pass"])
    def test_overflow(self):
        d=fixture(); d["samples"][5]["actors"][0]["overflow"]=True; self.assertFalse(evaluate(d)["pass"])
    def test_one_good_rest_idle(self):
        d=fixture()
        for s in d["samples"]:
            for i in range(1,4): s["actors"][i]=copy.deepcopy(d["samples"][0]["actors"][i])
        self.assertFalse(evaluate(d)["pass"])
    def test_passive_npc(self):
        d=fixture();d["npc_ai"]=False;self.assertFalse(evaluate(d)["pass"])
    def test_short_run(self):
        d=fixture();d["samples"]=d["samples"][:7];self.assertFalse(evaluate(d)["pass"])
    def test_regressed_counter(self):
        d=fixture(); d["samples"][15]["actors"][0]["exp"]=1;self.assertFalse(evaluate(d)["pass"])
    def test_missing_counter(self):
        d=fixture();del d["samples"][0]["actors"][0]["damage"];self.assertFalse(evaluate(d)["pass"])
    def test_bad_primary(self):
        d=fixture(); d["primary_ids"]=[1,1];self.assertFalse(evaluate(d)["pass"])
    def test_incident(self):
        d=fixture();d["samples"][3]["actors"][0]["incident"]="DRAINING";self.assertFalse(evaluate(d)["pass"])
    def test_early_burst_then_idle(self):
        d=fixture()
        for i,sample in enumerate(d["samples"]):
            c=min(i//4,5)
            for a in sample["actors"]:
                a.update(damage=c*3,kills=c,rewards=c,cycles=c,targets=c,
                         exp=1000+c*70,sp=100+c*2,loot=c)
        result=evaluate(d)
        self.assertFalse(result["pass"])
        self.assertTrue(any("final120s" in e for e in result["errors"]))
    def test_gap(self):
        d=fixture();d["samples"].pop(15);self.assertFalse(evaluate(d)["pass"])

if __name__ == "__main__": unittest.main()
