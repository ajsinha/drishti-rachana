/*
 * Copyright (c) 2026 Ashutosh Sinha <ajsinha@gmail.com>. All rights reserved.
 * Drishti Rachana. Proprietary: see the LICENSE file at the root of this repository.
 */
package com.ash.rachana.internal;

import java.util.Map;
import java.util.LinkedHashMap;

/**
 * Core's text rules, owned by Core so that every runtime (any JVM, the JavaScript build) gives the same answer: upper and lower
 * case (the tables below are the case mappings of Java 21, Unicode 15.0, with {@code Locale.ROOT}: one-to-one mappings plus the
 * full upper-case expansions such as {@code ß} to {@code SS}; lower case adds {@code İ} to {@code i̇} and the final sigma),
 * white space (Java's {@code Character.isWhitespace} set) and reversal by code point (a surrogate pair stays a pair).
 * {@code CoreTextTest} checks the tables against the JDK for every code point. Static, stateless, thread-safe.
 */
public final class CoreText {

    // the tables: "start,count,stride,delta;" in base 36 (a run of code points mapped by the same delta), and for the full
    // upper-case expansions "code point:code point code point…;"; written by CoreTextTest.tables() from Java 21
    private static final String UPPER_TABLE =
            "2p,26,1,-w;51,1,1,kn;68,23,1,-w;6w,7,1,-w;73,1,1,3d;75,24,2,-1;8h,1,1,-6g;8j,3,2,-1;8q,8,2,-1;97,23,2,-1;ai,3,"
            + "2,-1;an,1,1,-8c;ao,1,1,5f;ar,2,2,-1;aw,1,1,-1;b0,1,1,-1;b6,1,1,-1;b9,1,1,2p;bd,1,1,-1;be,1,1,4j;bi,1,1,3m;bl,3"
            + ",2,-1;bs,1,1,-1;bx,1,1,-1;c0,1,1,-1;c4,2,2,-1;c9,1,1,-1;cd,1,1,-1;cf,1,1,1k;cl,1,1,-1;cm,1,1,-2;co,1,1,-1;cp,1"
            + ",1,-2;cr,1,1,-1;cs,1,1,-2;cu,8,2,-1;d9,1,1,-27;db,9,2,-1;du,1,1,-1;dv,1,1,-2;dx,1,1,-1;e1,20,2,-1;f7,9,2,-1;fw"
            + ",1,1,-1;fz,2,1,8cf;g2,1,1,-1;g7,5,2,-1;gg,1,1,8bj;gh,1,1,8bg;gi,1,1,8bi;gj,1,1,-5u;gk,1,1,-5q;gm,2,1,-5p;gp,1,"
            + "1,-5m;gr,1,1,-5n;gs,1,1,wnj;gw,1,1,-5p;gx,1,1,wnf;gz,1,1,-5r;h1,1,1,wmg;h2,1,1,wn8;h4,1,1,-5t;h5,1,1,-5v;h6,1,"
            + "1,wn8;h7,1,1,8af;h8,1,1,wn5;hb,1,1,-5v;hd,1,1,8al;he,1,1,-5x;hh,1,1,-5y;hp,1,1,89z;hs,1,1,-62;hu,1,1,wn7;hv,1,"
            + "1,-62;hz,1,1,wmi;i0,1,1,-62;i1,1,1,-1x;i2,2,1,-61;i4,1,1,-1z;ia,1,1,-63;il,1,1,wlx;im,1,1,wlu;n9,1,1,2c;oh,2,2"
            + ",-1;on,1,1,-1;or,3,1,3m;q4,1,1,-12;q5,3,1,-11;q9,17,1,-w;qq,1,1,-v;qr,9,1,-w;r0,1,1,-1s;r1,2,1,-1r;r4,1,1,-1q;"
            + "r5,1,1,-1l;r9,1,1,-1b;ra,1,1,-1i;rb,1,1,-8;rd,12,2,-1;s0,1,1,-2e;s1,1,1,-28;s2,1,1,7;s3,1,1,-38;s5,1,1,-2o;s8,"
            + "1,1,-1;sb,1,1,-1;ts,32,1,-w;uo,16,1,-28;v5,17,2,-1;wb,27,2,-1;xu,7,2,-1;y7,1,1,-f;y9,48,2,-1;129,38,1,-1c;3bk,"
            + "43,1,2bk;3ct,3,1,2bk;3y0,6,1,-8;5mo,1,1,-4tq;5mp,1,1,-4tp;5mq,1,1,-4tg;5mr,2,1,-4te;5mt,1,1,-4tf;5mu,1,1,-4t8;"
            + "5mv,1,1,-4rp;5mw,1,1,r7m;5tl,1,1,r9g;5tp,1,1,2xy;5u6,1,1,raw;5xd,75,2,-1;61n,1,1,-1n;61t,48,2,-1;64g,8,1,8;64w"
            + ",6,1,8;65c,8,1,8;65s,8,1,8;668,6,1,8;66p,4,2,8;674,8,1,8;67k,2,1,22;67m,4,1,2e;67q,2,1,2s;67s,2,1,3k;67u,2,1,3"
            + "4;67w,2,1,3i;680,8,1,8;68g,8,1,8;68w,8,1,8;69c,2,1,8;69f,1,1,9;69q,1,1,-5k5;69v,1,1,9;6a8,2,1,8;6ao,2,1,8;6at,"
            + "1,1,7;6b7,1,1,9;6ku,1,1,-s;6ls,16,1,-g;6mc,1,1,-1;79s,26,1,-q;8q8,48,1,-1c;8rl,1,1,-1;8rp,1,1,-8bv;8rq,1,1,-8b"
            + "s;8rs,3,2,-1;8s3,1,1,-1;8s6,1,1,-1;8sh,50,2,-1;8vg,2,2,-1;8vn,1,1,-1;8w0,38,1,-5ls;8x3,1,1,-5ls;8x9,1,1,-5ls;w"
            + "u9,23,2,-1;ww1,14,2,-1;x0j,7,2,-1;x0z,31,2,-1;x2y,2,2,-1;x33,5,2,-1;x3g,1,1,-1;x3l,2,2,-1;x3o,1,1,1c;x3r,10,2,"
            + "-1;x4l,8,2,-1;x54,2,2,-1;x5d,1,1,-1;x5j,2,2,-1;x6e,1,1,-1;xub,1,1,-ps;xv4,80,1,-tzk;1ef5,26,1,-w;1fe0,40,1,-14"
            + ";1fiw,36,1,-14;1fo7,11,1,-13;1foj,15,1,-13;1foz,7,1,-13;1fp7,2,1,-13;1h34,51,1,-1s;1jgg,32,1,-w;20dc,32,1,-w;2"
            + "oma,34,1,-y;";

    private static final String LOWER_TABLE =
            "1t,26,1,w;5c,23,1,w;60,7,1,w;74,24,2,1;8g,1,1,-5j;8i,3,2,1;8p,8,2,1;96,23,2,1;ag,1,1,-3d;ah,3,2,1;ap,1,1,5u;aq"
            + ",2,2,1;au,1,1,5q;av,1,1,1;ax,2,1,5p;az,1,1,1;b2,1,1,27;b3,1,1,5m;b4,1,1,5n;b5,1,1,1;b7,1,1,5p;b8,1,1,5r;ba,1,1"
            + ",5v;bb,1,1,5t;bc,1,1,1;bg,1,1,5v;bh,1,1,5x;bj,1,1,5y;bk,3,2,1;bq,1,1,62;br,1,1,1;bt,1,1,62;bw,1,1,1;by,1,1,62;"
            + "bz,1,1,1;c1,2,1,61;c3,2,2,1;c7,1,1,63;c8,1,1,1;cc,1,1,1;ck,1,1,2;cl,1,1,1;cn,1,1,2;co,1,1,1;cq,1,1,2;cr,9,2,1;"
            + "da,9,2,1;dt,1,1,2;du,2,2,1;dy,1,1,-2p;dz,1,1,-1k;e0,20,2,1;f4,1,1,-3m;f6,9,2,1;fu,1,1,8bv;fv,1,1,1;fx,1,1,-4j;"
            + "fy,1,1,8bs;g1,1,1,1;g3,1,1,-5f;g4,1,1,1x;g5,1,1,1z;g6,5,2,1;og,2,2,1;om,1,1,1;ov,1,1,38;p2,1,1,12;p4,3,1,11;p8"
            + ",1,1,1s;pa,2,1,1r;pd,17,1,w;pv,9,1,w;r3,1,1,8;rc,12,2,1;s4,1,1,-1o;s7,1,1,1;s9,1,1,-7;sa,1,1,1;sd,3,1,-3m;sg,1"
            + "6,1,28;sw,32,1,w;v4,17,2,1;wa,27,2,1;xs,1,1,f;xt,7,2,1;y8,48,2,1;10x,38,1,1c;3a8,38,1,5ls;3bb,1,1,5ls;3bh,1,1,"
            + "5ls;3vk,80,1,tzk;3xs,6,1,8;5n4,43,1,-2bk;5od,3,1,-2bk;5xc,75,2,1;61q,1,1,-5vj;61s,48,2,1;64o,8,1,-8;654,6,1,-8"
            + ";65k,8,1,-8;660,8,1,-8;66g,6,1,-8;66x,4,2,-8;67c,8,1,-8;688,8,1,-8;68o,8,1,-8;694,8,1,-8;69k,2,1,-8;69m,2,1,-2"
            + "2;69o,1,1,-9;6a0,4,1,-2e;6a4,1,1,-9;6ag,2,1,-8;6ai,2,1,-2s;6aw,2,1,-8;6ay,2,1,-34;6b0,1,1,-7;6bc,2,1,-3k;6be,2"
            + ",1,-3i;6bg,1,1,-9;6jq,1,1,-5st;6ju,1,1,-6gv;6jv,1,1,-6di;6k2,1,1,s;6lc,16,1,g;6mb,1,1,1;792,26,1,q;8ow,48,1,1c"
            + ";8rk,1,1,1;8rm,1,1,-8af;8rn,1,1,-2xy;8ro,1,1,-89z;8rr,3,2,1;8rx,1,1,-8bg;8ry,1,1,-8al;8rz,1,1,-8bj;8s0,1,1,-8b"
            + "i;8s2,1,1,1;8s5,1,1,1;8se,2,1,-8cf;8sg,50,2,1;8vf,2,2,1;8vm,1,1,1;wu8,23,2,1;ww0,14,2,1;x0i,7,2,1;x0y,31,2,1;x"
            + "2x,2,2,1;x31,1,1,-r9g;x32,5,2,1;x3f,1,1,1;x3h,1,1,-wmg;x3k,2,2,1;x3q,10,2,1;x4a,1,1,-wn8;x4b,1,1,-wnj;x4c,1,1,"
            + "-wnf;x4d,1,1,-wn5;x4e,1,1,-wn8;x4g,1,1,-wlu;x4h,1,1,-wmi;x4i,1,1,-wlx;x4j,1,1,ps;x4k,8,2,1;x50,1,1,-1c;x51,1,1"
            + ",-wn7;x52,1,1,-raw;x53,2,2,1;x5c,1,1,1;x5i,2,2,1;x6d,1,1,1;1ee9,26,1,w;1fcw,40,1,14;1fhs,36,1,14;1fn4,11,1,13;"
            + "1fng,15,1,13;1fnw,7,1,13;1fo4,2,1,13;1h1c,51,1,1s;1jfk,32,1,w;20cg,32,1,w;2olc,34,1,y;";

    private static final String MULTI_TABLE =
            "67:2b 2b;95:jg 26;ds:22 lo;pc:pl lk ld;q8:px lk ld;13b:111 11u;61i:20 mp;61j:2c lk;61k:2f lm;61l:2h lm;61m:1t "
            + "ji;66o:px lv;66q:px lv lc;66s:px lv ld;66u:px lv n6;680:64o pl;681:64p pl;682:64q pl;683:64r pl;684:64s pl;685"
            + ":64t pl;686:64u pl;687:64v pl;688:64o pl;689:64p pl;68a:64q pl;68b:64r pl;68c:64s pl;68d:64t pl;68e:64u pl;68f"
            + ":64v pl;68g:65k pl;68h:65l pl;68i:65m pl;68j:65n pl;68k:65o pl;68l:65p pl;68m:65q pl;68n:65r pl;68o:65k pl;68p"
            + ":65l pl;68q:65m pl;68r:65n pl;68s:65o pl;68t:65p pl;68u:65q pl;68v:65r pl;68w:67c pl;68x:67d pl;68y:67e pl;68z"
            + ":67f pl;690:67g pl;691:67h pl;692:67i pl;693:67j pl;694:67c pl;695:67d pl;696:67e pl;697:67f pl;698:67g pl;699"
            + ":67h pl;69a:67i pl;69b:67j pl;69e:69m pl;69f:pd pl;69g:p2 pl;69i:pd n6;69j:pd n6 pl;69o:pd pl;69u:6a2 pl;69v:p"
            + "j pl;69w:p5 pl;69y:pj n6;69z:pj n6 pl;6a4:pj pl;6aa:pl lk lc;6ab:pl lk ld;6ae:pl n6;6af:pl lk n6;6aq:px lk lc;"
            + "6ar:px lk ld;6as:pt lv;6au:px n6;6av:px lk n6;6b6:6be pl;6b7:q1 pl;6b8:pb pl;6ba:q1 n6;6bb:q1 n6 pl;6bg:q1 pl;"
            + "1dkw:1y 1y;1dkx:1y 21;1dky:1y 24;1dkz:1y 1y 21;1dl0:1y 1y 24;1dl1:2b 2c;1dl2:2b 2c;1dlf:11g 11i;1dlg:11g 111;1"
            + "dlh:11g 117;1dli:11q 11i;1dlj:11g 119;";

    // the cased letters as Java's lower case sees them (general categories Lu, Ll, Lt and its fixed list of Other_Lowercase
    // and Other_Uppercase ranges) as "start-end;" ranges in base 36, for the final sigma
    private static final String CASED_TABLE =
            "1t-2i;2p-3e;51;5c-5y;60-6u;6w-ca;cc-cf;ck-ib;id-jc;jk-jl;kg-kk;n9;og-oj;om-on;oq-ot;ov;p2;p4-p6;p8;pa-pt;pv-s5"
            + ";s7-w1;wa-10v;10x-11y;128-13c;3a8-3b9;3bb;3bh;3bk-3cq;3ct-3cv;3vk-3xx;3y0-3y5;5mo-5mw;5n4-5oa;5od-5of;5q8-5sx;"
            + "5t7-5tj;5tl-5ui;5xc-651;654-659;65c-66d;66g-66l;66o-66v;66x;66z;671;673-67x;680-69g;69i-69o;69q;69u-69w;69y-6a"
            + "4;6a8-6ab;6ae-6aj;6ao-6b0;6b6-6b8;6ba-6bg;6iq;6iv;6iy-6j7;6j9;6jd-6jh;6jo;6jq;6js;6ju-6jx;6jz-6k4;6k9;6kc-6kf;"
            + "6kl-6kp;6ku;6lc-6m7;6mb-6mc;792-7ah;8ow-8sb;8se-8v8;8vf-8vi;8vm-8vn;8w0-8x1;8x3;8x9;wu8-wvh;ww0-wwr;x0i-x2n;x2"
            + "p-x3b;x3f-x3i;x3k-x56;x5c-x5d;x5f;x5h-x5l;x6d-x6e;x6i;xtc-xui;xuo-xuw;xv4-xxb;1dkw-1dl2;1dlf-1dlj;1ee9-1eey;1e"
            + "f5-1efu;1fcw-1ff3;1fhs-1fir;1fiw-1fjv;1fn4-1fne;1fng-1fnu;1fnw-1fo2;1fo4-1fo5;1fo7-1foh;1foj-1fox;1foz-1fp5;1f"
            + "p7-1fp8;1h1c-1h2q;1h34-1h4i;1jfk-1jhb;20cg-20e7;2kg0-2kic;2kie-2kkc;2kke-2kkf;2kki;2kkl-2kkm;2kkp-2kks;2kku-2k"
            + "l5;2kl7;2kl9-2klf;2klh-2kn9;2knb-2kne;2knh-2kno;2knq-2knw;2kny-2kop;2kor-2kou;2kow-2kp0;2kp2;2kp6-2kpc;2kpe-2k"
            + "yt;2kyw-2kzk;2kzm-2l0a;2l0c-2l16;2l18-2l1w;2l1y-2l2s;2l2u-2l3i;2l3k-2l4e;2l4g-2l54;2l56-2l60;2l62-2l6q;2l6s-2l"
            + "6z;2mm8-2mmh;2mmj-2mn2;2mn9-2mne;2olc-2on7;";

    // the code points that end the word around a sigma for Java 21's lower case (found by asking the JDK, "aΣ" + c + "a"),
    // as "start-end;" ranges in base 36
    private static final String BOUNDARY_TABLE =
            "0-x;z-12;14-18;1b;1m-1s;2j-2m;2o;3f-4p;4r-4s;4u-4x;50;52-54;57;5b;5z;6v;jm-jp;k2-kf;kl-kr;kt;kv-lb;ol;oo-op;ou"
            + ";ow-p1;p3;p7;p9;pu;s6;w2;10w;11z-120;122-127;13d;13f-13k;14w;14z;152;154-15b;163-166;16b-16n;16u-173;17f;17h-1"
            + "7j;19m-19p;1ck;1cu;1d5;1dp-1dq;1ds-1e6;1fv-1fw;1iq-1j3;1km-1kp;1kr-1ks;1ku-1kv;1m6-1mn;1ng-1nj;1nv-1nz;1oo;1ov"
            + ";1oy-1p3;1us-1ut;1v4;1vo;1vx-1vy;1w1-1w2;1wp;1wx;1wz-1x1;1x6-1x7;1xh-1xi;1xl-1xm;1xr-1xy;1y0-1y3;1y6;1yc-1yd;1"
            + "yq-1yr;1yy-1yz;1z1;1z3-1z4;1z8;1zf-1zi;1zl-1zm;209;20h;20k;20n;20q-20r;20t;20z-212;215-216;21a-21c;21e-21k;21p"
            + ";21r-21x;22e-22o;22s;232;236;23t;241;244;24a-24b;24m;24q;24u-24v;24x-25b;25g-25h;25s-260;268;26c;26l-26m;26p-2"
            + "6q;27d;27l;27o;27u-27v;285-286;289-28a;28e-28k;28o-28r;28u;290-291;29c;29k-29t;29w;2a3-2a5;2a9;2ae-2ag;2aj;2al"
            + ";2ao-2aq;2at-2av;2az-2b1;2be-2bh;2bn-2bp;2bt;2by-2bz;2c1-2c6;2c8-2cl;2cz-2db;2dp;2dt;2eh;2ey-2ez;2f9;2fd;2fi-2"
            + "fo;2fr;2fv-2fw;2fy-2fz;2g4-2g5;2gg-2gn;2gv;2h0;2h9;2hd;2i1;2ic;2ii-2ij;2it;2ix;2j2-2j8;2jb-2jg;2jj;2jo-2jp;2k0"
            + ";2k4-2kf;2kt;2kx;2md;2mh;2mn-2mr;2n8-2n9;2nt;2o0;2o4;2on-2op;2pe;2po;2pq-2pr;2pz-2q1;2q3-2q6;2qd;2qf;2qo-2qt;2"
            + "r4-2r5;2r8-2rk;2t7-2tb;2tr;2u2-2v4;2v7;2v9;2vf;2w4;2w6;2wu-2wv;2x1;2x3;2xb;2xm-2xn;2xs-2yn;2yp-2zb;2ze-2zj;304"
            + ";306;308;30a-30d;30o;31p-31s;32d;32w;33x-345;347-35r;37u-37z;3a6-3a7;3ba;3bc-3bg;3bi-3bj;3cr;3m1;3m6-3m7;3mf;3"
            + "mh;3mm-3mn;3nt;3ny-3nz;3ox;3p2-3p3;3pb;3pd;3pi-3pj;3pz;3rl;3rq-3rr;3tn-3to;3ts-3u0;3ul-3un;3v4-3vj;3xy-3xz;3y6"
            + "-3y7;4fh-4fi;4g0;4gr-4gv;4iz-4j1;4jd-4jj;4k6-4ke;4l1-4lb;4lw-4m7;4ml;4mp;4ms-4n3;4pg-4pi;4pk-4pn;4pq-4pr;4q2-4"
            + "q7;4qi-4qt;4qv-4qy;4re-4rj;4u1-4u7;4vf-4vj;4xi-4xr;4yn;4z0-4z3;4zg-4zp;50u-50v;511-51b;52k-52n;53e-53j;53v-54v"
            + ";55o-55r;57j;58d-58e;58q-58v;596-59i;59k-59r;5an-5bz;5e5-5e7;5ei-5ey;5f8-5fj;5is-5j3;5ko-5kv;5l6-5l8;5mm-5mn;5"
            + "mx-5n3;5ob-5oc;5og-5ov;5oz;5q3-5q7;652-653;65a-65b;66e-66f;66m-66n;66w;66y;670;672;67y-67z;69h;69p;69r-69t;69x"
            + ";6a5-6a7;6ac-6ad;6ak-6an;6b1-6b5;6b9;6bh-6bu;6c6-6cm;6co-6cp;6cv-6da;6dd-6dv;6dx-6e7;6ed;6eq-6er;6ey-6f2;6fe-6"
            + "fj;6fx-6hb;6i9-6ip;6ir-6iu;6iw-6ix;6j8;6ja-6jc;6ji-6jn;6jp;6jr;6jt;6jy;6ka-6kb;6kg-6kk;6kq-6kt;6kv;6mi-76n;78c"
            + "-7ah;7b4-7sl;7tg-8ov;8v9-8ve;8vo-8vw;8vy-8vz;8x2;8x4-8x8;8xa-8xb;8yw-8z2;8z4-8zi;907-90f;90n;90v;913;91b;91j;9"
            + "1r;91z;927;934-93q;93s-93t;93v-94e;94g-94p;94s-94v;94x-95o;95q-9hh;9hk-9i3;9i5-9i8;9iu-9iv;9j1-9lg;9lj-9lk;9ln"
            + "-9lq;9lt-9oe;9og-9ok;9ps;9sf-9sh;9sm-9sv;9ts-9v3;9vk-9wf;9wq-9xj;9xs;9y8-9z3;9ze-a0g;a0w-a9r;fcw-vj9;wi5-wjz;w"
            + "la-wlb;wst-wsv;wto-wu7;wvn;wvy;wz6-x06;x0g-x0h;x3d-x3e;x57-x5b;x5e;x5g;x5m-x69;x7s-x7v;x7x-x7z;x86-x8f;x9w-xa7"
            + ";xc6-xcf;xcq-xcv;xdk-xdm;xdo;xf2-xf3;xg4-xgf;xh9-xhb;xj5-xji;xju-xjz;xkv;xmf-xmn;xn2-xn3;xne-xnj;xo7-xo9;xqb-x"
            + "qy;xr2-xr3;xrk-xrl;xrr-xs0;xs7-xs8;xsf-xsg;xsn-xsv;xt3;xtb;xuj;xuy-xv3;xyj;xym-xyn;xyy-xz3;16lg-16lr;16mf-16mi"
            + ";16nw-16nz;188w-1df1;1dgu-1dgv;1dju-1dkv;1dl3-1dle;1dlk-1dlo;1dm1;1dmf;1dml;1dmn;1dmq;1dmt;1dpu-1dqq;1e0u-1e1b"
            + ";1e34-1e35;1e4o-1e5r;1e64-1e67;1e6o-1e73;1e7k;1e7p-1e8c;1e8g-1e8n;1e8p-1e8y;1e90-1e9b;1e9h;1ed9-1eda;1edc-1edo"
            + ";1edq-1edr;1ee2-1ee8;1eez-1ef2;1ef4;1efv-1eg5;1ein-1eip;1eiw-1eix;1ej4-1ej5;1ejc-1ejd;1ejh-1ek8;1ekc-1fcv;1ff4"
            + "-1fhr;1fis-1fiv;1fjw-1fn3;1fnf;1fnv;1fo3;1fo6;1foi;1foy;1fp6;1fp9-1h1b;1h2r-1h33;1h4j-1jfj;1jhc-20cf;20e8-2kfz"
            + ";2kid;2kkd;2kkg-2kkh;2kkj-2kkk;2kkn-2kko;2kkt;2kl6;2kl8;2klg;2kna;2knf-2kng;2knp;2knx;2koq;2kov;2kp1;2kp3-2kp5"
            + ";2kpd;2kyu-2kyv;2kzl;2l0b;2l17;2l1x;2l2t;2l3j;2l4f;2l55;2l61;2l6r;2l70-2mm7;2mmi;2mn3-2mn8;2mnf-2olb;2on8-nvnj"
            + ";";

    private static final Map<Integer, Integer> UPPER = runs(UPPER_TABLE);
    private static final Map<Integer, Integer> LOWER = runs(LOWER_TABLE);
    private static final Map<Integer, String> UPPER_FULL = expansions(MULTI_TABLE);
    private static final int[] CASED = ranges(CASED_TABLE);
    private static final int[] BOUNDARY = ranges(BOUNDARY_TABLE);
    private static final int CAPITAL_I_DOT = 0x130;
    private static final int CAPITAL_SIGMA = 0x3A3;
    private static final int FINAL_SIGMA = 0x3C2;

    private CoreText() {
    }

    private static Map<Integer, Integer> runs(String table) {
        Map<Integer, Integer> m = new LinkedHashMap<>();
        for (String run : table.split(";")) {
            String[] f = run.split(",");
            int start = Integer.parseInt(f[0], 36);
            int count = Integer.parseInt(f[1]);
            int stride = Integer.parseInt(f[2]);
            int delta = Integer.parseInt(f[3], 36);
            for (int k = 0; k < count; k++) {
                m.put(start + k * stride, start + k * stride + delta);
            }
        }
        return m;
    }

    private static Map<Integer, String> expansions(String table) {
        Map<Integer, String> m = new LinkedHashMap<>();
        for (String e : table.split(";")) {
            int colon = e.indexOf(':');
            StringBuilder b = new StringBuilder();
            for (String cp : e.substring(colon + 1).split(" ")) {
                b.appendCodePoint(Integer.parseInt(cp, 36));
            }
            m.put(Integer.parseInt(e.substring(0, colon), 36), b.toString());
        }
        return m;
    }

    /** The one-to-one upper case of a code point (no expansion): Java 21's {@code Character.toUpperCase(int)}. */
    public static int upper(int cp) {
        return cp < 128 ? (cp >= 'a' && cp <= 'z' ? cp - 32 : cp) : UPPER.getOrDefault(cp, cp);
    }

    /** The one-to-one lower case of a code point: Java 21's {@code Character.toLowerCase(int)}. */
    public static int lower(int cp) {
        return cp < 128 ? (cp >= 'A' && cp <= 'Z' ? cp + 32 : cp) : LOWER.getOrDefault(cp, cp);
    }

    /** The upper case of a text: Java 21's {@code toUpperCase(Locale.ROOT)}, expansions included ({@code ß} is {@code SS}). */
    public static String upper(String s) {
        if (ascii(s)) {
            return asciiCase(s, true);
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            String full = UPPER_FULL.get(cp);
            if (full != null) {
                b.append(full);
            } else {
                b.appendCodePoint(upper(cp));
            }
            i += Character.charCount(cp);
        }
        return b.toString();
    }

    /** The lower case of a text: Java 21's {@code toLowerCase(Locale.ROOT)}, with {@code İ} as {@code i̇} and the final sigma. */
    public static String lower(String s) {
        if (ascii(s)) {
            return asciiCase(s, false);
        }
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            if (cp == CAPITAL_I_DOT) {
                b.append("i̇");
            } else if (cp == CAPITAL_SIGMA && finalSigma(s, i)) {
                b.appendCodePoint(FINAL_SIGMA);
            } else {
                b.appendCodePoint(lower(cp));
            }
            i += Character.charCount(cp);
        }
        return b.toString();
    }

    /**
     * The final sigma as Java 21 decides it: within the word around the sigma, a cased letter somewhere before it and none
     * after it. Java finds the word with its word-break iterator; Core's word ends at the code points that end it for Java
     * between two letters (the boundary table); a hyphen, an apostrophe, a full stop or a double quote joins two letters or
     * two digits only; a combining mark (U+0300–U+036F) sits on the character before it. {@code CoreTextTest} draws 200,000
     * texts over letters of every case kind, digits, spaces, those punctuation marks and combining marks, and Java agrees on
     * each; other punctuation next to a sigma (a colon, a middle dot, an underscore, curly quotes) may still differ.
     */
    private static boolean finalSigma(String s, int at) {
        boolean before = false;
        for (int i = at; i > 0; ) {
            int cp = s.codePointBefore(i);
            i -= Character.charCount(cp);
            if (mark(cp) && !wordChar(base(s, i))) {
                break;                                          // a mark belongs to the character before it
            }
            if (cased(cp)) {
                before = true;
                break;
            }
            if (boundary(cp) || mid(cp) && !joins(base(s, i), next(s, i + Character.charCount(cp)))) {
                break;
            }
        }
        if (!before) {
            return false;
        }
        for (int k = at + 1; k < s.length(); ) {
            int cp = s.codePointAt(k);
            k += Character.charCount(cp);
            if (cased(cp)) {
                return false;
            }
            if (boundary(cp) || mid(cp) && !joins(base(s, k - Character.charCount(cp)), next(s, k))) {
                return true;
            }
        }
        return true;
    }

    /** A letter or digit of a word: not a boundary, not mid-word punctuation, not a combining mark (U+0300–U+036F). */
    private static boolean wordChar(int cp) {
        return !boundary(cp) && !mid(cp) && !mark(cp);
    }

    private static boolean mark(int cp) {
        return cp >= 0x300 && cp <= 0x36F;
    }

    /** The character a run of marks ending before {@code i} sits on; a space (a boundary) when there is none. */
    private static int base(String s, int i) {
        int k = i;
        while (k > 0) {
            int cp = s.codePointBefore(k);
            if (!mark(cp)) {
                return cp;
            }
            k -= Character.charCount(cp);
        }
        return ' ';
    }

    /** The character at {@code k}; a space (a boundary) at the end. A mark here does not join (it sits on the punctuation). */
    private static int next(String s, int k) {
        return k < s.length() ? s.codePointAt(k) : ' ';
    }

    /** Whether mid-word punctuation between these two joins them: two letters or two digits. */
    private static boolean joins(int before, int after) {
        boolean d1 = before >= '0' && before <= '9';
        boolean d2 = after >= '0' && after <= '9';
        return wordChar(before) && wordChar(after) && d1 == d2;
    }

    /** Punctuation that stays inside a word only between two word characters ("a-b", "can't", "3.5"). */
    private static boolean mid(int cp) {
        return cp == '-' || cp == '\'' || cp == '.' || cp == '"';
    }

    private static boolean boundary(int cp) {
        return in(BOUNDARY, cp);
    }

    private static int[] ranges(String table) {
        String[] rs = table.split(";");
        int[] out = new int[rs.length * 2];
        for (int i = 0; i < rs.length; i++) {
            int dash = rs[i].indexOf('-');
            out[2 * i] = Integer.parseInt(dash < 0 ? rs[i] : rs[i].substring(0, dash), 36);
            out[2 * i + 1] = dash < 0 ? out[2 * i] : Integer.parseInt(rs[i].substring(dash + 1), 36);
        }
        return out;
    }

    private static boolean cased(int cp) {
        return in(CASED, cp);
    }

    /** Whether a code point lies in one of the ranges: binary search. */
    private static boolean in(int[] ranges, int cp) {
        int lo = 0;
        int hi = ranges.length / 2 - 1;
        while (lo <= hi) {
            int mid = (lo + hi) >>> 1;
            if (cp < ranges[2 * mid]) {
                hi = mid - 1;
            } else if (cp > ranges[2 * mid + 1]) {
                lo = mid + 1;
            } else {
                return true;
            }
        }
        return false;
    }


    private static boolean ascii(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) >= 128) {
                return false;
            }
        }
        return true;
    }

    private static String asciiCase(String s, boolean up) {
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++) {
            char ch = c[i];
            if (up ? ch >= 'a' && ch <= 'z' : ch >= 'A' && ch <= 'Z') {
                c[i] = (char) (up ? ch - 32 : ch + 32);
            }
        }
        return new String(c);
    }

    /** Java's white space ({@code Character.isWhitespace}): the ASCII controls 9–13 and 28–31, the space, and the Unicode space
     *  separators except the no-break ones, plus the line and paragraph separators. */
    public static boolean isWhitespace(int cp) {
        return cp == ' ' || cp >= 9 && cp <= 13 || cp >= 28 && cp <= 31
                || cp >= 0x80 && (cp == 0x1680 || cp >= 0x2000 && cp <= 0x2006 || cp >= 0x2008 && cp <= 0x200A
                || cp == 0x2028 || cp == 0x2029 || cp == 0x205F || cp == 0x3000);
    }

    /** {@code String.strip()}: leading and trailing white space removed. */
    public static String strip(String s) {
        return stripTrailing(stripLeading(s));
    }

    public static String stripLeading(String s) {
        int i = 0;
        while (i < s.length() && isWhitespace(s.charAt(i))) {
            i++;
        }
        return i == 0 ? s : s.substring(i);
    }

    public static String stripTrailing(String s) {
        int j = s.length();
        while (j > 0 && isWhitespace(s.charAt(j - 1))) {
            j--;
        }
        return j == s.length() ? s : s.substring(0, j);
    }

    /** The text backwards by code point: a surrogate pair stays a pair. */
    public static String reverse(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = s.length(); i > 0; ) {
            int cp = s.codePointBefore(i);
            b.appendCodePoint(cp);
            i -= Character.charCount(cp);
        }
        return b.toString();
    }
}
