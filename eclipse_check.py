"""Checks the eclipse maths used in RealSky.java against known eclipses (2026-2027)."""
import math, datetime as dt
R=math.radians; S=lambda x: math.sin(R(x)); C=lambda x: math.cos(R(x))
J2000=dt.datetime(2000,1,1,12,tzinfo=dt.timezone.utc)
LON=[(6.288774,0,0,1,0),(1.274027,2,0,-1,0),(0.658314,2,0,0,0),(0.213618,0,0,2,0),(-0.185116,0,1,0,0),
     (-0.114332,0,0,0,2),(0.058793,2,0,-2,0),(0.057066,2,-1,-1,0),(0.053322,2,0,1,0),(0.045758,2,-1,0,0),
     (-0.040923,0,1,-1,0),(-0.034720,1,0,0,0),(-0.030383,0,1,1,0),(0.015327,2,0,0,-2),(-0.012528,0,0,1,2),
     (0.010980,0,0,1,-2),(0.010675,4,0,-1,0),(0.010034,0,0,3,0),(0.008548,4,0,-2,0),(-0.007888,2,1,-1,0),
     (-0.006766,2,1,0,0),(-0.005163,1,0,-1,0),(0.004987,1,1,0,0),(0.004036,2,-1,1,0)]
LAT=[(5.128122,0,0,0,1),(0.280602,0,0,1,1),(0.277693,0,0,1,-1),(0.173237,2,0,0,-1),(0.055413,2,0,-1,1),
     (0.046271,2,0,-1,-1),(0.032573,2,0,0,1),(0.017198,0,0,2,1),(0.009266,2,0,1,-1),(0.008822,0,0,2,-1)]
PAR=[(0.950724,0,0,0,0),(0.051818,0,0,1,0),(0.009531,2,0,-1,0),(0.007843,2,0,0,0),(0.002824,0,0,2,0)]
def elements(t):
    d=(t-J2000).total_seconds()/86400
    return d,(297.8501921+12.19074912*d, 357.5291092+0.98560028*d, 134.9633964+13.06499295*d, 93.2720950+13.22935024*d)
def moon(t):
    d,(D,M,Mp,F)=elements(t); arg=lambda a,b,c,e: a*D+b*M+c*Mp+e*F
    lam=218.3164477+13.17639648*d+sum(k*S(arg(a,b,c,e)) for k,a,b,c,e in LON)
    beta=sum(k*S(arg(a,b,c,e)) for k,a,b,c,e in LAT)
    par=sum(k*C(arg(a,b,c,e)) for k,a,b,c,e in PAR)
    lsun=280.46646+0.98564736*d+(1.914602)*S(M)+0.019993*S(2*M)
    return lam%360,beta,par,lsun%360
def sep(l1,b1,l2,b2): return math.degrees(math.acos(max(-1,min(1,S(b1)*S(b2)+C(b1)*C(b2)*C(l1-l2)))))
SUN_R, SUN_PAR = 0.2666, 0.00244
def overlap(r1,r2,d):
    """Fraction of disc r1's area covered by disc r2 at centre distance d."""
    if d>=r1+r2: return 0.0
    if d<=abs(r2-r1): return 1.0 if r2>=r1 else (r2*r2)/(r1*r1)
    a=r1*r1*math.acos((d*d+r1*r1-r2*r2)/(2*d*r1))+r2*r2*math.acos((d*d+r2*r2-r1*r1)/(2*d*r2))-0.5*math.sqrt((-d+r1+r2)*(d+r1-r2)*(d-r1+r2)*(d+r1+r2))
    return a/(math.pi*r1*r1)
def geo(t):
    l,b,p,ls=moon(t); return sep(l,b,ls,0), p
def solar(t):
    """Coverage for an observer on the eclipse's centre line (RealSky.solarCoverage)."""
    s,p=geo(t); rm=0.2725*p
    if s>p+SUN_R+rm+0.1: return 0.0
    smin=min(geo(t+dt.timedelta(minutes=m))[0] for m in range(-240,241,5))
    if smin>=p+SUN_R+rm: return 0.0
    along=math.sqrt(max(0,s*s-smin*smin)); miss=max(0,smin-p)
    return overlap(SUN_R,rm,math.hypot(along,miss))
def lunar(t):
    l,b,p,ls=moon(t); s=sep(l,b,ls+180,0); moon_r=0.2725*p
    umbra=1.02*(p-SUN_R+SUN_PAR); pen=1.02*(p+SUN_R+SUN_PAR)
    umag=(umbra+moon_r-s)/(2*moon_r); pmag=(pen+moon_r-s)/(2*moon_r)
    return umag,pmag
if __name__=='__main__':
    t=dt.datetime(2026,1,1,tzinfo=dt.timezone.utc); end=dt.datetime(2028,1,1,tzinfo=dt.timezone.utc); best={}
    while t<end:
        c=solar(t)
        if c>0:
            k=('SOLAR',t.strftime('%Y-%m'))
            if k not in best or c>best[k][1]: best[k]=(t,c)
        u,p=lunar(t)
        if p>0:
            k=('LUNAR',t.strftime('%Y-%m'))
            if k not in best or p>best[k][2]: best[k]=(t,u,p)
        t+=dt.timedelta(minutes=10)
    for k,v in sorted(best.items(),key=lambda x:x[1][0]):
        if k[0]=='SOLAR': print(f"SOLAR {v[0]:%Y-%m-%d %H:%M} max coverage {v[1]:.3f}")
        else: print(f"LUNAR {v[0]:%Y-%m-%d %H:%M} umbral {v[1]:+.3f} penumbral {v[2]:.3f}")
